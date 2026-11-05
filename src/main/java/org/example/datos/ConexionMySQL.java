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

    public static final String URL = "jdbc:mysql://" + HOST + ":" + PUERTO + "/" + BASE_DATOS
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    /** URL de conexión al servidor sin seleccionar base de datos. */
    public static final String URL_SERVIDOR = "jdbc:mysql://" + HOST + ":" + PUERTO
            + "/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    /** Usuario de MySQL en el equipo donde corre el servidor. */
    public static final String USUARIO = System.getProperty("quiz2.db.usuario", "Aaron");

    /** Contraseña de MySQL: cada grupo debe ajustar la de su equipo. */
    public static final String CLAVE = System.getProperty("quiz2.db.clave", "2424");

    /** Cantidad máxima de conexiones abiertas del pool. */
    public static final int TAMANO_POOL = 5;

    /** Conexiones disponibles del pool. */
    private static final BlockingQueue<Connection> POOL = new ArrayBlockingQueue<>(TAMANO_POOL);

    /** Permisos del pool: limitan la cantidad de conexiones simultáneas. */
    private static final Semaphore PERMISOS = new Semaphore(TAMANO_POOL);

    /** Indica si el pool ya fue inicializado. */
    private static volatile boolean poolInicializado;

    /**
     * Clase de utilidades: no se instancia.
     */
    private ConexionMySQL() {
    }

    /**
     * Carga el driver JDBC de MySQL.
     *
     * @throws SQLException si el driver no está en el classpath.
     */
    public static void cargarDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "No se encontro el driver mysql-connector-j. Verifique la libreria en la carpeta lib.", e);
        }
    }

    /**
     * Abre una conexión nueva con la base de datos.
     *
     * @return la conexión abierta.
     * @throws SQLException si la conexión falla.
     */
    public static Connection crearConexion() throws SQLException {
        cargarDriver();
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }

    /**
     * Obtiene una conexión del pool, creando una si el pool está vacío.
     *
     * @return la conexión obtenida del pool.
     * @throws SQLException si no se pudo crear la conexión.
     */
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

    /**
     * Devuelve una conexión al pool.
     *
     * @param conexion conexión a devolver, puede ser {@code null}.
     */
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

    /**
     * Cierra todas las conexiones del pool.
     */
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

    /**
     * Prepara el pool de conexiones.
     */
    private static synchronized void inicializarPool() {
        if (!poolInicializado) {
            poolInicializado = true;
        }
    }

    /**
     * Garantiza que la base de datos y las tablas del punto 6.3 existan (RF1).
     *
     * <p>El enunciado permite entregar el script {@code sql/quiz2_damas.sql},
     * pero el servidor también puede asegurar el esquema al arrancar para que
     * la solución funcione sin pasos manuales.</p>
     *
     * @throws SQLException si no se puede crear el esquema.
     */
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
