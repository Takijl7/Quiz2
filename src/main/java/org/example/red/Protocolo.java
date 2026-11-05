package org.example.red;

import org.example.logica.Lado;

public final class Protocolo {
    public static final String LOGIN = "LOGIN";
    public static final String MOV = "MOV";
    public static final String BYE = "BYE";
    public static final String WELCOME = "WELCOME";
    public static final String STATE = "STATE";
    public static final String TURN = "TURN";
    public static final String OK = "OK";
    public static final String ERR = "ERR";
    public static final String CAPTURE = "CAPTURE";
    public static final String KING = "KING";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String WIN = "WIN";
    public static final String DRAW = "DRAW";
    public static final String RESULT = "RESULT";
    public static final char SEPARADOR_COMANDO = ':';
    public static final char SEPARADOR_CAMPOS = '|';

    public record Mensaje(String comando, String argumentos) {}
    private Protocolo() {}

    public static Mensaje parsear(String linea) {
        String limpia = linea == null ? "" : linea.strip();
        int i = limpia.indexOf(SEPARADOR_COMANDO);
        if (i < 0) {
            return new Mensaje(limpia.toUpperCase(), "");
        }
        return new Mensaje(limpia.substring(0, i).toUpperCase(), limpia.substring(i + 1));
    }

    public static String[] campos(String argumentos) {
        return argumentos.split("[|\u2502]", -1);
    }

    public static String login(String usuario, String bando) {
        return LOGIN + SEPARADOR_COMANDO + usuario + SEPARADOR_CAMPOS + bando;
    }

    public static String mov(String origen, String destino) {
        return MOV + SEPARADOR_COMANDO + origen + "," + destino;
    }
    public static String welcome(Lado lado) {
        return WELCOME + SEPARADOR_COMANDO + lado;
    }
    public static String state(String tablero64) {
        return STATE + SEPARADOR_COMANDO + tablero64;
    }
    public static String turn(Lado lado) {
        return TURN + SEPARADOR_COMANDO + lado;
    }

    public static String err(String motivo) {
        return ERR + SEPARADOR_COMANDO + motivo.replace('\r', ' ').replace('\n', ' ');
    }

    public static String win(Lado ganador) {
        return WIN + SEPARADOR_COMANDO + ganador;
    }

    public static String result(String resultado, int puntos) {
        return RESULT + SEPARADOR_COMANDO + resultado + SEPARADOR_CAMPOS + puntos;
    }
    // Salto de línea para enviar mensajes por socket
    public static final String FIN_LINEA = "\n";

    // Obtiene el tipo de mensaje recibido
    public static String tipoDe(String linea) {
        return parsear(linea).comando();
    }

    // Obtiene los campos del mensaje
    public static String[] camposDe(String linea) {
        return campos(parsear(linea).argumentos());
    }

    // Genera un mensaje de movimiento
    public static String movimiento(String origen, String destino) {
        return mov(origen, destino);
    }

    // Genera un mensaje con el estado del tablero
    public static String estado(String tablero64) {
        return state(tablero64);
    }

    // Genera un mensaje con comando y contenido
    public static String mensaje(String comando, String contenido) {
        return comando + SEPARADOR_COMANDO + contenido;
    }
}