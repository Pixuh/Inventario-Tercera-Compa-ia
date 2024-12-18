import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductoFormGUI extends JFrame {
    private JTextField idField;
    private JTextField nombreField;
    private JTextField cantidadField;
    private JTextField fechaIngresoField;
    private JTextField ubicacionField;
    private JComboBox<String> bodegaComboBox;
    private JComboBox<String> subbodegaComboBox;
    private Map<String, Integer> bodegaMap = new HashMap<>();
    private Map<String, Integer> subbodegaMap = new HashMap<>();


    public ProductoFormGUI() {
        setTitle("Formulario de Producto");
        setSize(400, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Container container = getContentPane();
        container.setLayout(new GridLayout(8, 2));

        // Inicialización de campos
        idField = new JTextField();
        idField.setEnabled(false); // Deshabilitar para que sea solo lectura
        generarIdAutomatico(); // Generar el ID al abrir la ventana

        nombreField = new JTextField();
        cantidadField = new JTextField();
        fechaIngresoField = new JTextField();
        ubicacionField = new JTextField();

        // Botón para seleccionar fecha
        JButton fechaIngresoButton = new JButton("Seleccionar Fecha");
        fechaIngresoButton.addActionListener(e -> {
            JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
            JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
            dateSpinner.setEditor(timeEditor);
            int result = JOptionPane.showOptionDialog(null, dateSpinner, "Seleccione Fecha de Ingreso",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (result == JOptionPane.OK_OPTION) {
                Date selectedDate = (Date) dateSpinner.getValue();
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                fechaIngresoField.setText(formatter.format(selectedDate));
            }
        });

        // Inicialización de JComboBox
        bodegaComboBox = new JComboBox<>();
        bodegaComboBox.addActionListener(e -> actualizarSubbodegas());
        actualizarListaBodegas();

        subbodegaComboBox = new JComboBox<>();

        // Agregar componentes al contenedor
        container.add(new JLabel("ID:"));
        container.add(idField);
        container.add(new JLabel("Nombre:"));
        container.add(nombreField);
        container.add(new JLabel("Cantidad:"));
        container.add(cantidadField);
        container.add(new JLabel("Fecha de Ingreso:"));
        container.add(fechaIngresoField);
        container.add(fechaIngresoButton);
        container.add(new JLabel("Ubicación:"));
        container.add(ubicacionField);
        container.add(new JLabel("Bodega:"));
        container.add(bodegaComboBox);
        container.add(new JLabel("Subbodega:"));
        container.add(subbodegaComboBox);

        JButton submitButton = new JButton("Agregar Producto");
        submitButton.addActionListener(e -> agregarProducto());
        container.add(submitButton);
    }

    // Reemplaza este método en tu clase ProductoFormGUI
    private void agregarProducto() {
        try {
            // Validar campos vacíos
            if (idField.getText().trim().isEmpty() ||
                    nombreField.getText().trim().isEmpty() ||
                    cantidadField.getText().trim().isEmpty() ||
                    fechaIngresoField.getText().trim().isEmpty() ||
                    ubicacionField.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("Todos los campos deben estar llenos.");
            }

            // Validar cantidad
            int cantidad = Integer.parseInt(cantidadField.getText().trim());
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser un número positivo.");
            }

            // Validar selección de bodegas
            String nombreBodega = (String) bodegaComboBox.getSelectedItem();
            String nombreSubbodega = (String) subbodegaComboBox.getSelectedItem();
            if (nombreBodega == null || nombreSubbodega == null) {
                throw new IllegalArgumentException("Debe seleccionar una bodega y una subbodega.");
            }

            // Validar si el ID ya existe
            ProductoDAO productoDAO = new ProductoDAO();
            int idProducto = Integer.parseInt(idField.getText().trim());
            if (productoDAO.obtenerProductoPorId(idProducto) != null) {
                throw new IllegalArgumentException("El ID del producto ya existe en el inventario.");
            }

            // Parsear fecha
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            Date fechaIngreso = formatter.parse(fechaIngresoField.getText().trim());

            // Obtener IDs de bodega y subbodega desde los mapas
            Integer idBodegaPrincipal = bodegaMap.get(nombreBodega);
            Integer idSubbodega = subbodegaMap.get(nombreSubbodega);

            if (idBodegaPrincipal == null || idSubbodega == null) {
                throw new IllegalArgumentException("No se pudo encontrar el ID de la bodega o subbodega seleccionada.");
            }

            // Crear el objeto ProductoForm
            ProductoForm producto = new ProductoForm(
                    idProducto,
                    nombreField.getText().trim(),
                    cantidad,
                    fechaIngreso,
                    ubicacionField.getText().trim(),
                    idBodegaPrincipal,
                    idSubbodega
            );

            // Verificar si el producto ya existe en la misma bodega/subbodega
            ProductoForm productoExistente = productoDAO.obtenerProductoPorNombreYBodega(
                    producto.getNombre(),
                    idBodegaPrincipal,
                    idSubbodega
            );

            if (productoExistente != null) {
                // Producto ya existe, ofrecer opción para actualizar
                int opcion = JOptionPane.showConfirmDialog(
                        this,
                        "El producto ya existe en esta bodega/subbodega. ¿Desea actualizar la cantidad existente?",
                        "Producto Existente",
                        JOptionPane.YES_NO_OPTION
                );

                if (opcion == JOptionPane.YES_OPTION) {
                    productoDAO.actualizarCantidadProducto(
                            productoExistente.getId(),
                            idBodegaPrincipal,
                            idSubbodega,
                            productoExistente.getCantidad() + cantidad
                    );
                    JOptionPane.showMessageDialog(this, "Cantidad actualizada exitosamente.");
                } else {
                    JOptionPane.showMessageDialog(this, "No se realizó ninguna acción.");
                }
            } else {
                // Producto nuevo, agregarlo
                productoDAO.agregarProducto(producto);
                JOptionPane.showMessageDialog(this, "Producto agregado exitosamente.");
            }

            // Limpiar los campos después de agregar
            limpiarCampos();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Error: El campo 'Cantidad' debe contener un número válido.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al agregar producto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    // Método para llenar el JComboBox con la lista de bodegas
    private void actualizarListaBodegas() {
        try {
            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> bodegas = bodegaDAO.obtenerBodegasPrincipalesConIds();
            bodegaComboBox.removeAllItems();
            bodegaMap.clear(); // Limpia el mapa antes de llenarlo
            bodegas.forEach((id, nombre) -> {
                bodegaMap.put(nombre, id); // Asocia el nombre con el ID
                bodegaComboBox.addItem(nombre);
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar la lista de bodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Método para actualizar el JComboBox de subbodegas basado en la bodega seleccionada
    private void actualizarSubbodegas() {
        try {
            BodegaDAO bodegaDAO = new BodegaDAO();
            String nombreBodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
            if (nombreBodegaSeleccionada != null) {
                int idBodegaPrincipal = bodegaMap.get(nombreBodegaSeleccionada); // Obtenemos el ID como int
                List<String> nombresSubbodegas = bodegaDAO.obtenerSubbodegasPorBodegaPrincipal(idBodegaPrincipal); // Llamada corregida

                subbodegaComboBox.removeAllItems();
                subbodegaMap.clear(); // Limpia el mapa antes de llenarlo
                for (String nombreSubbodega : nombresSubbodegas) {
                    subbodegaComboBox.addItem(nombreSubbodega);
                    subbodegaMap.put(nombreSubbodega, bodegaDAO.obtenerIdSubbodegaPorNombre(nombreSubbodega));
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar la lista de subbodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiarCampos() {
        idField.setText("");
        nombreField.setText("");
        cantidadField.setText("");
        fechaIngresoField.setText("");
        ubicacionField.setText("");
        bodegaComboBox.setSelectedIndex(-1);
        subbodegaComboBox.removeAllItems();
    }

    private void generarIdAutomatico() {
        try {
            ProductoDAO productoDAO = new ProductoDAO();
            int nuevoId = productoDAO.obtenerSiguienteId();
            idField.setText(String.valueOf(nuevoId));
            idField.setEnabled(false); // Deshabilitar edición manual
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al generar el ID: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int obtenerIdBodegaSeleccionada() {
        String nombreBodega = (String) bodegaComboBox.getSelectedItem();
        return bodegaMap.get(nombreBodega);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ProductoFormGUI form = new ProductoFormGUI();
            form.setVisible(true);
        });
    }
}

