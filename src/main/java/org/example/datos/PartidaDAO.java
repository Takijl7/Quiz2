
package org.example.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class PartidaDAO {

    private static final String SQL_PARTIDA = """
            INSERT INTO partidas
            (fecha_inicio, fecha_fin, resultado,
             lado_blanco, lado_negro)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SQL_PARTICIPACION = """
            INSERT INTO participaciones
            (id_partida, id_jugador, bando,
             puntos, resultado, piezas_restantes)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_ACUMULADO = """
            UPDATE jugadores
            SET puntos_totales = puntos_totales + ?
            WHERE id_jugador = ?
            """;

    public int registrarPartida(RegistroPartida registro)
            throws SQLException {

        if (registro == null) {
            throw new SQLException(
                    "La partida no puede ser nula");
        }

        if (registro.idBlanco() <= 0
                || registro.idNegro() <= 0
                || registro.idBlanco() == registro.idNegro()) {

            throw new SQLException(
                    "Los jugadores deben tener IDs diferentes y validos");
        }

        try (Connection con =
                     ConexionMySQL.obtenerConexion()) {

            con.setAutoCommit(false);

            try {
                int idPartida =
                        insertarPartida(con, registro);

                insertarParticipacion(
                        con, idPartida,
                        registro.idBlanco(),
                        "B",
                        registro.puntosBlanco(),
                        registro.resultadoBlanco(),
                        registro.piezasBlanco()
                );

                insertarParticipacion(
                        con, idPartida,
                        registro.idNegro(),
                        "N",
                        registro.puntosNegro(),
                        registro.resultadoNegro(),
                        registro.piezasNegro()
                );

                actualizarPuntos(
                        con,
                        registro.idBlanco(),
                        registro.puntosBlanco()
                );

                actualizarPuntos(
                        con,
                        registro.idNegro(),
                        registro.puntosNegro()
                );

                con.commit();

                return idPartida;

            } catch (SQLException e) {

                try {
                    con.rollback();
                } catch (SQLException otroError) {
                    e.addSuppressed(otroError);
                }

                throw new SQLException(
                        "No se pudo guardar la partida: "
                                + e.getMessage(), e);
            }
        }
    }

    private int insertarPartida(
            Connection con,
            RegistroPartida r)
            throws SQLException {

        try (PreparedStatement ps =
                     con.prepareStatement(
                             SQL_PARTIDA,
                             Statement.RETURN_GENERATED_KEYS)) {

            ps.setTimestamp(
                    1, Timestamp.valueOf(r.inicio()));
            ps.setTimestamp(
                    2, Timestamp.valueOf(r.fin()));

            ps.setString(3, r.resultado());

            // Estas columnas son CHAR(1) en tu BD.
            ps.setString(4, "B");
            ps.setString(5, "N");

            if (ps.executeUpdate() != 1) {
                throw new SQLException(
                        "No se inserto la partida");
            }

            try (ResultSet claves =
                         ps.getGeneratedKeys()) {

                if (claves.next()) {
                    return claves.getInt(1);
                }
            }
        }

        throw new SQLException(
                "MySQL no devolvio el ID de la partida");
    }

    private void insertarParticipacion(
            Connection con,
            int idPartida,
            int idJugador,
            String bando,
            int puntos,
            String resultado,
            int piezas)
            throws SQLException {

        try (PreparedStatement ps =
                     con.prepareStatement(
                             SQL_PARTICIPACION)) {

            ps.setInt(1, idPartida);
            ps.setInt(2, idJugador);
            ps.setString(3, bando);
            ps.setInt(4, puntos);
            ps.setString(5, resultado);
            ps.setInt(6, piezas);

            if (ps.executeUpdate() != 1) {
                throw new SQLException(
                        "No se guardo la participacion");
            }
        }
    }

    private void actualizarPuntos(
            Connection con,
            int idJugador,
            int puntos)
            throws SQLException {

        try (PreparedStatement ps =
                     con.prepareStatement(SQL_ACUMULADO)) {

            ps.setInt(1, puntos);
            ps.setInt(2, idJugador);

            if (ps.executeUpdate() != 1) {
                throw new SQLException(
                        "No se actualizaron los puntos del jugador "
                                + idJugador);
            }
        }
    }
}
