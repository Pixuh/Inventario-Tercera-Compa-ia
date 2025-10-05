import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class EppDAO {

    public static class EppItem {
        public Integer idEpp;
        public Integer idProducto;
        public Integer idTipo;
        public String  codigoInterno;
        public String  numeroSerie;
        public String  talla;
        public Date    fechaFabricacion;
        public Date    fechaPuestaServicio;
        public Integer vidaUtilMeses;        // puede ser null (p.ej., cilindros)
        public Integer idBodegaPrincipal;
        public Integer idSubbodega;
        public String  estado;               // 'SERVICIO', 'BAJA', etc.
        public Date    fechaVencimiento;     // puede ser null
        public Date    proximaMantencion;    // puede ser null

        @Override
        public String toString() {
            return "EppItem{" +
                    "idEpp=" + idEpp +
                    ", idProducto=" + idProducto +
                    ", idTipo=" + idTipo +
                    ", codigoInterno='" + codigoInterno + '\'' +
                    ", numeroSerie='" + numeroSerie + '\'' +
                    ", talla='" + talla + '\'' +
                    ", idBodegaPrincipal=" + idBodegaPrincipal +
                    ", idSubbodega=" + idSubbodega +
                    ", estado='" + estado + '\'' +
                    '}';
        }
    }

    public static class MovimientoEpp {
        public Integer idMovimiento;
        public Integer idEpp;
        public Integer desdeBodega;
        public Integer desdeSubbodega;
        public Integer haciaBodega;
        public Integer haciaSubbodega;
        public String  motivo;
        public Timestamp fecha; // asume columna con DEFAULT now()

        @Override
        public String toString() {
            return "MovimientoEpp{" +
                    "idMovimiento=" + idMovimiento +
                    ", idEpp=" + idEpp +
                    ", desdeBodega=" + desdeBodega +
                    ", desdeSubbodega=" + desdeSubbodega +
                    ", haciaBodega=" + haciaBodega +
                    ", haciaSubbodega=" + haciaSubbodega +
                    ", motivo='" + motivo + '\'' +
                    ", fecha=" + fecha +
                    '}';
        }
    }

    public EppDAO() {}



    /** Inserta un nuevo ítem EPP en epp_item. Devuelve id generado. */
    public int insertar(EppItem e) throws SQLException {
        final String sql =
                "INSERT INTO epp_item (" +
                        " idproducto, id_tipo, codigo_interno, n_serie, talla," +
                        " fecha_fabricacion, fecha_puesta_servicio, vida_util_meses," +
                        " idbodega_principal, idsubbodega, estado, fecha_vencimiento, proxima_mantencion" +
                        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id_epp";

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1,  e.idProducto);
            ps.setInt(2,  e.idTipo);
            ps.setString(3, e.codigoInterno);
            ps.setString(4, e.numeroSerie);
            ps.setString(5, e.talla);

            if (e.fechaFabricacion != null)
                ps.setDate(6, new java.sql.Date(e.fechaFabricacion.getTime()));
            else ps.setNull(6, Types.DATE);

            if (e.fechaPuestaServicio != null)
                ps.setDate(7, new java.sql.Date(e.fechaPuestaServicio.getTime()));
            else ps.setNull(7, Types.DATE);

            if (e.vidaUtilMeses != null) ps.setInt(8, e.vidaUtilMeses);
            else ps.setNull(8, Types.INTEGER);

            ps.setInt(9,  e.idBodegaPrincipal);
            ps.setInt(10, e.idSubbodega);
            ps.setString(11, e.estado);

            if (e.fechaVencimiento != null)
                ps.setDate(12, new java.sql.Date(e.fechaVencimiento.getTime()));
            else ps.setNull(12, Types.DATE);

            if (e.proximaMantencion != null)
                ps.setDate(13, new java.sql.Date(e.proximaMantencion.getTime()));
            else ps.setNull(13, Types.DATE);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("No se obtuvo id_epp tras insertar.");
            }
        }
    }

    public EppItem obtenerPorId(int idEpp) throws SQLException {
        final String sql =
                "SELECT id_epp, idproducto, id_tipo, codigo_interno, n_serie, talla, " +
                        "       fecha_fabricacion, fecha_puesta_servicio, vida_util_meses, " +
                        "       idbodega_principal, idsubbodega, estado, " +
                        "       fecha_vencimiento, proxima_mantencion " +
                        "FROM epp_item WHERE id_epp=?";

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapEpp(rs);
            }
        }
    }

    public List<EppItem> listar(String texto, Integer idBodega, Integer idSubbodega, Integer idTipo, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id_epp, idproducto, id_tipo, codigo_interno, n_serie, talla, ")
                .append("fecha_fabricacion, fecha_puesta_servicio, vida_util_meses, ")
                .append("idbodega_principal, idsubbodega, estado, fecha_vencimiento, proxima_mantencion ")
                .append("FROM epp_item WHERE 1=1 ");

        if (texto != null && !texto.trim().isEmpty()) {
            sql.append("AND (codigo_interno ILIKE ? OR n_serie ILIKE ?) ");
        }
        if (idBodega != null)   sql.append("AND idbodega_principal = ? ");
        if (idSubbodega != null)sql.append("AND idsubbodega = ? ");
        if (idTipo != null)     sql.append("AND id_tipo = ? ");

        sql.append("ORDER BY id_epp ASC LIMIT ? OFFSET ?");

        List<EppItem> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {

            int i = 1;
            if (texto != null && !texto.trim().isEmpty()) {
                String t = "%" + texto.trim() + "%";
                ps.setString(i++, t);
                ps.setString(i++, t);
            }
            if (idBodega != null)    ps.setInt(i++, idBodega);
            if (idSubbodega != null) ps.setInt(i++, idSubbodega);
            if (idTipo != null)      ps.setInt(i++, idTipo);

            ps.setInt(i++, limit);
            ps.setInt(i, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapEpp(rs));
            }
        }
        return out;
    }

    /** Devuelve lista de tipos disponibles (epp_tipo). */
    public List<String[]> listarTipos() throws SQLException {
        final String sql = "SELECT id_tipo, tipo, subtipo FROM epp_tipo ORDER BY tipo, subtipo";
        List<String[]> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new String[]{
                        String.valueOf(rs.getInt("id_tipo")),
                        rs.getString("tipo"),
                        rs.getString("subtipo")
                });
            }
        }
        return out;
    }

    /** Obtiene la ubicación actual (bodega, subbodega) de un EPP con lock opcional. */
    public int[] obtenerUbicacion(int idEpp, boolean forUpdate, Connection c) throws SQLException {
        String sql = "SELECT idbodega_principal, idsubbodega FROM epp_item WHERE id_epp=? ";
        if (forUpdate) sql += "FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("EPP no existe (id=" + idEpp + ")");
                return new int[]{ rs.getInt(1), rs.getInt(2) };
            }
        }
    }

    /**
     * Mueve el ítem y registra el movimiento en epp_movimiento (misma transacción).
     * @param idEpp EPP a mover
     * @param idBodegaDestino bodega destino
     * @param idSubbodegaDestino subbodega destino
     * @param motivo texto libre (p.ej., 'ASIGNACION', 'TRASLADO', etc.)
     */
    public void moverItem(int idEpp, int idBodegaDestino, int idSubbodegaDestino, String motivo) throws SQLException {
        final String updItem =
                "UPDATE epp_item SET idbodega_principal=?, idsubbodega=? WHERE id_epp=?";
        final String insMov =
                "INSERT INTO epp_movimiento(" +
                        " id_epp, desde_bodega, desde_subbodega, hacia_bodega, hacia_subbodega, motivo) " +
                        "VALUES(?, ?, ?, ?, ?, ?)";

        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);

            // 1) Ubicación actual con lock
            int[] ubic = obtenerUbicacion(idEpp, true, c);
            int desdeB = ubic[0];
            int desdeS = ubic[1];

            // si destino = origen, evita ruido
            if (desdeB == idBodegaDestino && desdeS == idSubbodegaDestino) {
                c.rollback();
                throw new SQLException("El destino es igual al origen.");
            }

            // 2) Actualizar ubicación
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

    public List<MovimientoEpp> listarMovimientos(int idEpp, int limit, int offset) throws SQLException {
        final String sql =
                "SELECT id_mov, id_epp, desde_bodega, desde_subbodega, " +
                        "       hacia_bodega, hacia_subbodega, motivo, fecha " +
                        "FROM epp_movimiento WHERE id_epp=? " +
                        "ORDER BY fecha DESC, id_mov DESC LIMIT ? OFFSET ?";
        List<MovimientoEpp> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEpp);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MovimientoEpp m = new MovimientoEpp();
                    m.idMovimiento   = rs.getInt("id_mov");
                    m.idEpp          = rs.getInt("id_epp");
                    m.desdeBodega    = rs.getInt("desde_bodega");
                    m.desdeSubbodega = rs.getInt("desde_subbodega");
                    m.haciaBodega    = rs.getInt("hacia_bodega");
                    m.haciaSubbodega = rs.getInt("hacia_subbodega");
                    m.motivo         = rs.getString("motivo");
                    m.fecha          = rs.getTimestamp("fecha");
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** Actualiza el estado del ítem (p.ej., 'SERVICIO', 'BAJA', 'REPARACION'). */
    public void actualizarEstado(int idEpp, String nuevoEstado) throws SQLException {
        final String sql = "UPDATE epp_item SET estado=? WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idEpp);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("EPP no encontrado (id=" + idEpp + ")");
            }
        }
    }

    /** Actualiza fechas claves (útil tras mantención o inspección). */
    public void actualizarFechas(int idEpp, Date proximaMant, Date vencimiento) throws SQLException {
        final String sql = "UPDATE epp_item SET proxima_mantencion=?, fecha_vencimiento=? WHERE id_epp=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (proximaMant != null)
                ps.setDate(1, new java.sql.Date(proximaMant.getTime()));
            else ps.setNull(1, Types.DATE);

            if (vencimiento != null)
                ps.setDate(2, new java.sql.Date(vencimiento.getTime()));
            else ps.setNull(2, Types.DATE);

            ps.setInt(3, idEpp);
            ps.executeUpdate();
        }
    }


    private EppItem mapEpp(ResultSet rs) throws SQLException {
        EppItem e = new EppItem();
        e.idEpp              = rs.getInt("id_epp");
        e.idProducto         = rs.getInt("idproducto");
        e.idTipo             = rs.getInt("id_tipo");
        e.codigoInterno      = rs.getString("codigo_interno");
        e.numeroSerie        = rs.getString("n_serie");
        e.talla              = rs.getString("talla");
        Date fFab            = rs.getDate("fecha_fabricacion");
        Date fServ           = rs.getDate("fecha_puesta_servicio");
        e.fechaFabricacion   = (fFab == null ? null : new Date(fFab.getTime()));
        e.fechaPuestaServicio= (fServ == null ? null : new Date(fServ.getTime()));
        int v = rs.getInt("vida_util_meses");
        e.vidaUtilMeses      = rs.wasNull() ? null : v;
        e.idBodegaPrincipal  = rs.getInt("idbodega_principal");
        e.idSubbodega        = rs.getInt("idsubbodega");
        e.estado             = rs.getString("estado");
        Date fV              = rs.getDate("fecha_vencimiento");
        e.fechaVencimiento   = (fV == null ? null : new Date(fV.getTime()));
        Date fPM             = rs.getDate("proxima_mantencion");
        e.proximaMantencion  = (fPM == null ? null : new Date(fPM.getTime()));
        return e;
    }
}
