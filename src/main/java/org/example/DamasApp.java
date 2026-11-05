package org.example;

import org.example.red.ClienteDamas;
import org.example.red.ServidorDamas;

import javax.swing.*;

import org.example.presentacion.vista.tablero;

import java.util.Arrays;

public class DamasApp {
    public static void main(String[] args) {
        if (args.length == 0) {
            uso();
            return;
        }
        String[] resto = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase()) {
            case "servidor" -> ServidorDamas.main(resto);
            case "cliente" -> ClienteDamas.main(resto);
            default -> uso();
        }

        SwingUtilities.invokeLater(() -> {
            tablero view = new tablero();
            view.setVisible(true);
        });
    }

    private static void uso() {
        System.out.println("Uso:");
        System.out.println("  DamasApp servidor [puerto] [segundosPorTurno]");
        System.out.println("  DamasApp cliente  [host] [puerto]");
    }
}
