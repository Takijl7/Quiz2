package org.example.logica;

import org.example.datos.AlmacenMySQL;
import org.example.datos.AlmacenPartidas;
import org.example.datos.FilaRanking;
import org.example.datos.Jugador;
import org.example.datos.RegistroPartida;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class GestorPartida {
    public static final long TIEMPO_TURNO_MS = 120_000L;
    public static final int PUNTOS_VICTORIA = 5;
    private static final int MAX_USUARIO = 50;
    private static final int CADUCIDADES_PARA_EMPATE = 2;
    private enum Fase { ESPERANDO_JUGADORES, EN_CURSO, TERMINADA }
    private static final class Participante {
        final String usuario;
        final CanalJugador canal;
        final int idJugador;          // -1 si no se pudo consultar MySQL
        final int puntosAcumulados;

        Participante(String usuario, CanalJugador canal, Jugador registro) {
            this.usuario = usuario;
            this.canal = canal;
            this.idJugador = registro == null ? -1 : registro.id();
            this.puntosAcumulados = registro == null ? 0 : registro.puntosTotales();
        }
    }

    private final ReentrantLock candado = new ReentrantLock();
    private final Condition finDePartida = candado.newCondition();

    private final Map<Lado, Participante> jugadores = new EnumMap<>(Lado.class);
    private final AlmacenPartidas almacen;
    private final Consumer<String> bitacora;
    private final long tiempoTurnoMs;
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "RelojTurno");
        hilo.setDaemon(true);
        return hilo;
    });

    private volatile Consumer<String> visorTablero = estado -> { };
    private volatile boolean almacenDisponible = true;

    private Tablero tablero = new Tablero();
    private Fase fase = Fase.ESPERANDO_JUGADORES;
    private Lado turnoActual;
    private ScheduledFuture<?> alarma;
    private long generacionTurno;
    private int caducidadesConsecutivas;
    private LocalDateTime inicio;
    private ResultadoPartida resultado;

    public GestorPartida() {
        this(new AlmacenMySQL(), System.out::println, TIEMPO_TURNO_MS);
    }

    public GestorPartida(AlmacenPartidas almacen, Consumer<String> bitacora, long tiempoTurnoMs) {
        this.almacen = almacen;
        this.bitacora = bitacora;
        this.tiempoTurnoMs = tiempoTurnoMs;
    }

    public void setVisorTablero(Consumer<String> visor) {
        this.visorTablero = visor;
    }

    public Lado registrarJugador(String usuarioCrudo, String bandoCrudo, CanalJugador canal)
            throws LoginRechazadoException {
        String usuario = usuarioCrudo == null ? "" : usuarioCrudo.strip();
        if (usuario.isEmpty() || usuario.length() > MAX_USUARIO
                || usuario.chars().anyMatch(c -> Character.isISOControl(c) || c == '|' || c == '\u2502')) {
            throw new LoginRechazadoException("usuario inválido: use de 1 a " + MAX_USUARIO
                    + " caracteres, sin '|'");
        }
        Lado preferido = Lado.desde(bandoCrudo);
        if (preferido == null) {
            throw new LoginRechazadoException("bando inválido: use BLANCO o NEGRO");
        }

        Jugador registro = consultarJugador(usuario);

        candado.lock();
        try {
            if (fase != Fase.ESPERANDO_JUGADORES) {
                throw new LoginRechazadoException("la partida ya está completa o terminó");
            }
            for (Participante p : jugadores.values()) {
                if (p.usuario.equalsIgnoreCase(usuario)) {
                    throw new LoginRechazadoException("el usuario '" + usuario + "' ya está conectado");
                }
            }
            Lado asignado = jugadores.containsKey(preferido) ? preferido.rival() : preferido;
            Participante participante = new Participante(usuario, canal, registro);
            jugadores.put(asignado, participante);
            registrarEvento("Jugador '" + usuario + "' conectado como " + asignado
                    + " (puntos acumulados: " + participante.puntosAcumulados + ")");
            canal.bienvenida(asignado);

            if (jugadores.size() == 2) {
                iniciarPartida();
            }
            return asignado;
        } finally {
            candado.unlock();
        }
    }

    private Jugador consultarJugador(String usuario) {
        if (!almacenDisponible) {
            return null;
        }
        try {
            return almacen.obtenerOCrearJugador(usuario);
        } catch (SQLException e) {
            almacenDisponible = false;
            registrarEvento("AVISO: MySQL no disponible (" + e.getMessage()
                    + "). La partida se juega, pero no se guardará.");
            return null;
        }
    }

    private void iniciarPartida() {
        tablero = new Tablero();
        fase = Fase.EN_CURSO;
        inicio = LocalDateTime.now();
        turnoActual = Lado.BLANCAS;
        caducidadesConsecutivas = 0;
        registrarEvento("Partida iniciada. Juega primero " + turnoActual + ".");

        String estado = tablero.estado();
        visorTablero.accept(estado);
        difundir(canal -> canal.estado(estado));
        difundir(canal -> canal.turno(Lado.BLANCO));
        armarAlarma();
    }

    public void procesarMovimiento(Lado lado, String origen, String destino) {
        candado.lock();
        try {
            Participante jugador = jugadores.get(lado);
            if (jugador == null) {
                return;
            }
            if (fase != Fase.EN_CURSO) {
                jugador.canal.error(fase == Fase.TERMINADA
                        ? "la partida ya terminó" : "la partida no ha comenzado: falta el rival");
                return;
            }
            if (lado != turnoActual) {
                jugador.canal.error("no es su turno: juega " + turnoActual);
                return;
            }

            Tablero.Efecto efecto;
            try {
                efecto = tablero.jugar(lado, origen.strip(), destino.strip());
            } catch (JugadaInvalidaException e) {
                registrarEvento(lado + " intentó " + origen + "->" + destino + " y fue rechazado: " + e.getMessage());
                jugador.canal.error(e.getMessage());
                return; // el turno y el reloj siguen igual
            }

            cancelarAlarma();
            caducidadesConsecutivas = 0;
            jugador.canal.ok();
            if (efecto.captura()) {
                jugador.canal.captura();
            }
            if (efecto.coronacion()) {
                jugador.canal.coronacion();
            }
            registrarEvento(lado + " (" + jugador.usuario + ") jugó " + origen + "->" + destino
                    + (efecto.captura() ? " y capturó una ficha" : "")
                    + (efecto.coronacion() ? " y coronó una dama" : ""));

            String estado = tablero.estado();
            visorTablero.accept(estado);
            difundir(canal -> canal.estado(estado));

            Lado rival = lado.rival();
            if (!tablero.tieneMovimiento(rival)) {
                if (tablero.tieneMovimiento(lado)) {
                    finalizar(lado == Lado.BLANCAS ? ResultadoPartida.VICTORIA_BLANCO : ResultadoPartida.VICTORIA_NEGRO,
                            rival + " se quedó sin fichas o sin jugadas legales");
                } else {
                    finalizar(ResultadoPartida.EMPATE, "ningún bando tiene jugadas legales (bloqueo)");
                }
                return;
            }
            pasarTurnoA(rival);
        } finally {
            candado.unlock();
        }
    }

    private void armarAlarma() {
        cancelarAlarma();
        final long generacion = generacionTurno;
        alarma = reloj.schedule(() -> expirarTurno(generacion), tiempoTurnoMs, TimeUnit.MILLISECONDS);
    }

    private void cancelarAlarma() {
        generacionTurno++;
        if (alarma != null) {
            alarma.cancel(false);
            alarma = null;
        }
    }

    private void expirarTurno(long generacion) {
        candado.lock();
        try {
            if (fase != Fase.EN_CURSO || generacion != generacionTurno) {
                return; // alarma obsoleta: el jugador movió (o la partida acabó) antes de que ganáramos el candado
            }
            Lado vencido = turnoActual;
            caducidadesConsecutivas++;
            registrarEvento("Se agotó el tiempo de " + vencido + " (" + jugadores.get(vencido).usuario
                    + "); la jugada no se realiza y el turno pasa al rival.");
            difundir(CanalJugador::tiempoAgotado);

            if (caducidadesConsecutivas >= CADUCIDADES_PARA_EMPATE) {
                finalizar(ResultadoPartida.EMPATE, "ambos bandos agotaron su tiempo en turnos consecutivos");
                return;
            }
            pasarTurnoA(vencido.rival());
        } finally {
            candado.unlock();
        }
    }

    private void pasarTurnoA(Lado siguiente) {
        turnoActual = siguiente;
        difundir(canal -> canal.turno(siguiente));
        armarAlarma();
    }

    public void jugadorDesconectado(Lado lado) {
        candado.lock();
        try {
            if (!jugadores.containsKey(lado)) {
                return;
            }
            switch (fase) {
                case ESPERANDO_JUGADORES -> {
                    registrarEvento(lado + " se desconectó antes de empezar; el bando queda libre.");
                    jugadores.remove(lado);
                }
                case EN_CURSO -> {
                    registrarEvento(lado + " abandonó la partida.");
                    finalizar(lado.rival() == Lado.BLANCAS ? ResultadoPartida.VICTORIA_BLANCO : ResultadoPartida.VICTORIA_NEGRO,
                            lado + " abandonó");
                }
                case TERMINADA -> {}
            }
        } finally {
            candado.unlock();
        }
    }

    private void finalizar(ResultadoPartida resultadoFinal, String motivo) {
        fase = Fase.TERMINADA;
        resultado = resultadoFinal;
        cancelarAlarma();
        LocalDateTime fin = LocalDateTime.now();
        registrarEvento("FIN DE LA PARTIDA: " + resultadoFinal + " (" + motivo + ")");

        switch (resultadoFinal) {
            case VICTORIA_BLANCO -> difundir(canal -> canal.victoria(Lado.BLANCO));
            case VICTORIA_NEGRO -> difundir(canal -> canal.victoria(Lado.NEGRO));
            default -> difundir(CanalJugador::empate);
        }

        Participante blanco = jugadores.get(Lado.BLANCAS);
        Participante negro = jugadores.get(Lado.NEGRAS);
        String resBlanco = resultadoIndividual(resultadoFinal, Lado.BLANCAS);
        String resNegro = resultadoIndividual(resultadoFinal, Lado.NEGRAS);
        int puntosBlanco = "VICTORIA".equals(resBlanco) ? PUNTOS_VICTORIA : 0;
        int puntosNegro = "VICTORIA".equals(resNegro) ? PUNTOS_VICTORIA : 0;
        int piezasBlanco = tablero.contar(Lado.BLANCAS);
        int piezasNegro = tablero.contar(Lado.NEGRAS);

        registrarEvento("Marcador: " + blanco.usuario + " (BLANCO) " + piezasBlanco + " ficha(s), +" + puntosBlanco
                + " pts  |  " + negro.usuario + " (NEGRO) " + piezasNegro + " ficha(s), +" + puntosNegro + " pts");
        persistir(new RegistroPartida(inicio, fin, resultadoFinal.name(), blanco.idJugador, negro.idJugador,
                puntosBlanco, puntosNegro, resBlanco, resNegro, piezasBlanco, piezasNegro), blanco, negro);

        blanco.canal.resultado(resBlanco, puntosBlanco);
        negro.canal.resultado(resNegro, puntosNegro);
        finDePartida.signalAll();
    }

    private static String resultadoIndividual(ResultadoPartida r, Lado lado) {
        return switch (r) {
            case EMPATE -> "EMPATE";
            case VICTORIA_BLANCO -> lado == Lado.BLANCAS ? "VICTORIA" : "DERROTA";
            case VICTORIA_NEGRO -> lado == Lado.NEGRAS ? "VICTORIA" : "DERROTA";
            default -> throw new IllegalStateException("resultado final inesperado: " + r);
        };
    }

    private void persistir(RegistroPartida registro, Participante blanco, Participante negro) {
        if (!almacenDisponible || blanco.idJugador < 0 || negro.idJugador < 0) {
            registrarEvento("AVISO: el resultado no se guardó porque MySQL no estaba disponible.");
            return;
        }
        try {
            int idPartida = almacen.registrarPartida(registro);
            registrarEvento("Partida #" + idPartida + " guardada en MySQL.");
            List<FilaRanking> ranking = almacen.ranking();
            registrarEvento("RANKING (puntos acumulados):");
            int puesto = 1;
            for (FilaRanking fila : ranking) {
                registrarEvento(String.format("  %d. %-20s %3d pts  (%d partida(s), %d victoria(s))",
                        puesto++, fila.usuario(), fila.puntosTotales(), fila.partidas(), fila.victorias()));
            }
        } catch (SQLException e) {
            registrarEvento("AVISO: no se pudo guardar la partida en MySQL: " + e.getMessage());
        }
    }

    public void esperarFin() throws InterruptedException {
        candado.lock();
        try {
            while (fase != Fase.TERMINADA) {
                finDePartida.await();
            }
        } finally {
            candado.unlock();
        }
    }

    public boolean estaTerminada() {
        candado.lock();
        try {
            return fase == Fase.TERMINADA;
        } finally {
            candado.unlock();
        }
    }

    public Lado getTurnoActual() {
        candado.lock();
        try {
            return turnoActual;
        } finally {
            candado.unlock();
        }
    }

    public ResultadoPartida getResultado() {
        candado.lock();
        try {
            return resultado;
        } finally {
            candado.unlock();
        }
    }

    public String estadoTablero() {
        candado.lock();
        try {
            return tablero.estado();
        } finally {
            candado.unlock();
        }
    }

    public void registrarEvento(String mensaje) {
        bitacora.accept(mensaje);
    }

    public void cerrar() {
        reloj.shutdown();
        try {
            if (!reloj.awaitTermination(2, TimeUnit.SECONDS)) {
                reloj.shutdownNow();
            }
        } catch (InterruptedException e) {
            reloj.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void difundir(Consumer<CanalJugador> accion) {
        for (Participante p : jugadores.values()) {
            accion.accept(p.canal);
        }
    }
}