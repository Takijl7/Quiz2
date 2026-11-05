package org.example.logica;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas del tablero de damas. posX representa la fila (0 a 7),
 * posY representa la columna (0 a 7).
 *
 * El servidor debe decidir de quién es el turno; esta clase solo valida
 * y ejecuta movimientos sobre el tablero.
 */
public class Tablero {

    public static final int TAMANIO = 8;

    private final Ficha[][] tablero = new Ficha[TAMANIO][TAMANIO];

    // TipoFicha.REINA no distingue entre blanca y negra.
    // Conservamos el bando de cada ficha incluso después de coronarla.
    private final Map<Ficha, Lado> bandos = new IdentityHashMap<>();

    public Tablero() {
        int idBlancas = 0;
        int idNegras = 0;

        for (int fila = 0; fila < TAMANIO; fila++) {
            for (int columna = 0; columna < TAMANIO; columna++) {
                if (!esOscura(fila, columna)) {
                    continue;
                }

                if (fila < 3) {
                    Ficha ficha = new Ficha("NEGRO-" + (++idNegras), fila,
                            columna, TipoFicha.NEGRA);
                    tablero[fila][columna] = ficha;
                    bandos.put(ficha, Lado.NEGRO);
                } else if (fila > 4) {
                    Ficha ficha = new Ficha("BLANCO-" + (++idBlancas), fila,
                            columna, TipoFicha.BLANCA);
                    tablero[fila][columna] = ficha;
                    bandos.put(ficha, Lado.BLANCO);
                }
            }
        }
    }

    // -------- Coordenadas y creación de fichas para el cliente --------

    public static boolean coordenadaValida(String coordenada) {
        return coordenada != null && coordenada.matches("(?i)[a-h][1-8]");
    }

    public static int columnaDe(String coordenada) {
        if (!coordenadaValida(coordenada)) {
            throw new IllegalArgumentException("Coordenada invalida: " + coordenada);
        }
        return Character.toLowerCase(coordenada.charAt(0)) - 'a';
    }

    public static int filaDe(String coordenada) {
        if (!coordenadaValida(coordenada)) {
            throw new IllegalArgumentException("Coordenada invalida: " + coordenada);
        }
        return coordenada.charAt(1) - '1';
    }

    public static String coordenada(int columna, int fila) {
        if (!dentro(fila, columna)) {
            throw new IllegalArgumentException("Posicion fuera del tablero");
        }
        return "" + (char) ('a' + columna) + (fila + 1);
    }

    public static Lado ladoDe(char simbolo) {
        return switch (simbolo) {
            case 'B', 'R' -> Lado.BLANCO;
            case 'N', 'r' -> Lado.NEGRO;
            default -> null;
        };
    }

    public static List<Ficha> fichasIniciales(Lado lado) {
        if (lado == null) {
            throw new IllegalArgumentException("Bando no especificado");
        }

        List<Ficha> fichas = new ArrayList<>();
        int primeraFila = lado == Lado.NEGRO ? 0 : 5;
        TipoFicha tipo = lado == Lado.NEGRO ? TipoFicha.NEGRA : TipoFicha.BLANCA;

        for (int fila = primeraFila; fila < primeraFila + 3; fila++) {
            for (int columna = 0; columna < TAMANIO; columna++) {
                if (esOscura(fila, columna)) {
                    fichas.add(new Ficha(lado.name() + "-" + (fichas.size() + 1),
                            fila, columna, tipo));
                }
            }
        }
        return fichas;
    }

    private static boolean dentro(int fila, int columna) {
        return fila >= 0 && fila < TAMANIO && columna >= 0 && columna < TAMANIO;
    }

    private static boolean esOscura(int fila, int columna) {
        return (fila + columna) % 2 == 0;
    }

    private static Lado contrario(Lado lado) {
        return lado == Lado.BLANCO ? Lado.NEGRO : Lado.BLANCO;
    }

    public synchronized Ficha getFicha(int fila, int columna) {
        return dentro(fila, columna) ? tablero[fila][columna] : null;
    }

    public synchronized Ficha getFicha(String casilla) {
        return coordenadaValida(casilla)
                ? getFicha(filaDe(casilla), columnaDe(casilla)) : null;
    }

    public synchronized Lado bandoDe(Ficha ficha) {
        return bandos.get(ficha);
    }

    private boolean fichaUbicada(Ficha ficha) {
        return ficha != null && dentro(ficha.getPosX(), ficha.getPosY())
                && tablero[ficha.getPosX()][ficha.getPosY()] == ficha
                && bandos.containsKey(ficha);
    }

