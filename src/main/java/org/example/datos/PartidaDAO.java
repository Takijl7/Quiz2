package org.example.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class PartidaDAO {
    private static final String SQL_PARTIDA = "INSERT INTO partidas (fecha_inicio, fecha_fin, resultado, lado_blanco, lado_negro) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_PARTICIPACION = "INSERT INTO participaciones (id_partida, id_jugador, bando, puntos, resultado, piezas_restantes) " + "VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACUMULADO = "UPDATE jugadores SET puntos_totales = puntos_totales + ? WHERE id_jugador = ?";

    public int registrarPartida(RegistroPartida r) throws SQLException {
        try (Connection con = ConexionMySQL.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                int idPartida = insertarPartida(con, r);
                insertarParticipacion(con, idPartida, r.idBlanco(), "B", r.puntosBlanco(), r.resultadoBlanco(), r.piezasBlanco());
                insertarParticipacion(con, idPartida, r.idNegro(), "N", r.puntosNegro(), r.resultadoNegro(), r.piezasNegro());
                sumarPuntos(con, r.idBlanco(), r.puntosBlanco());
                sumarPuntos(con, r.idNegro(), r.puntosNegro());
                con.commit();
                return idPartida;
            } catch (SQLException e) {
                con.rollback();
                throw new SQLException("No se pudo registrar la partida (se hizo rollback): " + e.getMessage(), e);
            }
        }
    }

    private int insertarPartida(Connection con, RegistroPartida r) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_PARTIDA, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, Timestamp.valueOf(r.inicio()));
            ps.setTimestamp(2, Timestamp.valueOf(r.fin()));
            ps.setString(3, r.resultado());
            ps.setInt(4, r.idBlanco());
            ps.setInt(5, r.idNegro());
            ps.executeUpdate();
            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (claves.next()) {
                    return claves.getInt(1);
                }
                throw new SQLException("MySQL no devolvió el id de la partida");
            }
        }
    }

    private void insertarParticipacion(Connection con, int idPartida, int idJugador, String bando,
                                       int puntos, String resultado, int piezas) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_PARTICIPACION)) {
            ps.setInt(1, idPartida);
            ps.setInt(2, idJugador);
            ps.setString(3, bando);
            ps.setInt(4, puntos);
            ps.setString(5, resultado);
            ps.setInt(6, piezas);
            ps.executeUpdate();
        }
    }

    private void sumarPuntos(Connection con, int idJugador, int puntos) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_ACUMULADO)) {
            ps.setInt(1, puntos);
            ps.setInt(2, idJugador);
            ps.executeUpdate();
        }
    }
}