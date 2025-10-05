import java.sql.*;
import java.util.*;

public class EppTipoDAO {

    public int crearTipo(String tipo, String subtipo) throws SQLException {
        String sql = "INSERT INTO epp_tipo (tipo, subtipo) VALUES (?, ?) RETURNING id_tipo";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setString(2, subtipo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se pudo crear epp_tipo");
    }

    public Integer getIdTipo(String tipo, String subtipo) throws SQLException {
        String sql = "SELECT id_tipo FROM epp_tipo WHERE tipo=? AND subtipo=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setString(2, subtipo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return null;
            }
        }
    }

    public List<String[]> listarTipos() throws SQLException {
        String sql = "SELECT id_tipo, tipo, subtipo FROM epp_tipo ORDER BY tipo, subtipo";
        List<String[]> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new String[]{ String.valueOf(rs.getInt(1)), rs.getString(2), rs.getString(3) });
            }
        }
        return out;
    }
}
