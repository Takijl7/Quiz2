
package org.example;

import org.example.red.ClienteDamas;
import org.example.red.ServidorDamas;

import java.util.Arrays;

public class DamasApp {

    public static void main(String[] args) {

        // Verifica que se indique el modo de ejecución.
        if (args.length == 0) {
            mostrarUso();
            return;
        }

        String modo = args[0].toLowerCase();

        // Separamos los argumentos correspondientes
        // al servidor o al cliente.
        String[] argumentos = Arrays.copyOfRange(
                args, 1, args.length
        );

        switch (modo) {

            case "servidor" -> {
                System.out.println("Iniciando servidor de damas...");
                ServidorDamas.main(argumentos);
            }

            case "cliente" -> {
                System.out.println("Iniciando cliente de damas...");
                ClienteDamas.main(argumentos);
            }

            default -> {
                System.out.println("Modo no reconocido: " + modo);
                mostrarUso();
            }
        }
    }

    private static void mostrarUso() {

        System.out.println("================================");
        System.out.println("       JUEGO DE DAMAS");
        System.out.println("================================");

        System.out.println("Modos disponibles:");
        System.out.println();

        System.out.println("Servidor:");
        System.out.println("  DamasApp servidor 5050");

        System.out.println();

        System.out.println("Cliente:");
        System.out.println("  DamasApp cliente localhost 5050");

        System.out.println();

        System.out.println("Debe iniciar primero el servidor");
        System.out.println("y luego los dos clientes.");
    }
}
