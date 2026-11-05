package org.example.red;

import org.example.logica.CanalJugador;
import org.example.logica.GestorPartida;
import org.example.logica.Lado;
import org.example.logica.LoginRechazadoException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class HiloCliente implements Runnable, CanalJugador {
    private static final int TIMEOUT_LECTURA_MS = 30_000;
    private final Socket socket;
    private final GestorPartida gestor;
    private volatile BufferedWriter salida;
    private volatile boolean activo = true;
    private volatile Lado lado;

    public HiloCliente(Socket socket, GestorPartida gestor) {
        this.socket = socket;
        this.gestor = gestor;
    }

    @Override
    public void run() {
        String origen = String.valueOf(socket.getRemoteSocketAddress());
        gestor.registrarEvento("Conexión entrante desde " + origen);
        // try-with-resources: el socket y los flujos se cierran siempre, incluso ante una excepción
        try (socket;
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            salida = out;
            socket.setSoTimeout(TIMEOUT_LECTURA_MS);
            while (activo) {
                String linea;
                try {
                    linea = entrada.readLine();
                } catch (SocketTimeoutException esperando) {
                    if (gestor.estaTerminada()) break;
                    continue; // el jugador está pensando: se sigue esperando
                }
                if (linea == null) break; // el cliente cerró la conexión
                if (!procesar(linea)) break;
            }
        } catch (IOException e) {
            if (activo) {
                gestor.registrarEvento("Conexión con " + origen + " interrumpida: " + e.getMessage());
            }
        } finally {
            activo = false;
            if (lado != null) {
                gestor.jugadorDesconectado(lado);
            }
        }
    }

    private boolean procesar(String linea) {
        Protocolo.Mensaje mensaje = Protocolo.parsear(linea);
        switch (mensaje.comando()) {
            case Protocolo.LOGIN -> {
                if (lado != null) {
                    error("ya inició sesión");
                    return true;
                }
                String[] campos = Protocolo.campos(mensaje.argumentos());
                if (campos.length != 2) {
                    error("formato esperado LOGIN:usuario|bando");
                    return true;
                }
                try {
                    lado = gestor.registrarJugador(campos[0], campos[1], this);
                } catch (LoginRechazadoException e) {
                    error(e.getMessage());
                }
            }
            case Protocolo.MOV -> {
                if (lado == null) {
                    error("debe iniciar sesión con LOGIN antes de jugar");
                    return true;
                }
                String[] casillas = mensaje.argumentos().split(",", -1);
                if (casillas.length != 2) {
                    error("formato esperado MOV:origen,destino");
                    return true;
                }
                gestor.procesarMovimiento(lado, casillas[0], casillas[1]);
            }
            case Protocolo.BYE -> {
                return false;
            }
            default -> error("mensaje desconocido: " + mensaje.comando());
        }
        return true;
    }
    private synchronized void enviarLinea(String linea) {
        BufferedWriter w = salida;
        if (!activo || w == null) {
            return;
        }
        try {
            w.write(linea);
            w.write('\n');
            w.flush();
        } catch (IOException e) {
            activo = false;
        }
    }

    @Override public void bienvenida(Lado l) { enviarLinea(Protocolo.welcome(l)); }
    @Override public void estado(String tablero64) { enviarLinea(Protocolo.state(tablero64)); }
    @Override public void turno(Lado l) { enviarLinea(Protocolo.turn(l)); }
    @Override public void ok() { enviarLinea(Protocolo.OK); }
    @Override public void error(String motivo) { enviarLinea(Protocolo.err(motivo)); }
    @Override public void captura() { enviarLinea(Protocolo.CAPTURE); }
    @Override public void coronacion() { enviarLinea(Protocolo.KING); }
    @Override public void tiempoAgotado() { enviarLinea(Protocolo.TIMEOUT); }
    @Override public void victoria(Lado ganador) { enviarLinea(Protocolo.win(ganador)); }
    @Override public void empate() { enviarLinea(Protocolo.DRAW); }
    @Override public void resultado(String resultado, int puntos) { enviarLinea(Protocolo.result(resultado, puntos)); }

    void cerrarForzado() {
        activo = false;
        try {
            socket.close();
        } catch (IOException ignorada) {
            // ya estaba cerrado
        }
    }
}