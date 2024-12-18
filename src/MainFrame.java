import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainFrame extends JFrame implements BodegaObserver{

    // Variables de instancia
    private ProductoDAO productoDAO = new ProductoDAO();
    private JTable inventarioTable;
    private JTextField buscarField;
    private Map<String, Integer> bodegaPrincipalMap = new HashMap<>();
    private Map<String, Integer> subbodegaMap = new HashMap<>();
    private JComboBox<String> bodegaPrincipalComboBox; // Declaración del JComboBox para bodegas principales
    private JComboBox<String> subbodegaComboBox; // Declaración para subbodegas (si aplica)
    private JComboBox<String> bodegaPrincipalComboBoxAgregar;
    private JComboBox<String> subbodegaComboBoxAgregar;
    private JComboBox<String> bodegaPrincipalComboBoxActualizar;
    private JComboBox<String> subbodegaComboBoxActualizar;
    private JComboBox<String> bodegaPrincipalComboBoxEliminar;
    private JComboBox<String> subbodegaComboBoxEliminar;
    private JComboBox<String> bodegaDestinoComboBox; // ComboBox para la bodega destino
    private JComboBox<String> subbodegaDestinoComboBox; // ComboBox para la subbodega destino

    // Constructor principal
    public MainFrame() {
        setTitle("Inventario Tercera Compañia San Pedro de la paz");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Inicialización de componentes
        initComponents();
    }

    // Método para inicializar todos los componentes y layout
    // Métodos de pestañas
    private void initComponents() {
        // Panel para botones superiores
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Botón de Cerrar Sesión
        JButton logoutButton = createLogoutButton();
        topPanel.add(logoutButton);

        // Botón para gestionar bodegas
        JButton gestionarBodegasButton = new JButton("Gestionar Bodegas");
        gestionarBodegasButton.addActionListener(e -> {
            GestionBodegasFrame gestionBodegasFrame = new GestionBodegasFrame(this);

            // Registrar el MainFrame como observador
            gestionBodegasFrame.agregarObservador(this);

            gestionBodegasFrame.setVisible(true);

            // Refrescar bodegas cuando se cierre la ventana
            gestionBodegasFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    try {
                        refrescarBodegas();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Error al refrescar las bodegas: " + ex.getMessage());
                    }
                }
            });
        });

        topPanel.add(gestionarBodegasButton);

        // Añadir el panel superior al frame principal
        add(topPanel, BorderLayout.NORTH);

        // Crear pestañas
        JTabbedPane tabbedPane = createTabbedPane();

        // Agregar pestañas al JFrame
        add(tabbedPane, BorderLayout.CENTER);

        // Cargar datos iniciales en la tabla
        try {
            cargarDatosTabla(); // Asegúrate de que este método esté definido en tu clase
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar los datos iniciales: " + ex.getMessage());
        }
    }

    // Creación modular de pestañas
    private JTabbedPane createTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();

        try {
            JPanel agregarProductoPanel = createAgregarProductoPanel();
            tabbedPane.addTab("Agregar Producto", agregarProductoPanel);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar la pestaña 'Agregar Producto': " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        try {
            JPanel verInventarioPanel = createVerInventarioPanel();
            tabbedPane.addTab("Ver Inventario", verInventarioPanel);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar la pestaña 'Ver Inventario': " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        try {
            JPanel actualizarProductoPanel = createActualizarProductoPanel();
            tabbedPane.addTab("Actualizar Producto", actualizarProductoPanel);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar la pestaña 'Actualizar Producto': " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        try {
            JPanel eliminarProductoPanel = createEliminarProductoPanel();
            tabbedPane.addTab("Eliminar Producto", eliminarProductoPanel);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar la pestaña 'Eliminar Producto': " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        return tabbedPane;
    }

    private JPanel createVerInventarioPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        try {
            // Configuración del modelo de la tabla
            DefaultTableModel model = new DefaultTableModel(
                    new Object[]{"ID", "Nombre", "Cantidad", "Fecha Ingreso", "Ubicación", "Bodega Principal", "Subbodega"}, 0
            );
            inventarioTable = new JTable(model);
            JScrollPane scrollPane = new JScrollPane(inventarioTable);

            // Panel de filtros
            JPanel filtroPanel = createFiltroPanel();

            // Agregar componentes al panel principal
            panel.add(filtroPanel, BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            // Cargar todos los datos inicialmente
            cargarDatosTablaConFiltros(null, null, null);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar el panel 'Ver Inventario': " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        return panel;
    }

    // Método auxiliar: Creación del panel de filtros
    private JPanel createFiltroPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Campo de texto para búsqueda
        JTextField buscarField = new JTextField(20);

        // ComboBox de bodega principal
        JComboBox<String> bodegaComboBox = new JComboBox<>();
        bodegaComboBox.addItem("Todas las bodegas");
        cargarBodegasPrincipalesEnComboBox(bodegaComboBox);

        // ComboBox de subbodega
        JComboBox<String> subbodegaComboBox = new JComboBox<>();
        subbodegaComboBox.addItem("Todas las subbodegas");

        // Listener para el ComboBox de bodegas
        bodegaComboBox.addActionListener(e -> {
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
            if (bodegaSeleccionada != null && !bodegaSeleccionada.equals("Todas las bodegas")) {
                int idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionada);
                cargarSubbodegasEnComboBox(subbodegaComboBox, idBodegaPrincipal);
            } else {
                subbodegaComboBox.removeAllItems();
                subbodegaComboBox.addItem("Todas las subbodegas");
            }
            cargarDatosTablaConFiltros(buscarField.getText(), bodegaSeleccionada, null);
        });

        // Listener para el ComboBox de subbodegas
        subbodegaComboBox.addActionListener(e -> {
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
            String subbodegaSeleccionada = (String) subbodegaComboBox.getSelectedItem();
            cargarDatosTablaConFiltros(buscarField.getText(), bodegaSeleccionada, subbodegaSeleccionada);
        });

        // Listener para el campo de búsqueda
        buscarField.addActionListener(e -> {
            String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
            String subbodegaSeleccionada = (String) subbodegaComboBox.getSelectedItem();
            cargarDatosTablaConFiltros(buscarField.getText(), bodegaSeleccionada, subbodegaSeleccionada);
        });

        panel.add(new JLabel("Buscar:"));
        panel.add(buscarField);
        panel.add(new JLabel("Bodega:"));
        panel.add(bodegaComboBox);
        panel.add(new JLabel("Subbodega:"));
        panel.add(subbodegaComboBox);

        JButton refrescarButton = new JButton("Refrescar Inventario");
        refrescarButton.addActionListener(e -> cargarDatosTablaConFiltros(null, null, null));
        panel.add(refrescarButton);

        return panel;
    }

    // Método para cargar datos de la tabla con filtros
    private void cargarDatosTablaConFiltros(String textoBusqueda, String bodegaSeleccionada, String subbodegaSeleccionada) {
        try {
            System.out.println("Cargando datos de inventario...");
            System.out.println("Texto de búsqueda: " + textoBusqueda);
            System.out.println("Bodega seleccionada: " + bodegaSeleccionada);
            System.out.println("Subbodega seleccionada: " + subbodegaSeleccionada);

            List<ProductoForm> productos;

            // Obtener productos dependiendo de los filtros aplicados
            if ("Todas las bodegas".equals(bodegaSeleccionada)) {
                // Caso para todas las bodegas
                productos = productoDAO.obtenerProductos();
            } else {
                Integer idBodega = bodegaPrincipalMap.getOrDefault(bodegaSeleccionada, null);
                Integer idSubbodega = subbodegaMap.getOrDefault(subbodegaSeleccionada, null);

                System.out.println("ID de Bodega seleccionada: " + idBodega);
                System.out.println("ID de Subbodega seleccionada: " + idSubbodega);
                System.out.println("Aplicando filtros de búsqueda...");

                productos = productoDAO.buscarProductosPorFiltros(textoBusqueda, idBodega, idSubbodega);
            }

            // Verificar si los productos fueron recuperados
            System.out.println("Productos recuperados: " + productos.size());
            for (ProductoForm producto : productos) {
                System.out.println("Producto: " + producto.getNombre() + ", Cantidad: " + producto.getCantidad());
            }

            // Configurar el modelo de la tabla
            DefaultTableModel model = new DefaultTableModel();
            model.addColumn("ID");
            model.addColumn("Nombre");
            model.addColumn("Cantidad");
            model.addColumn("Fecha Ingreso");
            model.addColumn("Ubicación");
            model.addColumn("Bodega Principal");
            model.addColumn("Subbodega");

            for (ProductoForm producto : productos) {
                model.addRow(new Object[]{
                        producto.getId(),
                        producto.getNombre(),
                        producto.getCantidad(),
                        new SimpleDateFormat("yyyy-MM-dd").format(producto.getFechaIngreso()),
                        producto.getUbicacion(),
                        producto.getNombreBodegaPrincipal(),
                        producto.getNombreSubbodega()
                });
            }

            inventarioTable.setModel(model);

            if (productos.isEmpty()) {
                System.out.println("No se encontraron productos con los filtros seleccionados.");
            }

        } catch (Exception ex) {
            System.out.println("Error al cargar productos: " + ex.getMessage());
            ex.printStackTrace(); // Depuración de la excepción
            JOptionPane.showMessageDialog(this, "Error al cargar productos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDatosTabla() {
        try {
            // Obtener todos los productos con sus bodegas asociadas
            List<ProductoForm> productos = productoDAO.obtenerProductosConBodega();

            // Crear modelo de tabla
            DefaultTableModel model = new DefaultTableModel();
            model.addColumn("ID");
            model.addColumn("Nombre");
            model.addColumn("Cantidad");
            model.addColumn("Fecha Ingreso");
            model.addColumn("Ubicación");
            model.addColumn("Bodega Principal");
            model.addColumn("Subbodega");

            if (productos.isEmpty()) {
                // Mostrar mensaje si no hay productos
                JOptionPane.showMessageDialog(this, "No hay productos registrados en el inventario.", "Inventario vacío", JOptionPane.INFORMATION_MESSAGE);
            } else {
                // Cargar productos al modelo
                BodegaDAO bodegaDAO = new BodegaDAO();
                for (ProductoForm producto : productos) {
                    String nombreBodega = bodegaDAO.obtenerNombreBodegaPorId(producto.getIdBodegaPrincipal());
                    String nombreSubbodega = bodegaDAO.obtenerNombreSubbodegaPorId(producto.getIdSubbodega());

                    model.addRow(new Object[]{
                            producto.getId(),
                            producto.getNombre(),
                            producto.getCantidad(),
                            new SimpleDateFormat("yyyy-MM-dd").format(producto.getFechaIngreso()),
                            producto.getUbicacion(),
                            nombreBodega,
                            nombreSubbodega
                    });
                }
            }

            // Configurar el modelo en la tabla
            inventarioTable.setModel(model);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar productos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error inesperado: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDatosTablaConBusqueda(String textoBusqueda, Object bodegaSeleccionada) {
        try {
            List<ProductoForm> productos;
            if (bodegaSeleccionada == null || bodegaSeleccionada.equals("Todas las bodegas")) {
                productos = productoDAO.obtenerProductos();
            } else if (bodegaSeleccionada instanceof Integer) {
                productos = productoDAO.buscarProductosPorBodega(textoBusqueda, (Integer) bodegaSeleccionada);
            } else {
                throw new IllegalArgumentException("Tipo de bodega seleccionada no válido.");
            }

            DefaultTableModel model = new DefaultTableModel();
            model.addColumn("ID");
            model.addColumn("Nombre");
            model.addColumn("Cantidad");
            model.addColumn("Fecha Ingreso");
            model.addColumn("Ubicación");
            model.addColumn("Bodega Principal");
            model.addColumn("Subbodega");

            for (ProductoForm producto : productos) {
                model.addRow(new Object[]{
                        producto.getId(),
                        producto.getNombre(),
                        producto.getCantidad(),
                        new SimpleDateFormat("yyyy-MM-dd").format(producto.getFechaIngreso()),
                        producto.getUbicacion(),
                        producto.getIdBodegaPrincipal(),
                        producto.getIdSubbodega()
                });
            }

            inventarioTable.setModel(model);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar productos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JButton createLogoutButton() {
        JButton logoutButton = new JButton("Cerrar Sesión");

        // Configurar acción del botón
        logoutButton.addActionListener(e -> {
            try {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "¿Está seguro de que desea cerrar sesión?",
                        "Confirmar Cierre de Sesión",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                // Confirmar que el usuario seleccionó "Sí"
                if (confirm == JOptionPane.YES_OPTION) {
                    cerrarSesion();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Error al cerrar la sesión: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        return logoutButton;
    }

    private void cerrarSesion() {
        try {
            // Realizar cualquier tarea de limpieza necesaria antes de cerrar
            limpiarSesion();

            // Cerrar el frame actual
            dispose();

            // Abrir el frame de inicio de sesión
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        } catch (Exception ex) {
            // Mostrar un mensaje de error si algo falla
            JOptionPane.showMessageDialog(
                    this,
                    "Error al cerrar la sesión: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Limpia cualquier recurso o variable antes de cerrar sesión.
     * Este método se puede personalizar según las necesidades del sistema.
     */
    private void limpiarSesion() {
        // Ejemplo: Cerrar conexiones a la base de datos o limpiar datos temporales
        System.out.println("Limpieza de sesión realizada.");
    }

    private JPanel createAgregarProductoPanel() {
        // Inicialización del ComboBox de bodegas principales
        bodegaPrincipalComboBoxAgregar = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxAgregar); // Cargar bodegas principales en el ComboBox

        // Inicialización del panel y configuración del layout
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Etiquetas y campos de entrada
        JLabel nombreLabel = new JLabel("Nombre:");
        JTextField nombreField = new JTextField(20);

        JLabel cantidadLabel = new JLabel("Cantidad:");
        JTextField cantidadField = new JTextField(20);

        JLabel fechaIngresoLabel = new JLabel("Fecha de Ingreso:");
        JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);

        JLabel ubicacionLabel = new JLabel("Ubicación:");
        JTextField ubicacionField = new JTextField(20);

        JLabel bodegaPrincipalLabel = new JLabel("Bodega Principal:");
        JLabel subbodegaLabel = new JLabel("Subbodega:");

        subbodegaComboBoxAgregar = new JComboBox<>();
        subbodegaComboBoxAgregar.setEnabled(false); // Inicialmente deshabilitado

        // Listener para actualizar las subbodegas al seleccionar una bodega principal
        bodegaPrincipalComboBoxAgregar.addActionListener(e -> {
            String bodegaSeleccionada = (String) bodegaPrincipalComboBoxAgregar.getSelectedItem();
            System.out.println("Bodega seleccionada: " + bodegaSeleccionada); // Depuración

            if (bodegaSeleccionada != null && bodegaPrincipalMap.containsKey(bodegaSeleccionada)) {
                int idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionada);
                System.out.println("ID de Bodega Principal seleccionada: " + idBodegaPrincipal); // Depuración
                cargarSubbodegasEnComboBox(subbodegaComboBoxAgregar, idBodegaPrincipal);
                subbodegaComboBoxAgregar.setEnabled(true); // Habilitar si hay subbodegas disponibles
            } else {
                subbodegaComboBoxAgregar.removeAllItems();
                subbodegaComboBoxAgregar.addItem("No hay subbodegas disponibles");
                subbodegaComboBoxAgregar.setEnabled(false); // Deshabilitar si no hay subbodegas
            }
        });

        // Botón para agregar el producto
        JButton agregarButton = new JButton("Agregar Producto");
        agregarButton.addActionListener(e -> {
            try {
                // Validar campos obligatorios
                if (nombreField.getText().isEmpty() || cantidadField.getText().isEmpty() || ubicacionField.getText().isEmpty()) {
                    throw new IllegalArgumentException("Todos los campos son obligatorios.");
                }

                String nombreProducto = nombreField.getText().trim();
                int cantidad = Integer.parseInt(cantidadField.getText().trim());
                Date fechaIngreso = (Date) dateSpinner.getValue();
                String ubicacion = ubicacionField.getText().trim();

                Integer idBodegaPrincipal = obtenerIdDesdeComboBox(bodegaPrincipalComboBoxAgregar, bodegaPrincipalMap);
                Integer idSubbodega = obtenerIdDesdeComboBox(subbodegaComboBoxAgregar, subbodegaMap);

                // Depuración de IDs obtenidos
                System.out.println("ID Bodega Principal: " + idBodegaPrincipal);
                System.out.println("ID Subbodega: " + idSubbodega);

                if (idBodegaPrincipal == null || idSubbodega == null) {
                    throw new IllegalArgumentException("Debe seleccionar una Bodega Principal y una Subbodega.");
                }

                // Crear un nuevo producto y agregarlo
                ProductoForm nuevoProducto = new ProductoForm(0, nombreProducto, cantidad, fechaIngreso, ubicacion, idBodegaPrincipal, idSubbodega);
                productoDAO.agregarProducto(nuevoProducto);

                JOptionPane.showMessageDialog(this, "Producto agregado correctamente.");

                // Limpiar los campos después de agregar el producto
                limpiarCampos(nombreField, cantidadField, ubicacionField, bodegaPrincipalComboBoxAgregar, subbodegaComboBoxAgregar);

                // Recargar los datos de la tabla
                cargarDatosTabla();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Ingrese un número válido para la cantidad.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                mostrarErrorSQL("Error al agregar el producto", ex);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Añadir los componentes al panel con el layout
        gbc.gridx = 0; gbc.gridy = 0; panel.add(nombreLabel, gbc);
        gbc.gridx = 1; panel.add(nombreField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(cantidadLabel, gbc);
        gbc.gridx = 1; panel.add(cantidadField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(fechaIngresoLabel, gbc);
        gbc.gridx = 1; panel.add(dateSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 3; panel.add(ubicacionLabel, gbc);
        gbc.gridx = 1; panel.add(ubicacionField, gbc);
        gbc.gridx = 0; gbc.gridy = 4; panel.add(bodegaPrincipalLabel, gbc);
        gbc.gridx = 1; panel.add(bodegaPrincipalComboBoxAgregar, gbc);
        gbc.gridx = 0; gbc.gridy = 5; panel.add(subbodegaLabel, gbc);
        gbc.gridx = 1; panel.add(subbodegaComboBoxAgregar, gbc);
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; panel.add(agregarButton, gbc);

        return panel;
    }

    private JPanel createActualizarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Componentes para seleccionar producto
        JLabel productoLabel = new JLabel("Seleccionar Producto:");
        JComboBox<String> productoComboBox = new JComboBox<>();
        cargarProductosEnComboBox(productoComboBox);

        // Campo para cantidad
        JLabel cantidadLabel = new JLabel("Cantidad a Mover:");
        JTextField cantidadField = new JTextField(20);

        // Bodega y subbodega de origen
        JLabel bodegaOrigenLabel = new JLabel("Bodega Origen:");
        bodegaPrincipalComboBoxActualizar = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxActualizar);

        JLabel subbodegaOrigenLabel = new JLabel("Subbodega Origen:");
        subbodegaComboBoxActualizar = new JComboBox<>();
        bodegaPrincipalComboBoxActualizar.addActionListener(e -> {
            Integer idBodegaOrigen = obtenerIdDesdeComboBox(bodegaPrincipalComboBoxActualizar, bodegaPrincipalMap);
            if (idBodegaOrigen != null) {
                cargarSubbodegasEnComboBox(subbodegaComboBoxActualizar, idBodegaOrigen);
            } else {
                subbodegaComboBoxActualizar.removeAllItems();
            }
        });

        // Bodega y subbodega de destino
        JLabel bodegaDestinoLabel = new JLabel("Bodega Destino:");
        JComboBox<String> bodegaDestinoComboBox = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaDestinoComboBox);

        JLabel subbodegaDestinoLabel = new JLabel("Subbodega Destino:");
        JComboBox<String> subbodegaDestinoComboBox = new JComboBox<>();
        bodegaDestinoComboBox.addActionListener(e -> {
            Integer idBodegaDestino = obtenerIdDesdeComboBox(bodegaDestinoComboBox, bodegaPrincipalMap);
            if (idBodegaDestino != null) {
                cargarSubbodegasEnComboBox(subbodegaDestinoComboBox, idBodegaDestino);
            } else {
                subbodegaDestinoComboBox.removeAllItems();
            }
        });

        // Botón para actualizar
        JButton actualizarButton = new JButton("Mover Producto");
        actualizarButton.addActionListener(e -> {
            try {
                // Validar campos seleccionados
                String productoSeleccionado = (String) productoComboBox.getSelectedItem();
                int cantidad = Integer.parseInt(cantidadField.getText().trim());
                Integer idBodegaOrigen = obtenerIdDesdeComboBox(bodegaPrincipalComboBoxActualizar, bodegaPrincipalMap);
                Integer idSubbodegaOrigen = obtenerIdDesdeComboBox(subbodegaComboBoxActualizar, subbodegaMap);
                Integer idBodegaDestino = obtenerIdDesdeComboBox(bodegaDestinoComboBox, bodegaPrincipalMap);
                Integer idSubbodegaDestino = obtenerIdDesdeComboBox(subbodegaDestinoComboBox, subbodegaMap);

                if (productoSeleccionado == null || cantidad <= 0 || idBodegaOrigen == null || idSubbodegaOrigen == null || idBodegaDestino == null || idSubbodegaDestino == null) {
                    throw new IllegalArgumentException("Debe seleccionar todos los campos y una cantidad mayor a 0.");
                }

                int idProducto = productoDAO.obtenerIdProductoPorNombre(productoSeleccionado);

                // Mover el producto
                productoDAO.moverProducto(idProducto, idBodegaOrigen, idSubbodegaOrigen, idBodegaDestino, idSubbodegaDestino, cantidad);

                JOptionPane.showMessageDialog(this, "Producto movido exitosamente.");
                cargarDatosTabla();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Por favor ingrese un número válido para la cantidad.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException | SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error al mover producto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Añadir componentes al panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(productoLabel, gbc);
        gbc.gridx = 1;
        panel.add(productoComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(cantidadLabel, gbc);
        gbc.gridx = 1;
        panel.add(cantidadField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(bodegaOrigenLabel, gbc);
        gbc.gridx = 1;
        panel.add(bodegaPrincipalComboBoxActualizar, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(subbodegaOrigenLabel, gbc);
        gbc.gridx = 1;
        panel.add(subbodegaComboBoxActualizar, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(bodegaDestinoLabel, gbc);
        gbc.gridx = 1;
        panel.add(bodegaDestinoComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(subbodegaDestinoLabel, gbc);
        gbc.gridx = 1;
        panel.add(subbodegaDestinoComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        panel.add(actualizarButton, gbc);

        return panel;
    }

    private void cargarProductosEnComboBox(JComboBox<String> productoComboBox) {
        productoComboBox.removeAllItems(); // Limpia cualquier elemento existente
        try {
            // Obtener la lista de nombres de productos desde el DAO
            List<String> nombresProductos = productoDAO.obtenerNombresProductos();

            if (nombresProductos == null || nombresProductos.isEmpty()) {
                productoComboBox.addItem("No hay productos disponibles");
                productoComboBox.setEnabled(false); // Deshabilitar el ComboBox si no hay productos
            } else {
                productoComboBox.setEnabled(true); // Habilitar el ComboBox si hay productos
                for (String nombre : nombresProductos) {
                    productoComboBox.addItem(nombre);
                }
            }
        } catch (SQLException ex) {
            // En caso de error, deshabilitar el ComboBox y mostrar un mensaje claro
            productoComboBox.removeAllItems();
            productoComboBox.addItem("Error al cargar productos");
            productoComboBox.setEnabled(false);

            // Mostrar un mensaje de error al usuario
            JOptionPane.showMessageDialog(
                    this,
                    "Error al cargar productos desde la base de datos: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void cargarBodegasEnComboBox(JComboBox<String> comboBox) {
        comboBox.removeAllItems(); // Limpia cualquier elemento existente
        bodegaPrincipalMap.clear(); // Limpia el mapa de bodegas principales

        try {
            // Obtener nombres de bodegas principales junto con sus IDs
            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> bodegas = bodegaDAO.obtenerBodegasPrincipalesConIds();

            if (bodegas.isEmpty()) {
                comboBox.addItem("No hay bodegas disponibles");
                comboBox.setEnabled(false); // Deshabilitar el ComboBox si no hay bodegas
            } else {
                comboBox.setEnabled(true); // Habilitar el ComboBox si hay bodegas
                for (Map.Entry<Integer, String> entry : bodegas.entrySet()) {
                    int idBodega = entry.getKey();
                    String nombreBodega = entry.getValue();

                    bodegaPrincipalMap.put(nombreBodega, idBodega); // Actualiza el mapa
                    comboBox.addItem(nombreBodega); // Agrega la bodega al ComboBox
                }
            }
        } catch (SQLException e) {
            // Mostrar un mensaje de error en caso de fallo al cargar bodegas
            JOptionPane.showMessageDialog(
                    this,
                    "Error al cargar las bodegas: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private JPanel createEliminarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Etiqueta y ComboBox para seleccionar el producto
        JLabel productoLabel = new JLabel("Producto:");
        JComboBox<String> productoComboBox = new JComboBox<>();
        cargarProductosEnComboBox(productoComboBox);

        // Etiqueta y campo de texto para la cantidad a eliminar
        JLabel cantidadLabel = new JLabel("Cantidad:");
        JTextField cantidadField = new JTextField(20);

        // Etiqueta y ComboBox para seleccionar la bodega principal
        JLabel bodegaLabel = new JLabel("Bodega:");
        JComboBox<String> bodegaComboBox = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaComboBox);

        // Etiqueta y ComboBox para seleccionar la subbodega
        JLabel subbodegaLabel = new JLabel("Subbodega:");
        JComboBox<String> subbodegaComboBox = new JComboBox<>();

        // Listener para actualizar subbodegas según la bodega seleccionada
        bodegaComboBox.addActionListener(e -> {
            try {
                Integer idBodegaPrincipal = obtenerIdDesdeComboBox(bodegaComboBox, bodegaPrincipalMap);
                if (idBodegaPrincipal != null) {
                    cargarSubbodegasEnComboBox(subbodegaComboBox, idBodegaPrincipal);
                } else {
                    subbodegaComboBox.removeAllItems();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Botón para eliminar el producto
        JButton eliminarButton = new JButton("Eliminar");
        eliminarButton.addActionListener(e -> {
            try {
                // Validación de campos obligatorios
                String productoSeleccionado = (String) productoComboBox.getSelectedItem();
                Integer idBodegaPrincipal = obtenerIdDesdeComboBox(bodegaComboBox, bodegaPrincipalMap);
                Integer idSubbodega = obtenerIdDesdeComboBox(subbodegaComboBox, subbodegaMap);

                if (productoSeleccionado == null || idBodegaPrincipal == null || idSubbodega == null) {
                    throw new IllegalArgumentException("Debe seleccionar un producto, bodega y subbodega válidos.");
                }

                // Validar cantidad
                if (cantidadField.getText().trim().isEmpty() || Integer.parseInt(cantidadField.getText().trim()) <= 0) {
                    throw new IllegalArgumentException("Ingrese una cantidad mayor a 0.");
                }
                int cantidad = Integer.parseInt(cantidadField.getText().trim());

                // Obtener ID del producto
                int idProducto = productoDAO.obtenerIdProductoPorNombre(productoSeleccionado);

                // Eliminar cantidad del producto
                productoDAO.eliminarCantidadProducto(idProducto, idBodegaPrincipal, idSubbodega, cantidad);

                // Confirmación de éxito
                JOptionPane.showMessageDialog(this, "Producto eliminado correctamente.");

                // Actualizar la tabla de datos y limpiar campos
                cargarDatosTabla();
                productoComboBox.setSelectedIndex(-1);
                cantidadField.setText("");
                bodegaComboBox.setSelectedIndex(-1);
                subbodegaComboBox.removeAllItems();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "La cantidad debe ser un número válido.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                mostrarErrorSQL("Error al eliminar producto", ex);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Configurar el layout y añadir componentes
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(productoLabel, gbc);
        gbc.gridx = 1;
        panel.add(productoComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(cantidadLabel, gbc);
        gbc.gridx = 1;
        panel.add(cantidadField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(bodegaLabel, gbc);
        gbc.gridx = 1;
        panel.add(bodegaComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(subbodegaLabel, gbc);
        gbc.gridx = 1;
        panel.add(subbodegaComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        panel.add(eliminarButton, gbc);

        return panel;
    }

    // Método para cargar bodegas principales en el mapa y el ComboBox
    private void cargarBodegasPrincipalesEnComboBox(JComboBox<String> comboBox) {
        try {
            comboBox.removeAllItems();
            bodegaPrincipalMap.clear(); // Limpia el mapa

            // Agregar la opción "Todas las bodegas"
            comboBox.addItem("Todas las bodegas");

            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> bodegas = bodegaDAO.obtenerBodegasPrincipalesConIds();

            for (Map.Entry<Integer, String> entry : bodegas.entrySet()) {
                int idBodega = entry.getKey();
                String nombreBodega = entry.getValue();
                bodegaPrincipalMap.put(nombreBodega, idBodega);
                comboBox.addItem(nombreBodega); // Agregar nombres al ComboBox
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error al cargar las bodegas principales: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }



    // Método para cargar subbodegas en el mapa y el ComboBox
    private void cargarSubbodegasEnComboBox(JComboBox<String> comboBox, int idBodegaPrincipal) {
        try {
            comboBox.removeAllItems(); // Limpia los elementos actuales
            subbodegaMap.clear(); // Limpia el mapa de subbodegas

            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> subbodegas = bodegaDAO.obtenerSubbodegasPorBodegaPrincipalConIds(idBodegaPrincipal);

            for (Map.Entry<Integer, String> entry : subbodegas.entrySet()) {
                comboBox.addItem(entry.getValue());
                subbodegaMap.put(entry.getValue(), entry.getKey()); // Mapea el nombre al ID
            }

            if (comboBox.getItemCount() == 0) {
                comboBox.addItem("No hay subbodegas disponibles");
                comboBox.setEnabled(false);
            } else {
                comboBox.setEnabled(true);
            }

            System.out.println("Subbodegas cargadas: " + subbodegas); // Depuración

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Integer obtenerIdDesdeComboBox(JComboBox<String> comboBox, Map<String, Integer> mapa) {
        try {
            String seleccionado = (String) comboBox.getSelectedItem();
            System.out.println("Seleccionado del ComboBox: " + seleccionado); // Depuración
            System.out.println("Mapa: " + mapa); // Depuración

            if (seleccionado != null && mapa.containsKey(seleccionado)) {
                return mapa.get(seleccionado);
            } else {
                System.out.println("El mapa no contiene el seleccionado o es nulo."); // Depuración
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al obtener ID desde el ComboBox: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void limpiarCampos(Object... componentes) {
        for (Object componente : componentes) {
            if (componente instanceof JTextField) {
                ((JTextField) componente).setText("");
            } else if (componente instanceof JComboBox) {
                ((JComboBox<?>) componente).setSelectedIndex(-1); // Limpia la selección del JComboBox
            }
        }
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
        try {
            // Refrescar bodegas principales en todos los ComboBox relevantes
            if (bodegaPrincipalComboBox != null) {
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBox);
            }

            if (bodegaPrincipalComboBoxActualizar != null && subbodegaComboBoxActualizar != null) {
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxActualizar);
                String bodegaSeleccionada = (String) bodegaPrincipalComboBoxActualizar.getSelectedItem();
                if (bodegaSeleccionada != null && bodegaPrincipalMap.containsKey(bodegaSeleccionada)) {
                    int idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionada);
                    cargarSubbodegasEnComboBox(subbodegaComboBoxActualizar, idBodegaPrincipal);
                } else {
                    subbodegaComboBoxActualizar.removeAllItems();
                }
            }

            if (bodegaPrincipalComboBoxEliminar != null && subbodegaComboBoxEliminar != null) {
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxEliminar);
                String bodegaSeleccionada = (String) bodegaPrincipalComboBoxEliminar.getSelectedItem();
                if (bodegaSeleccionada != null && bodegaPrincipalMap.containsKey(bodegaSeleccionada)) {
                    int idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionada);
                    cargarSubbodegasEnComboBox(subbodegaComboBoxEliminar, idBodegaPrincipal);
                } else {
                    subbodegaComboBoxEliminar.removeAllItems();
                }
            }

            if (bodegaPrincipalComboBoxActualizar != null && subbodegaComboBoxActualizar != null) {
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxActualizar);
                String bodegaSeleccionada = (String) bodegaPrincipalComboBoxActualizar.getSelectedItem();
                if (bodegaSeleccionada != null && bodegaPrincipalMap.containsKey(bodegaSeleccionada)) {
                    int idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionada);
                    cargarSubbodegasEnComboBox(subbodegaComboBoxActualizar, idBodegaPrincipal);
                } else {
                    subbodegaComboBoxActualizar.removeAllItems();
                }
            }

            if (bodegaPrincipalComboBox != null) {
                cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBox);
            }

            if (bodegaDestinoComboBox != null && subbodegaDestinoComboBox != null) {
                cargarBodegasPrincipalesEnComboBox(bodegaDestinoComboBox);
                String bodegaSeleccionadaDestino = (String) bodegaDestinoComboBox.getSelectedItem();
                if (bodegaSeleccionadaDestino != null && bodegaPrincipalMap.containsKey(bodegaSeleccionadaDestino)) {
                    int idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionadaDestino);
                    cargarSubbodegasEnComboBox(subbodegaDestinoComboBox, idBodegaPrincipal);
                } else {
                    subbodegaDestinoComboBox.removeAllItems();
                }
            }

            // Refrescar la tabla de inventario si aplica
            cargarDatosTabla();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al refrescar las bodegas: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarSubbodegas(JComboBox<String> bodegaComboBox, JComboBox<String> subbodegaComboBox) {
        String bodegaSeleccionada = (String) bodegaComboBox.getSelectedItem();
        if (bodegaSeleccionada != null && bodegaPrincipalMap.containsKey(bodegaSeleccionada)) {
            int idBodegaPrincipal = bodegaPrincipalMap.get(bodegaSeleccionada);
            cargarSubbodegasEnComboBox(subbodegaComboBox, idBodegaPrincipal);
        } else {
            subbodegaComboBox.removeAllItems();
        }
    }

    @Override
    public void actualizarBodegas() {
        // Llama al método que refresca las bodegas principales y subbodegas
        refrescarBodegas();
    }

    private void cargarInventarioPorDefecto(DefaultTableModel model) {
        try {
            // Recuperar todos los productos sin filtros
            List<ProductoForm> productos = productoDAO.buscarProductosPorFiltros("", null, null);

            // Limpiar la tabla antes de cargar nuevos datos
            model.setRowCount(0);

            // Llenar la tabla con los productos recuperados
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
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar el inventario: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
