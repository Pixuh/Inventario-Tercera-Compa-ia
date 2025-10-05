import java.sql.*;
import java.util.*;

public class EraKitDAO {

    public int crearKit(int idEppArnes, int idEppMascara) throws SQLException {
        String sql = "INSERT INTO era_kit(id_epp_arnes, id_epp_mascara) VALUES(?,?) RETURNING id_kit";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEppArnes);
            ps.setInt(2, idEppMascara);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        throw new SQLException("No se pudo crear ERA kit");
    }

    public void agregarCilindro(int idKit, int idEppCilindro) throws SQLException {
        String sql = "INSERT INTO era_kit_cilindro(id_kit, id_epp_cilindro) VALUES(?,?) ON CONFLICT DO NOTHING";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idKit);
            ps.setInt(2, idEppCilindro);
            ps.executeUpdate();
        }
    }

    public Map<String,Object> obtenerKit(int idKit) throws SQLException {
        String cab = """
            SELECT k.id_kit, a.id_epp AS id_arnes, m.id_epp AS id_mascara
            FROM era_kit k
            JOIN epp_item a ON a.id_epp = k.id_epp_arnes
            JOIN epp_item m ON m.id_epp = k.id_epp_mascara
            WHERE k.id_kit=?
            """;
        String det = """
            SELECT c.id_epp_cilindro, ei.n_serie
            FROM era_kit_cilindro c
            JOIN epp_item ei ON ei.id_epp = c.id_epp_cilindro
            WHERE c.id_kit=?
            ORDER BY c.id_epp_cilindro
            """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps1 = c.prepareStatement(cab);
             PreparedStatement ps2 = c.prepareStatement(det)) {

            ps1.setInt(1, idKit);
            Map<String,Object> out = new LinkedHashMap<>();
            try (ResultSet rs = ps1.executeQuery()) {
                if (!rs.next()) return null;
                out.put("id_kit", rs.getInt(1));
                out.put("id_arnes", rs.getInt(2));
                out.put("id_mascara", rs.getInt(3));
            }

            ps2.setInt(1, idKit);
            List<Map<String,Object>> cilindros = new ArrayList<>();
            try (ResultSet rs = ps2.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> cRow = new LinkedHashMap<>();
                    cRow.put("id_epp_cilindro", rs.getInt(1));
                    cRow.put("n_serie", rs.getString(2));
                    cilindros.add(cRow);
                }
            }
            out.put("cilindros", cilindros);
            return out;
        }
    }
}
