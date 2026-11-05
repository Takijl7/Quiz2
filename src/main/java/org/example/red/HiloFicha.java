
package org.example.red;

import org.example.logica.Ficha;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class HiloFicha implements Runnable {

    private static final String CIERRE = "\u0000CIERRE";

    private final Ficha ficha;
    private final BiConsumer<String, String> emisor;
    private final BlockingQueue<String> ordenes = new LinkedBlockingQueue<>();

    private volatile boolean activo = true;
    private Thread hilo;

    public HiloFicha(Ficha ficha, BiConsumer<String, String> emisor) {
        this.ficha = ficha;
        this.emisor = emisor;
    }

    public Ficha getFicha() {
        return ficha;
    }

    // Cada ficha tiene su propio hilo.
    public synchronized void iniciar() {
        if (hilo != null) {
            return;
        }

        hilo = new Thread(this, "Ficha-" + ficha.getId());
        hilo.start();
    }

    // Recibe una solicitud de movimiento.
    public void solicitarMovimiento(String destino) {
        if (activo && destino != null) {
            ordenes.offer(destino.trim());
        }
    }

    // Detiene el procesamiento de movimientos.
    public void interrumpir() {
        activo = false;
        ordenes.clear();
        ordenes.offer(CIERRE);
    }

    @Override
    public void run() {
        try {
            while (activo) {
                String destino = ordenes.take();

                if (!activo || CIERRE.equals(destino)) {
                    break;
                }

                // En Ficha, posX representa fila
                // y posY representa columna.
                String origen = ""
                        + (char) ('a' + ficha.getPosY())
                        + (ficha.getPosX() + 1);

                // El cliente se encarga de enviar MOV.
                emisor.accept(origen, destino);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
