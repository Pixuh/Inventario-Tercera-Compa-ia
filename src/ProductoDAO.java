import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoDAO {

    // No abrimos conexión en el constructor
    public ProductoDAO() {}

    // === NUEVO: Helpers EPP ===
    public boolean esProductoEpp(int idProducto) throws SQLException {
        String sql = "SELECT es_epp FROM producto WHERE idproducto = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Producto no encontrado (id=" + idProducto + ")");
                return rs.getBoolean(1);
            }
        }
    }

    public void marcarProductoComoEpp(int idProducto, boolean esEpp, boolean esSerializable) throws SQLException {
        String sql = "UPDATE producto SET es_epp = ?, es_serializable = ? WHERE idproducto = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, esEpp);
            ps.setBoolean(2, esSerializable);
            ps.setInt(3, idProducto);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("No se pudo actualizar flags EPP del producto (id=" + idProducto + ")");
            }
        }
    }

    // === Listado con nombres de bodega/subbodega y cantidad por ubicación ===
    public List<ProductoForm> obtenerProductosConBodega(int limite, int offset) throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();

        String sql =
                "SELECT p.idproducto, p.nombre, bp.cantidad, p.fechaingreso, p.ubicacion, " +
                        "       bp.idbodega_principal, bp.idsubbodega, " +
                        "       bp2.nombre AS nombre_bodega_principal, " +
                        "       sb.nombre  AS nombre_subbodega " +
                        "FROM producto p " +
                        "JOIN bodega_producto bp   ON p.idproducto = bp.idproducto " +
                        "JOIN bodega_principal bp2 ON bp.idbodega_principal = bp2.idbodega_principal " +
                        "JOIN subbodega sb         ON bp.idsubbodega = sb.idsubbodega " +
                        "ORDER BY p.idproducto ASC " +
                        "LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limite);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    productos.add(new ProductoForm(
                            rs.getInt("idproducto"),
                            rs.getString("nombre"),
                            rs.getInt("cantidad"), // cantidad en esa subbodega
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

    public void agregarProducto(ProductoForm producto) throws SQLException {
        String sqlEstado = "SELECT id_estado FROM producto_estado WHERE nombre = ?";
        String sqlMaterial = "SELECT id_material FROM material_tipo WHERE nombre = ?";

        Integer idEstado = null;
        Integer idMaterial = null;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // === Resolver FKs por nombre (opcionales) ===
                if (producto.getEstadoProducto() != null) {
                    try (PreparedStatement ps = conn.prepareStatement(sqlEstado)) {
                        ps.setString(1, producto.getEstadoProducto());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) idEstado = rs.getInt(1);
                        }
                    }
                }
                if (producto.getTipoMaterial() != null) {
                    try (PreparedStatement ps = conn.prepareStatement(sqlMaterial)) {
                        ps.setString(1, producto.getTipoMaterial());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) idMaterial = rs.getInt(1);
                        }
                    }
                }

                // === INSERT a producto (devolviendo id) ===
                String sqlIns = """
                INSERT INTO producto
                (nombre, cantidad, fechaingreso, ubicacion,
                 idbodega_principal, idsubbodega,
                 marca, valor_referencial, id_estado, id_material,
                 observacion, requiere_mantencion, frecuencia_mantencion_meses,
                 aplica_vencimiento, es_epp, es_serializable)
                VALUES (?,?,?,?, ?,?,?, ?,?,?, ?,?,?, ?,?, ?)
                """;

                int idProductoGenerado;

                try (PreparedStatement psIns = conn.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
                    int i = 1;
                    psIns.setString(i++, producto.getNombre());
                    psIns.setInt(i++, producto.getCantidad());
                    psIns.setDate(i++, new java.sql.Date(producto.getFechaIngreso().getTime()));
                    psIns.setString(i++, producto.getUbicacion());

                    psIns.setInt(i++, producto.getIdBodegaPrincipal());
                    psIns.setInt(i++, producto.getIdSubbodega());

                    // marca
                    if (producto.getMarca() != null && !producto.getMarca().isEmpty())
                        psIns.setString(i++, producto.getMarca());
                    else
                        psIns.setNull(i++, Types.VARCHAR);

                    // valor_referencial
                    if (producto.getValor() != null)
                        psIns.setBigDecimal(i++, producto.getValor());
                    else
                        psIns.setNull(i++, Types.NUMERIC);

                    // id_estado
                    if (idEstado != null)
                        psIns.setInt(i++, idEstado);
                    else
                        psIns.setNull(i++, Types.INTEGER);

                    // id_material
                    if (idMaterial != null)
                        psIns.setInt(i++, idMaterial);
                    else
                        psIns.setNull(i++, Types.INTEGER);

                    // observacion
                    if (producto.getObservacion() != null && !producto.getObservacion().isEmpty())
                        psIns.setString(i++, producto.getObservacion());
                    else
                        psIns.setNull(i++, Types.VARCHAR);

                    // requiere_mantencion
                    if (producto.getRequiereMantencion() != null)
                        psIns.setBoolean(i++, producto.getRequiereMantencion());
                    else
                        psIns.setNull(i++, Types.BOOLEAN);

                    // frecuencia_mantencion_meses
                    if (producto.getFrecuenciaMantencionMeses() != null)
                        psIns.setInt(i++, producto.getFrecuenciaMantencionMeses());
                    else
                        psIns.setNull(i++, Types.INTEGER);

                    // aplica_vencimiento (¡nunca null!)
                    psIns.setBoolean(i++, Boolean.TRUE.equals(producto.getAplicaVencimiento()));

                    // es_epp
                    if (producto.getEsEpp() != null)
                        psIns.setBoolean(i++, producto.getEsEpp());
                    else
                        psIns.setNull(i++, Types.BOOLEAN);

                    // es_serializable
                    if (producto.getEsSerializable() != null)
                        psIns.setBoolean(i++, producto.getEsSerializable());
                    else
                        psIns.setNull(i++, Types.BOOLEAN);

                    psIns.executeUpdate();

                    try (ResultSet keys = psIns.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se pudo obtener el ID del nuevo producto.");
                        idProductoGenerado = keys.getInt(1);
                    }
                }

                // === Upsert en bodega_producto ===
                // Si ya existe fila para (producto, bodega, subbodega), sumamos; si no, insertamos.
                String sqlUpsert = """
                INSERT INTO bodega_producto (idproducto, idbodega_principal, idsubbodega, cantidad)
                VALUES (?,?,?,?)
                ON CONFLICT (idproducto, idbodega_principal, idsubbodega)
                DO UPDATE SET cantidad = bodega_producto.cantidad + EXCLUDED.cantidad
                """;

                try (PreparedStatement psBP = conn.prepareStatement(sqlUpsert)) {
                    psBP.setInt(1, idProductoGenerado);
                    psBP.setInt(2, producto.getIdBodegaPrincipal());
                    psBP.setInt(3, producto.getIdSubbodega());
                    psBP.setInt(4, producto.getCantidad());
                    psBP.executeUpdate();
                }

                // === Mantener cantidad total en producto (suma de todas las ubicaciones) ===
                String sqlSyncCantidad = """
                UPDATE producto p
                SET cantidad = COALESCE(t.total,0)
                FROM (SELECT idproducto, SUM(cantidad) AS total
                      FROM bodega_producto
                      GROUP BY idproducto) t
                WHERE p.idproducto = t.idproducto AND p.idproducto = ?
                """;
                try (PreparedStatement psSum = conn.prepareStatement(sqlSyncCantidad)) {
                    psSum.setInt(1, idProductoGenerado);
                    psSum.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // === Obtener por ID (datos básicos) ===
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

    // === Obtener todos (compat) ===
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
        } catch (SQLException e) {
            throw new SQLException("Error al obtener los productos: " + e.getMessage(), e);
        }
        return productos;
    }

    // === Buscar por bodega (compat) ===
    public List<ProductoForm> buscarProductosPorBodega(String textoBusqueda, Integer idBodega) throws SQLException {
        List<ProductoForm> productos = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT p.idproducto, p.nombre, bp.cantidad, p.fechaingreso, p.ubicacion, " +
                        "bp.idbodega_principal, bp.idsubbodega " +
                        "FROM producto p " +
                        "JOIN bodega_producto bp ON p.idproducto = bp.idproducto WHERE 1=1"
        );

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

    // === Nombres (igual) ===
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

    // === Eliminar producto (con sync de total) ===
    public void eliminarProducto(int idProducto, int idBodega, int idSubbodega, int cantidadEliminar) throws SQLException {
        String verificarCantidadQuery = "SELECT cantidad FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String actualizarCantidadQuery = "UPDATE bodega_producto SET cantidad = cantidad - ? WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String eliminarRegistroQuery = "DELETE FROM bodega_producto WHERE idproducto = ? AND idbodega_principal = ? AND idsubbodega = ?";
        String verificarExistenciasTotalesQuery = "SELECT SUM(cantidad) AS total FROM bodega_producto WHERE idproducto = ?";
        String eliminarProductoQuery = "DELETE FROM producto WHERE idproducto = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            int cantidadDisponible;
            try (PreparedStatement verificarStmt = conn.prepareStatement(verificarCantidadQuery)) {
                verificarStmt.setInt(1, idProducto);
                verificarStmt.setInt(2, idBodega);
                verificarStmt.setInt(3, idSubbodega);

                try (ResultSet rs = verificarStmt.executeQuery()) {
                    if (!rs.next()) throw new SQLException("El producto no existe en la subbodega especificada.");
                    cantidadDisponible = rs.getInt("cantidad");
                    if (cantidadEliminar > cantidadDisponible) {
                        throw new SQLException("Cantidad insuficiente para eliminar en esta subbodega.");
                    }
                }
            }

            if (cantidadEliminar == cantidadDisponible) {
                try (PreparedStatement eliminarStmt = conn.prepareStatement(eliminarRegistroQuery)) {
                    eliminarStmt.setInt(1, idProducto);
                    eliminarStmt.setInt(2, idBodega);
                    eliminarStmt.setInt(3, idSubbodega);
                    eliminarStmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement(actualizarCantidadQuery)) {
                    stmt.setInt(1, cantidadEliminar);
                    stmt.setInt(2, idProducto);
                    stmt.setInt(3, idBodega);
                    stmt.setInt(4, idSubbodega);
                    stmt.executeUpdate();
                }
            }

            try (PreparedStatement verificarTotalesStmt = conn.prepareStatement(verificarExistenciasTotalesQuery)) {
                verificarTotalesStmt.setInt(1, idProducto);

                try (ResultSet rs = verificarTotalesStmt.executeQuery()) {
                    if (rs.next()) {
                        int existenciasTotales = rs.getInt("total");
                        if (existenciasTotales <= 0) {
                            try (PreparedStatement eliminarProductoStmt = conn.prepareStatement(eliminarProductoQuery)) {
                                eliminarProductoStmt.setInt(1, idProducto);
                                eliminarProductoStmt.executeUpdate();
                            }
                        } else {
                            String updProd = "UPDATE producto SET cantidad = ? WHERE idproducto = ?";
                            try (PreparedStatement ps = conn.prepareStatement(updProd)) {
                                ps.setInt(1, existenciasTotales);
                                ps.setInt(2, idProducto);
                                ps.executeUpdate();
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

    // === Eliminar cantidad (con sync de total) ===
    public void eliminarCantidadProducto(int idProducto,
                                         int idBodegaPrincipal,
                                         int idSubbodega,
                                         int cantidadAEliminar) throws SQLException {
        if (cantidadAEliminar <= 0) {
            throw new IllegalArgumentException("La cantidad a eliminar debe ser > 0");
        }

        Connection con = null;
        boolean oldAutoCommit = true;

        try {
            con = DatabaseConnection.getConnection(); // usa tu forma de obtener conexión
            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            // 1) ¿Existe el producto en esa ubicación? Bloquea la fila.
            int cantidadActual = 0;
            try (PreparedStatement psSel = con.prepareStatement(
                    "SELECT cantidad " +
                            "FROM bodega_producto " +
                            "WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=? " +
                            "FOR UPDATE")) {
                psSel.setInt(1, idProducto);
                psSel.setInt(2, idBodegaPrincipal);
                psSel.setInt(3, idSubbodega);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("El producto no existe en la subbodega especificada.");
                    }
                    cantidadActual = rs.getInt(1);
                }
            }

            // 2) Calcula nueva cantidad para esa ubicación
            int nuevaCantidadUbic = cantidadActual - cantidadAEliminar;

            if (nuevaCantidadUbic > 0) {
                // Descuenta
                try (PreparedStatement psUpd = con.prepareStatement(
                        "UPDATE bodega_producto " +
                                "SET cantidad=? " +
                                "WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?")) {
                    psUpd.setInt(1, nuevaCantidadUbic);
                    psUpd.setInt(2, idProducto);
                    psUpd.setInt(3, idBodegaPrincipal);
                    psUpd.setInt(4, idSubbodega);
                    psUpd.executeUpdate();
                }
            } else {
                // Llega a 0 o negativo → elimina la fila de esa ubicación
                try (PreparedStatement psDel = con.prepareStatement(
                        "DELETE FROM bodega_producto " +
                                "WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?")) {
                    psDel.setInt(1, idProducto);
                    psDel.setInt(2, idBodegaPrincipal);
                    psDel.setInt(3, idSubbodega);
                    psDel.executeUpdate();
                }
            }

            // 3) Sincroniza cantidad total en producto (suma de todas las ubicaciones)
            int total = 0;
            try (PreparedStatement psSum = con.prepareStatement(
                    "SELECT COALESCE(SUM(cantidad),0) " +
                            "FROM bodega_producto WHERE idproducto=?")) {
                psSum.setInt(1, idProducto);
                try (ResultSet rs = psSum.executeQuery()) {
                    if (rs.next()) total = rs.getInt(1);
                }
            }

            try (PreparedStatement psUpdProd = con.prepareStatement(
                    "UPDATE producto SET cantidad=? WHERE idproducto=?")) {
                psUpdProd.setInt(1, total);
                psUpdProd.setInt(2, idProducto);
                psUpdProd.executeUpdate();
            }

            // 4) Si total == 0 → borrar producto si no tiene dependencias EPP,
            //    de lo contrario marcar SIN_STOCK (si tienes esa tabla/estado)
            if (total <= 0) {
                boolean tieneEpp = false;
                try (PreparedStatement psHas = con.prepareStatement(
                        "SELECT 1 FROM epp_item WHERE idproducto=? LIMIT 1")) {
                    psHas.setInt(1, idProducto);
                    try (ResultSet rs = psHas.executeQuery()) {
                        tieneEpp = rs.next();
                    }
                }

                if (!tieneEpp) {
                    try (PreparedStatement psDelProd = con.prepareStatement(
                            "DELETE FROM producto WHERE idproducto=?")) {
                        psDelProd.setInt(1, idProducto);
                        psDelProd.executeUpdate();
                    }
                } else {
                    // Marca el estado como SIN_STOCK si manejas catálogo de estados
                    try (PreparedStatement psUpdEstado = con.prepareStatement(
                            "UPDATE producto " +
                                    "SET id_estado = (SELECT id_estado FROM producto_estado WHERE nombre='SIN_STOCK' LIMIT 1) " +
                                    "WHERE idproducto=?")) {
                        psUpdEstado.setInt(1, idProducto);
                        psUpdEstado.executeUpdate();
                    }
                }
            }

            con.commit();
        } catch (SQLException ex) {
            if (con != null) con.rollback();
            throw ex;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
                try { con.close(); } catch (Exception ignored) {}
            }
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

            // sincroniza el total en producto
            String totalSql = "SELECT COALESCE(SUM(cantidad),0) FROM bodega_producto WHERE idproducto = ?";
            try (PreparedStatement psTot = conn.prepareStatement(totalSql)) {
                psTot.setInt(1, idProducto);
                try (ResultSet rs = psTot.executeQuery()) {
                    if (rs.next()) {
                        int total = rs.getInt(1);
                        String upd = "UPDATE producto SET cantidad=? WHERE idproducto=?";
                        try (PreparedStatement ps = conn.prepareStatement(upd)) {
                            ps.setInt(1, total);
                            ps.setInt(2, idProducto);
                            ps.executeUpdate();
                        }
                    }
                }
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
                        "JOIN producto p           ON p.idproducto = bp.idproducto " +
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
        return 1;
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

    // Devuelve id_material a partir del nombre (p.ej. 'RESCATE','INCENDIO','EPP') o null si no aplica
    private Integer resolveIdMaterial(Connection conn, String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) return null;
        final String sql = "SELECT id_material FROM material_tipo WHERE UPPER(nombre) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null; // no encontrado → no forzamos error
    }

    // Devuelve id_estado a partir del nombre (p.ej. 'ACTIVO','DESCONTINUADO','SIN_STOCK') o null
    private Integer resolveIdEstado(Connection conn, String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) return null;
        final String sql = "SELECT id_estado FROM producto_estado WHERE UPPER(nombre) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    // Lista nombres de productos que tienen stock (>0) en una ubicación exacta
    public List<String> obtenerProductosConStockEn(int idBodega, int idSubbodega) throws SQLException {
        String sql =
                "SELECT DISTINCT p.nombre " +
                        "FROM bodega_producto bp " +
                        "JOIN producto p ON p.idproducto = bp.idproducto " +
                        "WHERE bp.idbodega_principal = ? " +
                        "  AND bp.idsubbodega = ? " +
                        "  AND bp.cantidad > 0 " +
                        "ORDER BY p.nombre";
        List<String> out = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idBodega);
            ps.setInt(2, idSubbodega);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    // Lista "producto + ubicación" para poblar combos de mover/eliminar
    public java.util.List<ProductoUbicacion> listarProductoUbicaciones() throws java.sql.SQLException {
        String sql = """
        SELECT p.idproducto,
               p.nombre,
               bp.idbodega_principal,
               bp.idsubbodega,
               b.nombre  AS bodega_nombre,
               s.nombre  AS subbodega_nombre,
               bp.cantidad
        FROM bodega_producto bp
        JOIN producto p          ON p.idproducto = bp.idproducto
        JOIN bodega_principal b  ON b.idbodega_principal = bp.idbodega_principal
        JOIN subbodega s         ON s.idsubbodega = bp.idsubbodega
        WHERE bp.cantidad > 0
        ORDER BY p.nombre, b.nombre, s.nombre
    """;
        try (var c = DatabaseConnection.getConnection();
             var ps = c.prepareStatement(sql);
             var rs = ps.executeQuery()) {

            var out = new java.util.ArrayList<ProductoUbicacion>();
            while (rs.next()) {
                out.add(new ProductoUbicacion(
                        rs.getInt("idproducto"),
                        rs.getInt("idbodega_principal"),
                        rs.getInt("idsubbodega"),
                        rs.getString("nombre"),
                        rs.getString("bodega_nombre"),
                        rs.getString("subbodega_nombre"),
                        rs.getInt("cantidad")
                ));
            }
            return out;
        }
    }

    // Mover stock entre ubicaciones (transacción con UPSERT en destino)
    public void moverProducto(int idProducto,
                              int desdeBodega, int desdeSubbodega,
                              int haciaBodega, int haciaSubbodega,
                              int cantidad) throws java.sql.SQLException {
        if (cantidad <= 0) throw new IllegalArgumentException("Cantidad debe ser > 0");
        if (desdeBodega == haciaBodega && desdeSubbodega == haciaSubbodega)
            throw new IllegalArgumentException("El destino no puede ser igual al origen.");

        String qLock = """
        SELECT cantidad FROM bodega_producto
        WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?
        FOR UPDATE
    """;
        String qDec = """
        UPDATE bodega_producto
        SET cantidad = cantidad - ?
        WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?
    """;
        String qUpsertDest = """
        INSERT INTO bodega_producto(idproducto,idbodega_principal,idsubbodega,cantidad)
        VALUES (?,?,?,?)
        ON CONFLICT (idproducto,idbodega_principal,idsubbodega)
        DO UPDATE SET cantidad = bodega_producto.cantidad + EXCLUDED.cantidad
    """;
        String qCleanZero = """
        DELETE FROM bodega_producto
        WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?
          AND cantidad <= 0
    """;
        String qSyncTotales = """
        UPDATE producto p
        SET cantidad = COALESCE(t.total,0)
        FROM (SELECT idproducto, SUM(cantidad) AS total
              FROM bodega_producto GROUP BY idproducto) t
        WHERE p.idproducto = t.idproducto
    """;

        var c = DatabaseConnection.getConnection();
        boolean old = c.getAutoCommit();
        c.setAutoCommit(false);
        try (var psLock = c.prepareStatement(qLock);
             var psDec  = c.prepareStatement(qDec);
             var psIns  = c.prepareStatement(qUpsertDest);
             var psDel0 = c.prepareStatement(qCleanZero);
             var psSync = c.prepareStatement(qSyncTotales)) {

            // 1) Lock y validación stock origen
            psLock.setInt(1, idProducto); psLock.setInt(2, desdeBodega); psLock.setInt(3, desdeSubbodega);
            try (var rs = psLock.executeQuery()) {
                if (!rs.next()) throw new java.sql.SQLException("El producto no existe en el origen.");
                int disp = rs.getInt(1);
                if (disp < cantidad) throw new java.sql.SQLException("Stock insuficiente en el origen ("+disp+").");
            }

            // 2) Descuento en origen
            psDec.setInt(1, cantidad);
            psDec.setInt(2, idProducto);
            psDec.setInt(3, desdeBodega);
            psDec.setInt(4, desdeSubbodega);
            psDec.executeUpdate();

            // 3) UPSERT en destino (suma)
            psIns.setInt(1, idProducto);
            psIns.setInt(2, haciaBodega);
            psIns.setInt(3, haciaSubbodega);
            psIns.setInt(4, cantidad);
            psIns.executeUpdate();

            // 4) Limpia fila en cero
            psDel0.setInt(1, idProducto);
            psDel0.setInt(2, desdeBodega);
            psDel0.setInt(3, desdeSubbodega);
            psDel0.executeUpdate();

            // 5) Sincroniza totales
            psSync.executeUpdate();

            c.commit();
        } catch (java.sql.SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(old);
            c.close();
        }
    }

    // Eliminar cantidad en una ubicación exacta
    public void eliminarCantidad(int idProducto, int idBodega, int idSubbodega, int cantidad)
            throws java.sql.SQLException {
        if (cantidad <= 0) throw new IllegalArgumentException("Cantidad debe ser > 0");

        String qLock = """
        SELECT cantidad FROM bodega_producto
        WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?
        FOR UPDATE
    """;
        String qDec = """
        UPDATE bodega_producto
        SET cantidad = cantidad - ?
        WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?
    """;
        String qCleanZero = """
        DELETE FROM bodega_producto
        WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?
          AND cantidad <= 0
    """;
        String qSyncTotales = """
        UPDATE producto p
        SET cantidad = COALESCE(t.total,0)
        FROM (SELECT idproducto, SUM(cantidad) AS total
              FROM bodega_producto GROUP BY idproducto) t
        WHERE p.idproducto = t.idproducto
    """;

        var c = DatabaseConnection.getConnection();
        boolean old = c.getAutoCommit();
        c.setAutoCommit(false);
        try (var psLock = c.prepareStatement(qLock);
             var psDec  = c.prepareStatement(qDec);
             var psDel0 = c.prepareStatement(qCleanZero);
             var psSync = c.prepareStatement(qSyncTotales)) {

            psLock.setInt(1, idProducto); psLock.setInt(2, idBodega); psLock.setInt(3, idSubbodega);
            try (var rs = psLock.executeQuery()) {
                if (!rs.next()) throw new java.sql.SQLException("El producto no existe en la subbodega especificada.");
                int disp = rs.getInt(1);
                if (disp < cantidad) throw new java.sql.SQLException("Stock insuficiente ("+disp+").");
            }

            psDec.setInt(1, cantidad);
            psDec.setInt(2, idProducto);
            psDec.setInt(3, idBodega);
            psDec.setInt(4, idSubbodega);
            psDec.executeUpdate();

            psDel0.setInt(1, idProducto);
            psDel0.setInt(2, idBodega);
            psDel0.setInt(3, idSubbodega);
            psDel0.executeUpdate();

            psSync.executeUpdate();
            c.commit();
        } catch (java.sql.SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(old);
            c.close();
        }
    }

    public int ensureProductoEppPorTipoNombre(int idTipo,
                                              String talla,
                                              boolean esSerializable,
                                              int idBodega,
                                              int idSubbodega) throws SQLException {
        // 1) Nombre legible desde epp_tipo
        String nombre;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT tipo || ' / ' || subtipo FROM epp_tipo WHERE id_tipo = ?")) {
            ps.setInt(1, idTipo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("id_tipo no encontrado en epp_tipo: " + idTipo);
                nombre = rs.getString(1);
            }
        }
        if (talla != null && !talla.isBlank()) nombre = "EPP: " + nombre + " - Talla " + talla;
        else nombre = "EPP: " + nombre;

        // 2) ¿Ya existe un catálogo EPP con ese nombre?
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT idproducto FROM producto WHERE es_epp = TRUE AND nombre = ? LIMIT 1")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }

        // 3) Crear catálogo (ubicacion NUNCA nula)
        final String ins = """
        INSERT INTO producto(
            nombre, cantidad, fechaingreso, ubicacion,
            idbodega_principal, idsubbodega,
            marca, valor_referencial, id_estado, id_material,
            observacion, requiere_mantencion, frecuencia_mantencion_meses,
            aplica_vencimiento, es_epp, es_serializable
        ) VALUES ( ?, 0, CURRENT_DATE, ?, ?, ?, 
                   NULL, NULL, NULL, NULL,
                   NULL, FALSE, NULL,
                   FALSE, TRUE, ?)
        RETURNING idproducto
    """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(ins)) {
            int i=1;
            ps.setString(i++, nombre);
            ps.setString(i++, "");          // <- evita NOT NULL en 'ubicacion'
            ps.setInt(i++, idBodega);
            ps.setInt(i++, idSubbodega);
            ps.setBoolean(i++, esSerializable);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void upsertCantidad(Connection c,
                               int idProducto,
                               int idBodega,
                               int idSubbodega,
                               int delta) throws SQLException {
        String upd = """
        UPDATE bodega_producto
           SET cantidad = cantidad + ?
         WHERE idproducto=? AND idbodega_principal=? AND idsubbodega=?
    """;
        try (PreparedStatement ps = c.prepareStatement(upd)) {
            ps.setInt(1, delta);
            ps.setInt(2, idProducto);
            ps.setInt(3, idBodega);
            ps.setInt(4, idSubbodega);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                String ins = """
                INSERT INTO bodega_producto(idproducto,idbodega_principal,idsubbodega,cantidad)
                VALUES (?,?,?,?)
            """;
                try (PreparedStatement ps2 = c.prepareStatement(ins)) {
                    ps2.setInt(1, idProducto);
                    ps2.setInt(2, idBodega);
                    ps2.setInt(3, idSubbodega);
                    ps2.setInt(4, Math.max(delta, 0));
                    ps2.executeUpdate();
                }
            }
        }

        // Sincroniza el total del producto (opcional pero útil aquí)
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE producto p SET cantidad = COALESCE(t.total,0) " +
                        "FROM (SELECT idproducto, SUM(cantidad) total FROM bodega_producto GROUP BY idproducto) t " +
                        "WHERE p.idproducto = t.idproducto AND p.idproducto = ?")) {
            ps.setInt(1, idProducto);
            ps.executeUpdate();
        }
    }


}
