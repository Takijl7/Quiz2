package org.example.logica;

public class Ficha {
    private String id;
    private int posX;
    private int posY;
    private TipoFicha tipo;

    private boolean corona;

    public Ficha(String id, int posX, int posY, TipoFicha tipo) {
        this.id = id;
        this.posX = posX;
        this.tipo = tipo;
        this.posY = posY;
        corona = false;
    }

    public void mover(int x, int y){
        posX = x;
        posY = y;
    }

    public void coronada(){corona = true;}

    //Getter y Setters

    public int getPosX() {return posX;}

    public void setPosX(int posX) {this.posX = posX;}

    public int getPosY() {return posY;}

    public void setPosY(int posY) {this.posY = posY;}

    public TipoFicha getTipo() {
        return tipo;
    }

    public void setTipo(TipoFicha tipo) {
        this.tipo = tipo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
