import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class MainFrame extends JFrame implements BodegaObserver {

    private final ProductoDAO productoDAO = new ProductoDAO();
    private final Map<String, Integer> bodegaPrincipalMap = new HashMap<>();
    private final Map<String, Integer> subbodegaMap = new HashMap<>();

    private JTable inventarioTable;
    private JTextField buscarField;
    private JComboBox<String> bodegaPrincipalComboBox, subbodegaComboBox;

    private JComboBox<String> bodegaPrincipalComboBoxActualizar;
    private JComboBox<String> subbodegaComboBoxActualizar;
    private JComboBox<String> bodegaPrincipalComboBoxEliminar;
    private JComboBox<String> subbodegaComboBoxEliminar;
    private JComboBox<String> bodegaDestinoComboBox;
    private JComboBox<String> subbodegaDestinoComboBox;


    public MainFrame() {
        setTitle("Inventario Tercera Compañía San Pedro de la Paz");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initComponents(); // Iniciar la interfaz primero

        // Cargar los datos en segundo plano después de inicializar la UI
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    cargarDatosTabla(); // Llamada en segundo plano
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Error al cargar los datos: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }
        }.execute();
    }

    private void initComponents() {
        JTabbedPane tabbedPane = createTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        // Ejecutar la carga de datos en segundo plano
        SwingUtilities.invokeLater(() -> {
            try {
                cargarDatosTabla();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al cargar los datos: " + ex.getMessage());
            }
        });
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton logoutButton = new JButton("Cerrar Sesión");
        logoutButton.addActionListener(e -> cerrarSesion());
        panel.add(logoutButton);

        JButton gestionarBodegasButton = new JButton("Gestionar Bodegas");
        gestionarBodegasButton.addActionListener(e -> {
            GestionBodegasFrame frame = new GestionBodegasFrame(this);
            frame.agregarObservador(this);
            frame.setVisible(true);
        });
        panel.add(gestionarBodegasButton);

        return panel;
    }

    private JTabbedPane createTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Ver Inventario", createVerInventarioPanel());
        tabbedPane.addTab("Agregar Producto", createAgregarProductoPanel());
        tabbedPane.addTab("Actualizar Producto", createActualizarProductoPanel());
        tabbedPane.addTab("Eliminar Producto", createEliminarProductoPanel());
        return tabbedPane;
    }

    private JPanel createVerInventarioPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"ID", "Nombre", "Cantidad", "Fecha Ingreso", "Ubicación", "Bodega Principal", "Subbodega"}, 0
        );
        inventarioTable = new JTable(model);
        panel.add(new JScrollPane(inventarioTable), BorderLayout.CENTER);

        panel.add(createFiltroPanel(), BorderLayout.NORTH);
        cargarDatosTabla();

        return panel;
    }

    private JPanel createAgregarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        JTextField nombreField = new JTextField(20);
        JTextField cantidadField = new JTextField(20);
        JTextField ubicacionField = new JTextField(20);
        JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);

        JComboBox<String> bodegaPrincipalComboBoxAgregar = new JComboBox<>();
        JComboBox<String> subbodegaComboBoxAgregar = new JComboBox<>();

        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxAgregar);
        bodegaPrincipalComboBoxAgregar.addActionListener(e ->
                actualizarSubbodegasEn(bodegaPrincipalComboBoxAgregar, subbodegaComboBoxAgregar));

        JButton agregarButton = new JButton("Agregar Producto");
        agregarButton.addActionListener(e -> {
            try {
                String nombre = nombreField.getText().trim();
                int cantidad = Integer.parseInt(cantidadField.getText().trim());
                Date fechaIngreso = (Date) dateSpinner.getValue();
                String ubicacion = ubicacionField.getText().trim();

                Integer idBodegaPrincipal = obtenerIdDesdeComboBox(bodegaPrincipalComboBoxAgregar, bodegaPrincipalMap);
                Integer idSubbodega = obtenerIdDesdeComboBox(subbodegaComboBoxAgregar, subbodegaMap);

                if (idBodegaPrincipal == null || idSubbodega == null) {
                    throw new IllegalArgumentException("Debe seleccionar una bodega y una subbodega.");
                }

                productoDAO.agregarProducto(new ProductoForm(0, nombre, cantidad, fechaIngreso, ubicacion, idBodegaPrincipal, idSubbodega));
                JOptionPane.showMessageDialog(this, "Producto agregado correctamente.");
                cargarDatosTabla();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al agregar producto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1; panel.add(nombreField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1; panel.add(cantidadField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Fecha de Ingreso:"), gbc);
        gbc.gridx = 1; panel.add(dateSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Ubicación:"), gbc);
        gbc.gridx = 1; panel.add(ubicacionField, gbc);
        gbc.gridx = 0; gbc.gridy = 4; panel.add(new JLabel("Bodega Principal:"), gbc);
        gbc.gridx = 1; panel.add(bodegaPrincipalComboBoxAgregar, gbc);
        gbc.gridx = 0; gbc.gridy = 5; panel.add(new JLabel("Subbodega:"), gbc);
        gbc.gridx = 1; panel.add(subbodegaComboBoxAgregar, gbc);
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; panel.add(agregarButton, gbc);

        return panel;
    }


    private JPanel createActualizarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        JComboBox<String> productoComboBox = new JComboBox<>();
        cargarProductosEnComboBox(productoComboBox);

        JTextField cantidadField = new JTextField(20);

        JComboBox<String> bodegaOrigenComboBox = new JComboBox<>();
        JComboBox<String> subbodegaOrigenComboBox = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaOrigenComboBox);

        JComboBox<String> bodegaDestinoComboBox = new JComboBox<>();
        JComboBox<String> subbodegaDestinoComboBox = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaDestinoComboBox);

        bodegaOrigenComboBox.addActionListener(e -> actualizarSubbodegasEn(bodegaOrigenComboBox, subbodegaOrigenComboBox));
        bodegaDestinoComboBox.addActionListener(e -> actualizarSubbodegasEn(bodegaDestinoComboBox, subbodegaDestinoComboBox));

        JButton actualizarButton = new JButton("Mover Producto");
        actualizarButton.addActionListener(e -> {
            try {
                String productoSeleccionado = (String) productoComboBox.getSelectedItem();
                int cantidad = Integer.parseInt(cantidadField.getText().trim());
                Integer idBodegaOrigen = obtenerIdDesdeComboBox(bodegaOrigenComboBox, bodegaPrincipalMap);
                Integer idSubbodegaOrigen = obtenerIdDesdeComboBox(subbodegaOrigenComboBox, subbodegaMap);
                Integer idBodegaDestino = obtenerIdDesdeComboBox(bodegaDestinoComboBox, bodegaPrincipalMap);
                Integer idSubbodegaDestino = obtenerIdDesdeComboBox(subbodegaDestinoComboBox, subbodegaMap);

                if (productoSeleccionado == null || idBodegaOrigen == null || idSubbodegaOrigen == null || idBodegaDestino == null || idSubbodegaDestino == null) {
                    throw new IllegalArgumentException("Debe seleccionar todos los campos correctamente.");
                }

                int idProducto = productoDAO.obtenerIdProductoPorNombre(productoSeleccionado);
                productoDAO.moverProducto(idProducto, idBodegaOrigen, idSubbodegaOrigen, idBodegaDestino, idSubbodegaDestino, cantidad);

                JOptionPane.showMessageDialog(this, "Producto movido exitosamente.");
                cargarDatosTabla();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al mover producto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Producto:"), gbc);
        gbc.gridx = 1; panel.add(productoComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1; panel.add(cantidadField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Bodega Origen:"), gbc);
        gbc.gridx = 1; panel.add(bodegaOrigenComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Subbodega Origen:"), gbc);
        gbc.gridx = 1; panel.add(subbodegaOrigenComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 4; panel.add(new JLabel("Bodega Destino:"), gbc);
        gbc.gridx = 1; panel.add(bodegaDestinoComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 5; panel.add(new JLabel("Subbodega Destino:"), gbc);
        gbc.gridx = 1; panel.add(subbodegaDestinoComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; panel.add(actualizarButton, gbc);

        return panel;
    }

    private JPanel createEliminarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        JComboBox<String> productoComboBox = new JComboBox<>();
        cargarProductosEnComboBox(productoComboBox);

        JTextField cantidadField = new JTextField(20);

        JComboBox<String> bodegaComboBox = new JComboBox<>();
        JComboBox<String> subbodegaComboBox = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaComboBox);

        // Evento para actualizar subbodegas cuando cambia la bodega principal
        bodegaComboBox.addActionListener(e -> actualizarSubbodegasEn(bodegaComboBox, subbodegaComboBox));

        JButton eliminarButton = new JButton("Eliminar");
        eliminarButton.addActionListener(e -> {
            try {
                String productoSeleccionado = (String) productoComboBox.getSelectedItem();
                Integer idBodega = obtenerIdDesdeComboBox(bodegaComboBox, bodegaPrincipalMap);
                Integer idSubbodega = obtenerIdDesdeComboBox(subbodegaComboBox, subbodegaMap);

                if (productoSeleccionado == null || idBodega == null || idSubbodega == null) {
                    throw new IllegalArgumentException("Debe seleccionar un producto, bodega y subbodega válidos.");
                }

                int cantidad = Integer.parseInt(cantidadField.getText().trim());
                if (cantidad <= 0) {
                    throw new IllegalArgumentException("Ingrese una cantidad mayor a 0.");
                }

                int idProducto = productoDAO.obtenerIdProductoPorNombre(productoSeleccionado);
                productoDAO.eliminarCantidadProducto(idProducto, idBodega, idSubbodega, cantidad);

                JOptionPane.showMessageDialog(this, "Producto eliminado correctamente.");
                cargarDatosTabla();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "La cantidad debe ser un número válido.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al eliminar producto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Agregar componentes al panel
        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Producto:"), gbc);
        gbc.gridx = 1; panel.add(productoComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1; panel.add(cantidadField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Bodega:"), gbc);
        gbc.gridx = 1; panel.add(bodegaComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Subbodega:"), gbc);
        gbc.gridx = 1; panel.add(subbodegaComboBox, gbc);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; panel.add(eliminarButton, gbc);

        return panel;
    }

    private JPanel createFiltroPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        buscarField = new JTextField(20);
        bodegaPrincipalComboBox = new JComboBox<>();
        subbodegaComboBox = new JComboBox<>();

        bodegaPrincipalComboBox.addItem("Todas las bodegas");
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBox);
        subbodegaComboBox.addItem("Todas las subbodegas");

        bodegaPrincipalComboBox.addActionListener(e -> actualizarSubbodegas());
        subbodegaComboBox.addActionListener(e -> aplicarFiltros());
        buscarField.addActionListener(e -> aplicarFiltros());

        panel.add(new JLabel("Buscar:"));
        panel.add(buscarField);
        panel.add(new JLabel("Bodega:"));
        panel.add(bodegaPrincipalComboBox);
        panel.add(new JLabel("Subbodega:"));
        panel.add(subbodegaComboBox);

        JButton refrescarButton = new JButton("Refrescar");
        refrescarButton.addActionListener(e -> cargarDatosTabla());
        panel.add(refrescarButton);

        return panel;
    }

    private void aplicarFiltros() {
        String textoBusqueda = buscarField.getText();
        String bodegaSeleccionada = (String) bodegaPrincipalComboBox.getSelectedItem();
        String subbodegaSeleccionada = (String) subbodegaComboBox.getSelectedItem();
        cargarDatosTablaConFiltros(textoBusqueda, bodegaSeleccionada, subbodegaSeleccionada);
    }

    private void actualizarSubbodegas() {
        subbodegaComboBox.removeAllItems();
        String bodegaSeleccionada = (String) bodegaPrincipalComboBox.getSelectedItem();
        if (!"Todas las bodegas".equals(bodegaSeleccionada)) {
            int idBodegaPrincipal = bodegaPrincipalMap.getOrDefault(bodegaSeleccionada, -1);
            cargarSubbodegasEnComboBox(subbodegaComboBox, idBodegaPrincipal);
        } else {
            subbodegaComboBox.addItem("Todas las subbodegas");
        }
        aplicarFiltros();
    }

    private void cargarDatosTabla() {
        SwingWorker<List<ProductoForm>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ProductoForm> doInBackground() throws Exception {
                return productoDAO.obtenerProductosConBodega(50, 0);
            }

            @Override
            protected void done() {
                try {
                    List<ProductoForm> productos = get();
                    actualizarTabla(productos);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Error al cargar los productos: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void actualizarTabla(List<ProductoForm> productos) {
        DefaultTableModel model = (DefaultTableModel) inventarioTable.getModel();
        model.setRowCount(0); // Limpiar la tabla
        for (ProductoForm producto : productos) {
            model.addRow(new Object[]{
                    producto.getId(),
                    producto.getNombre(),
                    producto.getCantidad(),
                    producto.getFechaIngreso(),
                    producto.getUbicacion(),
                    producto.getIdBodegaPrincipal(),
                    producto.getIdSubbodega()
            });
        }
        inventarioTable.revalidate();
        inventarioTable.repaint();
    }

    private void cargarDatosTablaConFiltros(String textoBusqueda, String bodegaSeleccionada, String subbodegaSeleccionada) {
        try {
            List<ProductoForm> productos = productoDAO.buscarProductosPorFiltros(
                    textoBusqueda, bodegaPrincipalMap.getOrDefault(bodegaSeleccionada, null),
                    subbodegaMap.getOrDefault(subbodegaSeleccionada, null));

            DefaultTableModel model = (DefaultTableModel) inventarioTable.getModel();
            model.setRowCount(0);

            for (ProductoForm producto : productos) {
                model.addRow(new Object[]{
                        producto.getId(), producto.getNombre(), producto.getCantidad(),
                        new SimpleDateFormat("yyyy-MM-dd").format(producto.getFechaIngreso()),
                        producto.getUbicacion(), producto.getNombreBodegaPrincipal(), producto.getNombreSubbodega()
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar productos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarBodegasPrincipalesEnComboBox(JComboBox<String> comboBox) {
        try {
            comboBox.removeAllItems();
            bodegaPrincipalMap.clear();

            comboBox.addItem("Todas las bodegas");

            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> bodegas = bodegaDAO.obtenerBodegasPrincipalesConIds();

            for (Map.Entry<Integer, String> entry : bodegas.entrySet()) {
                bodegaPrincipalMap.put(entry.getValue(), entry.getKey());
                comboBox.addItem(entry.getValue());
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al cargar las bodegas principales: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarSubbodegasEnComboBox(JComboBox<String> comboBox, int idBodegaPrincipal) {
        try {
            comboBox.removeAllItems(); // Limpiar elementos previos
            subbodegaMap.clear(); // Limpiar mapa de subbodegas

            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> subbodegas = bodegaDAO.obtenerSubbodegasPorBodegaPrincipalConIds(idBodegaPrincipal);

            if (subbodegas.isEmpty()) {
                comboBox.addItem("No hay subbodegas disponibles");
                comboBox.setEnabled(false);
            } else {
                comboBox.setEnabled(true);
                for (Map.Entry<Integer, String> entry : subbodegas.entrySet()) {
                    subbodegaMap.put(entry.getValue(), entry.getKey());
                    comboBox.addItem(entry.getValue());
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarProductosEnComboBox(JComboBox<String> productoComboBox) {
        productoComboBox.removeAllItems(); // Limpiar elementos previos

        try {
            List<String> nombresProductos = productoDAO.obtenerNombresProductos();

            if (nombresProductos.isEmpty()) {
                productoComboBox.addItem("No hay productos disponibles");
                productoComboBox.setEnabled(false);
            } else {
                productoComboBox.setEnabled(true);
                for (String nombre : nombresProductos) {
                    productoComboBox.addItem(nombre);
                }
            }
        } catch (SQLException ex) {
            productoComboBox.addItem("Error al cargar productos");
            productoComboBox.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Error al cargar productos desde la base de datos: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarSubbodegasEn(JComboBox<String> bodegaComboBox, JComboBox<String> subbodegaComboBox) {
        subbodegaComboBox.removeAllItems(); // Limpia el ComboBox de subbodegas

        String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();

        if (bodegaSeleccionada != null && !"Todas las bodegas".equals(bodegaSeleccionada)) {
            Integer idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionada);
            cargarSubbodegasEnComboBox(subbodegaComboBox, idBodegaPrincipal);
        } else {
            subbodegaComboBox.addItem("Todas las subbodegas");
        }
    }

    private Integer obtenerIdDesdeComboBox(JComboBox<String> comboBox, Map<String, Integer> mapa) {
        try {
            String seleccionado = (String) comboBox.getSelectedItem();
            return (seleccionado != null && mapa.containsKey(seleccionado)) ? mapa.get(seleccionado) : null;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al obtener ID desde el ComboBox: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void cerrarSesion() {
        try {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "¿Está seguro de que desea cerrar sesión?",
                    "Confirmar Cierre de Sesión",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                limpiarSesion();
                dispose(); // Cierra la ventana actual

                // Abre la pantalla de inicio de sesión
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error al cerrar la sesión: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void limpiarSesion() {
        // Aquí puedes agregar tareas como cerrar conexiones a la base de datos, limpiar caché, etc.
        System.out.println("Limpieza de sesión realizada.");
    }

    private void mostrarErrorSQL(String mensaje, SQLException ex) {
        String detalleError = "Código SQL: " + ex.getErrorCode() + "\nMensaje: " + ex.getMessage();
        JOptionPane.showMessageDialog(
                this,
                mensaje + "\n" + detalleError,
                "Error de Base de Datos",
                JOptionPane.ERROR_MESSAGE
        );
    }

    public void refrescarBodegas() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBox);
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxActualizar);
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxEliminar);
                cargarBodegasPrincipalesEnComboBox(bodegaDestinoComboBox);
                return null;
            }

            @Override
            protected void done() {
                System.out.println("Bodegas actualizadas en segundo plano.");
            }
        }.execute();
    }

    @Override
    public void actualizarBodegas() {
        refrescarBodegas();
    }

}
