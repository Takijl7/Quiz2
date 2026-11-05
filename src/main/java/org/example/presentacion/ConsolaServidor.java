package org.example.presentacion;

import org.example.logica.ResultadoPartida;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ConsolaServidor {
    /** Formato de la hora en la bitácora. */
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Bitácora de la partida. */
    private final List<String> bitacora = new ArrayList<>();

    /**
     * Muestra el encabezado del servidor.
     *
     * @param puerto puerto de escucha.
     */
    public void mostrarEncabezado(int puerto) {
        System.out.println("============================================================");
        System.out.println(" Servidor de damas - Quiz 2 (Programación 3)");
        System.out.println(" Escuchando en el puerto " + puerto);
        System.out.println(" Esperando a los dos jugadores...");
        System.out.println("============================================================");
    }

    /**
     * Muestra un mensaje informativo del servidor.
     *
     * @param mensaje mensaje a mostrar.
     */
    public void mostrarMensaje(String mensaje) {
        System.out.println("[Servidor] " + mensaje);
    }

    /**
     * Muestra un aviso que no interrumpe el juego.
     *
     * @param mensaje aviso a mostrar.
     */
    public void mostrarAviso(String mensaje) {
        System.out.println("[Servidor] AVISO: " + mensaje);
    }

    /**
     * Muestra un error.
     *
     * @param mensaje error a mostrar.
     */
    public void mostrarError(String mensaje) {
        System.err.println("[Servidor] ERROR: " + mensaje);
    }

    /**
     * Agrega una línea a la bitácora de la partida (RF5).
     *
     * @param mensaje mensaje a registrar.
     */
    public void registrarEnBitacora(String mensaje) {
        String linea = LocalTime.now().format(HORA) + " - " + mensaje;
        bitacora.add(linea);
        System.out.println("[Bitacora] " + linea);
    }

    /**
     * Muestra el tablero de la partida.
     *
     * @param estadoTablero estado del tablero serializado en 64 caracteres.
     */
    public void mostrarTablero(String estadoTablero) {
        if (estadoTablero == null || estadoTablero.length() < 64) {
            return;
        }
        StringBuilder tablero = new StringBuilder();
        tablero.append("    a  b  c  d  e  f  g  h\n");
        tablero.append("  +-------------------------\n");
        for (int fila = 7; fila >= 0; fila--) {
            tablero.append(String.format("%d | ", fila + 1));
            for (int columna = 0; columna < 8; columna++) {
                tablero.append(simbolo(estadoTablero.charAt(fila * 8 + columna))).append("  ");
            }
            tablero.append("\n");
        }
        System.out.print(tablero);
    }

    /**
     * Convierte el código de una casilla en el símbolo que se muestra.
     *
     * @param codigo código de casilla.
     * @return el símbolo a imprimir.
     */
    private String simbolo(char codigo) {
        return switch (codigo) {
            case '#' -> ".";
            case '.' -> " ";
            case 'B' -> "B";
            case 'N' -> "N";
            case 'R' -> "R";
            case 'r' -> "r";
            default -> "?";
        };
    }

    /**
     * Muestra a qué bando le toca mover.
     *
     * @param bando bando con el turno.
     */
    public void mostrarTurno(String bando) {
        System.out.println("[Servidor] Turno de " + bando);
    }

    /**
     * Muestra el resultado final de la partida.
     *
     * @param resultado resultado global de la partida.
     */
    public void mostrarResultado(ResultadoPartida resultado) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(" FIN DE LA PARTIDA: " + resultado);
        System.out.println("============================================================");
    }

    /**
     * Muestra el ranking de jugadores por puntos acumulados (RF15).
     *
     * @param lineas una línea por jugador, ya formateadas.
     */
    public void mostrarRanking(List<String> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            return;
        }
        System.out.println(" Ranking de jugadores:");
        for (String linea : lineas) {
            System.out.println("   " + linea);
        }
    }

    /**
     * @return la bitácora completa de la partida.
     */
    public List<String> getBitacora() {
        return bitacora;
    }
}
