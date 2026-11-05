package org.example.presentacion;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ConsolaCliente {
    /** Lector de la entrada estándar. */
    private final Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

    /**
     * @return el lector de la entrada estándar.
     */
    public Scanner getScanner() {
        return scanner;
    }

    /**
     * Solicita el nombre de usuario del jugador.
     *
     * <p>Se comprueba siempre {@code hasNextLine()} para evitar el
     * {@code NoSuchElementException} cuando la consola se cierra.</p>
     *
     * @return el nombre de usuario ingresado.
     */
    public String pedirUsuario() {
        String usuario = limpiar(pedirLinea("Usuario: "));
        return (usuario == null || usuario.isBlank()) ? "jugador" : usuario;
    }

    /**
     * Quita el BOM y los espacios sobrantes que puede traer la consola.
     *
     * @param texto texto leído de la consola.
     * @return el texto limpio, o {@code null} si no se leyó nada.
     */
    private String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.replace("\uFEFF", "").trim();
    }

    /**
     * Solicita el bando preferido del jugador.
     *
     * @return el bando preferido: {@code BLANCO} o {@code NEGRO}.
     */
    public String pedirBandoPreferido() {
        String bando = pedirLinea("Bando preferido (BLANCO/NEGRO): ");
        return (bando == null || bando.isBlank()) ? "BLANCO" : bando.toUpperCase();
    }

    /**
     * Pide una línea al usuario.
     *
     * @param prompt texto que se muestra antes de leer.
     * @return la línea leída, o {@code null} si la consola se cerró.
     */
    public String pedirLinea(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine().trim();
    }

    /**
     * Muestra el encabezado del cliente.
     *
     * @param usuario nombre de usuario del jugador.
     * @param bando   bando asignado.
     * @param host    dirección del servidor.
     * @param puerto  puerto del servidor.
     */
    public void mostrarEncabezado(String usuario, String bando, String host, int puerto) {
        System.out.println("============================================================");
        System.out.println(" Cliente de damas - Quiz 2 (Programación 3)");
        System.out.println(" Jugador: " + usuario + " | Bando: " + bando);
        System.out.println(" Servidor: " + host + ":" + puerto);
        System.out.println(" Escriba la jugada como 'c3 d4' o 'salir' para terminar.");
        System.out.println("============================================================");
    }

    /**
     * Muestra un mensaje informativo.
     *
     * @param mensaje mensaje a mostrar.
     */
    public void mostrarMensaje(String mensaje) {
        System.out.println("[Cliente] " + mensaje);
    }

    /**
     * Muestra un aviso que no interrumpe el juego.
     *
     * @param mensaje aviso a mostrar.
     */
    public void mostrarAviso(String mensaje) {
        System.out.println("[Cliente] AVISO: " + mensaje);
    }

    /**
     * Muestra un error.
     *
     * @param mensaje error a mostrar.
     */
    public void mostrarError(String mensaje) {
        System.out.println("[Cliente] ERROR: " + mensaje);
    }

    /**
     * Muestra el tablero recibido del servidor.
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
     * @param turno   bando con el turno.
     * @param miBando bando del jugador.
     */
    public void mostrarTurno(String turno, String miBando) {
        if (turno.equalsIgnoreCase(miBando)) {
            System.out.println("[Cliente] SU TURNO (" + turno + ").");
        } else {
            System.out.println("[Cliente] Turno de " + turno + ". Espere...");
        }
    }

    /**
     * Muestra el resultado final de la partida.
     *
     * @param resultado resultado individual del jugador.
     * @param puntos    puntos obtenidos.
     */
    public void mostrarResultado(String resultado, int puntos) {
        System.out.println("============================================================");
        System.out.println(" FIN DE LA PARTIDA: " + resultado + " - " + puntos + " punto(s)");
        System.out.println("============================================================");
    }
}
