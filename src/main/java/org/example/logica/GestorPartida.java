
package org.example.logica;

import java.util.concurrent.locks.ReentrantLock;

public class GestorPartida {

    public static final long TIEMPO_TURNO_CONFIGURABLE = 120_000L;

    private final ReentrantLock candado = new ReentrantLock();

    private Tablero tablero = new Tablero();
    private Lado turnoActual;
    private ResultadoPartida resultado;

    private boolean iniciada;
    private int caducidadesConsecutivas;

    public record RespuestaMovimiento(
            boolean aceptado,
            boolean captura,
            boolean coronacion,
            String motivo,
            ResultadoPartida resultado
    ) {}

    public void iniciarPartida(Lado primerBando) {

        candado.lock();

        try {
            tablero = new Tablero();

            turnoActual = primerBando == null
                    ? Lado.BLANCO
                    : primerBando;

            resultado = null;
            caducidadesConsecutivas = 0;
            iniciada = true;

        } finally {
            candado.unlock();
        }
    }

    public RespuestaMovimiento procesarMovimiento(
            Lado lado, String origen, String destino) {

        candado.lock();

        try {
            if (!iniciada || resultado != null) {
                return new RespuestaMovimiento(
                        false, false, false,
                        "La partida no esta activa",
                        resultado
                );
            }

            if (lado != turnoActual) {
                return new RespuestaMovimiento(
                        false, false, false,
                        "No es su turno", null
                );
            }

            if (!Tablero.coordenadaValida(origen)
                    || !Tablero.coordenadaValida(destino)) {

                return new RespuestaMovimiento(
                        false, false, false,
                        "Coordenadas invalidas", null
                );
            }

            Ficha pieza = tablero.getFicha(origen);

            if (pieza == null || tablero.bandoDe(pieza) != lado) {
                return new RespuestaMovimiento(
                        false, false, false,
                        "El origen no contiene una ficha propia",
                        null
                );
            }

            boolean eraReina =
                    pieza.getTipo() == TipoFicha.REINA;

            int antes = tablero.contarFichas(rival(lado));

            if (!tablero.mover(origen, destino, lado)) {
                return new RespuestaMovimiento(
                        false, false, false,
                        "Movimiento ilegal, destino ocupado "
                                + "o captura obligatoria",
                        null
                );
            }

            boolean captura =
                    tablero.contarFichas(rival(lado)) < antes;

            boolean coronacion =
                    !eraReina
                            && pieza.getTipo() == TipoFicha.REINA
                            && (
                            (lado == Lado.NEGRO
                                    && Tablero.filaDe(destino) == 7)
                                    ||
                                    (lado == Lado.BLANCO
                                            && Tablero.filaDe(destino) == 0)
                    );

            caducidadesConsecutivas = 0;

            Lado contrario = rival(lado);

            // Revisar si el rival todavía puede jugar.
            if (!tablero.tieneMovimiento(contrario)) {

                if (tablero.tieneMovimiento(lado)) {
                    resultado = victoriaDe(lado);
                } else {
                    resultado = ResultadoPartida.EMPATE;
                }

            } else {
                turnoActual = contrario;
            }

            return new RespuestaMovimiento(
                    true, captura, coronacion,
                    "OK", resultado
            );

        } finally {
            candado.unlock();
        }
    }

    public boolean expirarTurno(Lado esperado) {

        candado.lock();

        try {
            if (!iniciada
                    || resultado != null
                    || turnoActual != esperado) {
                return false;
            }

            caducidadesConsecutivas++;

            if (caducidadesConsecutivas >= 2) {
                resultado = ResultadoPartida.EMPATE;
            } else {
                turnoActual = rival(turnoActual);
            }

            return true;

        } finally {
            candado.unlock();
        }
    }

    public void jugadorDesconectado(Lado lado) {

        candado.lock();

        try {
            if (iniciada && resultado == null && lado != null) {
                resultado = victoriaDe(rival(lado));
            }

        } finally {
            candado.unlock();
        }
    }

    public String estadoTablero() {

        candado.lock();

        try {
            return tablero.estadoTablero();

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

    public boolean estaTerminada() {

        candado.lock();

        try {
            return resultado != null;

        } finally {
            candado.unlock();
        }
    }

    private static Lado rival(Lado lado) {

        return lado == Lado.BLANCO
                ? Lado.NEGRO
                : Lado.BLANCO;
    }

    private static ResultadoPartida victoriaDe(Lado lado) {

        return lado == Lado.BLANCO
                ? ResultadoPartida.VICTORIA_BLANCO
                : ResultadoPartida.VICTORIA_NEGRO;
    }
}