    // -------- Reglas de desplazamiento y captura --------

    /**
     * Verifica un movimiento sencillo. Si cualquier ficha del mismo bando
     * puede capturar, es obligatorio realizar esa captura primero.
     */
    public synchronized boolean desplazamientoValido(Ficha ficha, int nuevaFila,
                                                     int nuevaColumna) {
        if (!fichaUbicada(ficha) || !dentro(nuevaFila, nuevaColumna)
                || !esOscura(nuevaFila, nuevaColumna)
                || tablero[nuevaFila][nuevaColumna] != null) {
            return false;
        }

        int cambioFila = nuevaFila - ficha.getPosX();
        int cambioCol = nuevaColumna - ficha.getPosY();
        if (Math.abs(cambioFila) != 1 || Math.abs(cambioCol) != 1) {
            return false;
        }

        Lado lado = bandos.get(ficha);
        if (ficha.getTipo() != TipoFicha.REINA &&
                cambioFila != (lado == Lado.NEGRO ? 1 : -1)) {
            return false;
        }

        return !hayCaptura(lado);
    }

    /**
     * Una captura consiste en saltar exactamente dos casillas diagonales,
     * pasando sobre una ficha rival y llegando a una casilla vacía.
     */
    public synchronized boolean comerValido(Ficha ficha, int nuevaFila,
                                            int nuevaColumna) {
        if (!fichaUbicada(ficha) || !dentro(nuevaFila, nuevaColumna)
                || !esOscura(nuevaFila, nuevaColumna)
                || tablero[nuevaFila][nuevaColumna] != null) {
            return false;
        }

        int cambioFila = nuevaFila - ficha.getPosX();
        int cambioCol = nuevaColumna - ficha.getPosY();
        if (Math.abs(cambioFila) != 2 || Math.abs(cambioCol) != 2) {
            return false;
        }

        Lado lado = bandos.get(ficha);
        if (ficha.getTipo() != TipoFicha.REINA &&
                cambioFila != (lado == Lado.NEGRO ? 2 : -2)) {
            return false;
        }

        int filaMedia = (ficha.getPosX() + nuevaFila) / 2;
        int colMedia = (ficha.getPosY() + nuevaColumna) / 2;
        Ficha rival = tablero[filaMedia][colMedia];
        return rival != null && bandos.get(rival) == contrario(lado);
    }

    private void moverInterno(Ficha ficha, int nuevaFila, int nuevaColumna) {
        tablero[ficha.getPosX()][ficha.getPosY()] = null;
        tablero[nuevaFila][nuevaColumna] = ficha;
        ficha.setPosX(nuevaFila);
        ficha.setPosY(nuevaColumna);

        Lado lado = bandos.get(ficha);
        if (ficha.getTipo() != TipoFicha.REINA
                && ((lado == Lado.NEGRO && nuevaFila == TAMANIO - 1)
                || (lado == Lado.BLANCO && nuevaFila == 0))) {
            ficha.setTipo(TipoFicha.REINA);
            ficha.coronada();
        }
    }

    // Mantiene el método original del proyecto.
    public synchronized void desplazamiento(Ficha ficha, int nuevaFila, int nuevaColumna) {
        if (desplazamientoValido(ficha, nuevaFila, nuevaColumna)) {
            moverInterno(ficha, nuevaFila, nuevaColumna);
        }
    }

    // Mantiene el método original: devuelve la ficha capturada, o null.
    public synchronized Ficha comer(Ficha ficha, int nuevaFila, int nuevaColumna) {
        if (!comerValido(ficha, nuevaFila, nuevaColumna)) {
            return null;
        }

        int filaMedia = (ficha.getPosX() + nuevaFila) / 2;
        int colMedia = (ficha.getPosY() + nuevaColumna) / 2;
        Ficha victima = tablero[filaMedia][colMedia];
        tablero[filaMedia][colMedia] = null;
        bandos.remove(victima);
        moverInterno(ficha, nuevaFila, nuevaColumna);
        return victima;
    }

    public synchronized Ficha capturar(Ficha ficha, int nuevaFila, int nuevaColumna) {
        return comer(ficha, nuevaFila, nuevaColumna);
    }

