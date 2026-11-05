package org.example.presentacion.vista;

import javax.swing.*;
import java.awt.*;

public class tablero extends JFrame{
    private JPanel panelRaiz;
    private JPanel panelTablero;
    private JButton Reiniciar;
    private JLabel turno;
    private JLabel DAMAS;

    private final JButton[][] casillas = new JButton[8][8];
    //Va en la logica v
    //private final int[][] tablero = new int[8][8]; // 0 vacío, 1 negra, 2 blanca, 3 dama negra, 4 dama blanca
    private int filaSel = -1, colSel = -1;
    private boolean turnoBlancas = true;

    public tablero() {
        setContentPane(panelRaiz);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        construirTablero();

        pack();
        setLocationRelativeTo(null);
    }

    private void construirTablero() {
        panelTablero.removeAll();
        panelTablero.setLayout(new GridLayout(8, 8));

        for (int fila = 0; fila < 8; fila++) {
            for (int col = 0; col < 8; col++) {

                JButton b = new JButton();
                b.setPreferredSize(new Dimension(70, 70));
                b.setFocusPainted(false);
                b.setBorderPainted(false);
                b.setOpaque(true);

                boolean oscura = (fila + col) % 2 == 1;

                b.setBackground(
                        oscura
                                ? new Color(120, 80, 50)
                                : new Color(240, 220, 180)
                );

                if (oscura) {
                    final int f = fila;
                    final int c = col;
                    b.addActionListener(e -> alHacerClic(f, c));
                } else {
                    b.setEnabled(false);
                }

                casillas[fila][col] = b;
                panelTablero.add(b);
            }
        }

        colocarFichasIniciales();

        panelTablero.revalidate();
        panelTablero.repaint();
    }

    private void colocarFichasIniciales() {
        for (int fila = 0; fila < 8; fila++) {
            for (int col = 0; col < 8; col++) {
                if ((fila + col) % 2 == 1) {
                    if (fila < 3) casillas[fila][col].setText("●");      // negras
                    else if (fila > 4) casillas[fila][col].setText("○"); // blancas
                }
            }
        }
    }

    private void alHacerClic(int fila, int col) {System.out.println("Clic en " + fila + "," + col);}

    public JButton getCasilla(int x, int y) {return casillas[x][y];}
}
