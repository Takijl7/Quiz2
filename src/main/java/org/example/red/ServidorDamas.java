package org.example.red;

import org.example.datos.ConexionMySQL;
import org.example.datos.JugadorDAO;
import org.example.datos.PartidaDAO;
import org.example.logica.GestorPartida;
import org.example.logica.Lado;
import org.example.logica.ResultadoPartida;
import org.example.presentacion.ConsolaServidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
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
    private static final int MAX_JUGADORES = 2;
    private final int puerto;
    private final GestorPartida gestor = new GestorPartida();
    private final ConsolaServidor consola = new ConsolaServidor();
    private final List<HiloCliente> clientes = new ArrayList<>();
    private final Set<Lado> bandosAsignados = new HashSet<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_JUGADORES);
    private final ScheduledExecutorService programador = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch finPartida = new CountDownLatch(1);
    private ServerSocket serverSocket;
    private ScheduledFuture<?> temporizador;
    private volatile boolean cerrado;
    private boolean partidaIniciada;
    private boolean partidaCerrada;
    private LocalDateTime inicioPartida;
    private boolean baseDeDatosDisponible;

    public ServidorDamas(int puerto) {
        this.puerto = puerto;
    }

    public static void main(String[] args) {
        // Salida en UTF-8 explícito, como exige el punto 6.7 del enunciado.
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));

        int puerto = PUERTO_POR_DEFECTO;
        if (args.length > 0) {
            try {
                puerto = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[Servidor] Puerto inválido: '" + args[0]
                        + "'. Se usará el puerto por defecto " + PUERTO_POR_DEFECTO + ".");
            }
        }
        iniciar(puerto);
    }

    public static void iniciar(int puerto) {
        new ServidorDamas(puerto).ejecutar();
    }

    public void ejecutar() {
        try {
            prepararBaseDeDatos();
            try (ServerSocket socketServidor = new ServerSocket(puerto)) {
                this.serverSocket = socketServidor;
                consola.mostrarEncabezado(puerto);
                int recibidas = 0;
                while (recibidas < MAX_JUGADORES && !cerrado) {
                    Socket conexion = socketServidor.accept();
                    recibidas++;
                    executor.submit(new HiloCliente(conexion, this));
                }
                finPartida.await();
            }
        } catch (IOException e) {
            consola.mostrarError("Error de entrada/salida en el servidor: " + e.getMessage());
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
            consola.mostrarMensaje("MySQL listo en " + ConexionMySQL.HOST + ":" + ConexionMySQL.PUERTO
                    + " (base '" + ConexionMySQL.BASE_DATOS + "').");
        } catch (SQLException e) {
            baseDeDatosDisponible = false;
            consola.mostrarAviso("MySQL no está disponible (" + e.getMessage()
                    + "). La partida se puede jugar, pero el resultado no se guardará.");
        }
    }

    public synchronized Lado asignarBando(Lado preferido) {
        Lado asignado = null;
        if (preferido != null && !bandosAsignados.contains(preferido)) {
            asignado = preferido;
        } else if (!bandosAsignados.contains(Lado.NEGRO)) {
            asignado = Lado.NEGRO;
        } else if (!bandosAsignados.contains(Lado.BLANCO)) {
            asignado = Lado.BLANCO;
        }
        if (asignado != null) {
            bandosAsignados.add(asignado);
        }
        return asignado;
    }

    public void registrarJugador(HiloCliente cliente) {
        if (!baseDeDatosDisponible) {
            return;
        }
        Connection conexion = null;
        try {
            conexion = ConexionMySQL.obtenerConexion();
            Jugador jugador = new JugadorDAO(conexion).registrarSiNoExiste(cliente.getUsuario());
            cliente.setIdJugador(jugador.id());
            cliente.setPuntosAcumulados(jugador.puntosTotales());
            consola.registrarEnBitacora("Jugador " + cliente.getUsuario() + " registrado con "
                    + jugador.puntosTotales() + " punto(s) acumulados.");
        } catch (SQLException e) {
            baseDeDatosDisponible = false;
            consola.mostrarAviso("No se pudo registrar al jugador en MySQL: " + e.getMessage());
        } finally {
            if (conexion != null) {
                ConexionMySQL.devolverConexion(conexion);
            }
        }
    }

    public synchronized void registrarCliente(HiloCliente cliente) {
        clientes.add(cliente);
        if (clientes.size() == MAX_JUGADORES && !partidaIniciada) {
            iniciarPartida();
        }
    }

    private void iniciarPartida() {
        partidaIniciada = true;
        Lado primerBando = Lado.BLANCO;
        gestor.iniciarPartida(primerBando);
        inicioPartida = LocalDateTime.now();
        consola.registrarEnBitacora("Partida iniciada. Juega primero " + primerBando + ".");
        broadcast(Protocolo.estado(gestor.estadoTablero()));
        broadcast(Protocolo.mensaje(Protocolo.TURN, primerBando.name()));
        mostrarEstadoEnConsola();
        iniciarTemporizador();
    }

    public void mostrarEstadoEnConsola() {
        consola.mostrarTablero(gestor.estadoTablero());
        consola.mostrarTurno(String.valueOf(gestor.getTurnoActual()));
    }

    public void broadcast(String mensaje) {
        List<HiloCliente> copia;
        synchronized (this) {
            copia = new ArrayList<>(clientes);
        }
        for (HiloCliente cliente : copia) {
            cliente.enviar(mensaje);
        }
    }

    public void iniciarTemporizador() {
        cancelarTemporizador();
        Lado lado = gestor.getTurnoActual();
        temporizador = programador.schedule(() -> {
            if (!gestor.expirarTurno(lado)) {
                return;
            }
            broadcast(Protocolo.TIMEOUT);
            consola.registrarEnBitacora("Se agotó el tiempo de " + lado + "; el turno pasó al rival.");
            if (gestor.estaTerminada()) {
                cerrarPartida(gestor.getResultado());
            } else {
                broadcast(Protocolo.mensaje(Protocolo.TURN, gestor.getTurnoActual().name()));
                iniciarTemporizador();
            }
        }, GestorPartida.TIEMPO_TURNO_CONFIGURABLE, TimeUnit.MILLISECONDS);
    }

    public void cancelarTemporizador() {
        if (temporizador != null) {
            temporizador.cancel(false);
            temporizador = null;
        }
    }

    public synchronized void cerrarPartida(ResultadoPartida resultado) {
        if (resultado == null || partidaCerrada) {
            return;
        }
        partidaCerrada = true;
        for (HiloCliente cliente : clientes) {
            cliente.calcularResultado(resultado);
        }
        persistirPartida(resultado);
        if (resultado == ResultadoPartida.EMPATE) {
            broadcast(Protocolo.DRAW);
        } else {
            broadcast(Protocolo.mensaje(Protocolo.WIN, resultado.ganador().name()));
        }
        for (HiloCliente cliente : clientes) {
            cliente.enviar(Protocolo.resultado(cliente.getResultadoIndividual(), cliente.getPuntosObtenidos()));
        }
        consola.mostrarResultado(resultado);
        mostrarRanking();
        finPartida.countDown();
    }

    private void persistirPartida(ResultadoPartida resultado) {
        if (!baseDeDatosDisponible) {
            consola.mostrarAviso("La partida no se registró en MySQL porque la base de datos no está disponible.");
            return;
        }
        Connection conexion = null;
        try {
            conexion = ConexionMySQL.obtenerConexion();
            conexion.setAutoCommit(false);
            PartidaDAO partidaDAO = new PartidaDAO(conexion);
            int idBlanco = idDelBando(Lado.BLANCO);
            int idNegro = idDelBando(Lado.NEGRO);
            int idPartida = partidaDAO.registrarPartida(inicioPartida, LocalDateTime.now(),
                    resultado, idBlanco, idNegro);
            for (HiloCliente cliente : clientes) {
                partidaDAO.registrarParticipacion(idPartida, cliente.getIdJugador(), cliente.getBando(),
                        cliente.getPuntosObtenidos(), cliente.getResultadoIndividual(),
                        gestor.piezasRestantes(cliente.getBando()));
                partidaDAO.actualizarPuntosAcumulados(cliente.getIdJugador(), cliente.getPuntosObtenidos());
            }
            conexion.commit();
            consola.registrarEnBitacora("Partida registrada en MySQL con id " + idPartida + ".");
        } catch (SQLException e) {
            try {
                if (conexion != null) {
                    conexion.rollback();
                }
            } catch (SQLException excepcion) {
                consola.mostrarError("No se pudo revertir la transacción: " + excepcion.getMessage());
            }
            consola.mostrarError("No se pudo registrar la partida: " + e.getMessage());
        } finally {
            if (conexion != null) {
                ConexionMySQL.devolverConexion(conexion);
            }
        }
    }

    private void mostrarRanking() {
        if (!baseDeDatosDisponible) {
            return;
        }
        Connection conexion = null;
        try {
            conexion = ConexionMySQL.obtenerConexion();
            List<Jugador> ranking = new JugadorDAO(conexion).ranking();
            List<String> lineas = new ArrayList<>();
            int posicion = 1;
            for (Jugador jugador : ranking) {
                lineas.add(posicion + ". " + jugador.usuario() + " - " + jugador.puntosTotales() + " punto(s)");
                posicion++;
            }
            consola.mostrarRanking(lineas);
        } catch (SQLException e) {
            consola.mostrarError("No se pudo consultar el ranking: " + e.getMessage());
        } finally {
            if (conexion != null) {
                ConexionMySQL.devolverConexion(conexion);
            }
        }
    }

    private int idDelBando(Lado lado) {
        for (HiloCliente cliente : clientes) {
            if (cliente.getBando() == lado) {
                return cliente.getIdJugador();
            }
        }
        return 0;
    }

    public GestorPartida getGestor() {
        return gestor;
    }
    public ConsolaServidor getConsola() {
        return consola;
    }

    public void cerrar() {
        if (cerrado) { return; }
        cerrado = true;
        cancelarTemporizador();

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (HiloCliente cliente : clientes) {
            cliente.cerrarSocket();
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // El socket ya estaba cerrado: no hay nada que hacer.
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        programador.shutdownNow();
        ConexionMySQL.cerrarPool();
        consola.mostrarMensaje("Servidor cerrado.");
    }
}