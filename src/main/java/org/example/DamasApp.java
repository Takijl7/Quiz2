package org.example;

import javax.swing.*;

import org.example.presentacion.vista.tablero;

public class DamasApp {
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            tablero view = new tablero();
            view.setVisible(true);
        });

    }
}
