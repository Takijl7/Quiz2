package org.example.datos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

public class ConexionMySQL {
    public static final String BASE_DATOS = "quiz2_damas";
    public static final String HOST = System.getProperty("quiz2.db.host", "localhost");
    public static final int PUERTO = Integer.getInteger("quiz2.db.puerto", 3306);
    public static final String URL = "jdbc:mysql://" + HOST + ":" + PUERTO + "/" + BASE_DATOS + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    public static final String URL_SERVIDOR = "jdbc:mysql://" + HOST + ":" + PUERTO + "/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    public static final String USUARIO = System.getProperty("quiz2.db.usuario", "root");
    public static final String CLAVE = System.getProperty("quiz2.db.clave", "");
    public static final int TAMANO_POOL = 3;
    private static final BlockingQueue<Connection> POOL = new ArrayBlockingQueue<>(TAMANO_POOL);
    private static final Semaphore PERMISOS = new Semaphore(TAMANO_POOL);
    private static volatile boolean poolInicializado;

    private ConexionMySQL() {}

    public static void cargarDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "No se encontro el driver mysql-connector-j. Verifique la libreria en la carpeta lib.", e);
        }
    }

    public static Connection crearConexion() throws SQLException {
        cargarDriver();
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }

    public static Connection obtenerConexion() throws SQLException {
        inicializarPool();
        try {
            PERMISOS.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrumpido al esperar una conexion del pool.", e);
        }
        Connection conexion = POOL.poll();
        if (conexion == null || conexion.isClosed() || !conexion.isValid(2)) {
            try {
                conexion = crearConexion();
            } catch (SQLException e) {
                PERMISOS.release();
                throw e;
            }
        }
        return conexion;
    }

    public static void devolverConexion(Connection conexion) {
        try {
            if (conexion != null && !conexion.isClosed()) {
                conexion.setAutoCommit(true);
                POOL.offer(conexion);
            }
        } catch (SQLException e) {
            // Una conexión caída se descarta: el pool abre otra la proxima vez.
        } finally {
            PERMISOS.release();
        }
    }

    public static void cerrarPool() {
        Connection conexion;
        while ((conexion = POOL.poll()) != null) {
            try {
                conexion.close();
            } catch (SQLException e) {
                // Se ignora: la conexion ya no se va a reutilizar.
            }
        }
        poolInicializado = false;
    }

    private static synchronized void inicializarPool() {
        if (!poolInicializado) {
            poolInicializado = true;
        }
    }

    public static void asegurarEsquema() throws SQLException {
        cargarDriver();

        // 1. Crear la base de datos si no existe.
        try (Connection conexion = DriverManager.getConnection(URL_SERVIDOR, USUARIO, CLAVE);
             Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate("CREATE DATABASE IF NOT EXISTS " + BASE_DATOS
                    + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            // Puede que el usuario no tenga permiso de CREATE y la base ya exista.
            // En ese caso se sigue: el siguiente paso trabaja sobre la base.
        }

        // 2. Crear las tablas normalizadas sobre la base de datos.
        try (Connection conexion = DriverManager.getConnection(URL, USUARIO, CLAVE);
             Statement sentencia = conexion.createStatement()) {

            sentencia.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS jugadores (
                        id_jugador     INT AUTO_INCREMENT PRIMARY KEY,
                        usuario        VARCHAR(50) NOT NULL UNIQUE,
                        puntos_totales INT  NOT NULL DEFAULT 0,
                        fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB""");

            sentencia.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS partidas (
                        id_partida   INT AUTO_INCREMENT PRIMARY KEY,
                        fecha_inicio DATETIME NOT NULL,
                        fecha_fin    DATETIME NOT NULL,
                        resultado    VARCHAR(16) NOT NULL,
                        lado_blanco  CHAR(1) NOT NULL,
                        lado_negro   CHAR(1) NOT NULL
                    ) ENGINE=InnoDB""");

            sentencia.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS participaciones (
                        id_partida         INT NOT NULL,
                        id_jugador        INT NOT NULL,
                        bando             CHAR(1) NOT NULL,
                        puntos            INT NOT NULL DEFAULT 0,
                        resultado         VARCHAR(12) NOT NULL,
                        piezas_restantes  INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id_partida, id_jugador),
                        CONSTRAINT fk_part FOREIGN KEY (id_partida)  REFERENCES partidas(id_partida),
                        CONSTRAINT fk_jug  FOREIGN KEY (id_jugador) REFERENCES jugadores(id_jugador)
                    ) ENGINE=InnoDB""");
        }
    }
}