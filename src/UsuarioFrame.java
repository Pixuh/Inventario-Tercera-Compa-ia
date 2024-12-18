import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

public class UsuarioFrame extends JFrame {

    private final ProductoDAO productoDAO = new ProductoDAO();

    public UsuarioFrame() {
        setTitle("Inventario de Bomberos - Usuario");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Crear panel principal
        JPanel verInventarioPanel = new JPanel(new BorderLayout());
        JTable inventarioTable = new JTable();
        verInventarioPanel.add(new JScrollPane(inventarioTable), BorderLayout.CENTER);

        // Panel para filtros
        JPanel filtroPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField buscarField = new JTextField(20);
        JComboBox<String> bodegaComboBox = new JComboBox<>();
        JButton buscarButton = new JButton("Buscar");
        JButton refreshButton = new JButton("Refrescar");

        filtroPanel.add(new JLabel("Buscar Producto:"));
        filtroPanel.add(buscarField);
        filtroPanel.add(new JLabel("Bodega:"));
        filtroPanel.add(bodegaComboBox);
        filtroPanel.add(buscarButton);
        filtroPanel.add(refreshButton);

        verInventarioPanel.add(filtroPanel, BorderLayout.NORTH);

        // Listener para refrescar el inventario
        refreshButton.addActionListener(e -> cargarInventario(inventarioTable, "", null));

        // Listener para buscar productos
        buscarButton.addActionListener(e -> {
            String textoBusqueda = buscarField.getText().trim();
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();

            if (textoBusqueda.isEmpty() && (bodegaSeleccionada == null || bodegaSeleccionada.equals("Todas las bodegas"))) {
                JOptionPane.showMessageDialog(this, "Por favor, introduzca un término de búsqueda o seleccione una bodega.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                return;
            }

            cargarInventario(inventarioTable, textoBusqueda, bodegaSeleccionada);
        });

        // Cargar datos iniciales
        cargarInventario(inventarioTable, "", null);
        cargarBodegasEnComboBox(bodegaComboBox);

        // Configurar JFrame
        add(verInventarioPanel);
    }

    // Método para cargar el inventario
    private void cargarInventario(JTable inventarioTable, String textoBusqueda, String bodegaSeleccionada) {
        try {
            Integer bodegaId = null; // ID de la bodega seleccionada
            if (bodegaSeleccionada != null && !bodegaSeleccionada.equals("Todas las bodegas")) {
                BodegaDAO bodegaDAO = new BodegaDAO();
                bodegaId = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(bodegaSeleccionada);
            }

            List<ProductoForm> productos = productoDAO.buscarProductosPorBodega(textoBusqueda, bodegaId);
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


    // Método para cargar las bodegas en el JComboBox
    private void cargarBodegasEnComboBox(JComboBox<String> bodegaComboBox) {
        try {
            BodegaDAO bodegaDAO = new BodegaDAO();
            List<String> nombresBodegas = bodegaDAO.obtenerNombresBodegasPrincipales();
            bodegaComboBox.removeAllItems();
            bodegaComboBox.addItem("Todas las bodegas"); // Opción más descriptiva
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
