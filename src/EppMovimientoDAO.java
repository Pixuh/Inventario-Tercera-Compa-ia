import java.sql.*;
import java.util.*;

public class EppMovimientoDAO {

    public List<Map<String,Object>> listarPorItem(int idEpp, Integer limit) throws SQLException {
        String sql = """
            SELECT m.id_mov, m.fecha, m.motivo,
                   b1.nombre AS desde_bodega, s1.nombre AS desde_subbodega,
                   b2.nombre AS hacia_bodega, s2.nombre AS hacia_subbodega
            FROM epp_movimiento m
            JOIN bodega_principal b1 ON b1.idbodega_principal = m.desde_bodega
            JOIN subbodega       s1 ON s1.idsubbodega       = m.desde_subbodega
            JOIN bodega_principal b2 ON b2.idbodega_principal = m.hacia_bodega
            JOIN subbodega       s2 ON s2.idsubbodega       = m.hacia_subbodega
            WHERE m.id_epp=?
            ORDER BY m.fecha DESC
            """;
        if (limit != null && limit > 0) sql += " LIMIT " + limit;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id_mov", rs.getInt(1));
                    m.put("fecha", rs.getTimestamp(2));
                    m.put("motivo", rs.getString(3));
                    m.put("desde_bodega", rs.getString(4));
                    m.put("desde_subbodega", rs.getString(5));
                    m.put("hacia_bodega", rs.getString(6));
                    m.put("hacia_subbodega", rs.getString(7));
                    out.add(m);
                }
                return out;
            }
        }
    }

    public void moverItem(int idEpp, int idBodegaDestino, int idSubbodegaDestino, String motivo) throws SQLException {
        final String selectUbic =
                "SELECT idbodega_principal, idsubbodega FROM epp_item WHERE id_epp=? FOR UPDATE";
        final String updItem =
                "UPDATE epp_item SET idbodega_principal=?, idsubbodega=? WHERE id_epp=?";
        final String insMov =
                "INSERT INTO epp_movimiento(id_epp, desde_bodega, desde_subbodega, hacia_bodega, hacia_subbodega, motivo) " +
                        "VALUES(?, ?, ?, ?, ?, ?)";

        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);

            Integer desdeB = null, desdeS = null;

            // 1) Bloquear y leer ubicación actual
            try (PreparedStatement ps = c.prepareStatement(selectUbic)) {
                ps.setInt(1, idEpp);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        throw new SQLException("EPP no existe");
                    }
                    desdeB = rs.getInt(1);
                    desdeS = rs.getInt(2);
                }
            }

            // 2) Actualizar ubicación
            try (PreparedStatement psU = c.prepareStatement(updItem)) {
                psU.setInt(1, idBodegaDestino);
                psU.setInt(2, idSubbodegaDestino);
                psU.setInt(3, idEpp);
                if (psU.executeUpdate() == 0) {
                    c.rollback();
                    throw new SQLException("No se pudo actualizar la ubicación del EPP");
                }
            }

            // 3) Registrar movimiento
            try (PreparedStatement psI = c.prepareStatement(insMov)) {
                psI.setInt(1, idEpp);
                psI.setInt(2, desdeB);
                psI.setInt(3, desdeS);
                psI.setInt(4, idBodegaDestino);
                psI.setInt(5, idSubbodegaDestino);
                psI.setString(6, (motivo == null || motivo.isBlank()) ? "TRASLADO" : motivo.trim());
                psI.executeUpdate();
            }

            c.commit();
        }
    }

}
