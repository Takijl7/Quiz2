package org.example.red;

import org.example.datos.ConexionMySQL;
import org.example.datos.FilaRanking;
import org.example.datos.Jugador;
import org.example.datos.JugadorDAO;
import org.example.datos.PartidaDAO;
import org.example.datos.RegistroPartida;
import org.example.logica.GestorPartida;
import org.example.logica.Lado;
import org.example.logica.ResultadoPartida;
import org.example.presentacion.ConsolaServidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ServidorDamas {
    public static final int PUERTO_POR_DEFECTO = 5050;
    private final int puerto;
    private final GestorPartida gestor = new GestorPartida();
    private final ConsolaServidor consola = new ConsolaServidor();
    private final List<HiloCliente> clientes = new ArrayList<>();
    private final Set<Lado> bandosAsignados = new HashSet<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch finPartida = new CountDownLatch(1);
    private ServerSocket serverSocket;
    private ScheduledFuture<?> alarma;
    private long versionTurno;
    private volatile boolean cerrado;
    private boolean partidaIniciada;
    private boolean partidaCerrada;
    private boolean baseDeDatosDisponible;
    private LocalDateTime inicioPartida;

    public ServidorDamas(int puerto) { this.puerto = puerto; }

    public static void main(String[] args) {
        int puerto = PUERTO_POR_DEFECTO;
        if (args.length > 0) {
            try {
                puerto = Integer.parseInt(args[0]);
                if (puerto < 1 || puerto > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido: " + args[0]);
                return;
            }
        }
        iniciar(puerto);
    }

    public static void iniciar(int puerto) { new ServidorDamas(puerto).ejecutar(); }

    public void ejecutar() {
        try {
            prepararBaseDeDatos();
            try (ServerSocket servidor = new ServerSocket(puerto)) {
                serverSocket = servidor;
                consola.mostrarEncabezado(puerto);
                int aceptados = 0;
                while (!cerrado && aceptados < 2) {
                    Socket conexion = servidor.accept();
                    aceptados++;
                    executor.submit(new HiloCliente(conexion, this));
                }
                finPartida.await();
            }
        } catch (SocketException e) {
            if (!cerrado) consola.mostrarError("Socket: " + e.getMessage());
        } catch (IOException e) {
            consola.mostrarError("Error de red: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cerrar();
        }
    }

    private void prepararBaseDeDatos() {
        try {
            ConexionMySQL.asegurarEsquema();
            baseDeDatosDisponible = true;
            consola.mostrarMensaje("MySQL disponible.");
        } catch (SQLException e) {
            baseDeDatosDisponible = false;
            consola.mostrarAviso("Sin MySQL: " + e.getMessage());
        }
    }

    public synchronized Lado asignarBando(Lado preferido) {
        if (partidaIniciada || partidaCerrada || bandosAsignados.size() >= 2) return null;
        Lado elegido = !bandosAsignados.contains(preferido) ? preferido
                : (preferido == Lado.BLANCO ? Lado.NEGRO : Lado.BLANCO);
        if (elegido == null || bandosAsignados.contains(elegido)) return null;
        bandosAsignados.add(elegido);
        return elegido;
    }

    public void registrarJugador(HiloCliente cliente) {
        if (!baseDeDatosDisponible) return;
        try {
            Jugador jugador = new JugadorDAO().obtenerOCrear(cliente.getUsuario());
            cliente.setIdJugador(jugador.id());
            cliente.setPuntosAcumulados(jugador.puntosTotales());
            consola.registrarEnBitacora("Jugador " + cliente.getUsuario() + " registrado.");
        } catch (SQLException e) {
            baseDeDatosDisponible = false;
            consola.mostrarAviso("No se registró jugador: " + e.getMessage());
        }
    }

    public synchronized void registrarCliente(HiloCliente cliente) {
        // Evita que dos participantes jueguen con el mismo usuario.
        for (HiloCliente otro : clientes) {
            if (otro.getUsuario().equalsIgnoreCase(cliente.getUsuario())) {
                cliente.enviar(Protocolo.err("El usuario ya está conectado"));
                cliente.cerrarForzado();
                bandosAsignados.remove(cliente.getLado());
                return;
            }
        }
        clientes.add(cliente);
        cliente.setProcesadorMovimiento((origen, destino) -> procesarMovimiento(cliente, origen, destino));
        cliente.setAlDesconectar(() -> desconectarCliente(cliente));
        consola.registrarEnBitacora(cliente.getUsuario() + " conectado: " + cliente.getLado());
        if (clientes.size() == 2 && !partidaIniciada) iniciarPartida();
    }

    private synchronized void iniciarPartida() {
        partidaIniciada = true;
        inicioPartida = LocalDateTime.now();
        gestor.iniciarPartida(Lado.BLANCO);
        consola.registrarEnBitacora("Partida iniciada.");
        broadcast(Protocolo.estado(gestor.estadoTablero()));
        broadcast(Protocolo.turn(gestor.getTurnoActual()));
        mostrarEstadoEnConsola();
        iniciarTemporizador();
    }

    public synchronized void procesarMovimiento(HiloCliente cliente, String origen, String destino) {
        if (!partidaIniciada || partidaCerrada || !clientes.contains(cliente)) {
            cliente.enviar(Protocolo.err("La partida no está activa"));
            return;
        }
        GestorPartida.RespuestaMovimiento respuesta =
                gestor.procesarMovimiento(cliente.getLado(), origen, destino);
        if (!respuesta.aceptado()) {
            cliente.enviar(Protocolo.err(respuesta.motivo()));
            return;
        }
        cancelarTemporizador();
        cliente.enviar(Protocolo.OK);
        if (respuesta.captura()) cliente.enviar(Protocolo.CAPTURE);
        if (respuesta.coronacion()) cliente.enviar(Protocolo.KING);
        consola.registrarEnBitacora(cliente.getUsuario() + ": " + origen + " -> " + destino);
        broadcast(Protocolo.estado(gestor.estadoTablero()));
        mostrarEstadoEnConsola();
        if (respuesta.resultado() != null) {
            cerrarPartida(respuesta.resultado());
        } else {
            broadcast(Protocolo.turn(gestor.getTurnoActual()));
            iniciarTemporizador();
        }
    }

    private synchronized void desconectarCliente(HiloCliente cliente) {
        if (cerrado || partidaCerrada) return;
        consola.registrarEnBitacora("Desconectado: " + cliente.getUsuario());
        if (!partidaIniciada) {
            clientes.remove(cliente);
            bandosAsignados.remove(cliente.getLado());
            // Como ya se aceptó un socket, esta partida no admitirá otro ingreso.
            // Liberamos la espera si ya no queda nadie en la partida.
            if (clientes.isEmpty()) finPartida.countDown();
            return;
        }
        gestor.jugadorDesconectado(cliente.getLado());
        cerrarPartida(gestor.getResultado());
    }

    public synchronized void iniciarTemporizador() {
        cancelarTemporizador();
        Lado lado = gestor.getTurnoActual();
        long version = versionTurno;
        alarma = reloj.schedule(() -> {
            synchronized (ServidorDamas.this) {
                if (partidaCerrada || version != versionTurno) return;
                if (!gestor.expirarTurno(lado)) return;
                broadcast(Protocolo.TIMEOUT);
                consola.registrarEnBitacora("Tiempo agotado: " + lado);
                if (gestor.estaTerminada()) cerrarPartida(gestor.getResultado());
                else {
                    broadcast(Protocolo.turn(gestor.getTurnoActual()));
                    iniciarTemporizador();
                }
            }
        }, GestorPartida.TIEMPO_TURNO_CONFIGURABLE, TimeUnit.MILLISECONDS);
    }

    public synchronized void cancelarTemporizador() {
        versionTurno++;
        if (alarma != null) {
            alarma.cancel(false);
            alarma = null;
        }
    }

    public void mostrarEstadoEnConsola() {
        consola.mostrarTablero(gestor.estadoTablero());
        consola.mostrarTurno(String.valueOf(gestor.getTurnoActual()));
    }

    public synchronized void broadcast(String mensaje) {
        for (HiloCliente cliente : new ArrayList<>(clientes)) cliente.enviar(mensaje);
    }

    public synchronized void cerrarPartida(ResultadoPartida resultado) {
        if (partidaCerrada) return;
        partidaCerrada = true;
        cancelarTemporizador();
        if (resultado == null) {
            consola.mostrarAviso("Partida finalizada sin resultado.");
            finPartida.countDown();
            return;
        }
        switch (resultado) {
            case EMPATE -> broadcast(Protocolo.DRAW);
            case VICTORIA_BLANCO -> broadcast(Protocolo.win(Lado.BLANCO));
            case VICTORIA_NEGRO -> broadcast(Protocolo.win(Lado.NEGRO));
        }
        String estado = gestor.estadoTablero();
        int piezasBlanco = 0, piezasNegro = 0;
        for (char c : estado.toCharArray()) {
            if (c == 'B' || c == 'R') piezasBlanco++;
            if (c == 'N' || c == 'r') piezasNegro++;
        }
        HiloCliente blanco = null, negro = null;
        for (HiloCliente cliente : clientes) {
            if (cliente.getLado() == Lado.BLANCO) blanco = cliente;
            if (cliente.getLado() == Lado.NEGRO) negro = cliente;
        }
        if (blanco != null && negro != null) {
            int puntosBlanco = resultado == ResultadoPartida.VICTORIA_BLANCO ? 5 : 0;
            int puntosNegro = resultado == ResultadoPartida.VICTORIA_NEGRO ? 5 : 0;
            String resBlanco = resultado == ResultadoPartida.EMPATE ? "EMPATE" : puntosBlanco == 5 ? "VICTORIA" : "DERROTA";
            String resNegro = resultado == ResultadoPartida.EMPATE ? "EMPATE" : puntosNegro == 5 ? "VICTORIA" : "DERROTA";
            if (baseDeDatosDisponible && blanco.getIdJugador() > 0 && negro.getIdJugador() > 0) {
                try {
                    RegistroPartida registro = new RegistroPartida(inicioPartida, LocalDateTime.now(),
                            resultado.name(), blanco.getIdJugador(), negro.getIdJugador(),
                            puntosBlanco, puntosNegro, resBlanco, resNegro, piezasBlanco, piezasNegro);
                    int id = new PartidaDAO().registrarPartida(registro);
                    consola.registrarEnBitacora("Partida guardada: #" + id);
                    mostrarRanking();
                } catch (SQLException e) {
                    consola.mostrarError("No se pudo guardar la partida: " + e.getMessage());
                }
            }
            blanco.enviar(Protocolo.result(resBlanco, puntosBlanco));
            negro.enviar(Protocolo.result(resNegro, puntosNegro));
        }
        consola.registrarEnBitacora("Resultado final: " + resultado);
        finPartida.countDown();
    }

    private void mostrarRanking() {
        try {
            for (FilaRanking fila : new JugadorDAO().ranking())
                consola.mostrarMensaje(fila.usuario() + ": " + fila.puntosTotales() + " puntos");
        } catch (SQLException e) {
            consola.mostrarError("Error en ranking: " + e.getMessage());
        }
    }

    public synchronized void cerrar() {
        if (cerrado) return;
        cerrado = true;
        cancelarTemporizador();
        for (HiloCliente cliente : clientes) cliente.cerrarForzado();
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException e) {
                consola.mostrarAviso("Error al cerrar socket: " + e.getMessage());
            }
        }
        executor.shutdown();
        reloj.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            if (!reloj.awaitTermination(5, TimeUnit.SECONDS)) reloj.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            reloj.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ConexionMySQL.cerrarPool();
        consola.mostrarMensaje("Servidor finalizado.");
    }
}
