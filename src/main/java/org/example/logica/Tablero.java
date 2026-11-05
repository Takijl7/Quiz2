package org.example.logica;


//LOS RESULSTADOS DE LAS OPERACIONES ACA SE DEBEN REFLEJAR EN LA VIEW
public class Tablero {
    private Ficha[][] tablero;
    //private char[][] tablero; Para version sin GUI

    public Tablero() {
        tablero = new Ficha[8][8];

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {

                //Colocamos las Fichas

                String id = i + "," + j;

                // Filas 1, 2 y 3: fichas negras
                if (i < 3 && (i + j) % 2 == 0) {
                    tablero[i][j] = new Ficha(id, i, j, TipoFicha.NEGRA);
                }

                // Filas 6, 7 y 8: fichas blancas
                else if (i > 4 && (i + j) % 2 == 0) {
                    tablero[i][j] = new Ficha(id, i, j, TipoFicha.BLANCA);
                }

            }
        }
    }

    private boolean dentro(int fila, int col) {
        return fila >= 0 && fila < 8 && col >= 0 && col < 8;
    }

    private boolean esDiagonal(Ficha f, int nX, int nY) {
        return Math.abs(f.getPosX() - nX) == Math.abs(f.getPosY() - nY) && f.getPosX() != nX;
    }

    public boolean tieneAccion(Ficha f) { return puedeComer(f) || puedeDesplazar(f); }

    public void desplazamiento(Ficha f, int nX, int nY){
        if(desplazamientoValido(f,nX,nY)){
            Ficha aux = f;
            tablero[f.getPosX()][f.getPosY()] = null; //tablero[f.getPosX()][f.getPosY()] = " ";

            tablero[nX][nY] = f; //tablero[nX][nY] = f.button.text; o algo asi

            // Actualizamos las coordenadas de la ficha
            f.setPosX(nX);
            f.setPosY(nY);
        }
    }

    public Ficha comer(Ficha f, int nX, int nY){
        if(comerValido(f,nX,nY)) {
            //Tomamos a la victima
            int victimaX = (f.getPosX() + nX) / 2;
            int victimaY = (f.getPosY() + nY) / 2;
            Ficha victima = tablero[victimaX][victimaY];

            //La desintegramos
            tablero[victimaX][victimaY] = null;

            //Nos movemos
            Ficha aux = f;
            tablero[f.getPosX()][f.getPosY()] = null; //tablero[f.getPosX()][f.getPosY()] = " ";

            tablero[nX][nY] = f; //tablero[nX][nY] = f.button.text; o algo asi

            // Actualizamos las coordenadas de la ficha
            f.setPosX(nX);
            f.setPosY(nY);

            return victima;
        }
        else return null;
    }



    private boolean puedeDesplazar(Ficha f) { //todo
        return false;
    }

    private boolean puedeComer(Ficha f) { //todo
        return false;
    }


    public boolean desplazamientoValido(Ficha f, int nX, int nY) {

        if (f == null || !dentro(nX, nY)) {
            return false;
        }

        int x = f.getPosX();
        int y = f.getPosY();

        // Verificamos que la ficha exista en su posicion.
        if (!dentro(x, y) || tablero[x][y] != f) {
            return false;
        }

        // El destino debe ser una casilla oscura vacia.
        if ((nX + nY) % 2 != 0 || tablero[nX][nY] != null) {
            return false;
        }

        int diferenciaX = nX - x;
        int diferenciaY = nY - y;

        // El movimiento normal es una casilla diagonal.
        if (Math.abs(diferenciaX) != 1 ||
                Math.abs(diferenciaY) != 1) {
            return false;
        }

        // La reina puede avanzar o retroceder.
        if (f.getTipo() == TipoFicha.REINA) {
            return true;
        }

        // Las negras avanzan hacia la fila 8.
        if (f.getTipo() == TipoFicha.NEGRA) {
            return diferenciaX == 1;
        }

        // Las blancas avanzan hacia la fila 1.
        if (f.getTipo() == TipoFicha.BLANCA) {
            return diferenciaX == -1;
        }

        return false;
    }


    public boolean comerValido(Ficha f, int nX, int nY){
        int x = f.getPosX();
        int y = f.getPosY();

        //Validamos la posicion final y la direcion, si es reina cualquier direccion es compatible
        if(dentro(nX,nY) && tablero[nX][nY] == null){

            //Comprobamos el movimiento en diagonal
            if (esDiagonal(f, nX, nY)) {

                if(f.getTipo() == TipoFicha.REINA) {
                    //Comprobamos que haya una ficha en medio

                //Comprovamos que el movimiento sean dos casilla
                } else if (Math.abs(f.getPosX() - nX) == Math.abs(f.getPosY() - nY)
                        && f.getPosX() == nX
                        && Math.abs(f.getPosX() - nX) == 2) {
                    if (f.getTipo() == TipoFicha.BLANCA) {
                        return nY > y;
                    }
                    if (f.getTipo() == TipoFicha.NEGRA) {
                        return nY < y;
                    }
                }
            }
        }
        return false;
    }

    public boolean victoria(){//todo
        return false;
    }

    public boolean derrota(){//todo
        return false;
    }

    public boolean empate(){//todo
        return false;
    }


    // Tamaño del tablero de damas.
    public static final int TAMANIO = 8;

    // Comprueba coordenadas como a1, b6 o h8.
    public static boolean coordenadaValida(String coordenada) {
        return coordenada != null
                && coordenada.matches("(?i)[a-h][1-8]");
    }

    public static int columnaDe(String coordenada) {
        if (!coordenadaValida(coordenada)) {
            throw new IllegalArgumentException(
                    "Coordenada invalida: " + coordenada);
        }
        return Character.toLowerCase(coordenada.charAt(0)) - 'a';
    }

    public static int filaDe(String coordenada) {
        if (!coordenadaValida(coordenada)) {
            throw new IllegalArgumentException(
                    "Coordenada invalida: " + coordenada);
        }
        return coordenada.charAt(1) - '1';
    }

    public static String coordenada(int columna, int fila) {
        if (columna < 0 || columna >= TAMANIO
                || fila < 0 || fila >= TAMANIO) {
            throw new IllegalArgumentException(
                    "Posicion fuera del tablero");
        }
        return "" + (char) ('a' + columna) + (fila + 1);
    }

    // Identifica el bando según el caracter recibido del servidor.
    public static Lado ladoDe(char simbolo) {
        return switch (simbolo) {
            case 'B', 'R' -> Lado.BLANCO;
            case 'N', 'r' -> Lado.NEGRO;
            default -> null;
        };
    }

    // Crea las 12 fichas iniciales de un jugador.
    public static java.util.List<Ficha> fichasIniciales(Lado lado) {
        java.util.List<Ficha> fichas = new java.util.ArrayList<>();

        if (lado == null) {
            throw new IllegalArgumentException(
                    "Bando no especificado");
        }

        int primeraFila = lado == Lado.NEGRO ? 0 : 5;

        TipoFicha tipo = lado == Lado.NEGRO
                ? TipoFicha.NEGRA : TipoFicha.BLANCA;

        for (int fila = primeraFila; fila < primeraFila + 3; fila++) {
            for (int columna = 0; columna < TAMANIO; columna++) {
                if ((fila + columna) % 2 == 0) {
                    String id = lado.name() + "-" + (fichas.size() + 1);

                    fichas.add(new Ficha(
                            id, fila, columna, tipo));
                }
            }
        }

        return fichas;
    }

}
