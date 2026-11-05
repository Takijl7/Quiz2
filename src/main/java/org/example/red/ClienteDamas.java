
package org.example.red;

import org.example.logica.Ficha;
import org.example.logica.Lado;
import org.example.logica.Tablero;
import org.example.presentacion.ConsolaCliente;

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
import java.util.Locale;
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

    private final CountDownLatch sesionIniciada =
            new CountDownLatch(1);

    private final String host;
    private final int puerto;

    private Socket socket;
    private BufferedReader lector;
    private PrintWriter escritor;

    private String usuario = "";
    private String bandoPreferido = "";

    private volatile String bando = "";
    private volatile String estadoTablero = "";
    private volatile String turnoActual = "";

    private volatile boolean terminado = false;

    public ClienteDamas(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    public static void main(String[] args) {

        String host = HOST_POR_DEFECTO;
        int puerto = PUERTO_POR_DEFECTO;

        if (args.length > 0) {
            host = args[0];
        }

        if (args.length > 1) {
            try {
                puerto = Integer.parseInt(args[1]);

                if (puerto < 1 || puerto > 65535) {
                    throw new NumberFormatException();
                }

            } catch (NumberFormatException e) {
                System.err.println(
                        "Puerto invalido: " + args[1]);
                return;
            }
        }

        new ClienteDamas(host, puerto).ejecutar();
    }

    public void ejecutar() {

        try {
            pedirIdentidad();

            if (usuario.isBlank() || bandoPreferido.isBlank()) {
                return;
            }

            abrirSocket();

            enviar(Protocolo.login(usuario, bandoPreferido));

            Thread hiloLector = new Thread(
                    this::leerServidor,
                    "LectorCliente"
            );

            hiloLector.setDaemon(true);
            hiloLector.start();

            if (!sesionIniciada.await(15, TimeUnit.SECONDS)) {
                throw new IOException(
                        "El servidor no respondio al LOGIN.");
            }

            if (bando.isEmpty()) {
                throw new IOException(
                        "El servidor no asigno un bando.");
            }

            crearFichasYHilos();

            consola.mostrarEncabezado(
                    usuario, bando, host, puerto);

            leerConsola();

        } catch (IOException e) {
            consola.mostrarError(e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } finally {
            cerrar();
        }
    }

    private void pedirIdentidad() {
        usuario = consola.pedirUsuario();
        bandoPreferido = consola.pedirBandoPreferido();
    }

    private void abrirSocket() throws IOException {

        socket = new Socket();

        socket.connect(
                new InetSocketAddress(host, puerto),
                5000
        );

        socket.setSoTimeout(TIMEOUT_LECTURA);

        lector = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        escritor = new PrintWriter(
                new OutputStreamWriter(
                        socket.getOutputStream(),
                        StandardCharsets.UTF_8
                ),
                true
        );
    }

    // Crea exactamente doce hilos, uno por ficha.
    private void crearFichasYHilos() {

        Lado lado = Lado.valueOf(bando.toUpperCase(
                Locale.ROOT));

        for (Ficha ficha : Tablero.fichasIniciales(lado)) {

            fichas.add(ficha);

            HiloFicha hilo = new HiloFicha(
                    ficha,
                    this::enviarMovimiento
            );

            hilosFicha.add(hilo);
            hilo.iniciar();
        }

        consola.mostrarMensaje(
                "Se crearon " + hilosFicha.size()
                        + " hilos de ficha.");
    }

    // Recibe mensajes del servidor en un hilo separado.
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
                consola.mostrarError(
                        "Conexion perdida: " + e.getMessage());
            }

        } finally {
            terminado = true;
            sesionIniciada.countDown();

            synchronized (candadoTurno) {
                candadoTurno.notifyAll();
            }
        }
    }

    private void manejarMensaje(String mensaje) {

        Protocolo.Mensaje recibido =
                Protocolo.parsear(mensaje);

        String tipo = recibido.comando();
        String valor = recibido.argumentos();

        switch (tipo) {

            case Protocolo.WELCOME -> {
                bando = valor.trim();

                consola.mostrarMensaje(
                        "Bando asignado: " + bando);

                sesionIniciada.countDown();
            }

            case Protocolo.STATE -> {

                if (valor.length() != 64) {
                    consola.mostrarAviso(
                            "Estado del tablero invalido.");
                    return;
                }

                estadoTablero = valor;

                sincronizarFichas(valor);
                consola.mostrarTablero(valor);
            }

            case Protocolo.TURN -> {

                synchronized (candadoTurno) {
                    turnoActual = valor.trim();
                    candadoTurno.notifyAll();
                }

                consola.mostrarTurno(
                        turnoActual, bando);
            }

            case Protocolo.OK ->
                    consola.mostrarMensaje(
                            "Jugada aceptada.");

            case Protocolo.ERR ->
                    consola.mostrarError(valor);

            case Protocolo.CAPTURE ->
                    consola.mostrarMensaje(
                            "Captura realizada.");

            case Protocolo.KING ->
                    consola.mostrarMensaje(
                            "Ficha coronada a dama.");

            case Protocolo.TIMEOUT ->
                    consola.mostrarAviso(
                            "Tiempo agotado. Turno cedido.");

            case Protocolo.WIN ->
                    consola.mostrarMensaje(
                            "Gano el bando " + valor);

            case Protocolo.DRAW ->
                    consola.mostrarMensaje(
                            "La partida termino en empate.");

            case Protocolo.RESULT -> {

                String[] campos = Protocolo.campos(valor);

                String resultado =
                        campos.length > 0 ? campos[0] : "";

                int puntos = 0;

                if (campos.length > 1) {
                    try {
                        puntos = Integer.parseInt(campos[1]);
                    } catch (NumberFormatException e) {
                        consola.mostrarAviso(
                                "Puntaje recibido invalido.");
                    }
                }

                consola.mostrarResultado(resultado, puntos);
                terminado = true;

                synchronized (candadoTurno) {
                    candadoTurno.notifyAll();
                }
            }

            default ->
                    consola.mostrarAviso(
                            "Mensaje desconocido: " + mensaje);
        }
    }

    // Actualiza posiciones a partir del estado autorizado.
    private void sincronizarFichas(String estado) {

        if (bando.isBlank() || hilosFicha.isEmpty()) {
            return;
        }

        Lado propio = Lado.valueOf(bando);

        List<String> posiciones = new ArrayList<>();

        for (int fila = 0; fila < Tablero.TAMANIO; fila++) {
            for (int columna = 0;
                 columna < Tablero.TAMANIO;
                 columna++) {

                char simbolo = estado.charAt(
                        fila * Tablero.TAMANIO + columna);

                if (Tablero.ladoDe(simbolo) == propio) {
                    posiciones.add(
                            Tablero.coordenada(columna, fila));
                }
            }
        }

        synchronized (hilosFicha) {

            List<String> libres =
                    new ArrayList<>(posiciones);

            List<HiloFicha> desplazadas =
                    new ArrayList<>();

            for (HiloFicha hilo : hilosFicha) {
                if (!libres.remove(
                        hilo.getFicha().getCoordenada())) {
                    desplazadas.add(hilo);
                }
            }

            while (desplazadas.size() > libres.size()) {

                HiloFicha capturada =
                        desplazadas.remove(0);

                hilosFicha.remove(capturada);
                capturada.interrumpir();
            }

            for (int i = 0;
                 i < desplazadas.size() && i < libres.size();
                 i++) {

                String casilla = libres.get(i);

                desplazadas.get(i).getFicha().setPosicion(
                        Tablero.columnaDe(casilla),
                        Tablero.filaDe(casilla)
                );
            }
        }
    }

    // Lee los movimientos ingresados por teclado.
    private void leerConsola() {

        Scanner scanner = consola.getScanner();

        while (!terminado && scanner.hasNextLine()) {

            String linea = scanner.nextLine().trim();

            if (linea.equalsIgnoreCase("salir")
                    || linea.equalsIgnoreCase("q")) {
                break;
            }

            if (linea.isBlank()) {
                continue;
            }

            String[] partes =
                    linea.split("[\\s,;\\-]+");

            if (partes.length != 2) {

                consola.mostrarAviso(
                        "Formato: origen destino. Ejemplo: b6 c5");
                continue;
            }

            solicitarMovimiento(
                    partes[0], partes[1]);
        }
    }

    public void solicitarMovimiento(
            String origen, String destino) {

        origen = origen.trim().toLowerCase(Locale.ROOT);
        destino = destino.trim().toLowerCase(Locale.ROOT);

        if (!Tablero.coordenadaValida(origen)
                || !Tablero.coordenadaValida(destino)) {

            consola.mostrarAviso(
                    "Coordenadas invalidas.");
            return;
        }

        synchronized (hilosFicha) {

            for (HiloFicha hilo : hilosFicha) {

                if (hilo.getFicha().getCoordenada()
                        .equalsIgnoreCase(origen)) {

                    hilo.solicitarMovimiento(destino);
                    return;
                }
            }
        }

        consola.mostrarError(
                "No tiene una ficha en " + origen);
    }

    // El movimiento sale desde el hilo de la ficha.
    private void enviarMovimiento(
            String origen, String destino) {

        synchronized (candadoTurno) {

            if (terminado) {
                return;
            }

            if (!turnoActual.equalsIgnoreCase(bando)) {

                consola.mostrarAviso(
                        "No es su turno.");
                return;
            }

            enviar(Protocolo.mov(origen, destino));
        }
    }

    private void enviar(String mensaje) {

        synchronized (candadoEnvio) {

            if (escritor != null && !terminado) {
                escritor.println(mensaje);
                escritor.flush();
            }
        }
    }

    private void cerrar() {

        if (socket != null && !socket.isClosed()) {
            enviar(Protocolo.BYE);
        }

        terminado = true;

        synchronized (hilosFicha) {
            for (HiloFicha hilo : hilosFicha) {
                hilo.interrumpir();
            }
        }

        synchronized (candadoTurno) {
            candadoTurno.notifyAll();
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                consola.mostrarAviso(
                        "Error al cerrar: " + e.getMessage());
            }
        }
    }

    public String getBando() {
        return bando;
    }

    public String getUsuario() {
        return usuario;
    }
}
