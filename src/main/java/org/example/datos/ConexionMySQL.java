
package org.example.datos;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConexionMySQL {

    public static final String BASE_DATOS = "quiz2_damas";

    public static final String HOST =
            System.getProperty("quiz2.db.host", "localhost");

    public static final int PUERTO =
            Integer.getInteger("quiz2.db.puerto", 3306);

    public static final String USUARIO =
            System.getProperty("quiz2.db.usuario", "Aaron");

    public static final String CLAVE =
            System.getProperty("quiz2.db.clave", "2424");

    public static final String URL =
            "jdbc:mysql://" + HOST + ":" + PUERTO + "/"
                    + BASE_DATOS
                    + "?useSSL=false&serverTimezone=UTC"
                    + "&allowPublicKeyRetrieval=true";

    public static final String URL_SERVIDOR =
            "jdbc:mysql://" + HOST + ":" + PUERTO + "/"
                    + "?useSSL=false&serverTimezone=UTC"
                    + "&allowPublicKeyRetrieval=true";

    public static final int TAMANO_POOL = 3;

    private static final BlockingQueue<Connection> POOL =
            new ArrayBlockingQueue<>(TAMANO_POOL);

    private static final Semaphore PERMISOS =
            new Semaphore(TAMANO_POOL, true);

    private ConexionMySQL() {
    }

    public static void cargarDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "No se encontro mysql-connector-j", e);
        }
    }

    public static Connection crearConexion() throws SQLException {
        cargarDriver();
        return DriverManager.getConnection(
                URL, USUARIO, CLAVE);
    }

    /**
     * Obtiene una conexion administrada.
     * close() devuelve la conexion al pool.
     */
    public static Connection obtenerConexion()
            throws SQLException {

        try {
            if (!PERMISOS.tryAcquire(
                    5, TimeUnit.SECONDS)) {

                throw new SQLException(
                        "Tiempo agotado esperando conexion MySQL");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new SQLException(
                    "Espera de conexion interrumpida", e);
        }

        Connection real = null;

        try {
            real = POOL.poll();

            if (real != null
                    && (real.isClosed() || !real.isValid(2))) {

                cerrarFisica(real);
                real = null;
            }

            if (real == null) {
                real = crearConexion();
            }

            final Connection conexionReal = real;
            AtomicBoolean devuelta = new AtomicBoolean();

            return (Connection) Proxy.newProxyInstance(
                    ConexionMySQL.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, metodo, argumentos) -> {

                        String nombre = metodo.getName();

                        if (nombre.equals("close")
                                && metodo.getParameterCount() == 0) {

                            if (devuelta.compareAndSet(false, true)) {
                                devolverReal(conexionReal);
                            }

                            return null;
                        }

                        if (nombre.equals("isClosed")
                                && metodo.getParameterCount() == 0) {

                            return devuelta.get()
                                    || conexionReal.isClosed();
                        }

                        if (devuelta.get()) {
                            throw new SQLException(
                                    "Conexion ya devuelta al pool");
                        }

                        try {
                            return metodo.invoke(
                                    conexionReal, argumentos);

                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });

        } catch (Exception e) {

            cerrarFisica(real);
            PERMISOS.release();

            if (e instanceof SQLException sql) {
                throw sql;
            }

            throw new SQLException(
                    "Error obteniendo conexion MySQL", e);
        }
    }

    private static void devolverReal(Connection real) {

        boolean reutilizada = false;

        try {
            if (!real.isClosed()) {

                if (!real.getAutoCommit()) {
                    real.rollback();
                }

                real.setAutoCommit(true);
                real.setReadOnly(false);

                reutilizada = POOL.offer(real);
            }

        } catch (SQLException e) {
            System.err.println(
                    "Conexion descartada: " + e.getMessage());

        } finally {

            if (!reutilizada) {
                cerrarFisica(real);
            }

            PERMISOS.release();
        }
    }

    private static void cerrarFisica(Connection conexion) {
        if (conexion == null) {
            return;
        }

        try {
            conexion.close();
        } catch (SQLException ignored) {
        }
    }

    public static void devolverConexion(Connection conexion) {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public static void cerrarPool() {

        Connection conexion;

        while ((conexion = POOL.poll()) != null) {
            cerrarFisica(conexion);
        }
    }

    /**
     * Crea las tablas solo cuando no existen.
     * No altera ni elimina tablas existentes.
     */
    public static void asegurarEsquema()
            throws SQLException {

        cargarDriver();

        try (Connection conexion =
                     DriverManager.getConnection(
                             URL_SERVIDOR, USUARIO, CLAVE);
             Statement sentencia =
                     conexion.createStatement()) {

            sentencia.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS "
                            + BASE_DATOS
                            + " DEFAULT CHARACTER SET utf8mb4 "
                            + "COLLATE utf8mb4_unicode_ci");

        } catch (SQLException e) {
            // Puede existir la BD sin permiso de CREATE.
            // La conexion siguiente comprobara el acceso.
        }

        try (Connection conexion = crearConexion();
             Statement sentencia =
                     conexion.createStatement()) {

            sentencia.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS jugadores (
                        id_jugador INT AUTO_INCREMENT PRIMARY KEY,
                        usuario VARCHAR(50) NOT NULL UNIQUE,
                        puntos_totales INT NOT NULL DEFAULT 0,
                        fecha_registro TIMESTAMP
                            DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB
                    """);

            sentencia.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS partidas (
                        id_partida INT AUTO_INCREMENT PRIMARY KEY,
                        fecha_inicio DATETIME NOT NULL,
                        fecha_fin DATETIME NOT NULL,
                        resultado VARCHAR(16) NOT NULL,
                        lado_blanco CHAR(1) NOT NULL,
                        lado_negro CHAR(1) NOT NULL
                    ) ENGINE=InnoDB
                    """);

            sentencia.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS participaciones (
                        id_partida INT NOT NULL,
                        id_jugador INT NOT NULL,
                        bando CHAR(1) NOT NULL,
                        puntos INT NOT NULL DEFAULT 0,
                        resultado VARCHAR(12) NOT NULL,
                        piezas_restantes INT NOT NULL DEFAULT 0,

                        PRIMARY KEY (
                            id_partida, id_jugador),

                        CONSTRAINT fk_part
                            FOREIGN KEY (id_partida)
                            REFERENCES partidas(id_partida),

                        CONSTRAINT fk_jug
                            FOREIGN KEY (id_jugador)
                            REFERENCES jugadores(id_jugador)
                    ) ENGINE=InnoDB
                    """);
        }
    }
}
