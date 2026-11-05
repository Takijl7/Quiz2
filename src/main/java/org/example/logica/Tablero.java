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

                // Primeras tres filas: fichas blancas
                if (i < 3 && (i + j) % 2 == 0) {
                    tablero[i][j] = new Ficha(id, i, j, TipoFicha.BLANCA);
                }

                // Últimas tres filas: fichas negras
                else if (i > 4 && (i + j) % 2 == 0) {
                    tablero[i][j] = new Ficha(id, i, j, TipoFicha.NEGRA);
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

    public boolean desplazamientoValido(Ficha f, int nX, int nY){
        int x = f.getPosX();
        int y = f.getPosY();

        //Comprovamos que el movimiento sea una casilla


        //Movimiento a la misma casilla es invalido
        if(x == nX && y == nY){return false;}

        //Validamos la posicion final y la direcion, si es reina cualquier direccion es compatible
        if(dentro(nX,nY) && tablero[nX][nY] == null){
            if(f.getTipo() == TipoFicha.REINA){

                //Comprobamos el movimiento en diagonalw
                if(esDiagonal(f,nX,nY)){

                }
            }else if(f.getTipo() == TipoFicha.BLANCA){

            }else if(f.getTipo() == TipoFicha.NEGRA){}
        }
        return false;
    }

    public boolean comerValido(Ficha f, int nX, int nY){
        int x = f.getPosX();
        int y = f.getPosY();

        //Comprovamos que el movimiento sean dos casillas


        //Buscamos

        return true;
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
}
