import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BodegaDAO {

    public List<String> obtenerSubbodegasPorBodegaPrincipal(int idBodegaPrincipal) throws SQLException {
        List<String> subbodegas = new ArrayList<>();
        String sql = "SELECT nombre FROM subbodega WHERE idbodega_principal = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idBodegaPrincipal);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    subbodegas.add(rs.getString("nombre"));
                }
            }
        }
        return subbodegas;
    }

    public Map<Integer, String> obtenerBodegasPrincipalesConIds() throws SQLException {
        Map<Integer, String> bodegasMap = new HashMap<>();
        String sql = "SELECT idbodega_principal, nombre FROM bodega_principal";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                bodegasMap.put(rs.getInt("idbodega_principal"), rs.getString("nombre"));
            }
        }
        return bodegasMap;
    }

    public int obtenerIdSubbodegaPorNombre(String nombre) throws SQLException {
        String sql = "SELECT idsubbodega FROM subbodega WHERE nombre = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("idsubbodega");
                }
            }
        }
        throw new SQLException("Subbodega no encontrada: " + nombre);
    }

    public String obtenerNombreBodegaPorId(int idBodega) throws SQLException {
        String sql = "SELECT nombre FROM bodega_principal WHERE idbodega_principal = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idBodega);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("nombre");
                }
            }
        }
        return null;
    }

    public String obtenerNombreSubbodegaPorId(int idSubbodega) throws SQLException {
        String sql = "SELECT nombre FROM subbodega WHERE idsubbodega = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idSubbodega);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("nombre");
                }
            }
        }
        return null;
    }

    public Map<Integer, String> obtenerSubbodegasPorBodegaPrincipalConIds(int idBodegaPrincipal) throws SQLException {
        Map<Integer, String> subbodegasMap = new HashMap<>();
        String sql = "SELECT idsubbodega, nombre FROM subbodega WHERE idbodega_principal = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idBodegaPrincipal);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int idSubbodega = rs.getInt("idsubbodega");
                    String nombre = rs.getString("nombre");
                    subbodegasMap.put(idSubbodega, nombre);
                }
            }
        }
        return subbodegasMap;
    }

    public void crearBodegaPrincipal(String nombre, int capacidad) throws SQLException {
        String sql = "INSERT INTO bodega_principal (nombre, capacidad) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setInt(2, capacidad);
            pstmt.executeUpdate();
        }
    }

    public void crearSubbodega(String nombre, int capacidad, int idBodegaPrincipal) throws SQLException {
        String sql = "INSERT INTO subbodega (nombre, capacidad, idbodega_principal) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setInt(2, capacidad);
            pstmt.setInt(3, idBodegaPrincipal);
            pstmt.executeUpdate();
        }
    }

    public int obtenerIdBodegaPrincipalPorNombre(String nombre) throws SQLException {
        String sql = "SELECT idbodega_principal FROM bodega_principal WHERE nombre = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("idbodega_principal");
                }
            }
        }
        throw new SQLException("Bodega Principal no encontrada: " + nombre);
    }

    public List<String> obtenerNombresBodegasPrincipales() throws SQLException {
        List<String> nombresBodegas = new ArrayList<>();
        String query = "SELECT nombre FROM bodega_principal";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                nombresBodegas.add(rs.getString("nombre"));
            }
        }
        return nombresBodegas;
    }

    public void eliminarBodegaPrincipal(String nombreBodega) throws SQLException {
        String query = "DELETE FROM bodega_principal WHERE nombre = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nombreBodega);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No se encontró la bodega principal con el nombre especificado.");
            }
        }
    }

    public int obtenerIdBodegaPrincipal(String nombreBodega) throws SQLException {
        String query = "SELECT idbodega_principal FROM bodega_principal WHERE nombre = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nombreBodega);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("idbodega_principal");
                } else {
                    throw new SQLException("No se encontró una bodega principal con el nombre: " + nombreBodega);
                }
            }
        }
    }

    public Map<String, Integer> obtenerSubbodegasPorIdPrincipal(int idBodegaPrincipal) throws SQLException {
        Map<String, Integer> subbodegas = new HashMap<>();

        String sql = "SELECT idsubbodega, nombre FROM subbodega WHERE idbodega_principal = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idBodegaPrincipal);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("idsubbodega");
                String nombre = rs.getString("nombre");
                subbodegas.put(nombre, id);
            }
        }
        return subbodegas;
    }

}