    // Útil para GestorPartida: el bando recibido debe ser el de la ficha.
    public synchronized boolean mover(String origen, String destino, Lado lado) {
        if (!coordenadaValida(origen) || !coordenadaValida(destino) || lado == null) {
            return false;
        }
        Ficha ficha = getFicha(origen);
        if (ficha == null || bandoDe(ficha) != lado) {
            return false;
        }
        int fila = filaDe(destino);
        int columna = columnaDe(destino);
        if (comerValido(ficha, fila, columna)) {
            comer(ficha, fila, columna);
            return true;
        }
        if (desplazamientoValido(ficha, fila, columna)) {
            desplazamiento(ficha, fila, columna);
            return true;
        }
        return false;
    }

    public synchronized boolean hayCaptura(Lado lado) {
        if (lado == null) {
            return false;
        }
        for (int fila = 0; fila < TAMANIO; fila++) {
            for (int col = 0; col < TAMANIO; col++) {
                Ficha ficha = tablero[fila][col];
                if (ficha != null && bandos.get(ficha) == lado && puedeComer(ficha)) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean tieneMovimiento(Lado lado) {
        if (lado == null) {
            return false;
        }
        for (int fila = 0; fila < TAMANIO; fila++) {
            for (int col = 0; col < TAMANIO; col++) {
                Ficha ficha = tablero[fila][col];
                if (ficha != null && bandos.get(ficha) == lado && tieneAccion(ficha)) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean tieneAccion(Ficha ficha) {
        return puedeComer(ficha) || puedeDesplazar(ficha);
    }

    private boolean puedeComer(Ficha ficha) {
        if (!fichaUbicada(ficha)) {
            return false;
        }
        for (int df : new int[]{-2, 2}) {
            for (int dc : new int[]{-2, 2}) {
                if (comerValido(ficha, ficha.getPosX() + df,
                        ficha.getPosY() + dc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean puedeDesplazar(Ficha ficha) {
        if (!fichaUbicada(ficha)) {
            return false;
        }
        Lado lado = bandos.get(ficha);
        int[] direcciones = ficha.getTipo() == TipoFicha.REINA
                ? new int[]{-1, 1} : new int[]{lado == Lado.NEGRO ? 1 : -1};

        for (int df : direcciones) {
            for (int dc : new int[]{-1, 1}) {
                int nuevaFila = ficha.getPosX() + df;
                int nuevaCol = ficha.getPosY() + dc;
                if (dentro(nuevaFila, nuevaCol) && esOscura(nuevaFila, nuevaCol)
                        && tablero[nuevaFila][nuevaCol] == null) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------- Resultados --------

    public synchronized int contarFichas(Lado lado) {
        int cantidad = 0;
        for (int fila = 0; fila < TAMANIO; fila++) {
            for (int col = 0; col < TAMANIO; col++) {
                Ficha ficha = tablero[fila][col];
                if (ficha != null && bandos.get(ficha) == lado) {
                    cantidad++;
                }
            }
        }
        return cantidad;
    }

    public synchronized boolean empate() {
        // El empate por dos turnos caducados debe gestionarlo GestorPartida.
        return !tieneMovimiento(Lado.BLANCO) && !tieneMovimiento(Lado.NEGRO);
    }

    public synchronized boolean victoria(Lado lado) {
        if (lado == null || contarFichas(lado) == 0 || empate()) {
            return false;
        }
        Lado rival = contrario(lado);
        return contarFichas(rival) == 0 || !tieneMovimiento(rival);
    }

    public synchronized boolean derrota(Lado lado) {
        return lado != null && victoria(contrario(lado));
    }

    // Conservamos los métodos sin parámetro del Tablero original.
    public synchronized boolean victoria() {
        return victoria(Lado.BLANCO) || victoria(Lado.NEGRO);
    }

    public synchronized boolean derrota() {
        return victoria();
    }

    // -------- Estado serializado para mensajes STATE (64 caracteres) --------

    public synchronized String serializar() {
        StringBuilder estado = new StringBuilder(TAMANIO * TAMANIO);
        for (int fila = 0; fila < TAMANIO; fila++) {
            for (int col = 0; col < TAMANIO; col++) {
                if (!esOscura(fila, col)) {
                    estado.append('.');
                    continue;
                }
                Ficha ficha = tablero[fila][col];
                if (ficha == null) {
                    estado.append('#');
                } else {
                    Lado lado = bandos.get(ficha);
                    if (ficha.getTipo() == TipoFicha.REINA) {
                        estado.append(lado == Lado.BLANCO ? 'R' : 'r');
                    } else {
                        estado.append(lado == Lado.BLANCO ? 'B' : 'N');
                    }
                }
            }
        }
        return estado.toString();
    }

    public synchronized String estadoTablero() {
        return serializar();
    }
}
