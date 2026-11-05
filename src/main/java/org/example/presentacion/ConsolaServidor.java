
package org.example.presentacion;

public class ConsolaServidor {

    public void mostrarEncabezado(int puerto) {
        System.out.println("=== SERVIDOR DE DAMAS ===");
        System.out.println("Escuchando en el puerto " + puerto);
    }

    public void mostrarMensaje(String mensaje) {
        System.out.println("[SERVIDOR] " + mensaje);
    }

    public void mostrarAviso(String mensaje) {
        System.out.println("[AVISO] " + mensaje);
    }

    public void mostrarError(String mensaje) {
        System.err.println("[ERROR] " + mensaje);
    }

    public void registrarEnBitacora(String evento) {
        System.out.println("[BITACORA] " + evento);
    }

    public void mostrarTurno(String turno) {
        System.out.println("Turno actual: " + turno);
    }

    public void mostrarTablero(String estado) {
        if (estado == null || estado.length() != 64) {
            mostrarAviso("No se puede mostrar el tablero: el estado debe tener 64 caracteres.");
            return;
        }

        System.out.println("    a b c d e f g h");

        for (int fila = 7; fila >= 0; fila--) {
            System.out.print((fila + 1) + " | ");

            for (int columna = 0; columna < 8; columna++) {
                System.out.print(estado.charAt(fila * 8 + columna) + " ");
            }

            System.out.println();
        }
    }
}
