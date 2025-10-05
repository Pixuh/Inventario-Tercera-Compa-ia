public class ProductoUbicacion {
    public final int idProducto, idBodega, idSubbodega, cantidad;
    public final String nombre, bodegaNombre, subbodegaNombre;

    public ProductoUbicacion(int idP, int idBod, int idSub,
                             String nom, String bodNom, String subNom, int cant) {
        idProducto = idP;
        idBodega = idBod;
        idSubbodega = idSub;
        nombre = nom;
        bodegaNombre = bodNom;
        subbodegaNombre = subNom;
        cantidad = cant;
    }

    @Override public String toString() {
        return nombre + " — " + bodegaNombre + " / " + subbodegaNombre + " (" + cantidad + ")";
    }
}
