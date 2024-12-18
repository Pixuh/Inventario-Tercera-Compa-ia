import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoDAO {

    private Connection connection;

    public ProductoDAO() {
        try {
            this.connection = DatabaseConnection.getConnection(); // Usar la conexión de DatabaseConnection
        } catch (SQLException e) {
            throw new RuntimeException("Error al conectar con la base de datos: " + e.getMessage());
        }
    }
    // Método para obtener todos los productos con la información de la bodega principal y subbodega
    public List<ProductoForm> obtenerProductosConBodega() throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();
        String sql = "SELECT p.idproducto, p.nombre, bp.cantidad, p.fechaingreso, p.ubicacion, " +
                "bp.idbodega_principal, bp.idsubbodega " +
                "FROM producto p " +
                "JOIN bodega_producto bp ON p.idproducto = bp.idproducto";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                ProductoForm producto = new ProductoForm(
                        rs.getInt("idproducto"),
                        rs.getString("nombre"),
                        rs.getInt("cantidad"),
                        rs.getDate("fechaingreso"),
                        rs.getString("ubicacion"),
                        rs.getInt("idbodega_principal"),
                        rs.getInt("idsubbodega")
                );
                productos.add(producto);
            }
        }
        return productos;
    }

    // Método para agregar un producto
    public void agregarProducto(ProductoForm producto) throws SQLException {
        String verificarProducto = "SELECT idproducto FROM producto WHERE nombre = ?";
        String insertarProducto = "INSERT INTO producto (nombre, cantidad, fechaingreso, ubicacion) VALUES (?, ?, ?, ?)";
        String verificarBodegaProducto = "SELECT cantidad FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String actualizarCantidad = "UPDATE bodega_producto SET cantidad = cantidad + ? WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String insertarBodegaProducto = "INSERT INTO bodega_producto (idproducto, idbodega_principal, idsubbodega, cantidad) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            int idProducto;

            // Verificar si el producto ya existe en la tabla `producto`
            try (PreparedStatement pstmtVerificarProducto = conn.prepareStatement(verificarProducto)) {
                pstmtVerificarProducto.setString(1, producto.getNombre());
                try (ResultSet rs = pstmtVerificarProducto.executeQuery()) {
                    if (rs.next()) {
                        idProducto = rs.getInt("idproducto"); // Producto ya existe
                    } else {
                        // Insertar nuevo producto en la tabla `producto`
                        try (PreparedStatement pstmtInsertarProducto = conn.prepareStatement(insertarProducto, Statement.RETURN_GENERATED_KEYS)) {
                            pstmtInsertarProducto.setString(1, producto.getNombre());
                            pstmtInsertarProducto.setInt(2, producto.getCantidad());
                            pstmtInsertarProducto.setDate(3, new java.sql.Date(producto.getFechaIngreso().getTime()));
                            pstmtInsertarProducto.setString(4, producto.getUbicacion());
                            pstmtInsertarProducto.executeUpdate();

                            try (ResultSet generatedKeys = pstmtInsertarProducto.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    idProducto = generatedKeys.getInt(1);
                                } else {
                                    throw new SQLException("No se pudo obtener el ID del nuevo producto.");
                                }
                            }
                        }
                    }
                }
            }

            // Verificar si el producto ya existe en la bodega y subbodega
            try (PreparedStatement pstmtVerificarBodegaProducto = conn.prepareStatement(verificarBodegaProducto)) {
                pstmtVerificarBodegaProducto.setInt(1, idProducto);
                pstmtVerificarBodegaProducto.setInt(2, producto.getIdBodegaPrincipal());
                pstmtVerificarBodegaProducto.setInt(3, producto.getIdSubbodega());

                try (ResultSet rs = pstmtVerificarBodegaProducto.executeQuery()) {
                    if (rs.next()) {
                        // Producto ya existe en la bodega: actualizar cantidad
                        int nuevaCantidad = producto.getCantidad();
                        try (PreparedStatement pstmtActualizarCantidad = conn.prepareStatement(actualizarCantidad)) {
                            pstmtActualizarCantidad.setInt(1, nuevaCantidad);
                            pstmtActualizarCantidad.setInt(2, idProducto);
                            pstmtActualizarCantidad.setInt(3, producto.getIdBodegaPrincipal());
                            pstmtActualizarCantidad.setInt(4, producto.getIdSubbodega());
                            pstmtActualizarCantidad.executeUpdate();
                        }
                    } else {
                        // Producto no existe en la bodega: insertar nuevo registro
                        try (PreparedStatement pstmtInsertarBodegaProducto = conn.prepareStatement(insertarBodegaProducto)) {
                            pstmtInsertarBodegaProducto.setInt(1, idProducto);
                            pstmtInsertarBodegaProducto.setInt(2, producto.getIdBodegaPrincipal());
                            pstmtInsertarBodegaProducto.setInt(3, producto.getIdSubbodega());
                            pstmtInsertarBodegaProducto.setInt(4, producto.getCantidad());
                            pstmtInsertarBodegaProducto.executeUpdate();
                        }
                    }
                }
            }

            conn.commit();
        } catch (SQLException ex) {
            throw new SQLException("Error al agregar producto: " + ex.getMessage(), ex);
        }
    }

    // Método para obtener un producto por su ID
    public ProductoForm obtenerProductoPorId(int idProducto) throws SQLException {
        String query = "SELECT idproducto, nombre, cantidad, fechaingreso, ubicacion, idbodega_principal, idsubbodega " +
                "FROM producto WHERE idproducto = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, idProducto);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ProductoForm(
                            rs.getInt("idproducto"),
                            rs.getString("nombre"),
                            rs.getInt("cantidad"),
                            rs.getDate("fechaingreso"),
                            rs.getString("ubicacion"),
                            rs.getInt("idbodega_principal"),
                            rs.getInt("idsubbodega")
                    );
                }
            }
        }
        throw new SQLException("No se encontró el producto con ID: " + idProducto);
    }

    // Método para mover un producto entre bodegas
    public void moverProducto(int idProducto, int idBodegaOrigen, int idSubbodegaOrigen,
                              int idBodegaDestino, int idSubbodegaDestino, int cantidadMover) throws SQLException {
        String restarCantidadQuery = "UPDATE bodega_producto SET cantidad = cantidad - ? WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String sumarCantidadQuery = "INSERT INTO bodega_producto (idproducto, idbodega_principal, idsubbodega, cantidad) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT (idproducto, idbodega_principal, idsubbodega) " +
                "DO UPDATE SET cantidad = bodega_producto.cantidad + EXCLUDED.cantidad";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false); // Iniciar transacción

            // Verificar capacidad en el destino
            if (!verificarCapacidadSubbodega(idSubbodegaDestino, cantidadMover)) {
                throw new SQLException("Espacio insuficiente en la subbodega destino.");
            }

            // Restar cantidad en el origen
            try (PreparedStatement restarStmt = connection.prepareStatement(restarCantidadQuery)) {
                restarStmt.setInt(1, cantidadMover);
                restarStmt.setInt(2, idProducto);
                restarStmt.setInt(3, idBodegaOrigen);
                restarStmt.setInt(4, idSubbodegaOrigen);
                restarStmt.executeUpdate();
            }

            // Sumar cantidad en el destino
            try (PreparedStatement sumarStmt = connection.prepareStatement(sumarCantidadQuery)) {
                sumarStmt.setInt(1, idProducto);
                sumarStmt.setInt(2, idBodegaDestino);
                sumarStmt.setInt(3, idSubbodegaDestino);
                sumarStmt.setInt(4, cantidadMover);
                sumarStmt.executeUpdate();
            }

            connection.commit(); // Confirmar transacción
        } catch (SQLException ex) {
            throw new SQLException("Error al mover el producto: " + ex.getMessage(), ex);
        }
    }

    // Método para obtener todos los productos
    public List<ProductoForm> obtenerProductos() throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();
        String sql = "SELECT p.idproducto, p.nombre, bp.cantidad, p.fechaingreso, p.ubicacion, " +
                "bp.idbodega_principal, bp.idsubbodega " +
                "FROM producto p " +
                "JOIN bodega_producto bp ON p.idproducto = bp.idproducto";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                ProductoForm producto = new ProductoForm(
                        rs.getInt("idproducto"),
                        rs.getString("nombre"),
                        rs.getInt("cantidad"),
                        rs.getDate("fechaingreso"),
                        rs.getString("ubicacion"),
                        rs.getInt("idbodega_principal"),
                        rs.getInt("idsubbodega")
                );
                productos.add(producto);
            }
        }
        return productos;
    }

    // Método para buscar productos por nombre
    public List<ProductoForm> buscarProductosPorBodega(String textoBusqueda, Integer bodegaSeleccionada) throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();
        String sql = "SELECT * FROM producto WHERE nombre LIKE ? AND (idbodega_principal = ? OR ? IS NULL)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + textoBusqueda + "%");

            if (bodegaSeleccionada != null) {
                stmt.setInt(2, bodegaSeleccionada);
                stmt.setInt(3, bodegaSeleccionada);
            } else {
                stmt.setNull(2, Types.INTEGER);
                stmt.setNull(3, Types.INTEGER);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    productos.add(new ProductoForm(
                            rs.getInt("idproducto"),
                            rs.getString("nombre"),
                            rs.getInt("cantidad"),
                            rs.getDate("fechaingreso"),
                            rs.getString("ubicacion"),
                            rs.getInt("idbodega_principal"),
                            rs.getInt("idsubbodega")
                    ));
                }
            }
        }
        return productos;
    }

    // Método para obtener nombres de productos
    public List<String> obtenerNombresProductos() throws SQLException {
        List<String> nombresProductos = new ArrayList<>();
        String query = "SELECT nombre FROM producto";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                nombresProductos.add(rs.getString("nombre"));
            }
        }
        return nombresProductos;
    }

    // Método para obtener las subbodegas de una bodega principal
    public List<Integer> obtenerIdsSubbodegasPorBodega(int idBodegaPrincipal) throws SQLException {
        List<Integer> idsSubbodegas = new ArrayList<>();
        String query = "SELECT idsubbodega FROM subbodega WHERE idbodega_principal = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, idBodegaPrincipal);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    idsSubbodegas.add(rs.getInt("idsubbodega"));
                }
            }
        }
        return idsSubbodegas;
    }

    private boolean verificarCapacidadSubbodega(int idSubbodega, int cantidadMover) throws SQLException {
        String verificarCapacidadQuery = "SELECT sb.capacidad - COALESCE(SUM(bp.cantidad), 0) AS espacio_disponible " +
                "FROM subbodega sb " +
                "LEFT JOIN bodega_producto bp ON sb.idsubbodega = bp.idsubbodega " +
                "WHERE sb.idsubbodega = ? " +
                "GROUP BY sb.capacidad";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(verificarCapacidadQuery)) {
            stmt.setInt(1, idSubbodega);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int espacioDisponible = rs.getInt("espacio_disponible");
                    return espacioDisponible >= cantidadMover;
                }
            }
        }
        return false;
    }

    public void eliminarProducto(int idProducto, int idBodega, int idSubbodega, int cantidadEliminar) throws SQLException {
        String verificarCantidadQuery = "SELECT cantidad FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String actualizarCantidadQuery = "UPDATE bodega_producto SET cantidad = cantidad - ? WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String eliminarRegistroQuery = "DELETE FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String verificarExistenciasTotalesQuery = "SELECT SUM(cantidad) AS total FROM bodega_producto WHERE idproducto = ?";
        String eliminarProductoQuery = "DELETE FROM producto WHERE idproducto = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            // Verificar cantidad disponible en la subbodega
            try (PreparedStatement verificarStmt = conn.prepareStatement(verificarCantidadQuery)) {
                verificarStmt.setInt(1, idProducto);
                verificarStmt.setInt(2, idBodega);
                verificarStmt.setInt(3, idSubbodega);

                try (ResultSet rs = verificarStmt.executeQuery()) {
                    if (rs.next()) {
                        int cantidadDisponible = rs.getInt("cantidad");
                        if (cantidadEliminar > cantidadDisponible) {
                            throw new SQLException("Cantidad insuficiente para eliminar en esta subbodega.");
                        }
                    } else {
                        throw new SQLException("El producto no existe en la subbodega especificada.");
                    }
                }
            }

            // Actualizar o eliminar el registro en la subbodega
            try (PreparedStatement stmt = conn.prepareStatement(actualizarCantidadQuery)) {
                stmt.setInt(1, cantidadEliminar);
                stmt.setInt(2, idProducto);
                stmt.setInt(3, idBodega);
                stmt.setInt(4, idSubbodega);
                stmt.executeUpdate();
            }

            try (PreparedStatement eliminarStmt = conn.prepareStatement(eliminarRegistroQuery)) {
                eliminarStmt.setInt(1, idProducto);
                eliminarStmt.setInt(2, idBodega);
                eliminarStmt.setInt(3, idSubbodega);
                eliminarStmt.executeUpdate();
            }

            // Verificar si quedan existencias totales del producto
            try (PreparedStatement verificarTotalesStmt = conn.prepareStatement(verificarExistenciasTotalesQuery)) {
                verificarTotalesStmt.setInt(1, idProducto);

                try (ResultSet rs = verificarTotalesStmt.executeQuery()) {
                    if (rs.next()) {
                        int existenciasTotales = rs.getInt("total");
                        if (existenciasTotales <= 0) {
                            // Eliminar el producto de la tabla producto si no hay existencias
                            try (PreparedStatement eliminarProductoStmt = conn.prepareStatement(eliminarProductoQuery)) {
                                eliminarProductoStmt.setInt(1, idProducto);
                                eliminarProductoStmt.executeUpdate();
                            }
                        }
                    }
                }
            }

            conn.commit();
        } catch (SQLException ex) {
            throw new SQLException("Error al eliminar producto: " + ex.getMessage(), ex);
        }
    }

    public void eliminarCantidadProducto(int idProducto, int idBodega, int idSubbodega, int cantidadEliminar) throws SQLException {
        String verificarCantidadQuery = "SELECT cantidad FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String actualizarCantidadQuery = "UPDATE bodega_producto SET cantidad = cantidad - ? WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String eliminarRegistroQuery = "DELETE FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            // Verificar cantidad disponible
            try (PreparedStatement verificarStmt = conn.prepareStatement(verificarCantidadQuery)) {
                verificarStmt.setInt(1, idProducto);
                verificarStmt.setInt(2, idBodega);
                verificarStmt.setInt(3, idSubbodega);

                try (ResultSet rs = verificarStmt.executeQuery()) {
                    if (rs.next()) {
                        int cantidadDisponible = rs.getInt("cantidad");

                        if (cantidadEliminar > cantidadDisponible) {
                            throw new SQLException("Cantidad insuficiente para eliminar.");
                        } else if (cantidadEliminar == cantidadDisponible) {
                            // Si la cantidad a eliminar es igual a la disponible, eliminar el registro
                            try (PreparedStatement eliminarStmt = conn.prepareStatement(eliminarRegistroQuery)) {
                                eliminarStmt.setInt(1, idProducto);
                                eliminarStmt.setInt(2, idBodega);
                                eliminarStmt.setInt(3, idSubbodega);
                                eliminarStmt.executeUpdate();
                            }
                        } else {
                            // Si hay productos restantes, actualizar la cantidad
                            try (PreparedStatement actualizarStmt = conn.prepareStatement(actualizarCantidadQuery)) {
                                actualizarStmt.setInt(1, cantidadEliminar);
                                actualizarStmt.setInt(2, idProducto);
                                actualizarStmt.setInt(3, idBodega);
                                actualizarStmt.setInt(4, idSubbodega);
                                actualizarStmt.executeUpdate();
                            }
                        }
                    } else {
                        throw new SQLException("El producto no existe en la subbodega especificada.");
                    }
                }
            }

            conn.commit();
        } catch (SQLException ex) {
            throw new SQLException("Error al eliminar cantidad de producto: " + ex.getMessage(), ex);
        }
    }

    public int obtenerIdProductoPorNombre(String nombreProducto) throws SQLException {
        String query = "SELECT idproducto FROM producto WHERE nombre = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nombreProducto);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("idproducto");
                } else {
                    throw new SQLException("Producto no encontrado: " + nombreProducto);
                }
            }
        }
    }

    public ProductoForm obtenerProductoPorNombreYBodega(String nombre, int idBodegaPrincipal, int idSubbodega) throws SQLException {
        String sql = "SELECT p.idproducto, p.nombre, bp.cantidad, p.fechaingreso, p.ubicacion, bp.idbodega_principal, bp.idsubbodega " +
                "FROM producto p " +
                "JOIN bodega_producto bp ON p.idproducto = bp.idproducto " +
                "WHERE p.nombre = ? AND bp.idbodega_principal = ? AND bp.idsubbodega = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombre);
            stmt.setInt(2, idBodegaPrincipal);
            stmt.setInt(3, idSubbodega);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ProductoForm(
                            rs.getInt("idproducto"),
                            rs.getString("nombre"),
                            rs.getInt("cantidad"),
                            rs.getDate("fechaingreso"),
                            rs.getString("ubicacion"),
                            rs.getInt("idbodega_principal"),
                            rs.getInt("idsubbodega")
                    );
                }
            }
        }
        return null; // No encontrado
    }

    public void actualizarCantidadProducto(int idProducto, int idBodegaPrincipal, int idSubbodega, int cantidadAgregar) throws SQLException {
        String sql = "UPDATE bodega_producto SET cantidad = cantidad + ? " +
                "WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cantidadAgregar);
            stmt.setInt(2, idProducto);
            stmt.setInt(3, idBodegaPrincipal);
            stmt.setInt(4, idSubbodega);
            stmt.executeUpdate();
        }
    }

    public List<ProductoForm> buscarProductosPorFiltros(String textoBusqueda, Integer idBodegaPrincipal, Integer idSubbodega) throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();
        String sql = "SELECT * FROM producto p "
                + "LEFT JOIN bodega_producto bp ON p.idproducto = bp.idproducto "
                + "WHERE 1=1 ";

        if (textoBusqueda != null && !textoBusqueda.isEmpty()) {
            sql += "AND p.nombre ILIKE ? ";
        }
        if (idBodegaPrincipal != null) {
            sql += "AND bp.idbodega_principal = ? ";
        }
        if (idSubbodega != null) {
            sql += "AND bp.idsubbodega = ? ";
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int index = 1;

            if (textoBusqueda != null && !textoBusqueda.isEmpty()) {
                stmt.setString(index++, "%" + textoBusqueda + "%");
            }
            if (idBodegaPrincipal != null) {
                stmt.setInt(index++, idBodegaPrincipal);
            }
            if (idSubbodega != null) {
                stmt.setInt(index++, idSubbodega);
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                productos.add(new ProductoForm(
                        rs.getInt("idproducto"),
                        rs.getString("nombre"),
                        rs.getInt("cantidad"),
                        rs.getDate("fechaingreso"),
                        rs.getString("ubicacion"),
                        rs.getInt("idbodega_principal"),
                        rs.getInt("idsubbodega")
                ));
            }
        }

        return productos;
    }

    public int obtenerSiguienteId() throws SQLException {
        String sql = "SELECT MAX(idproducto) + 1 AS siguiente_id FROM producto";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("siguiente_id");
            }
        }
        return 1; // Si no hay productos, el ID inicial será 1
    }

    public int obtenerCantidadDisponible(int idProducto, int idBodega, int idSubbodega) throws SQLException {
        String query = "SELECT cantidad FROM inventario " +
                "WHERE id_producto = ? AND id_bodega = ? AND id_subbodega = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, idProducto);
            statement.setInt(2, idBodega);
            statement.setInt(3, idSubbodega);

            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getInt("cantidad");
            } else {
                return 0; // Si no se encuentra el producto, se asume cantidad 0
            }
        }
    }

}
