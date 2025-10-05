import java.sql.*;
import java.util.*;

public class EppAsignacionDAO {

    public int asignar(int idEpp, int idUsuario, String observacion) throws SQLException {
        String sqlIns = "INSERT INTO epp_asignacion(id_epp, idusuario, observacion) VALUES (?,?,?) RETURNING id_asignacion";
        String sqlUpd = "UPDATE epp_item SET estado='SERVICIO' WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            int id;
            try (PreparedStatement ps = c.prepareStatement(sqlIns)) {
                ps.setInt(1, idEpp);
                ps.setInt(2, idUsuario);
                ps.setString(3, observacion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); throw new SQLException("No se pudo asignar"); }
                    id = rs.getInt(1);
                }
            }
            try (PreparedStatement psU = c.prepareStatement(sqlUpd)) {
                psU.setInt(1, idEpp);
                psU.executeUpdate();
            }
            c.commit();
            return id;
        }
    }

    public void devolver(int idAsignacion, String observacion) throws SQLException {
        String sel = "SELECT id_epp FROM epp_asignacion WHERE id_asignacion=? AND fecha_devolucion IS NULL FOR UPDATE";
        String updAsig = "UPDATE epp_asignacion SET fecha_devolucion=CURRENT_TIMESTAMP, observacion=COALESCE(?, observacion) WHERE id_asignacion=?";
        String updItem = "UPDATE epp_item SET estado='SERVICIO' WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            Integer idEpp=null;
            try (PreparedStatement ps = c.prepareStatement(sel)) {
                ps.setInt(1, idAsignacion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); throw new SQLException("Asignación no abierta"); }
                    idEpp = rs.getInt(1);
                }
            }
            try (PreparedStatement psA = c.prepareStatement(updAsig)) {
                psA.setString(1, observacion);
                psA.setInt(2, idAsignacion);
                psA.executeUpdate();
            }
            try (PreparedStatement psI = c.prepareStatement(updItem)) {
                psI.setInt(1, idEpp);
                psI.executeUpdate();
            }
            c.commit();
        }
    }

    public List<Map<String,Object>> historialPorItem(int idEpp) throws SQLException {
        String sql = """
            SELECT a.id_asignacion, u.nombre AS usuario, a.fecha_asignacion, a.fecha_devolucion, a.observacion
            FROM epp_asignacion a
            JOIN usuario u ON u.idusuario = a.idusuario
            WHERE a.id_epp=?
            ORDER BY a.fecha_asignacion DESC
            """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("id_asignacion", rs.getInt(1));
                    row.put("usuario", rs.getString(2));
                    row.put("fecha_asignacion", rs.getTimestamp(3));
                    row.put("fecha_devolucion", rs.getTimestamp(4));
                    row.put("observacion", rs.getString(5));
                    out.add(row);
                }
                return out;
            }
        }
    }
}
