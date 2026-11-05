package org.example.red;

import org.example.logica.Ficha;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class HiloFicha implements Runnable {
    private static final String CIERRE = "\u0000CIERRE";
    private final Ficha ficha;
    private final Consumer<String> emisor;
    private final BlockingQueue<String> ordenes = new LinkedBlockingQueue<>();

    public HiloFicha(Ficha ficha, Consumer<String> emisor) {
        this.ficha = ficha;
        this.emisor = emisor;
    }

    public Ficha getFicha() {
        return ficha;
    }
    public void ordenarMover(String destino) {
        ordenes.add(destino);
    }
    public void detener() {
        ordenes.add(CIERRE);
    }

    @Override
    public void run() {
        try {
            while (true) {
                String destino = ordenes.take();
                if (CIERRE.equals(destino)) { return; }
                if (ficha.estaViva()) {
                    emisor.accept(Protocolo.mov(ficha.getPosicion().notacion(), destino));
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}