import java.util.Date;

public class ProductoForm {
    private int id;
    private String nombre;
    private int cantidad;
    private Date fechaIngreso;
    private String ubicacion;
    private int idBodegaPrincipal;
    private int idSubbodega;
    private String nombreBodegaPrincipal; // Nuevo atributo
    private String nombreSubbodega;       // Nuevo atributo

    // Constructor original
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

    // Constructor ampliado
    public ProductoForm(int id, String nombre, int cantidad, Date fechaIngreso,
                        String ubicacion, int idBodegaPrincipal, int idSubbodega,
                        String nombreBodegaPrincipal, String nombreSubbodega) {
        this.id = id;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.fechaIngreso = fechaIngreso;
        this.ubicacion = ubicacion;
        this.idBodegaPrincipal = idBodegaPrincipal;
        this.idSubbodega = idSubbodega;
        this.nombreBodegaPrincipal = nombreBodegaPrincipal;
        this.nombreSubbodega = nombreSubbodega;
    }

    // Getters existentes
    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCantidad() {
        return cantidad;
    }

    public Date getFechaIngreso() {
        return fechaIngreso;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public int getIdBodegaPrincipal() {
        return idBodegaPrincipal;
    }

    public int getIdSubbodega() {
        return idSubbodega;
    }

    // Nuevos getters
    public String getNombreBodegaPrincipal() {
        return nombreBodegaPrincipal;
    }

    public String getNombreSubbodega() {
        return nombreSubbodega;
    }

    // Nuevos setters
    public void setNombreBodegaPrincipal(String nombreBodegaPrincipal) {
        this.nombreBodegaPrincipal = nombreBodegaPrincipal;
    }

    public void setNombreSubbodega(String nombreSubbodega) {
        this.nombreSubbodega = nombreSubbodega;
    }

    // Métodos necesarios
    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public void setFechaIngreso(Date fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    // Método toString
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
                '}';
    }
}
