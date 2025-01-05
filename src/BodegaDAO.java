import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BodegaDAO {

    // Obtener los nombres de las subbodegas de una bodega principal específica
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


    // Obtener los IDs de las bodegas principales
    public List<Integer> obtenerIdsBodegasPrincipales() throws SQLException {
        List<Integer> idsBodegas = new ArrayList<>();
        String sql = "SELECT idbodega_principal FROM bodega_principal";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                idsBodegas.add(rs.getInt("idbodega_principal"));
            }
        }
        return idsBodegas;
    }

    // Obtener los IDs de las subbodegas
    public List<Integer> obtenerIdsSubbodegas() throws SQLException {
        List<Integer> idsSubbodegas = new ArrayList<>();
        String sql = "SELECT idsubbodega FROM subbodega";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                idsSubbodegas.add(rs.getInt("idsubbodega"));
            }
        }
        return idsSubbodegas;
    }

    // Obtener mapa de IDs y nombres de bodegas principales
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

    // Obtener productos de la base de datos
    public List<ProductoForm> obtenerProductos() throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();
        String sql = "SELECT p.idproducto, p.nombre, p.cantidad, p.fechaingreso, p.ubicacion, " +
                "p.idbodega_principal, p.idsubbodega " +
                "FROM producto p";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                ProductoForm producto = new ProductoForm(
                        rs.getInt("idproducto"),          // ID del producto
                        rs.getString("nombre"),          // Nombre del producto
                        rs.getInt("cantidad"),           // Cantidad
                        new java.util.Date(rs.getDate("fechaingreso").getTime()), // Fecha de ingreso
                        rs.getString("ubicacion"),       // Ubicación
                        rs.getInt("idbodega_principal"), // ID de la bodega principal
                        rs.getInt("idsubbodega")         // ID de la subbodega
                );
                productos.add(producto);
            }
        }
        return productos;
    }

    // Agregar una nueva bodega principal
    public void agregarBodegaPrincipal(BodegaForm bodega) throws SQLException {
        String sql = "INSERT INTO bodega_principal (idbodega_principal, nombre, capacidad) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, bodega.getIdBodega());
            pstmt.setString(2, bodega.getNombre());
            pstmt.setInt(3, bodega.getCapacidad());

            pstmt.executeUpdate();
        }
    }

    // Agregar una nueva subbodega
    public void agregarSubbodega(BodegaForm bodega) throws SQLException {
        String sql = "INSERT INTO subbodega (idsubbodega, nombre, capacidad, idbodega_principal) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, bodega.getIdBodega());
            pstmt.setString(2, bodega.getNombre());
            pstmt.setInt(3, bodega.getCapacidad());
            pstmt.setInt(4, bodega.getIdBodegaPadre());

            pstmt.executeUpdate();
        }
    }

    // Actualizar una bodega principal
    public void actualizarBodegaPrincipal(BodegaForm bodega) throws SQLException {
        String sql = "UPDATE bodega_principal SET nombre = ?, capacidad = ? WHERE idbodega_principal = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, bodega.getNombre());
            pstmt.setInt(2, bodega.getCapacidad());
            pstmt.setInt(3, bodega.getIdBodega());

            pstmt.executeUpdate();
        }
    }

    // Actualizar una subbodega
    public void actualizarSubbodega(BodegaForm bodega) throws SQLException {
        String sql = "UPDATE subbodega SET nombre = ?, capacidad = ?, idbodega_principal = ? WHERE idsubbodega = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, bodega.getNombre());
            pstmt.setInt(2, bodega.getCapacidad());
            pstmt.setInt(3, bodega.getIdBodegaPadre());
            pstmt.setInt(4, bodega.getIdBodega());

            pstmt.executeUpdate();
        }
    }

    // Eliminar una bodega principal por su ID
    public void eliminarBodegaPrincipal(int idBodegaPrincipal) throws SQLException {
        String sql = "DELETE FROM bodega_principal WHERE idbodega_principal = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, idBodegaPrincipal);
            pstmt.executeUpdate();
        }
    }

    // Eliminar una subbodega por su ID
    public void eliminarSubbodega(int idSubbodega) throws SQLException {
        String sql = "DELETE FROM subbodega WHERE idsubbodega = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, idSubbodega);
            pstmt.executeUpdate();
        }
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

    public List<Integer> obtenerIdsSubbodegasPorBodega(int idBodegaPrincipal) throws SQLException {
        List<Integer> idsSubbodegas = new ArrayList<>();
        String query = "SELECT idsubbodega FROM subbodega WHERE idbodega_principal = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, idBodegaPrincipal);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    idsSubbodegas.add(resultSet.getInt("idsubbodega"));
                }
            }
        }

        return idsSubbodegas;
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



}
