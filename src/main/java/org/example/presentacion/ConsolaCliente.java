
package org.example.presentacion;

import java.util.Scanner;

public class ConsolaCliente {

    private final Scanner scanner = new Scanner(System.in);

    public Scanner getScanner() {
        return scanner;
    }

    public String pedirLinea(String indicacion) {
        System.out.print(indicacion);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : null;
    }

    public String pedirUsuario() {
        while (true) {
            String usuario = pedirLinea("Nombre de usuario: ");

            if (usuario == null) {
                return "";
            }

            if (!usuario.isBlank() && usuario.length() <= 50
                    && !usuario.contains("|")) {
                return usuario;
            }

            mostrarAviso("Ingrese un usuario de 1 a 50 caracteres, sin |.");
        }
    }

    public String pedirBandoPreferido() {
        while (true) {
            String entrada = pedirLinea("Bando preferido (BLANCO/NEGRO): ");

            if (entrada == null) {
                return "";
            }

            if (entrada.equalsIgnoreCase("BLANCO")
                    || entrada.equalsIgnoreCase("NEGRO")) {
                return entrada.toUpperCase();
            }

            mostrarAviso("Debe escribir BLANCO o NEGRO.");
        }
    }

    public void mostrarEncabezado(String usuario, String bando,
                                  String host, int puerto) {
        System.out.println("=== CLIENTE DE DAMAS ===");
        System.out.println("Jugador: " + usuario + " | Bando: " + bando);
        System.out.println("Servidor: " + host + ":" + puerto);
        System.out.println("Para jugar, escriba origen y destino: b6 c5");
        System.out.println("Para salir, escriba q");
    }

    public void mostrarMensaje(String mensaje) {
        System.out.println("[CLIENTE] " + mensaje);
    }

    public void mostrarAviso(String mensaje) {
        System.out.println("[AVISO] " + mensaje);
    }

    public void mostrarError(String mensaje) {
        System.err.println("[ERROR] " + mensaje);
    }

    public void mostrarTurno(String turno, String bandoPropio) {
        System.out.println("Turno actual: " + turno
                + (turno.equalsIgnoreCase(bandoPropio)
                ? " (es tu turno)" : " (espera)"));
    }

    public void mostrarResultado(String resultado, int puntos) {
        System.out.println("Resultado final: " + resultado
                + " | Puntos: " + puntos);
    }

    public void mostrarTablero(String estado) {
        if (estado == null || estado.length() != 64) {
            mostrarAviso("Estado inválido: se esperan 64 caracteres.");
            return;
        }

        System.out.println("    a b c d e f g h");

        for (int fila = 7; fila >= 0; fila--) {
            System.out.print((fila + 1) + " | ");

            for (int columna = 0; columna < 8; columna++) {
                System.out.print(
                        estado.charAt(fila * 8 + columna) + " "
                );
            }

            System.out.println();
        }
    }
}
