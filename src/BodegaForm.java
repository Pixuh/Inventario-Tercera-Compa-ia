public class BodegaForm {
    private int idBodega; // ID de la bodega
    private String nombre; // Nombre de la bodega
    private int capacidad; // Capacidad de la bodega
    private Integer idBodegaPadre; // ID de la bodega principal si es una subbodega (puede ser null)
    private String tipoBodega; // Tipo de bodega: "Principal" o "Subbodega"

    /**
     * Constructor principal para todas las bodegas.
     *
     * @param idBodega     ID de la bodega.
     * @param nombre       Nombre de la bodega.
     * @param capacidad    Capacidad de la bodega.
     * @param idBodegaPadre ID de la bodega principal (null si es una bodega principal).
     * @param tipoBodega   Tipo de bodega ("Principal" o "Subbodega").
     */
    public BodegaForm(int idBodega, String nombre, int capacidad, Integer idBodegaPadre, String tipoBodega) {
        this.idBodega = idBodega;
        this.nombre = nombre;
        this.capacidad = capacidad;
        this.idBodegaPadre = idBodegaPadre;
        this.tipoBodega = tipoBodega;
    }

    /**
     * Constructor para bodegas principales.
     *
     * @param idBodega  ID de la bodega.
     * @param nombre    Nombre de la bodega.
     * @param capacidad Capacidad de la bodega.
     */
    public BodegaForm(int idBodega, String nombre, int capacidad) {
        this(idBodega, nombre, capacidad, null, "Principal");
    }

    /**
     * Constructor para subbodegas.
     *
     * @param idBodega      ID de la subbodega.
     * @param nombre        Nombre de la subbodega.
     * @param capacidad     Capacidad de la subbodega.
     * @param idBodegaPadre ID de la bodega principal asociada.
     */
    public BodegaForm(int idBodega, String nombre, int capacidad, Integer idBodegaPadre) {
        this(idBodega, nombre, capacidad, idBodegaPadre, "Subbodega");
    }

    // Getters y setters para todos los campos
    public int getIdBodega() {
        return idBodega;
    }

    public void setIdBodega(int idBodega) {
        if (idBodega <= 0) {
            throw new IllegalArgumentException("El ID de la bodega debe ser mayor a 0.");
        }
        this.idBodega = idBodega;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la bodega no puede estar vacío.");
        }
        this.nombre = nombre.trim();
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser mayor a 0.");
        }
        this.capacidad = capacidad;
    }

    public Integer getIdBodegaPadre() {
        return idBodegaPadre;
    }

    public void setIdBodegaPadre(Integer idBodegaPadre) {
        this.idBodegaPadre = idBodegaPadre;
    }

    public String getTipoBodega() {
        return tipoBodega;
    }

    public void setTipoBodega(String tipoBodega) {
        if (!"Principal".equalsIgnoreCase(tipoBodega) && !"Subbodega".equalsIgnoreCase(tipoBodega)) {
            throw new IllegalArgumentException("El tipo de bodega debe ser 'Principal' o 'Subbodega'.");
        }
        this.tipoBodega = tipoBodega;
    }

    // Método para representar la clase como un String, útil para depuración y visualización
    @Override
    public String toString() {
        return "BodegaForm{" +
                "idBodega=" + idBodega +
                ", nombre='" + nombre + '\'' +
                ", capacidad=" + capacidad +
                ", idBodegaPadre=" + idBodegaPadre +
                ", tipoBodega='" + tipoBodega + '\'' +
                '}';
    }
}
