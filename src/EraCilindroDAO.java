import java.sql.*;
import java.util.*;

public class EraCilindroDAO {

    public void crearOActualizar(int idEppCilindro, double capacidadL, int presionBar, java.util.Date fechaUltHidro, java.util.Date proxHidro) throws SQLException {
        String upsert = """
            INSERT INTO era_cilindro(id_epp, capacidad_l, presion_bar, fecha_ult_hidro, prox_hidro)
            VALUES(?,?,?,?,?)
            ON CONFLICT (id_epp)
            DO UPDATE SET capacidad_l=EXCLUDED.capacidad_l,
                          presion_bar=EXCLUDED.presion_bar,
                          fecha_ult_hidro=EXCLUDED.fecha_ult_hidro,
                          prox_hidro=EXCLUDED.prox_hidro
            """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setInt(1, idEppCilindro);
            ps.setDouble(2, capacidadL);
            ps.setInt(3, presionBar);
            ps.setDate(4, fechaUltHidro==null?null:new java.sql.Date(fechaUltHidro.getTime()));
            ps.setDate(5, proxHidro==null?null:new java.sql.Date(proxHidro.getTime()));
            ps.executeUpdate();
        }
    }

    public List<Integer> cilindrosConHidroProxima(int dias) throws SQLException {
        String sql = "SELECT id_epp FROM era_cilindro WHERE prox_hidro IS NOT NULL AND prox_hidro <= CURRENT_DATE + (? || ' days')::interval ORDER BY prox_hidro";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, dias);
            List<Integer> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getInt(1));
            }
            return out;
        }
    }

    public Map<String,Object> get(int idEpp) throws SQLException {
        String sql = "SELECT id_epp, capacidad_l, presion_bar, fecha_ult_hidro, prox_hidro FROM era_cilindro WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id_epp", rs.getInt(1));
                m.put("capacidad_l", rs.getDouble(2));
                m.put("presion_bar", rs.getInt(3));
                m.put("fecha_ult_hidro", rs.getDate(4));
                m.put("prox_hidro", rs.getDate(5));
                return m;
            }
        }
    }
}
