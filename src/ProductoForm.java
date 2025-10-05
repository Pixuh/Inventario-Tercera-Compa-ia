import java.math.BigDecimal;
import java.util.Date;

public class ProductoForm {
    // ===== Campos existentes =====
    private int id;
    private String nombre;
    private int cantidad;
    private Date fechaIngreso;
    private String ubicacion;
    private int idBodegaPrincipal;
    private int idSubbodega;
    private String nombreBodegaPrincipal; // para listados
    private String nombreSubbodega;       // para listados

    // ===== Nuevos metadatos =====
    private String marca;
    private BigDecimal valor;                  // $$
    private String estadoProducto;             // p.ej. NUEVO | USADO | DADO_BAJA
    private String tipoMaterial;               // Rescate | Incendio | EPP
    private String observacion;
    private Date fechaVencimiento;             // nullable

    // Mantención / vencimiento a nivel producto
    private Boolean requiereMantencion;        // nullable
    private Integer frecuenciaMantencionMeses; // nullable
    private Date proximaMantencion;            // nullable (no se usa en INSERT de producto)
    private Boolean aplicaVencimiento;         // <<============= NUEVO

    // Opcional (útil para la capa EPP/serializados)
    private Boolean esEpp;                     // nullable para no romper código
    private Boolean esSerializable;            // nullable

    // ===== Constructores existentes (no tocar) =====
    public ProductoForm(int id, String nombre, int cantidad, Date fechaIngreso,
                        String ubicacion, int idBodegaPrincipal, int idSubbodega) {
        this.id = id;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.fechaIngreso = fechaIngreso;
        this.ubicacion = ubicacion;
        this.idBodegaPrincipal = idBodegaPrincipal;
        this.idSubbodega = idSubbodega;
    }

    public ProductoForm(int id, String nombre, int cantidad, Date fechaIngreso,
                        String ubicacion, int idBodegaPrincipal, int idSubbodega,
                        String nombreBodegaPrincipal, String nombreSubbodega) {
        this(id, nombre, cantidad, fechaIngreso, ubicacion, idBodegaPrincipal, idSubbodega);
        this.nombreBodegaPrincipal = nombreBodegaPrincipal;
        this.nombreSubbodega = nombreSubbodega;
    }

    // ===== Constructor extendido (lista completa con metadatos) =====
    public ProductoForm(
            int id, String nombre, int cantidad, Date fechaIngreso, String ubicacion,
            int idBodegaPrincipal, int idSubbodega,
            String nombreBodegaPrincipal, String nombreSubbodega,
            String marca, BigDecimal valor, String estadoProducto, String tipoMaterial,
            String observacion, Date fechaVencimiento,
            Boolean requiereMantencion, Integer frecuenciaMantencionMeses, Date proximaMantencion,
            Boolean esEpp, Boolean esSerializable,
            Boolean aplicaVencimiento // <<============= NUEVO parámetro
    ) {
        this(id, nombre, cantidad, fechaIngreso, ubicacion, idBodegaPrincipal, idSubbodega,
                nombreBodegaPrincipal, nombreSubbodega);
        this.marca = marca;
        this.valor = valor;
        this.estadoProducto = estadoProducto;
        this.tipoMaterial = tipoMaterial;
        this.observacion = observacion;
        this.fechaVencimiento = fechaVencimiento;
        this.requiereMantencion = requiereMantencion;
        this.frecuenciaMantencionMeses = frecuenciaMantencionMeses;
        this.proximaMantencion = proximaMantencion;
        this.esEpp = esEpp;
        this.esSerializable = esSerializable;
        this.aplicaVencimiento = aplicaVencimiento; // << nuevo
    }

    // ===== Getters existentes =====
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public int getCantidad() { return cantidad; }
    public Date getFechaIngreso() { return fechaIngreso; }
    public String getUbicacion() { return ubicacion; }
    public int getIdBodegaPrincipal() { return idBodegaPrincipal; }
    public int getIdSubbodega() { return idSubbodega; }
    public String getNombreBodegaPrincipal() { return nombreBodegaPrincipal; }
    public String getNombreSubbodega() { return nombreSubbodega; }

