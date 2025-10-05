import java.sql.*;
import java.util.*;

public class EppMantencionDAO {

    public int iniciarMantencion(int idEpp, String tipo, String observacion) throws SQLException {
        String ins = "INSERT INTO epp_mantencion(id_epp, fecha_inicio, tipo, observacion) VALUES (?, CURRENT_TIMESTAMP, ?, ?) RETURNING id_mantencion";
        String upd = "UPDATE epp_item SET estado='EN_MANTENCION' WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            int id;
            try (PreparedStatement ps = c.prepareStatement(ins)) {
                ps.setInt(1, idEpp);
                ps.setString(2, tipo);
                ps.setString(3, observacion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); throw new SQLException("No se pudo iniciar mantención"); }
                    id = rs.getInt(1);
                }
            }
            try (PreparedStatement psU = c.prepareStatement(upd)) {
                psU.setInt(1, idEpp);
                psU.executeUpdate();
            }
            c.commit();
            return id;
        }
    }

    public void cerrarMantencion(int idMantencion, java.util.Date fechaFin, String observacion, java.util.Date proximaMant) throws SQLException {
        String sel = "SELECT id_epp FROM epp_mantencion WHERE id_mantencion=? AND fecha_fin IS NULL FOR UPDATE";
        String updM = "UPDATE epp_mantencion SET fecha_fin=?, observacion=COALESCE(?, observacion) WHERE id_mantencion=?";
        String updI = "UPDATE epp_item SET estado='SERVICIO', proxima_mantencion=COALESCE(?, proxima_mantencion) WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            Integer idEpp=null;
            try (PreparedStatement ps = c.prepareStatement(sel)) {
                ps.setInt(1, idMantencion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); throw new SQLException("Mantención no abierta"); }
                    idEpp = rs.getInt(1);
                }
            }
            try (PreparedStatement psM = c.prepareStatement(updM)) {
                psM.setTimestamp(1, new java.sql.Timestamp( (fechaFin!=null?fechaFin:new java.util.Date()).getTime() ));
                psM.setString(2, observacion);
                psM.setInt(3, idMantencion);
                psM.executeUpdate();
            }
            try (PreparedStatement psI = c.prepareStatement(updI)) {
                psI.setDate(1, proximaMant==null?null:new java.sql.Date(proximaMant.getTime()));
                psI.setInt(2, idEpp);
                psI.executeUpdate();
            }
            c.commit();
        }
    }

    public List<Map<String,Object>> historialPorItem(int idEpp) throws SQLException {
        String sql = "SELECT id_mantencion, fecha_inicio, fecha_fin, tipo, observacion FROM epp_mantencion WHERE id_epp=? ORDER BY fecha_inicio DESC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id_mantencion", rs.getInt(1));
                    m.put("fecha_inicio", rs.getTimestamp(2));
                    m.put("fecha_fin", rs.getTimestamp(3));
                    m.put("tipo", rs.getString(4));
                    m.put("observacion", rs.getString(5));
                    out.add(m);
                }
                return out;
            }
        }
    }
}
