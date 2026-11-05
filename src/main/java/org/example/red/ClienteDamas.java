package org.example.red;

import org.example.logica.Ficha;
import org.example.logica.Lado;
import org.example.logica.Tablero;
import org.example.presentacion.ConsolaCliente;
import org.example.presentacion.swing.TableroSwing;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ClienteDamas {
    public static final String HOST_POR_DEFECTO = "localhost";
    public static final int PUERTO_POR_DEFECTO = 5050;
    private static final int TIMEOUT_LECTURA = 1000;
    private final ConsolaCliente consola = new ConsolaCliente();
    private final List<Ficha> fichas = new ArrayList<>();
    private final List<HiloFicha> hilosFicha = new ArrayList<>();
    private final Object candadoEnvio = new Object();
    private final Object candadoTurno = new Object();
    private final Object candadoMovimiento = new Object();
    private static final long ESPERA_TURNO_MILLIS = 60_000L;
    private final CountDownLatch sesionIniciada = new CountDownLatch(1);
    private final String host;
    private final int puerto;
    private final boolean usarSwing;
    private Socket socket;
    private BufferedReader lector;
    private PrintWriter escritor;
    private volatile String usuario = "";
    private volatile String bandoPreferido = "";
    private volatile String bando = "";
    private volatile String estadoTablero = "";
    private volatile String turnoActual = "";
    private volatile String mensajeSistema = "";
    private volatile boolean terminado;
    private volatile TableroSwing ventanaSwing;

    public ClienteDamas(String host, int puerto, boolean usarSwing) {
        this.host = host;
        this.puerto = puerto;
        this.usarSwing = usarSwing;
    }

    public static void main(String[] args) {
        // Salida en UTF-8 explícito, como exige el punto 6.7 del enunciado.
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8));

        String host = HOST_POR_DEFECTO;
        int puerto = PUERTO_POR_DEFECTO;
        boolean swing = false;
        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                puerto = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("[Cliente] Puerto inválido: '" + args[1]
                        + "'. Se usará el puerto por defecto " + PUERTO_POR_DEFECTO + ".");
            }
        }
        for (String argumento : args) {
            if ("swing".equalsIgnoreCase(argumento) || "--swing".equalsIgnoreCase(argumento)) {
                swing = true;
            }
        }
        conectar(host, puerto, swing);
    }

    public static void conectar(String host, int puerto, boolean usarSwing) {
        new ClienteDamas(host, puerto, usarSwing).ejecutar();
    }

    public void ejecutar() {
        try {
            pedirIdentidad();
            abrirSocket();
            enviar(Protocolo.login(usuario, bandoPreferido));

            Thread hiloLector = new Thread(this::leerServidor, "LectorCliente");
            hiloLector.setDaemon(true);
            hiloLector.start();

            if (!sesionIniciada.await(15, TimeUnit.SECONDS)) {
                throw new IOException("El servidor no respondió al inicio de sesión.");
            }
            if (bando.isEmpty()) {
                throw new IOException("El servidor no asignó un bando.");
            }

            crearFichasYHilos();
            if (usarSwing) {
                abrirSwing();
            }
            consola.mostrarEncabezado(usuario, bando, host, puerto);
            leerConsola();
        } catch (IOException e) {
            consola.mostrarError("Error del cliente: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cerrar();
        }
    }

    private void pedirIdentidad() {
        if (usarSwing) {
            usuario = TableroSwing.pedirUsuario();
            bandoPreferido = TableroSwing.pedirBando();
        } else {
            usuario = consola.pedirUsuario();
            bandoPreferido = consola.pedirBandoPreferido();
        }
    }

    private void abrirSocket() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, puerto), 5000);
        socket.setSoTimeout(TIMEOUT_LECTURA);
        lector = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        escritor = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private void crearFichasYHilos() {
        Lado lado = Lado.valueOf(bando.toUpperCase());
        for (Ficha ficha : Tablero.fichasIniciales(lado)) {
            fichas.add(ficha);
            HiloFicha hilo = new HiloFicha(ficha, this::enviarMovimiento);
            hilosFicha.add(hilo);
            hilo.iniciar();
        }
        consola.mostrarMensaje("Se crearon " + hilosFicha.size() + " hilos de ficha (uno por cada ficha).");
    }

    private void sincronizarFichas(String estado) {
        if (bando.isEmpty() || estado.length() < Tablero.TAMANIO * Tablero.TAMANIO) {
            return;
        }
        Lado propio = Lado.valueOf(bando.toUpperCase());

        List<String> enTablero = new ArrayList<>();
        for (int fila = 0; fila < Tablero.TAMANIO; fila++) {
            for (int columna = 0; columna < Tablero.TAMANIO; columna++) {
                if (Tablero.ladoDe(estado.charAt(fila * Tablero.TAMANIO + columna)) == propio) {
                    enTablero.add(Tablero.coordenada(columna, fila));
                }
            }
        }

        // Las fichas que siguen en su casilla no se mueven.
        List<String> libres = new ArrayList<>(enTablero);
        List<HiloFicha> desplazadas = new ArrayList<>();
        for (HiloFicha hilo : hilosFicha) {
            if (libres.remove(hilo.getFicha().getCoordenada())) {
                continue;
            }
            desplazadas.add(hilo);
        }

        // Las fichas que sobran fueron capturadas por el rival.
        while (desplazadas.size() > libres.size()) {
            HiloFicha capturada = desplazadas.remove(0);
            hilosFicha.remove(capturada);
            capturada.interrumpir();
        }

        // Las que se movieron reciben la casilla nueva que ocupa su ficha.
        for (int i = 0; i < desplazadas.size(); i++) {
            String coordenada = libres.get(i);
            desplazadas.get(i).getFicha().setPosicion(
                    Tablero.columnaDe(coordenada), Tablero.filaDe(coordenada));
        }
    }

    private void abrirSwing() {
        SwingUtilities.invokeLater(() -> {
            ventanaSwing = new TableroSwing("Juego de Damas - " + usuario, bando, this::solicitarMovimiento);
            ventanaSwing.setAlCerrar(() -> terminado = true);
            ventanaSwing.setVisible(true);
            actualizarVista();
        });
    }

    private void leerServidor() {
        try {
            while (!terminado) {
                String linea;
                try {
                    linea = lector.readLine();
                } catch (SocketTimeoutException e) {
                    continue;
                }
                if (linea == null) {
                    break;
                }
                manejarMensaje(linea);
            }
        } catch (IOException e) {
            if (!terminado) {
                consola.mostrarError("Se perdió la conexión con el servidor: " + e.getMessage());
            }
        } finally {
            terminado = true;
            sesionIniciada.countDown();
        }
    }

    private void manejarMensaje(String mensaje) {
        String tipo = Protocolo.tipoDe(mensaje);
        String[] campos = Protocolo.camposDe(mensaje);
        String valor = campos.length > 0 ? campos[0].trim() : "";

        switch (tipo) {
            case Protocolo.WELCOME -> {
                bando = valor;
                consola.mostrarMensaje("Bando asignado: " + bando);
                sesionIniciada.countDown();
            }
            case Protocolo.STATE -> {
                estadoTablero = valor;
                sincronizarFichas(valor);
                consola.mostrarTablero(valor);
            }
            case Protocolo.TURN -> {
                synchronized (candadoTurno) {
                    turnoActual = valor;
                    candadoTurno.notifyAll();
                }
                mensajeSistema = "Turno de " + turnoActual;
                consola.mostrarTurno(turnoActual, bando);
            }
            case Protocolo.OK -> mensajeSistema = "Jugada aceptada";
            case Protocolo.CAPTURE -> {
                mensajeSistema = "Captura realizada";
                consola.mostrarMensaje("¡Captura!");
            }
            case Protocolo.KING -> {
                mensajeSistema = "Su ficha fue coronada a dama";
                consola.mostrarMensaje("¡Coronada a dama!");
            }
            case Protocolo.TIMEOUT -> {
                mensajeSistema = "Se agotó su tiempo de turno";
                consola.mostrarAviso("Se agotó el tiempo de turno; el turno pasó al rival.");
            }
            case Protocolo.ERR -> {
                mensajeSistema = "Error: " + valor;
                consola.mostrarError(valor);
            }
            case Protocolo.WIN -> {
                mensajeSistema = "Ganó el bando " + valor;
                consola.mostrarMensaje(mensajeSistema);
            }
            case Protocolo.DRAW -> {
                mensajeSistema = "La partida terminó en empate";
                consola.mostrarMensaje(mensajeSistema);
            }
            case Protocolo.RESULT -> {
                String resultadoIndividual = campos.length > 0 ? campos[0] : "";
                int puntos = campos.length > 1 ? Integer.parseInt(campos[1]) : 0;
                mensajeSistema = resultadoIndividual + " - " + puntos + " punto(s)";
                consola.mostrarResultado(resultadoIndividual, puntos);
                terminado = true;
            }
            default -> consola.mostrarAviso("Mensaje desconocido del servidor: " + mensaje);
        }
        actualizarVista();
    }

    private void actualizarVista() {
        TableroSwing ventana = ventanaSwing;
        if (ventana == null) {
            return;
        }
        if (!estadoTablero.isEmpty()) {
            ventana.actualizarTablero(estadoTablero);
        }
        ventana.setTurno(turnoActual);
        ventana.setJugador(usuario, bando);
        ventana.setMensaje(mensajeSistema);
        if (terminado) {
            ventana.finalizar(mensajeSistema);
        }
    }

    private void leerConsola() {
        Scanner scanner = consola.getScanner();
        while (!terminado) {
            if (!scanner.hasNextLine()) {
                break;
            }
            String linea = scanner.nextLine().trim();
            if (linea.isEmpty()) {
                continue;
            }
            if ("salir".equalsIgnoreCase(linea) || "q".equalsIgnoreCase(linea)) {
                break;
            }
            String[] jugada = interpretarJugada(linea);
            if (jugada != null) {
                solicitarMovimiento(jugada[0], jugada[1]);
            }
        }
    }

    private String[] interpretarJugada(String linea) {
        String[] partes = linea.split("[\\s,;\\-]+");
        if (partes.length == 0 || !Tablero.coordenadaValida(partes[0])) {
            consola.mostrarAviso("Use el formato c3 d4 (por ejemplo: b6 c5).");
            return null;
        }
        if (partes.length >= 2) {
            if (!Tablero.coordenadaValida(partes[1])) {
                consola.mostrarAviso("Coordenada de destino inválida: " + partes[1]);
                return null;
            }
            return new String[]{partes[0], partes[1]};
        }
        String destino = consola.pedirLinea("Destino (por ejemplo d4): ");
        if (destino == null || destino.isBlank() || !Tablero.coordenadaValida(destino.trim())) {
            return null;
        }
        return new String[]{partes[0], destino.trim()};
    }

    public void solicitarMovimiento(String origen, String destino) {
        for (HiloFicha hilo : hilosFicha) {
            if (hilo.getFicha().getCoordenada().equalsIgnoreCase(origen.trim())) {
                hilo.solicitarMovimiento(destino.trim());
                return;
            }
        }
        consola.mostrarError("No tiene ninguna ficha en " + origen + ".");
    }

    private void enviarMovimiento(String origen, String destino) {
        if (terminado) {
            return;
        }
        // Los 12 hilos de ficha se serializan: solo puede haber una jugada en
        // vuelo, de lo contrario el servidor recibiría jugadas fuera de orden.
        synchronized (candadoMovimiento) {
            if (terminado) {
                return;
            }
            // Si la jugada se escribe antes de que llegue el turno, el hilo de
            // la ficha espera a que le toque en lugar de perder la jugada.
            synchronized (candadoTurno) {
                long limite = System.currentTimeMillis() + ESPERA_TURNO_MILLIS;
                while (!terminado && !turnoActual.equalsIgnoreCase(bando)
                        && System.currentTimeMillis() < limite) {
                    try {
                        candadoTurno.wait(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (terminado) {
                return;
            }
            if (!turnoActual.equalsIgnoreCase(bando)) {
                consola.mostrarAviso("No es su turno: ahora juega " + turnoActual + ".");
                return;
            }
            enviar(Protocolo.movimiento(origen, destino));
        }
    }

    private void enviar(String mensaje) {
        synchronized (candadoEnvio) {
            if (escritor != null) {
                escritor.print(mensaje);
                escritor.print(Protocolo.FIN_LINEA);
                escritor.flush();
            }
        }
    }

    private void cerrar() {
        try {
            if (socket != null && !socket.isClosed()) {
                enviar(Protocolo.BYE);
                socket.close();
            }
        } catch (IOException e) {
            // El socket ya estaba cerrado.
        }
        TableroSwing ventana = ventanaSwing;
        if (ventana != null) {
            SwingUtilities.invokeLater(ventana::cerrar);
        }
    }

    public String getBando() {
        return bando;
    }
    public String getUsuario() {
        return usuario;
    }
}