import java.sql.*;
import java.util.*;

public class EppInspeccionDAO {

    public int registrarInspeccion(int idEpp, String resultado, String observacion, java.util.Date proxima) throws SQLException {
        String ins = "INSERT INTO epp_inspeccion(id_epp, fecha, resultado, observacion, proxima_inspeccion) VALUES(?, CURRENT_DATE, ?, ?, ?) RETURNING id_inspeccion";
        String upd = "UPDATE epp_item SET proxima_mantencion = COALESCE(?, proxima_mantencion) WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            int id;
            try (PreparedStatement ps = c.prepareStatement(ins)) {
                ps.setInt(1, idEpp);
                ps.setString(2, resultado);
                ps.setString(3, observacion);
                ps.setDate(4, proxima == null ? null : new java.sql.Date(proxima.getTime()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); throw new SQLException("No se pudo registrar inspección"); }
                    id = rs.getInt(1);
                }
            }
            try (PreparedStatement psU = c.prepareStatement(upd)) {
                psU.setDate(1, proxima == null ? null : new java.sql.Date(proxima.getTime()));
                psU.setInt(2, idEpp);
                psU.executeUpdate();
            }
            c.commit();
            return id;
        }
    }

    public List<Map<String,Object>> inspeccionesPorItem(int idEpp) throws SQLException {
        String sql = "SELECT id_inspeccion, fecha, resultado, observacion, proxima_inspeccion FROM epp_inspeccion WHERE id_epp=? ORDER BY fecha DESC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id_inspeccion", rs.getInt(1));
                    m.put("fecha", rs.getDate(2));
                    m.put("resultado", rs.getString(3));
                    m.put("observacion", rs.getString(4));
                    m.put("proxima_inspeccion", rs.getDate(5));
                    out.add(m);
                }
                return out;
            }
        }
    }
}
