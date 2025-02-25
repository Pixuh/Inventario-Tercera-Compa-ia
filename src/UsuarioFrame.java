import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

public class UsuarioFrame extends JFrame {

    private final ProductoDAO productoDAO = new ProductoDAO();
    private JComboBox<String> subbodegaComboBox;

    public UsuarioFrame() {
        setTitle("Inventario de Bomberos - Usuario");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        subbodegaComboBox = new JComboBox<>(); // ✅ Inicialización correcta aquí

        JPanel verInventarioPanel = new JPanel(new BorderLayout());
        JTable inventarioTable = new JTable();
        verInventarioPanel.add(new JScrollPane(inventarioTable), BorderLayout.CENTER);

        JPanel filtroPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField buscarField = new JTextField(20);
        JComboBox<String> bodegaComboBox = new JComboBox<>();
        JButton buscarButton = new JButton("Buscar");
        JButton refreshButton = new JButton("Refrescar");

        filtroPanel.add(new JLabel("Buscar Producto:"));
        filtroPanel.add(buscarField);
        filtroPanel.add(new JLabel("Bodega:"));
        filtroPanel.add(bodegaComboBox);
        filtroPanel.add(new JLabel("Subbodega:"));
        filtroPanel.add(subbodegaComboBox);  // ✅ Ahora usará la instancia correcta

        filtroPanel.add(buscarButton);
        filtroPanel.add(refreshButton);

        verInventarioPanel.add(filtroPanel, BorderLayout.NORTH);

        // Listener para refrescar el inventario
        refreshButton.addActionListener(e -> {
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
            String subbodegaSeleccionada = (String) subbodegaComboBox.getSelectedItem();
            cargarInventario(inventarioTable, "", bodegaSeleccionada, subbodegaSeleccionada);
        });

        // Listener para buscar productos
        buscarButton.addActionListener(e -> {
            String textoBusqueda = buscarField.getText().trim();
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();

            if (textoBusqueda.isEmpty() && (bodegaSeleccionada == null || bodegaSeleccionada.equals("Todas las bodegas"))) {
                JOptionPane.showMessageDialog(this, "Por favor, introduzca un término de búsqueda o seleccione una bodega.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                return;
            }

            cargarInventario(inventarioTable, "", null, null);


        });

        // Listener para actualizar subbodegas y cargar productos automáticamente al seleccionar una bodega
        bodegaComboBox.addActionListener(e -> {
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
            actualizarSubbodegas(bodegaSeleccionada);
            cargarInventario(inventarioTable, "", bodegaSeleccionada, null);  // Cargar todos los productos de la bodega
        });

        // Listener para actualizar el inventario cuando se seleccione una subbodega
        subbodegaComboBox.addActionListener(e -> {
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
            String subbodegaSeleccionada = (String) subbodegaComboBox.getSelectedItem();
            cargarInventario(inventarioTable, "", bodegaSeleccionada, subbodegaSeleccionada);
        });


        // Cargar datos iniciales
        cargarInventario(inventarioTable, "", null, null);

        cargarBodegasEnComboBox(bodegaComboBox);

        // Configurar JFrame
        add(verInventarioPanel);
    }

    // Método para cargar el inventario
    private void cargarInventario(JTable inventarioTable, String textoBusqueda, String bodegaSeleccionada, String subbodegaSeleccionada) {
        try {
            Integer bodegaId = null;
            Integer subbodegaId = null;

            BodegaDAO bodegaDAO = new BodegaDAO();

            // Obtener ID de la bodega seleccionada
            if (bodegaSeleccionada != null && !bodegaSeleccionada.equals("Todas las bodegas")) {
                bodegaId = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(bodegaSeleccionada);
            }

            // Obtener ID de la subbodega seleccionada si hay
            if (subbodegaSeleccionada != null && !subbodegaSeleccionada.equals("Todas las subbodegas")) {
                subbodegaId = bodegaDAO.obtenerIdSubbodegaPorNombre(subbodegaSeleccionada);
            }

            // Buscar productos por filtros
            List<ProductoForm> productos = productoDAO.buscarProductosPorFiltros(textoBusqueda, bodegaId, subbodegaId);

            // Configuración de la tabla
            String[] columnNames = {"ID", "Nombre", "Cantidad", "Fecha de Ingreso", "Ubicación", "Bodega Principal", "Subbodega"};
            Object[][] data = new Object[productos.size()][7];

            for (int i = 0; i < productos.size(); i++) {
                ProductoForm producto = productos.get(i);
                data[i][0] = producto.getId();
                data[i][1] = producto.getNombre();
                data[i][2] = producto.getCantidad();
                data[i][3] = new SimpleDateFormat("yyyy-MM-dd").format(producto.getFechaIngreso());
                data[i][4] = producto.getUbicacion();
                data[i][5] = producto.getNombreBodegaPrincipal();
                data[i][6] = producto.getNombreSubbodega();
            }

            inventarioTable.setModel(new DefaultTableModel(data, columnNames));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar inventario: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarSubbodegas(String nombreBodegaSeleccionada) {
        try {
            BodegaDAO bodegaDAO = new BodegaDAO();
            subbodegaComboBox.removeAllItems();
            subbodegaComboBox.addItem("Todas las subbodegas");

            if (nombreBodegaSeleccionada != null && !nombreBodegaSeleccionada.equals("Todas las bodegas")) {
                int idBodegaPrincipal = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(nombreBodegaSeleccionada);
                List<String> nombresSubbodegas = bodegaDAO.obtenerSubbodegasPorBodegaPrincipal(idBodegaPrincipal);

                for (String nombreSubbodega : nombresSubbodegas) {
                    subbodegaComboBox.addItem(nombreSubbodega);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Método para cargar las bodegas en el JComboBox
    private void cargarBodegasEnComboBox(JComboBox<String> bodegaComboBox) {
        try {
            BodegaDAO bodegaDAO = new BodegaDAO();  // Crear el objeto fuera del try
            List<String> nombresBodegas = bodegaDAO.obtenerNombresBodegasPrincipales();
            bodegaComboBox.removeAllItems();
            bodegaComboBox.addItem("Todas las bodegas"); // Opción predeterminada

            for (String nombreBodega : nombresBodegas) {
                bodegaComboBox.addItem(nombreBodega);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar bodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }



    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            UsuarioFrame frame = new UsuarioFrame();
            frame.setVisible(true);
        });
    }
}
