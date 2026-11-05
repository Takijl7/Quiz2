package org.example.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

public class JugadorDAO {

    private static final String SQL_BUSCAR = "SELECT id_jugador, usuario, puntos_totales FROM jugadores WHERE usuario = ?";
    private static final String SQL_INSERTAR = "INSERT INTO jugadores (usuario) VALUES (?)";

    private static final String SQL_RANKING = """
            SELECT j.usuario, j.puntos_totales,
                   COUNT(p.id_partida) AS partidas,
                   COALESCE(SUM(p.resultado = 'VICTORIA'), 0) AS victorias
            FROM jugadores j
            LEFT JOIN participaciones p ON p.id_jugador = j.id_jugador
            GROUP BY j.id_jugador, j.usuario, j.puntos_totales
            ORDER BY j.puntos_totales DESC, victorias DESC, j.usuario ASC
            LIMIT 10
            """;

    public Jugador obtenerOCrear(String usuario) throws SQLException {
        try (Connection con = ConexionMySQL.obtenerConexion()) {
            Jugador existente = buscar(con, usuario);
            if (existente != null) {
                return existente;
            }
            try (PreparedStatement ps = con.prepareStatement(SQL_INSERTAR)) {
                ps.setString(1, usuario);
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException duplicado) {
                // otro hilo lo insertó justo antes: se reutiliza
            }
            Jugador creado = buscar(con, usuario);
            if (creado == null) {
                throw new SQLException("No se pudo registrar al jugador '" + usuario + "'");
            }
            return creado;
        }
    }

    private Jugador buscar(Connection con, String usuario) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_BUSCAR)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Jugador(rs.getInt("id_jugador"), rs.getString("usuario"), rs.getInt("puntos_totales"));
                }
                return null;
            }
        }
    }

    public List<FilaRanking> ranking() throws SQLException {
        List<FilaRanking> filas = new ArrayList<>();
        try (Connection con = ConexionMySQL.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(SQL_RANKING);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                filas.add(new FilaRanking(rs.getString("usuario"), rs.getInt("puntos_totales"),
                        rs.getInt("partidas"), rs.getInt("victorias")));
            }
        }
        return filas;
    }
}