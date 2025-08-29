import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoDAO {

    private Connection connection;

    public ProductoDAO() {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            this.connection = conn;
        } catch (SQLException e) {
            throw new RuntimeException("Error al conectar con la base de datos: " + e.getMessage());
        } finally {
            DatabaseConnection.closeConnection(conn);
        }
    }

    // Método para obtener todos los productos con la información de la bodega principal y subbodega
    public List<ProductoForm> obtenerProductosConBodega(int limite, int offset) throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();

        String sql = "SELECT " +
                "    p.idproducto, " +
                "    p.nombre, " +
                "    p.cantidad, " +
                "    p.fechaingreso, " +
                "    p.ubicacion, " +
                "    bp2.nombre AS nombre_bodega_principal, " +  // Alias correcto
                "    sb.nombre AS nombre_subbodega " +          // Alias correcto
                "FROM producto p " +
                "JOIN bodega_producto bp ON p.idproducto = bp.idproducto " +
                "JOIN bodega_principal bp2 ON bp.idbodega_principal = bp2.idbodega_principal " +
                "JOIN subbodega sb ON bp.idsubbodega = sb.idsubbodega " +
                "LIMIT ? OFFSET ?";  // Agregar límite y desplazamiento

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limite);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ProductoForm producto = new ProductoForm(
                            rs.getInt("idproducto"),
                            rs.getString("nombre"),
                            rs.getInt("cantidad"),
                            rs.getDate("fechaingreso"),
                            rs.getString("ubicacion"),
                            0,  // No estás almacenando el ID de la bodega en ProductoForm, por lo que se deja en 0.
                            0,  // Lo mismo para la subbodega.
                            rs.getString("nombre_bodega_principal"),  // Nombre correcto del alias en SQL.
                            rs.getString("nombre_subbodega")          // Nombre correcto del alias en SQL.
                    );
                    productos.add(producto);
                }
            }
        }
        return productos;
    }

    // Método para agregar un producto
    public void agregarProducto(ProductoForm producto) throws SQLException {
        String verificarProducto = "SELECT idproducto FROM producto WHERE nombre = ?";
        String insertarProducto  = "INSERT INTO producto (nombre, cantidad, fechaingreso, ubicacion, idbodega_principal, idsubbodega) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        String verificarBodegaProducto = "SELECT cantidad FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String actualizarCantidad      = "UPDATE bodega_producto SET cantidad = cantidad + ? WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String insertarBodegaProducto  = "INSERT INTO bodega_producto (idproducto, idbodega_principal, idsubbodega, cantidad) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            int idProducto;

            // 1) ¿Existe el producto por nombre?
            try (PreparedStatement psVerProd = conn.prepareStatement(verificarProducto);
                 PreparedStatement psInsProd = conn.prepareStatement(insertarProducto, Statement.RETURN_GENERATED_KEYS)) {

                psVerProd.setString(1, producto.getNombre());

                try (ResultSet rs = psVerProd.executeQuery()) {
                    if (rs.next()) {
                        idProducto = rs.getInt("idproducto"); // Ya existe
                    } else {
                        // 2) Insertar en producto (incluyendo IDs obligatorios)
                        psInsProd.setString(1, producto.getNombre());
                        psInsProd.setInt(2, producto.getCantidad());
                        psInsProd.setDate(3, new java.sql.Date(producto.getFechaIngreso().getTime()));
                        psInsProd.setString(4, producto.getUbicacion());
                        psInsProd.setInt(5, producto.getIdBodegaPrincipal()); // ← FALTABA
                        psInsProd.setInt(6, producto.getIdSubbodega());       // ← FALTABA
                        psInsProd.executeUpdate();

                        try (ResultSet keys = psInsProd.getGeneratedKeys()) {
                            if (keys.next()) {
                                idProducto = keys.getInt(1);
                            } else {
                                throw new SQLException("No se pudo obtener el ID del nuevo producto.");
                            }
                        }
                    }
                }
            }

            // 3) Upsert en bodega_producto
            try (PreparedStatement psVerBP = conn.prepareStatement(verificarBodegaProducto);
                 PreparedStatement psUpdBP = conn.prepareStatement(actualizarCantidad);
                 PreparedStatement psInsBP = conn.prepareStatement(insertarBodegaProducto)) {

                psVerBP.setInt(1, idProducto);
                psVerBP.setInt(2, producto.getIdBodegaPrincipal());
                psVerBP.setInt(3, producto.getIdSubbodega());

                try (ResultSet rs = psVerBP.executeQuery()) {
                    if (rs.next()) {
                        // Ya existe en esa bodega/subbodega → sumar cantidad
                        psUpdBP.setInt(1, producto.getCantidad());
                        psUpdBP.setInt(2, idProducto);
                        psUpdBP.setInt(3, producto.getIdBodegaPrincipal());
                        psUpdBP.setInt(4, producto.getIdSubbodega());
                        psUpdBP.executeUpdate();
                    } else {
                        // No existe → insertar relación
                        psInsBP.setInt(1, idProducto);
                        psInsBP.setInt(2, producto.getIdBodegaPrincipal());
                        psInsBP.setInt(3, producto.getIdSubbodega());
                        psInsBP.setInt(4, producto.getCantidad());
                        psInsBP.executeUpdate();
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
        } catch (SQLException e) {
            throw new SQLException("Error al obtener el producto con ID: " + idProducto, e);
        }

        throw new SQLException("No se encontró el producto con ID: " + idProducto);
    }

    // Método para mover un producto entre bodegas
    public void moverProducto(int idProducto,
                              int idBodegaOrigen, int idSubbodegaOrigen,
                              int idBodegaDestino, int idSubbodegaDestino,
                              int cantidadMover) throws SQLException {

        if (cantidadMover <= 0) throw new SQLException("La cantidad a mover debe ser mayor a cero.");
        if (idBodegaOrigen == idBodegaDestino && idSubbodegaOrigen == idSubbodegaDestino) {
            throw new SQLException("El origen y el destino no pueden ser iguales.");
        }

        final String lockOrigen = "SELECT cantidad FROM bodega_producto " +
                "WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ? FOR UPDATE";

        final String updateOrigen = "UPDATE bodega_producto SET cantidad = cantidad - ? " +
                "WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";

        final String deleteOrigen = "DELETE FROM bodega_producto " +
                "WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";

        final String upsertDestino = "INSERT INTO bodega_producto (idproducto, idbodega_principal, idsubbodega, cantidad) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (idproducto, idbodega_principal, idsubbodega) " +
                "DO UPDATE SET cantidad = bodega_producto.cantidad + EXCLUDED.cantidad";

        // Capacidad destino (misma conexión/tx)
        final String capacidadDestino = "SELECT sb.capacidad - COALESCE(SUM(bp.cantidad), 0) AS espacio_disponible " +
                "FROM subbodega sb " +
                "LEFT JOIN bodega_producto bp ON sb.idsubbodega = bp.idsubbodega " +
                "WHERE sb.idsubbodega = ? " +
                "GROUP BY sb.capacidad";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            // 0) Validar capacidad en destino dentro de la misma transacción
            int espacioDisponible = Integer.MAX_VALUE; // por si no hay registros en bp
            try (PreparedStatement psCap = conn.prepareStatement(capacidadDestino)) {
                psCap.setInt(1, idSubbodegaDestino);
                try (ResultSet rs = psCap.executeQuery()) {
                    if (rs.next()) {
                        espacioDisponible = rs.getInt("espacio_disponible");
                    }
                }
            }
            if (espacioDisponible < cantidadMover) {
                conn.rollback();
                throw new SQLException("Espacio insuficiente en la subbodega destino.");
            }

            // 1) Lock + validación de stock en origen
            int cantOrigen;
            try (PreparedStatement psLock = conn.prepareStatement(lockOrigen)) {
                psLock.setInt(1, idProducto);
                psLock.setInt(2, idBodegaOrigen);
                psLock.setInt(3, idSubbodegaOrigen);
                try (ResultSet rs = psLock.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        throw new SQLException("El producto no existe en la ubicación de origen.");
                    }
                    cantOrigen = rs.getInt("cantidad");
                }
            }
            if (cantidadMover > cantOrigen) {
                conn.rollback();
                throw new SQLException("Cantidad a mover excede la disponible en el origen.");
            }

            // 2) Restar en origen
            try (PreparedStatement psUpd = conn.prepareStatement(updateOrigen)) {
                psUpd.setInt(1, cantidadMover);
                psUpd.setInt(2, idProducto);
                psUpd.setInt(3, idBodegaOrigen);
                psUpd.setInt(4, idSubbodegaOrigen);
                if (psUpd.executeUpdate() == 0) {
                    conn.rollback();
                    throw new SQLException("No se pudo actualizar la cantidad en el origen.");
                }
            }

            // 3) Borrar fila de origen si queda en 0 (opcional pero limpio)
            if (cantOrigen - cantidadMover == 0) {
                try (PreparedStatement psDel = conn.prepareStatement(deleteOrigen)) {
                    psDel.setInt(1, idProducto);
                    psDel.setInt(2, idBodegaOrigen);
                    psDel.setInt(3, idSubbodegaOrigen);
                    psDel.executeUpdate();
                }
            }

            // 4) Upsert en destino
            try (PreparedStatement psUpsert = conn.prepareStatement(upsertDestino)) {
                psUpsert.setInt(1, idProducto);
                psUpsert.setInt(2, idBodegaDestino);
                psUpsert.setInt(3, idSubbodegaDestino);
                psUpsert.setInt(4, cantidadMover);
                psUpsert.executeUpdate();
            }

            conn.commit();
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

        // Uso de try-with-resources para garantizar el cierre adecuado de recursos
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
        } catch (SQLException e) {
            throw new SQLException("Error al obtener los productos: " + e.getMessage(), e);
        }
        return productos;
    }

    // Método para buscar productos por nombre
    public List<ProductoForm> buscarProductosPorBodega(String textoBusqueda, Integer idBodega) throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT p.idproducto, p.nombre, bp.cantidad, p.fechaingreso, p.ubicacion, " +
                        "bp.idbodega_principal, bp.idsubbodega " +
                        "FROM producto p " +
                        "JOIN bodega_producto bp ON p.idproducto = bp.idproducto WHERE 1=1"
        );

        // Agregar filtros dinámicamente
        if (textoBusqueda != null && !textoBusqueda.isEmpty()) {
            sql.append(" AND p.nombre ILIKE ?");
        }
        if (idBodega != null) {
            sql.append(" AND bp.idbodega_principal = ?");
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int index = 1;
            if (textoBusqueda != null && !textoBusqueda.isEmpty()) {
                stmt.setString(index++, "%" + textoBusqueda + "%");
            }
            if (idBodega != null) {
                stmt.setInt(index++, idBodega);
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
        List<String> nombres = new ArrayList<>();
        String sql = "SELECT DISTINCT nombre FROM producto ORDER BY nombre";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) nombres.add(rs.getString(1));
        }
        return nombres;
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
        } catch (SQLException e) {
            throw new SQLException("Error al verificar la capacidad de la subbodega: " + e.getMessage(), e);
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
            if (cantidadEliminar > 0) {
                try (PreparedStatement stmt = conn.prepareStatement(actualizarCantidadQuery)) {
                    stmt.setInt(1, cantidadEliminar);
                    stmt.setInt(2, idProducto);
                    stmt.setInt(3, idBodega);
                    stmt.setInt(4, idSubbodega);
                    stmt.executeUpdate();
                }
            }

            // Eliminar el registro de bodega si no hay cantidades restantes
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
                            // Eliminar el producto si no quedan existencias
                            try (PreparedStatement eliminarProductoStmt = conn.prepareStatement(eliminarProductoQuery)) {
                                eliminarProductoStmt.setInt(1, idProducto);
                                eliminarProductoStmt.executeUpdate();
                            }
                        }
                    }
                }
            }

            conn.commit(); // Confirmar transacción
        } catch (SQLException ex) {
            throw new SQLException("Error al eliminar producto: " + ex.getMessage(), ex);
        }
    }

    public void eliminarCantidadProducto(int idProducto, int idBodega, int idSubbodega, int cantidadEliminar) throws SQLException {
        String verificarCantidadQuery = "SELECT cantidad FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String actualizarCantidadQuery = "UPDATE bodega_producto SET cantidad = cantidad - ? WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String eliminarRegistroQuery = "DELETE FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Iniciar transacción

            // Verificar la cantidad disponible en la subbodega
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
                            // Si quedan productos, actualizar la cantidad
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

            conn.commit(); // Confirmar la transacción
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
                    return rs.getInt("idproducto"); // Retorna el ID si se encuentra
                } else {
                    throw new SQLException("Producto no encontrado: " + nombreProducto);
                }
            }
        } catch (SQLException e) {
            throw new SQLException("Error al obtener el ID del producto: " + e.getMessage(), e);
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

            int filasAfectadas = stmt.executeUpdate();

            if (filasAfectadas == 0) {
                throw new SQLException("No se encontró el producto con el ID especificado en la bodega y subbodega dadas.");
            }
        } catch (SQLException e) {
            throw new SQLException("Error al actualizar la cantidad del producto: " + e.getMessage(), e);
        }
    }

    public List<ProductoForm> buscarProductosPorFiltros(String textoBusqueda, Integer idBodegaPrincipal, Integer idSubbodega) throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT p.idproducto, p.nombre, bp.cantidad, p.fechaingreso, p.ubicacion, " +
                        "       bp.idbodega_principal, bp.idsubbodega, " +
                        "       bp2.nombre AS nombre_bodega_principal, " +
                        "       sb.nombre  AS nombre_subbodega " +
                        "FROM bodega_producto bp " +
                        "JOIN producto p          ON p.idproducto = bp.idproducto " +
                        "JOIN bodega_principal bp2 ON bp2.idbodega_principal = bp.idbodega_principal " +
                        "JOIN subbodega sb         ON sb.idsubbodega = bp.idsubbodega " +
                        "WHERE 1=1 "
        );

        if (textoBusqueda != null && !textoBusqueda.isEmpty()) {
            sql.append("AND p.nombre ILIKE ? ");
        }
        if (idBodegaPrincipal != null) {
            sql.append("AND bp.idbodega_principal = ? ");
        }
        if (idSubbodega != null) {
            sql.append("AND bp.idsubbodega = ? ");
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int i = 1;
            if (textoBusqueda != null && !textoBusqueda.isEmpty()) {
                stmt.setString(i++, "%" + textoBusqueda + "%");
            }
            if (idBodegaPrincipal != null) {
                stmt.setInt(i++, idBodegaPrincipal);
            }
            if (idSubbodega != null) {
                stmt.setInt(i++, idSubbodega);
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
                            rs.getInt("idsubbodega"),
                            rs.getString("nombre_bodega_principal"),
                            rs.getString("nombre_subbodega")
                    ));
                }
            }
        }

        return productos;
    }


    public int obtenerSiguienteId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(idproducto), 0) + 1 AS siguiente_id FROM producto";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("siguiente_id");
            }
        }
        return 1; // Si no hay productos, el ID inicial será 1
    }

    public static class UbicacionProducto {
        public final int idBodega, idSubbodega, cantidad;
        public final String nombreBodega, nombreSubbodega;
        public UbicacionProducto(int idBodega, int idSubbodega, int cantidad, String nb, String ns) {
            this.idBodega = idBodega; this.idSubbodega = idSubbodega; this.cantidad = cantidad;
            this.nombreBodega = nb; this.nombreSubbodega = ns;
        }
    }

    public List<String[]> obtenerUbicacionesDeProducto(String nombreProducto) throws SQLException {
        String sql =
                "SELECT bp2.nombre AS bodega, sb.nombre AS subbodega " +
                        "FROM producto p " +
                        "JOIN bodega_producto bp ON p.idproducto = bp.idproducto " +
                        "JOIN bodega_principal bp2 ON bp.idbodega_principal = bp2.idbodega_principal " +
                        "JOIN subbodega sb ON bp.idsubbodega = sb.idsubbodega " +
                        "WHERE p.nombre = ? AND bp.cantidad > 0 " +
                        "ORDER BY bp2.nombre, sb.nombre";
        List<String[]> res = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, nombreProducto);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    res.add(new String[]{ rs.getString("bodega"), rs.getString("subbodega") });
                }
            }
        }
        return res;
    }

}
