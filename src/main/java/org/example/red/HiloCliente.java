
package org.example.red;

import org.example.logica.Lado;
import org.example.logica.Tablero;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Atiende a un jugador conectado mediante
 * su propio hilo de servidor.
 */
public class HiloCliente implements Runnable {

    private static final int TIMEOUT_LECTURA_MS = 30_000;

    private final Socket socket;
    private final ServidorDamas servidor;

    private BufferedWriter salida;
    private volatile boolean activo = true;
    private volatile Lado lado;
    private volatile String usuario;

    private volatile int idJugador = -1;
    private volatile int puntosAcumulados;

    private volatile BiConsumer<String, String> procesadorMovimiento;
    private volatile Runnable alDesconectar;

    public HiloCliente(Socket socket, ServidorDamas servidor) {
        this.socket = Objects.requireNonNull(socket);
        this.servidor = Objects.requireNonNull(servidor);
    }

    public String getUsuario() {
        return usuario;
    }

    public Lado getLado() {
        return lado;
    }

    public int getIdJugador() {
        return idJugador;
    }

    public int getPuntosAcumulados() {
        return puntosAcumulados;
    }

    public void setIdJugador(int idJugador) {
        this.idJugador = idJugador;
    }

    public void setPuntosAcumulados(int puntos) {
        this.puntosAcumulados = puntos;
    }

    /*
     * El servidor conectará aquí el procesamiento
     * de movimientos cuando se integre la partida.
     */
    public void setProcesadorMovimiento(
            BiConsumer<String, String> procesador) {
        this.procesadorMovimiento =
                Objects.requireNonNull(procesador);
    }

    public void setAlDesconectar(Runnable accion) {
        this.alDesconectar =
                Objects.requireNonNull(accion);
    }

    @Override
    public void run() {

        try (Socket conexion = socket;
             BufferedReader entrada =
                     new BufferedReader(
                             new InputStreamReader(
                                     conexion.getInputStream(),
                                     StandardCharsets.UTF_8));
             BufferedWriter escritor =
                     new BufferedWriter(
                             new OutputStreamWriter(
                                     conexion.getOutputStream(),
                                     StandardCharsets.UTF_8))) {

            synchronized (this) {
                salida = escritor;
            }

            conexion.setSoTimeout(TIMEOUT_LECTURA_MS);

            while (activo) {
                String linea;

                try {
                    linea = entrada.readLine();
                } catch (SocketTimeoutException e) {
                    // El temporizador del turno
                    // lo controla el servidor.
                    continue;
                }

                if (linea == null || !procesar(linea)) {
                    break;
                }
            }

        } catch (IOException e) {
            if (activo) {
                System.err.println(
                        "[SERVIDOR] Conexion interrumpida: "
                                + e.getMessage());
            }

        } finally {
            activo = false;

            Runnable accion = alDesconectar;

            if (accion != null && lado != null) {
                accion.run();
            }
        }
    }

    private boolean procesar(String linea) {

        Protocolo.Mensaje mensaje =
                Protocolo.parsear(linea);

        switch (mensaje.comando()) {

            case Protocolo.LOGIN ->
                    procesarLogin(mensaje.argumentos());

            case Protocolo.MOV ->
                    procesarMovimiento(mensaje.argumentos());

            case Protocolo.BYE -> {
                return false;
            }

            default -> enviar(
                    Protocolo.err(
                            "Comando no reconocido: "
                                    + mensaje.comando()));
        }

        return true;
    }

    private void procesarLogin(String argumentos) {

        if (lado != null) {
            enviar(Protocolo.err("Ya inicio sesion"));
            return;
        }

        String[] campos = Protocolo.campos(argumentos);

        if (campos.length != 2) {
            enviar(Protocolo.err(
                    "Formato: LOGIN:usuario|bando"));
            return;
        }

        String nombre = campos[0].trim();

        if (nombre.isEmpty() || nombre.length() > 50) {
            enviar(Protocolo.err(
                    "El usuario debe tener entre 1 y 50 caracteres"));
            return;
        }

        Lado preferido;

        try {
            preferido = Lado.valueOf(
                    campos[1].trim().toUpperCase(Locale.ROOT));

        } catch (IllegalArgumentException e) {
            enviar(Protocolo.err(
                    "Bando invalido; use BLANCO o NEGRO"));
            return;
        }

        Lado asignado = servidor.asignarBando(preferido);

        if (asignado == null) {
            enviar(Protocolo.err(
                    "La partida ya tiene dos jugadores"));
            return;
        }

        usuario = nombre;
        lado = asignado;

        // Registrar jugador en la base de datos.
        servidor.registrarJugador(this);

        // Comunicar al cliente su bando asignado.
        enviar(Protocolo.welcome(asignado));

        // Avisar al servidor que el cliente ingreso.
        servidor.registrarCliente(this);
    }

    private void procesarMovimiento(String argumentos) {

        if (lado == null) {
            enviar(Protocolo.err(
                    "Debe iniciar sesion antes de jugar"));
            return;
        }

        String[] casillas = argumentos.split(",", -1);

        if (casillas.length != 2) {
            enviar(Protocolo.err(
                    "Formato: MOV:origen,destino"));
            return;
        }

        String origen =
                casillas[0].trim().toLowerCase(Locale.ROOT);

        String destino =
                casillas[1].trim().toLowerCase(Locale.ROOT);

        if (!Tablero.coordenadaValida(origen)
                || !Tablero.coordenadaValida(destino)) {

            enviar(Protocolo.err(
                    "Coordenadas invalidas"));
            return;
        }

        /*
         * El servidor es la autoridad del tablero.
         * Este hilo solo recibe y entrega la jugada.
         */
        BiConsumer<String, String> procesador =
                procesadorMovimiento;

        if (procesador == null) {
            enviar(Protocolo.err(
                    "La partida aun no admite movimientos"));
            return;
        }

        procesador.accept(origen, destino);
    }

    /*
     * Los mensajes se envian de manera sincronizada
     * para evitar que varios hilos mezclen la salida.
     */
    public synchronized void enviar(String mensaje) {

        if (!activo || salida == null) {
            return;
        }

        try {
            salida.write(mensaje);
            salida.newLine();
            salida.flush();

        } catch (IOException e) {
            activo = false;
            cerrarForzado();
        }
    }

    public void cerrarForzado() {

        activo = false;

        try {
            socket.close();

        } catch (IOException ignored) {
            // El socket ya estaba cerrado.
        }
    }
}
