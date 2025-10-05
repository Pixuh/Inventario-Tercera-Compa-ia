import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EppService {

    /**
     * Crea un lote de items EPP serializados para un producto dado.
     *
     * @param idProducto        id del producto (catálogo)
     * @param idTipo            id de epp_tipo (tipo/subtipo ya resuelto)
     * @param prefijo           prefijo del código interno, ej: "CB1-CHAQ"
     * @param cantidad          cuántos items crear
     * @param talla             talla a guardar (puede ser vacío)
     * @param idBodega          bodega destino
     * @param idSubbodega       subbodega destino
     * @return número de ítems creados
     */
    public int crearLote(int idProducto,
                         int idTipo,
                         String prefijo,
                         int cantidad,
                         String talla,
                         int idBodega,
                         int idSubbodega) throws SQLException {

        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser > 0");
        if (prefijo == null || prefijo.isBlank()) throw new IllegalArgumentException("Prefijo requerido");

        final String sqlMax =
                // Toma el tramo numérico al final del código y busca el mayor para continuar la secuencia
                "SELECT COALESCE(MAX(CAST(RIGHT(codigo_interno, 4) AS INTEGER)), 0) " +
                        "FROM epp_item WHERE codigo_interno LIKE ?";

        final String sqlIns =
                "INSERT INTO epp_item (" +
                        "  idproducto, id_tipo, codigo_interno, n_serie, talla, " +
                        "  fecha_puesta_servicio, vida_util_meses, idbodega_principal, idsubbodega, estado" +
                        ") VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?, 'SERVICIO')";

        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);

            int base;
            // 1) calcular desde qué número seguimos para ese prefijo
            try (PreparedStatement ps = c.prepareStatement(sqlMax)) {
                ps.setString(1, prefijo + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    base = (rs.next() ? rs.getInt(1) : 0);
                }
            }

            int creados = 0;

            // 2) insertar ítems uno a uno
            try (PreparedStatement ins = c.prepareStatement(sqlIns)) {
                for (int i = 1; i <= cantidad; i++) {
                    String codigo = prefijo + "-" + String.format("%04d", base + i);

                    ins.setInt(1, idProducto);
                    ins.setInt(2, idTipo);
                    ins.setString(3, codigo);
                    ins.setString(4, null);                          // n_serie (si lo quieres, cámbialo)
                    ins.setString(5, (talla == null ? "" : talla));  // talla
                    ins.setObject(6, 120);                           // vida_util_meses (ajusta por tipo)
                    ins.setInt(7, idBodega);
                    ins.setInt(8, idSubbodega);

                    ins.executeUpdate();
                    creados++;
                }
            }

            c.commit();
            return creados;
        }
    }

    // === LISTAR ITEMS POR PRODUCTO ===
    public java.util.List<ItemEppDTO> listarItemsPorProducto(int idProducto) throws SQLException {
        String sql = """
        SELECT 
            ei.id_epp        AS id,            -- ajusta a tu PK real: id_epp / idepp
            ei.codigo_interno AS codigo,
            ei.n_serie        AS serie,
            ei.talla          AS talla,
            bp.nombre         AS bodega,
            sb.nombre         AS subbodega,
            ei.estado         AS estado
        FROM epp_item ei
        JOIN bodega_principal bp ON bp.idbodega_principal = ei.idbodega_principal
        JOIN subbodega        sb ON sb.idsubbodega        = ei.idsubbodega
        WHERE ei.idproducto = ? 
          AND COALESCE(ei.estado,'SERVICIO') = 'SERVICIO'
        ORDER BY ei.id_epp
        """;

        java.util.List<ItemEppDTO> out = new java.util.ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemEppDTO d = new ItemEppDTO();
                    d.idEpp     = rs.getInt("id");
                    d.codigo    = rs.getString("codigo");
                    d.serie     = rs.getString("serie");
                    d.talla     = rs.getString("talla");
                    d.bodega    = rs.getString("bodega");
                    d.subbodega = rs.getString("subbodega");
                    d.estado    = rs.getString("estado");
                    out.add(d);
                }
            }
        }
        return out;
    }

    // === LISTAR MOVIMIENTOS DE UN ÍTEM ===
    public java.util.List<MovDTO> listarMovimientos(int idEpp) throws SQLException {
        String sql = """
        SELECT 
            fecha,
            desde_bodega,
            desde_subbodega,
            hacia_bodega,
            hacia_subbodega,
            motivo
        FROM epp_movimiento
        WHERE id_epp = ?         -- ajusta a tu columna real: id_epp / id_epp / idepp
        ORDER BY fecha DESC
        """;

        java.util.List<MovDTO> out = new java.util.ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MovDTO m = new MovDTO();
                    m.fecha          = rs.getString("fecha");
                    m.desdeBodega    = rs.getString("desde_bodega");
                    m.desdeSubbodega = rs.getString("desde_subbodega");
                    m.haciaBodega    = rs.getString("hacia_bodega");
                    m.haciaSubbodega = rs.getString("hacia_subbodega");
                    m.motivo         = rs.getString("motivo");
                    out.add(m);
                }
            }
        }
        return out;
    }

    // === MOVER ÍTEM + REGISTRAR MOVIMIENTO (misma TX) ===
    public void moverItem(int idEpp, int idBodegaDestino, int idSubbodegaDestino, String motivo) throws SQLException {
        // Lee ubicación actual con FOR UPDATE, actualiza e inserta en epp_movimiento en una sola transacción
        final String selectUbic = """
        SELECT idbodega_principal, idsubbodega
        FROM epp_item
        WHERE id_epp = ?           -- ajusta a tu PK real: id_epp / idepp
        FOR UPDATE
        """;
        final String updItem = """
        UPDATE epp_item
        SET idbodega_principal = ?, idsubbodega = ?
        WHERE id_epp = ?           -- ajusta PK
        """;
        final String insMov = """
        INSERT INTO epp_movimiento
        (id_epp, fecha, desde_bodega, desde_subbodega, hacia_bodega, hacia_subbodega, motivo)
        VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?)
        """;

        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);

            Integer desdeB = null, desdeS = null;

            try (PreparedStatement ps = c.prepareStatement(selectUbic)) {
                ps.setInt(1, idEpp);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); throw new SQLException("EPP no existe"); }
                    desdeB = rs.getInt(1);
                    desdeS = rs.getInt(2);
                }
            }

            try (PreparedStatement psU = c.prepareStatement(updItem)) {
                psU.setInt(1, idBodegaDestino);
                psU.setInt(2, idSubbodegaDestino);
                psU.setInt(3, idEpp);
                if (psU.executeUpdate() == 0) { c.rollback(); throw new SQLException("No se pudo mover el EPP"); }
            }

            try (PreparedStatement psI = c.prepareStatement(insMov)) {
                psI.setInt(1, idEpp);
                psI.setInt(2, desdeB);
                psI.setInt(3, desdeS);
                psI.setInt(4, idBodegaDestino);
                psI.setInt(5, idSubbodegaDestino);
                psI.setString(6, motivo == null ? "" : motivo.trim());
                psI.executeUpdate();
            }

            c.commit();
        }
    }

    // Dentro de EppService
    public static class ItemEppDTO {
        public int idEpp;
        public String codigo;
        public String serie;
        public String talla;
        public String bodega;
        public String subbodega;
        public String estado;
    }

    public static class MovDTO {
        public String fecha;
        public String desdeBodega;
        public String desdeSubbodega;
        public String haciaBodega;
        public String haciaSubbodega;
        public String motivo;
    }

    public void agregarEppSimple(int idTipo,
                                 String talla,
                                 String serieOpcional,
                                 String estadoUi,         // "Nuevo" en la UI -> mapeado a SERVICIO
                                 int idBodega,
                                 int idSubbodega,
                                 int cantidad) throws SQLException {

        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser > 0");

        // Estado válido para la tabla (evita el CHECK)
        String estado = (estadoUi == null ? "" : estadoUi.trim().toUpperCase());
        if (estado.isEmpty() || estado.equals("NUEVO")) estado = "SERVICIO";

        // 1) Asegura un "catálogo" de producto EPP para este tipo/talla en esa ubicación
        ProductoDAO productoDAO = new ProductoDAO();
        int idProducto = productoDAO.ensureProductoEppPorTipoNombre(
                idTipo, (talla == null ? "" : talla), /*esSerializable=*/true, idBodega, idSubbodega);

        // 2) Prefijo de código interno a partir de epp_tipo (por ej. "ESTRUCTURAL/Casco - Talla L" -> "EST-CAS-L")
        String prefijo = obtenerPrefijoCodigo(idTipo, talla);

        final String sqlMax = "SELECT COALESCE(MAX(CAST(RIGHT(codigo_interno,4) AS INTEGER)),0) " +
                "FROM epp_item WHERE codigo_interno LIKE ?";
        final String sqlIns = """
        INSERT INTO epp_item(
            idproducto, id_tipo, codigo_interno, n_serie, talla,
            fecha_puesta_servicio, vida_util_meses, idbodega_principal, idsubbodega, estado
        ) VALUES (?, ?, ?, ?, ?, CURRENT_DATE, NULL, ?, ?, ?)
    """;

        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 3) Base correlativa por prefijo (para generar 0001, 0002, …)
                int base;
                try (PreparedStatement ps = c.prepareStatement(sqlMax)) {
                    ps.setString(1, prefijo + "%");
                    try (ResultSet rs = ps.executeQuery()) { base = rs.next() ? rs.getInt(1) : 0; }
                }

                // 4) Crear N ítems (si el usuario puso serie, la usamos en el primero; si no, queda null)
                try (PreparedStatement ins = c.prepareStatement(sqlIns)) {
                    for (int i = 1; i <= cantidad; i++) {
                        String codigo = prefijo + String.format("%04d", base + i);

                        ins.setInt(1, idProducto);
                        ins.setInt(2, idTipo);
                        ins.setString(3, codigo);
                        // Primera unidad: usa la serie si vino; resto null
                        ins.setString(4, (i == 1 && serieOpcional != null && !serieOpcional.isBlank())
                                ? serieOpcional.trim() : null);
                        ins.setString(5, (talla == null ? "" : talla));
                        ins.setInt(6, idBodega);
                        ins.setInt(7, idSubbodega);
                        ins.setString(8, estado);
                        ins.executeUpdate();
                    }
                }

                // 5) Sincroniza stock por ubicación y total
                productoDAO.upsertCantidad(c, idProducto, idBodega, idSubbodega, +cantidad);

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    private String obtenerPrefijoCodigo(int idTipo, String talla) throws SQLException {
        String tipo = "", subtipo = "";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT tipo, subtipo FROM epp_tipo WHERE id_tipo=?")) {
            ps.setInt(1, idTipo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { tipo = rs.getString(1); subtipo = rs.getString(2); }
            }
        }
        String abv = abreviar(tipo) + "-" + abreviar(subtipo);
        if (talla != null && !talla.isBlank()) abv += "-" + talla.trim().toUpperCase();
        return (abv + "-").replaceAll("[^A-Z0-9-]", "").toUpperCase();
    }

    private String abreviar(String s) {
        if (s == null) return "";
        s = s.trim().toUpperCase();
        // toma primeras 3 letras de cada palabra: "Estructural Casco" -> "EST-CAS"
        String[] parts = s.split("\\s+|/");
        StringBuilder out = new StringBuilder();
        for (int i=0;i<parts.length;i++) {
            if (parts[i].isEmpty()) continue;
            if (out.length()>0) out.append("-");
            out.append(parts[i].substring(0, Math.min(3, parts[i].length())));
        }
        return out.toString();
    }


}