    // ===== Setters existentes =====
    public void setNombreBodegaPrincipal(String nombreBodegaPrincipal) { this.nombreBodegaPrincipal = nombreBodegaPrincipal; }
    public void setNombreSubbodega(String nombreSubbodega) { this.nombreSubbodega = nombreSubbodega; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public void setFechaIngreso(Date fechaIngreso) { this.fechaIngreso = fechaIngreso; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    // ===== Getters nuevos =====
    public String getMarca() { return marca; }
    public BigDecimal getValor() { return valor; }
    public String getEstadoProducto() { return estadoProducto; }
    public String getTipoMaterial() { return tipoMaterial; }
    public String getObservacion() { return observacion; }
    public Date getFechaVencimiento() { return fechaVencimiento; }
    public Boolean getRequiereMantencion() { return requiereMantencion; }
    public Integer getFrecuenciaMantencionMeses() { return frecuenciaMantencionMeses; }
    public Date getProximaMantencion() { return proximaMantencion; }
    public Boolean getEsEpp() { return esEpp; }
    public Boolean getEsSerializable() { return esSerializable; }
    public Boolean getAplicaVencimiento() { return aplicaVencimiento; } // << nuevo

    // ===== Setters nuevos =====
    public void setMarca(String marca) { this.marca = marca; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public void setEstadoProducto(String estadoProducto) { this.estadoProducto = estadoProducto; }
    public void setTipoMaterial(String tipoMaterial) { this.tipoMaterial = tipoMaterial; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public void setFechaVencimiento(Date fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public void setRequiereMantencion(Boolean requiereMantencion) { this.requiereMantencion = requiereMantencion; }
    public void setFrecuenciaMantencionMeses(Integer frecuenciaMantencionMeses) { this.frecuenciaMantencionMeses = frecuenciaMantencionMeses; }
    public void setProximaMantencion(Date proximaMantencion) { this.proximaMantencion = proximaMantencion; }
    public void setEsEpp(Boolean esEpp) { this.esEpp = esEpp; }
    public void setEsSerializable(Boolean esSerializable) { this.esSerializable = esSerializable; }
    public void setAplicaVencimiento(Boolean aplicaVencimiento) { this.aplicaVencimiento = aplicaVencimiento; } // << nuevo

    // ===== Helpers para la UI (opcionales) =====
    public boolean isVencidoHoy() {
        return fechaVencimiento != null && new Date().after(fechaVencimiento);
    }

    public String getMantencionInfo() {
        if (Boolean.TRUE.equals(requiereMantencion)) {
            String freq = (frecuenciaMantencionMeses != null) ? (frecuenciaMantencionMeses + "m") : "";
            String prox = (proximaMantencion != null) ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(proximaMantencion) : "";
            if (!freq.isEmpty() && !prox.isEmpty()) return freq + " • " + prox;
            if (!freq.isEmpty()) return freq;
            if (!prox.isEmpty()) return prox;
            return "Sí";
        }
        return "";
    }

    // ===== toString =====
    @Override
    public String toString() {
        return "ProductoForm{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", cantidad=" + cantidad +
                ", fechaIngreso=" + fechaIngreso +
                ", ubicacion='" + ubicacion + '\'' +
                ", idBodegaPrincipal=" + idBodegaPrincipal +
                ", idSubbodega=" + idSubbodega +
                ", nombreBodegaPrincipal='" + nombreBodegaPrincipal + '\'' +
                ", nombreSubbodega='" + nombreSubbodega + '\'' +
                ", marca='" + marca + '\'' +
                ", valor=" + valor +
                ", estadoProducto='" + estadoProducto + '\'' +
                ", tipoMaterial='" + tipoMaterial + '\'' +
                ", observacion='" + observacion + '\'' +
                ", fechaVencimiento=" + fechaVencimiento +
                ", requiereMantencion=" + requiereMantencion +
                ", frecuenciaMantencionMeses=" + frecuenciaMantencionMeses +
                ", proximaMantencion=" + proximaMantencion +
                ", aplicaVencimiento=" + aplicaVencimiento +   // << nuevo
                ", esEpp=" + esEpp +
                ", esSerializable=" + esSerializable +
                '}';
    }
}
