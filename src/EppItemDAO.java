import java.sql.*;
import java.util.*;

public class EppItemDAO {

    public int crearItem(EppItemForm f) throws SQLException {
        String sql = """
            INSERT INTO epp_item(
              idproducto, id_tipo, codigo_interno, n_serie, talla,
              fecha_fabricacion, fecha_puesta_servicio, vida_util_meses,
              idbodega_principal, idsubbodega, estado,
              fecha_vencimiento, proxima_mantencion
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            RETURNING id_epp
            """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, f.idProducto);
            ps.setInt(2, f.idTipo);
            ps.setString(3, f.codigoInterno);
            ps.setString(4, f.nSerie);
            ps.setString(5, f.talla);
            ps.setDate(6, f.fechaFabricacion == null ? null : new java.sql.Date(f.fechaFabricacion.getTime()));
            ps.setDate(7, f.fechaPuestaServicio == null ? null : new java.sql.Date(f.fechaPuestaServicio.getTime()));
            if (f.vidaUtilMeses == null) ps.setNull(8, Types.INTEGER); else ps.setInt(8, f.vidaUtilMeses);
            ps.setInt(9, f.idBodega);
            ps.setInt(10, f.idSubbodega);
            ps.setString(11, f.estado); // 'SERVICIO','EN_MANTENCION','FUERA_DE_SERVICIO','DADO_DE_BAJA'
            ps.setDate(12, f.fechaVencimiento == null ? null : new java.sql.Date(f.fechaVencimiento.getTime()));
            ps.setDate(13, f.proximaMantencion == null ? null : new java.sql.Date(f.proximaMantencion.getTime()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se pudo crear epp_item");
    }

    public Map<String,Object> getItem(int idEpp) throws SQLException {
        String sql = """
            SELECT ei.*, p.nombre AS producto, t.tipo, t.subtipo,
                   bp.nombre AS bodega_nombre, sb.nombre AS subbodega_nombre
            FROM epp_item ei
            JOIN producto p ON p.idproducto = ei.idproducto
            JOIN epp_tipo t ON t.id_tipo = ei.id_tipo
            JOIN bodega_principal bp ON bp.idbodega_principal = ei.idbodega_principal
            JOIN subbodega sb ON sb.idsubbodega = ei.idsubbodega
            WHERE ei.id_epp=?
            """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ResultSetMetaData md = rs.getMetaData();
                Map<String,Object> row = new LinkedHashMap<>();
                for (int i=1;i<=md.getColumnCount();i++) row.put(md.getColumnName(i), rs.getObject(i));
                return row;
            }
        }
    }

    public void cambiarEstado(int idEpp, String nuevoEstado) throws SQLException {
        String sql = "UPDATE epp_item SET estado=? WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idEpp);
            if (ps.executeUpdate()==0) throw new SQLException("Item no encontrado");
        }
    }

    public void moverItem(int idEpp, int idBodegaDestino, int idSubbodegaDestino, String motivo) throws SQLException {
        final String selectUbic = "SELECT idbodega_principal, idsubbodega FROM epp_item WHERE id_epp=? FOR UPDATE";
        final String updItem    = "UPDATE epp_item SET idbodega_principal=?, idsubbodega=? WHERE id_epp=?";
        final String insMov     =
                "INSERT INTO epp_movimiento(id_epp, desde_bodega, desde_subbodega, hacia_bodega, hacia_subbodega, motivo) " +
                        "VALUES(?, ?, ?, ?, ?, ?)";

        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);

            int desdeB; // usa sentinel si quieres: = -1;
            int desdeS; // = -1;

            // 1) Leer ubicación actual con lock
            try (PreparedStatement ps = c.prepareStatement(selectUbic)) {
                ps.setInt(1, idEpp);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        throw new SQLException("EPP no existe (id=" + idEpp + ")");
                    }
                    desdeB = rs.getInt(1);
                    desdeS = rs.getInt(2);
                }
            }

            // 2) Actualizar a destino
            try (PreparedStatement psU = c.prepareStatement(updItem)) {
                psU.setInt(1, idBodegaDestino);
                psU.setInt(2, idSubbodegaDestino);
                psU.setInt(3, idEpp);
                psU.executeUpdate();
            }

            // 3) Registrar movimiento
            try (PreparedStatement psM = c.prepareStatement(insMov)) {
                psM.setInt(1, idEpp);
                psM.setInt(2, desdeB);
                psM.setInt(3, desdeS);
                psM.setInt(4, idBodegaDestino);
                psM.setInt(5, idSubbodegaDestino);
                psM.setString(6, motivo);
                psM.executeUpdate();
            }

            c.commit();
        }
    }


    /** Listado básico con filtros opcionales. */
    public List<Map<String,Object>> listar(String texto, Integer idBodega, Integer idSubbodega, String estado) throws SQLException {
        StringBuilder sb = new StringBuilder("""
            SELECT ei.id_epp, p.nombre AS producto, t.tipo, t.subtipo,
                   ei.codigo_interno, ei.n_serie, ei.talla, ei.estado,
                   bp.nombre AS bodega, sb.nombre AS subbodega,
                   ei.fecha_vencimiento, ei.proxima_mantencion
            FROM epp_item ei
            JOIN producto p ON p.idproducto=ei.idproducto
            JOIN epp_tipo t ON t.id_tipo=ei.id_tipo
            JOIN bodega_principal bp ON bp.idbodega_principal=ei.idbodega_principal
            JOIN subbodega sb ON sb.idsubbodega=ei.idsubbodega
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (texto!=null && !texto.isBlank()) { sb.append(" AND (p.nombre ILIKE ? OR ei.codigo_interno ILIKE ? OR ei.n_serie ILIKE ?)"); params.add("%"+texto+"%"); params.add("%"+texto+"%"); params.add("%"+texto+"%"); }
        if (idBodega!=null)   { sb.append(" AND ei.idbodega_principal=?"); params.add(idBodega); }
        if (idSubbodega!=null){ sb.append(" AND ei.idsubbodega=?"); params.add(idSubbodega); }
        if (estado!=null && !estado.isBlank()){ sb.append(" AND ei.estado=?"); params.add(estado); }
        sb.append(" ORDER BY ei.id_epp ASC");

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i=0;i<params.size();i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("id_epp", rs.getInt("id_epp"));
                    row.put("producto", rs.getString("producto"));
                    row.put("tipo", rs.getString("tipo"));
                    row.put("subtipo", rs.getString("subtipo"));
                    row.put("codigo_interno", rs.getString("codigo_interno"));
                    row.put("n_serie", rs.getString("n_serie"));
                    row.put("talla", rs.getString("talla"));
                    row.put("estado", rs.getString("estado"));
                    row.put("bodega", rs.getString("bodega"));
                    row.put("subbodega", rs.getString("subbodega"));
                    row.put("fecha_vencimiento", rs.getDate("fecha_vencimiento"));
                    row.put("proxima_mantencion", rs.getDate("proxima_mantencion"));
                    out.add(row);
                }
                return out;
            }
        }
    }

    /** Próximos a vencer (vencimiento o mantención) dentro de N días. */
    public List<Integer> proximosAVencer(int dias) throws SQLException {
        String sql = """
          SELECT id_epp FROM epp_item
          WHERE (fecha_vencimiento IS NOT NULL AND fecha_vencimiento <= CURRENT_DATE + (? || ' days')::interval)
             OR (proxima_mantencion IS NOT NULL AND proxima_mantencion <= CURRENT_DATE + (? || ' days')::interval)
          ORDER BY COALESCE(fecha_vencimiento, proxima_mantencion)
          """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, dias);
            ps.setInt(2, dias);
            List<Integer> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getInt(1));
            }
            return out;
        }
    }

    // DTO simple para crear items (evitamos depender de tus Forms de UI)
    public static class EppItemForm {
        public int idProducto, idTipo, idBodega, idSubbodega;
        public String codigoInterno, nSerie, talla, estado;
        public java.util.Date fechaFabricacion, fechaPuestaServicio, fechaVencimiento, proximaMantencion;
        public Integer vidaUtilMeses;
    }
}
