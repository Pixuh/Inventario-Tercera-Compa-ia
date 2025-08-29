import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

// CSV / archivos
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// Fechas
import java.text.SimpleDateFormat;
import java.util.Date;

// Swing utilidades
import javax.swing.JFileChooser;
import javax.swing.table.DefaultTableModel;

// Portapapeles (si usas copiar tabla)
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public class MainFrame extends JFrame implements BodegaObserver {

    private final Map<String, java.util.List<String>> subbodegasPorBodegaOrigen = new HashMap<>();
    private boolean bloqueaEventosOrigen = false;

    private final ProductoDAO productoDAO = new ProductoDAO();
    private Map<String, Integer> bodegaPrincipalMap = new LinkedHashMap<>();
    private Map<String, Integer> subbodegaMap       = new LinkedHashMap<>();

    private JTable inventarioTable;
    private JTextField buscarField;
    // Estado UI
    private final JLabel syncLabel = new JLabel("Listo");

    private JComboBox<String> bodegaPrincipalComboBox, subbodegaComboBox;
    private JComboBox<String> bodegaPrincipalComboBoxActualizar;
    private JComboBox<String> subbodegaComboBoxActualizar;
    private JComboBox<String> bodegaPrincipalComboBoxEliminar;
    private JComboBox<String> subbodegaComboBoxEliminar;
    private JComboBox<String> bodegaDestinoComboBox;
    private JComboBox<String> subbodegaDestinoComboBox;
    private JComboBox<String> productoComboBoxActualizar;
    private JComboBox<String> productoComboBoxEliminar;

    // --- Paso 9: overlay de carga + botón refrescar como field ---
    private JPanel loadingGlass;
    private JLabel loadingLabel;
    private JButton refrescarButton;  // antes era local en createFiltroPanel

    // Paso 10: exportación CSV
    private JMenuItem exportCsvItem;

    // STATUS & UX
    private JLabel totalLabel = new JLabel("Total: 0");
    private Timer buscarDebounce;                   // debounce de búsqueda

    public MainFrame() {
        setTitle("Inventario Tercera Compañía San Pedro de la Paz");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initComponents(); // Iniciar la interfaz primero
        setJMenuBar(createMenuBar());

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

        add(createStatusBar(), BorderLayout.SOUTH);

    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = createTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        // Menú/Toolbar
        add(createToolBar(), BorderLayout.NORTH);

        // Atajos
        registrarAtajos();

        // ¡Overlay ya lo crea createVerInventarioPanel()!

        // Carga inicial en el EDT pero después de montar UI
        SwingUtilities.invokeLater(() -> {
            try {
                cargarDatosTabla();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error al cargar los datos: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
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

        // Modelo no editable
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"ID", "Nombre", "Cantidad", "Fecha Ingreso", "Ubicación", "Bodega Principal", "Subbodega"}, 0
        ) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        // Tabla
        inventarioTable = new JTable(model);
        inventarioTable.setAutoCreateRowSorter(true);
        inventarioTable.setFillsViewportHeight(true);
        inventarioTable.setRowHeight(22);

        // Renderer: zebra + alineación
        inventarioTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                if (!s) comp.setBackground((r % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE);
                setHorizontalAlignment((c == 0 || c == 2) ? SwingConstants.CENTER : SwingConstants.LEFT);
                return comp;
            }
        });

        // Centro con overlay
        JPanel center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(inventarioTable), BorderLayout.CENTER);

        panel.add(createFiltroPanel(), BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);

        // 🔹 Crear overlay de carga ANTES de la carga inicial
        buildLoadingOverlay(center);

        // Menú contextual: copiar fila
        JPopupMenu pm = new JPopupMenu();
        pm.add(new JMenuItem(new AbstractAction("Copiar fila") {
            @Override public void actionPerformed(ActionEvent e) {
                int r = inventarioTable.getSelectedRow();
                if (r < 0) return;
                int mr = inventarioTable.convertRowIndexToModel(r);
                TableModel tm = inventarioTable.getModel();
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < tm.getColumnCount(); c++) {
                    if (c > 0) sb.append('\t');
                    sb.append(String.valueOf(tm.getValueAt(mr, c)));
                }
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(sb.toString()), null);
            }
        }));
        inventarioTable.setComponentPopupMenu(pm);

        // 🔹 Carga inicial
        cargarDatosTabla();

        return panel;
    }

    private JPanel createAgregarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Campos
        JTextField nombreField = new JTextField(20);
        JTextField cantidadField = new JTextField(20);
        JTextField ubicacionField = new JTextField(20);

        JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);

        JComboBox<String> bodegaPrincipalComboBoxAgregar = new JComboBox<>();
        JComboBox<String> subbodegaComboBoxAgregar = new JComboBox<>();

        // Cargar bodegas y enlazar subbodegas
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxAgregar);
        bodegaPrincipalComboBoxAgregar.addActionListener(e ->
                actualizarSubbodegasEn(bodegaPrincipalComboBoxAgregar, subbodegaComboBoxAgregar));

        // Botón Agregar
        JButton agregarButton = new JButton("Agregar Producto");
        agregarButton.addActionListener(e -> {
            try {
                // --- Lectura y validación básica ---
                String nombre = nombreField.getText().trim();
                if (nombre.isEmpty()) throw new IllegalArgumentException("Ingrese un nombre de producto.");

                int cantidad;
                try {
                    cantidad = Integer.parseInt(cantidadField.getText().trim());
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("La cantidad debe ser un número entero.");
                }
                if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");

                Date fechaIngreso = (Date) dateSpinner.getValue();
                String ubicacion = ubicacionField.getText().trim();

                String nombreBodegaSel = (String) bodegaPrincipalComboBoxAgregar.getSelectedItem();
                String nombreSubbodegaSel = (String) subbodegaComboBoxAgregar.getSelectedItem();

                if (nombreBodegaSel == null || nombreBodegaSel.isEmpty()) {
                    throw new IllegalArgumentException("Debe seleccionar una bodega principal.");
                }
                if (nombreSubbodegaSel == null || nombreSubbodegaSel.isEmpty()
                        || "No hay subbodegas disponibles".equalsIgnoreCase(nombreSubbodegaSel)) {
                    throw new IllegalArgumentException("Debe seleccionar una subbodega válida.");
                }

                // --- Resolver IDs por nombre usando el DAO ---
                BodegaDAO bodegaDAO = new BodegaDAO();
                int idBodegaPrincipal = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(nombreBodegaSel);
                int idSubbodega      = bodegaDAO.obtenerIdSubbodegaPorNombre(nombreSubbodegaSel);

                // --- Construir y agregar ---
                ProductoForm producto = new ProductoForm(
                        0,
                        nombre,
                        cantidad,
                        fechaIngreso,
                        ubicacion,
                        idBodegaPrincipal,
                        idSubbodega
                );

                productoDAO.agregarProducto(producto);

                JOptionPane.showMessageDialog(this, "Producto agregado correctamente.");

                // Refrescar tabla principal / inventario
                cargarDatosTabla();

                // Refrescar combos de los otros tabs (si existen)
                if (productoComboBoxActualizar != null) cargarProductosEnComboBox(productoComboBoxActualizar);
                if (productoComboBoxEliminar   != null) cargarProductosEnComboBox(productoComboBoxEliminar);

                // Limpiar formulario
                nombreField.setText("");
                cantidadField.setText("");
                ubicacionField.setText("");
                // Mantener la fecha o, si prefieres, reestablecer:
                // dateSpinner.setValue(new Date());

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Error al agregar producto: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        // --- Layout ---
        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1; panel.add(nombreField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1; panel.add(cantidadField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Fecha de Ingreso:"), gbc);
        gbc.gridx = 1; panel.add(dateSpinner, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Ubicación:"), gbc);
        gbc.gridx = 1; panel.add(ubicacionField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Bodega Principal:"), gbc);
        gbc.gridx = 1; panel.add(bodegaPrincipalComboBoxAgregar, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Subbodega:"), gbc);
        gbc.gridx = 1; panel.add(subbodegaComboBoxAgregar, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; panel.add(agregarButton, gbc);

        return panel;
    }

    private JPanel createActualizarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0; gbc.gridy = 0;

        // --- Producto ---
        panel.add(new JLabel("Producto:"), gbc);
        gbc.gridx = 1;
        productoComboBoxActualizar = new JComboBox<>();
        cargarProductosEnComboBox(productoComboBoxActualizar);
        panel.add(productoComboBoxActualizar, gbc);

        productoComboBoxActualizar.addActionListener(e -> cargarOrigenesParaProducto());

        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1;
        JTextField cantidadField = new JTextField(20);
        panel.add(cantidadField, gbc);

        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Bodega Origen:"), gbc);
        gbc.gridx = 1;
        bodegaPrincipalComboBoxActualizar = new JComboBox<>();
        bodegaPrincipalComboBoxActualizar.setEnabled(false);
        panel.add(bodegaPrincipalComboBoxActualizar, gbc);

        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Subbodega Origen:"), gbc);
        gbc.gridx = 1;
        subbodegaComboBoxActualizar = new JComboBox<>();
        subbodegaComboBoxActualizar.setEnabled(false);
        panel.add(subbodegaComboBoxActualizar, gbc);

        bodegaPrincipalComboBoxActualizar.addActionListener(e -> cargarSubbodegasOrigenFiltradas());

        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Bodega Destino:"), gbc);
        gbc.gridx = 1;
        bodegaDestinoComboBox = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaDestinoComboBox);
        panel.add(bodegaDestinoComboBox, gbc);

        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Subbodega Destino:"), gbc);
        gbc.gridx = 1;
        subbodegaDestinoComboBox = new JComboBox<>();
        panel.add(subbodegaDestinoComboBox, gbc);

        // Al cambiar bodega destino -> cargar sus subbodegas
        bodegaDestinoComboBox.addActionListener(e -> actualizarSubbodegasEn(bodegaDestinoComboBox, subbodegaDestinoComboBox));

        // --- Botón mover ---
        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2;
        JButton moverBtn = new JButton("Mover Producto");
        moverBtn.addActionListener(e -> {
            try {
                String nombreProducto = (String) productoComboBoxActualizar.getSelectedItem();
                if (nombreProducto == null || nombreProducto.isEmpty() || nombreProducto.startsWith("No hay")) {
                    throw new IllegalArgumentException("Seleccione un producto válido.");
                }

                int cantidad = Integer.parseInt(cantidadField.getText().trim());
                if (cantidad <= 0) throw new IllegalArgumentException("Ingrese una cantidad mayor a 0.");

                String bodegaOrigenNom    = (String) bodegaPrincipalComboBoxActualizar.getSelectedItem();
                String subbodegaOrigenNom = (String) subbodegaComboBoxActualizar.getSelectedItem();
                String bodegaDestinoNom   = (String) bodegaDestinoComboBox.getSelectedItem();
                String subbodegaDestinoNom= (String) subbodegaDestinoComboBox.getSelectedItem();

                if (bodegaOrigenNom == null || subbodegaOrigenNom == null ||
                        bodegaDestinoNom == null || subbodegaDestinoNom == null) {
                    throw new IllegalArgumentException("Complete bodega y subbodega de origen y destino.");
                }
                // Opcional: evitar mover al mismo lugar
                if (bodegaOrigenNom.equals(bodegaDestinoNom) && subbodegaOrigenNom.equals(subbodegaDestinoNom)) {
                    throw new IllegalArgumentException("El destino no puede ser igual al origen.");
                }

                // Resolver IDs por NOMBRE
                BodegaDAO bodegaDAO = new BodegaDAO();
                int idProducto       = productoDAO.obtenerIdProductoPorNombre(nombreProducto);
                int idBodegaOrigen   = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(bodegaOrigenNom);
                int idSubOrigen      = bodegaDAO.obtenerIdSubbodegaPorNombre(subbodegaOrigenNom);
                int idBodegaDestino  = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(bodegaDestinoNom);
                int idSubDestino     = bodegaDAO.obtenerIdSubbodegaPorNombre(subbodegaDestinoNom);

                // Mover
                productoDAO.moverProducto(idProducto, idBodegaOrigen, idSubOrigen,
                        idBodegaDestino, idSubDestino, cantidad);

                JOptionPane.showMessageDialog(this, "Producto movido exitosamente.");
                // Refrescos
                cargarDatosTabla();
                cargarOrigenesParaProducto(); // para que el origen se actualice según nueva distribución

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "La cantidad debe ser un número válido.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al mover producto: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(moverBtn, gbc);

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
                String nombreBodega = (String) bodegaComboBox.getSelectedItem();
                String nombreSubbodega = (String) subbodegaComboBox.getSelectedItem();

                if (productoSeleccionado == null || productoSeleccionado.isEmpty())
                    throw new IllegalArgumentException("Seleccione un producto.");
                if (nombreBodega == null || nombreBodega.isEmpty())
                    throw new IllegalArgumentException("Seleccione una bodega.");
                if (nombreSubbodega == null || nombreSubbodega.isEmpty()
                        || "No hay subbodegas disponibles".equalsIgnoreCase(nombreSubbodega))
                    throw new IllegalArgumentException("Seleccione una subbodega válida.");

                int cantidad = Integer.parseInt(cantidadField.getText().trim());
                if (cantidad <= 0) throw new IllegalArgumentException("Ingrese una cantidad mayor a 0.");

                //Resolver IDs por NOMBRE con el DAO (no usar mapas de la UI)
                BodegaDAO bodegaDAO = new BodegaDAO();
                int idBodega = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(nombreBodega);
                int idSubbodega = bodegaDAO.obtenerIdSubbodegaPorNombre(nombreSubbodega);

                int idProducto = productoDAO.obtenerIdProductoPorNombre(productoSeleccionado);

                // Ejecutar eliminación
                productoDAO.eliminarCantidadProducto(idProducto, idBodega, idSubbodega, cantidad);

                JOptionPane.showMessageDialog(this, "Producto eliminado correctamente.");

                // Refrescar tabla y combos
                cargarDatosTabla();
                if (productoComboBoxActualizar != null) cargarProductosEnComboBox(productoComboBoxActualizar);
                if (productoComboBoxEliminar   != null) cargarProductosEnComboBox(productoComboBoxEliminar);

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

        // 1) llenar bodegas
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBox);

        // 2) insertar opciones globales al inicio
        bodegaPrincipalComboBox.insertItemAt("Todas las bodegas", 0);
        bodegaPrincipalComboBox.setSelectedIndex(0);

        subbodegaComboBox.addItem("Todas las subbodegas");
        subbodegaComboBox.setSelectedIndex(0);

        // cuando cambia bodega, recargar subbodegas del filtro
        bodegaPrincipalComboBox.addActionListener((ActionEvent e) -> {
            String bodegaSeleccionada = (String) bodegaPrincipalComboBox.getSelectedItem();
            actualizarSubbodegasFiltro(bodegaSeleccionada);
        });

        // cuando cambia subbodega o presionas Enter en buscar → aplicar filtros
        subbodegaComboBox.addActionListener(e -> aplicarFiltros());
        buscarField.addActionListener(e -> aplicarFiltros());

        panel.add(new JLabel("Buscar:"));
        panel.add(buscarField);
        panel.add(new JLabel("Bodega:"));
        panel.add(bodegaPrincipalComboBox);
        panel.add(new JLabel("Subbodega:"));
        panel.add(subbodegaComboBox);

        refrescarButton = new JButton("Refrescar");
        refrescarButton.addActionListener(e -> aplicarFiltros());
        panel.add(refrescarButton);

        return panel;
    }

    private void aplicarFiltros() {
        try {
            String texto    = (buscarField != null) ? buscarField.getText() : "";
            String bodegaNom = (bodegaPrincipalComboBox != null) ? (String)bodegaPrincipalComboBox.getSelectedItem() : null;
            String subbNom   = (subbodegaComboBox != null) ? (String)subbodegaComboBox.getSelectedItem() : null;

            Integer idBodega = null, idSubbodega = null;
            BodegaDAO bodegaDAO = new BodegaDAO();

            if (bodegaNom != null && !"Todas las bodegas".equals(bodegaNom)) {
                idBodega = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(bodegaNom);
            }
            if (subbNom != null && !"Todas las subbodegas".equals(subbNom)) {
                idSubbodega = bodegaDAO.obtenerIdSubbodegaPorNombre(subbNom);
            }

            cargarDatosTablaConFiltros(texto, idBodega, idSubbodega);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al aplicar filtros: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarSubbodegas(String nombreBodegaSeleccionada) {
        try {
            subbodegaComboBox.removeAllItems();
            subbodegaComboBox.addItem("Todas las subbodegas");
            subbodegaMap.clear();

            if (nombreBodegaSeleccionada != null && !nombreBodegaSeleccionada.equals("Todas las bodegas")) {
                BodegaDAO bodegaDAO = new BodegaDAO();
                int idBodega = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(nombreBodegaSeleccionada);

                // tu DAO ya da nombre -> id
                Map<String, Integer> nombreAId = bodegaDAO.obtenerSubbodegasPorIdPrincipal(idBodega);
                for (Map.Entry<String, Integer> e : nombreAId.entrySet()) {
                    subbodegaComboBox.addItem(e.getKey());
                    subbodegaMap.put(e.getKey(), e.getValue()); // nombre -> id
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarDatosTabla() {
        setLoading(true);
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
                } finally {
                    setLoading(false);
                }
            }
        };
        worker.execute();
    }

    private void actualizarTabla(List<ProductoForm> productos) {
        DefaultTableModel model = (DefaultTableModel) inventarioTable.getModel();
        model.setRowCount(0); // Limpiar la tabla antes de actualizar

        for (ProductoForm producto : productos) {
            model.addRow(new Object[]{
                    producto.getId(),
                    producto.getNombre(),
                    producto.getCantidad(),
                    new SimpleDateFormat("yyyy-MM-dd").format(producto.getFechaIngreso()),
                    producto.getUbicacion(),
                    producto.getNombreBodegaPrincipal() != null ? producto.getNombreBodegaPrincipal() : "Desconocida",
                    producto.getNombreSubbodega() != null ? producto.getNombreSubbodega() : "Desconocida"
            });
        }

        inventarioTable.revalidate();
        inventarioTable.repaint();
    }

    private void cargarDatosTablaConFiltros(String textoBusqueda, Integer idBodega, Integer idSubbodega) {
        setLoading(true);
        try {
            List<ProductoForm> productos = productoDAO.buscarProductosPorFiltros(textoBusqueda, idBodega, idSubbodega);

            DefaultTableModel model = (DefaultTableModel) inventarioTable.getModel();
            model.setRowCount(0);

            for (ProductoForm p : productos) {
                model.addRow(new Object[]{
                        p.getId(),
                        p.getNombre(),
                        p.getCantidad(),
                        new SimpleDateFormat("yyyy-MM-dd").format(p.getFechaIngreso()),
                        p.getUbicacion(),
                        p.getNombreBodegaPrincipal() != null ? p.getNombreBodegaPrincipal() : "Desconocida",
                        p.getNombreSubbodega() != null ? p.getNombreSubbodega() : "Desconocida"
                });
            }
            inventarioTable.revalidate();
            inventarioTable.repaint();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar productos: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setLoading(false);
        }
    }

    private void cargarBodegasPrincipalesEnComboBox(JComboBox<String> comboBox) {
        try {
            comboBox.removeAllItems();
            bodegaPrincipalMap.clear();

            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> idANombre = bodegaDAO.obtenerBodegasPrincipalesConIds();

            for (Map.Entry<Integer, String> e : idANombre.entrySet()) {
                String nombre = e.getValue();
                Integer id    = e.getKey();
                bodegaPrincipalMap.put(nombre, id);  // nombre -> id
                comboBox.addItem(nombre);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error al cargar bodegas principales: " + e.getMessage(),
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
                    Integer id = entry.getKey();
                    String nombre = entry.getValue();
                    subbodegaMap.put(nombre, id);         // nombre -> id
                    comboBox.addItem(nombre);
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarProductosEnComboBox(JComboBox<String> combo) {
        // Guardar selección actual (si la hay)
        final String seleccionAnterior = (combo.getSelectedItem() instanceof String)
                ? (String) combo.getSelectedItem() : null;

        combo.setEnabled(false);            // evitar clicks mientras carga
        combo.setModel(new DefaultComboBoxModel<>(new String[]{"Cargando..."}));

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                // Consulta en segundo plano, sin bloquear EDT
                return productoDAO.obtenerNombresProductos(); // usa DISTINCT + ORDER BY en el DAO
            }

            @Override
            protected void done() {
                try {
                    List<String> nombres = get();

                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    if (nombres == null || nombres.isEmpty()) {
                        model.addElement("No hay productos disponibles");
                        combo.setModel(model);
                        combo.setEnabled(false);
                        return;
                    }

                    // Llenar de una sola vez
                    for (String n : nombres) model.addElement(n);
                    combo.setModel(model);
                    combo.setEnabled(true);

                    // Reaplicar selección si sigue existiendo
                    if (seleccionAnterior != null) {
                        model.setSelectedItem(seleccionAnterior);
                    }

                } catch (Exception ex) {
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    model.addElement("Error al cargar productos");
                    combo.setModel(model);
                    combo.setEnabled(false);
                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Error al cargar productos desde la base de datos: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private void actualizarSubbodegasEn(JComboBox<String> bodegaComboBox, JComboBox<String> subbodegaComboBox) {
        try {
            // limpiar UI y mapa
            subbodegaMap.clear();
            subbodegaComboBox.removeAllItems();

            String nombreBodega = (String) bodegaComboBox.getSelectedItem();
            if (nombreBodega == null || nombreBodega.isEmpty()) {
                subbodegaComboBox.addItem("No hay subbodegas disponibles");
                subbodegaComboBox.setEnabled(false);
                return;
            }

            // 1) crear DAO
            BodegaDAO bodegaDAO = new BodegaDAO();

            // 2) obtener el id de la bodega por NOMBRE (tu DAO lo tiene)
            int idBodega = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(nombreBodega);

            // 3) preferimos el método que ya retorna nombre->id
            Map<String, Integer> nombreAId = bodegaDAO.obtenerSubbodegasPorIdPrincipal(idBodega);

            if (nombreAId == null || nombreAId.isEmpty()) {
                subbodegaComboBox.addItem("No hay subbodegas disponibles");
                subbodegaComboBox.setEnabled(false);
            } else {
                subbodegaComboBox.setEnabled(true);
                for (Map.Entry<String, Integer> e : nombreAId.entrySet()) {
                    String nombreSub = e.getKey();
                    Integer idSub    = e.getValue();
                    subbodegaMap.put(nombreSub, idSub);     // nombre -> id
                    subbodegaComboBox.addItem(nombreSub);
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Error al actualizar subbodegas: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Integer obtenerIdDesdeComboBox(JComboBox<String> comboBox, Map<String, Integer> nombreAId) {
        String nombre = (String) comboBox.getSelectedItem();
        return (nombre == null) ? null : nombreAId.get(nombre);
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

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusBar.add(totalLabel);
        statusBar.add(new JLabel("  |  "));
        statusBar.add(syncLabel);
        statusBar.add(new JLabel("  |  Versión: v1.02"));
        return statusBar;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // ===== Archivo =====
        JMenu archivo = new JMenu("Archivo");
        exportCsvItem = new JMenuItem("Exportar CSV");
        exportCsvItem.setAccelerator(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_E,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() // Ctrl en Win/Linux, Cmd en macOS
        ));
        exportCsvItem.addActionListener(e -> exportarInventarioCSV());
        archivo.add(exportCsvItem);
        menuBar.add(archivo);

        // ===== Opciones (lo que ya tenías) =====
        JMenu menu = new JMenu("Opciones");
        JMenuItem gestionarBodegasItem = new JMenuItem("Gestionar Bodegas");
        gestionarBodegasItem.addActionListener(e -> abrirGestionBodegas());
        menu.add(gestionarBodegasItem);

        JMenuItem cerrarSesionItem = new JMenuItem("Cerrar Sesión");
        cerrarSesionItem.addActionListener(e -> cerrarSesion());
        menu.add(cerrarSesionItem);

        menuBar.add(menu);
        return menuBar;
    }


    private void abrirGestionBodegas() {
        GestionBodegasFrame frame = new GestionBodegasFrame(this);
        frame.agregarObservador(this);
        frame.setVisible(true);
    }

    private String obtenerNombreBodegaPrincipal(int idBodegaPrincipal) {
        for (Map.Entry<String, Integer> entry : bodegaPrincipalMap.entrySet()) {
            if (entry.getValue() == idBodegaPrincipal) {
                return entry.getKey();
            }
        }
        return "Desconocida";
    }

    private String obtenerNombreSubbodega(int idSubbodega) {
        for (Map.Entry<String, Integer> entry : subbodegaMap.entrySet()) {
            if (entry.getValue() == idSubbodega) {
                return entry.getKey();
            }
        }
        return "Desconocida";
    }

    private void actualizarSubbodegasFiltro(String nombreBodegaSeleccionada) {
        try {
            subbodegaComboBox.removeAllItems();
            subbodegaComboBox.addItem("Todas las subbodegas");
            subbodegaComboBox.setSelectedIndex(0);

            if (nombreBodegaSeleccionada != null && !nombreBodegaSeleccionada.equals("Todas las bodegas")) {
                BodegaDAO bodegaDAO = new BodegaDAO();
                int idBodega = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(nombreBodegaSeleccionada);

                // nombre -> id
                Map<String, Integer> nombreAId = bodegaDAO.obtenerSubbodegasPorIdPrincipal(idBodega);
                for (String nombreSub : nombreAId.keySet()) {
                    subbodegaComboBox.addItem(nombreSub);
                }
            }
            // aplicar filtro cada vez que se cambia la bodega
            aplicarFiltros();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarOrigenesParaProducto() {
        String producto = (String) productoComboBoxActualizar.getSelectedItem();
        if (producto == null || producto.isEmpty() || producto.startsWith("No hay")) {
            limpiarOrigenes();
            return;
        }

        bloqueaEventosOrigen = true;
        try {
            limpiarOrigenes(); // asegura limpio + bandera

            java.util.List<String[]> ubicaciones = productoDAO.obtenerUbicacionesDeProducto(producto);

            if (ubicaciones == null || ubicaciones.isEmpty()) {
                return;
            }

            for (String[] par : ubicaciones) {
                String bodega = par[0];
                String subbod = par[1];
                subbodegasPorBodegaOrigen
                        .computeIfAbsent(bodega, k -> new ArrayList<>())
                        .add(subbod);
            }

            for (String bodega : subbodegasPorBodegaOrigen.keySet()) {
                bodegaPrincipalComboBoxActualizar.addItem(bodega);
            }
            bodegaPrincipalComboBoxActualizar.setEnabled(true);

            if (bodegaPrincipalComboBoxActualizar.getItemCount() > 0) {
                bodegaPrincipalComboBoxActualizar.setSelectedIndex(0);
                cargarSubbodegasOrigenFiltradas();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar orígenes: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            bloqueaEventosOrigen = false;
        }
    }

    private void cargarSubbodegasOrigenFiltradas() {
        if (bloqueaEventosOrigen) return;

        String bodegaSel = (String) bodegaPrincipalComboBoxActualizar.getSelectedItem();

        bloqueaEventosOrigen = true;
        try {
            subbodegaComboBoxActualizar.removeAllItems();

            if (bodegaSel == null || bodegaSel.isEmpty()) {
                subbodegaComboBoxActualizar.setEnabled(false);
                return;
            }

            java.util.List<String> lista = subbodegasPorBodegaOrigen.get(bodegaSel);
            if (lista == null || lista.isEmpty()) {
                subbodegaComboBoxActualizar.setEnabled(false);
                return;
            }

            for (String nombre : lista) {
                subbodegaComboBoxActualizar.addItem(nombre);
            }
            subbodegaComboBoxActualizar.setEnabled(true);
            subbodegaComboBoxActualizar.setSelectedIndex(0);
        } finally {
            bloqueaEventosOrigen = false;
        }
    }

    private void limpiarOrigenes() {
        bloqueaEventosOrigen = true;
        try {
            subbodegasPorBodegaOrigen.clear();

            if (bodegaPrincipalComboBoxActualizar != null) {
                bodegaPrincipalComboBoxActualizar.removeAllItems();
                bodegaPrincipalComboBoxActualizar.setEnabled(false);
            }
            if (subbodegaComboBoxActualizar != null) {
                subbodegaComboBoxActualizar.removeAllItems();
                subbodegaComboBoxActualizar.setEnabled(false);
            }
        } finally {
            bloqueaEventosOrigen = false;
        }
    }

    // === TOOLBAR SUPERIOR ===
    private JToolBar createToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        // Eliminar (si ya tienes acción real, conéctala aquí)
        tb.add(new JButton(new AbstractAction("Eliminar") {
            @Override public void actionPerformed(ActionEvent e) {
                // tabbedPane.setSelectedIndex(3); // si quieres saltar a "Eliminar Producto"
            }
        }));

        tb.addSeparator();

        // Exportar CSV
        tb.add(new JButton(new AbstractAction("Exportar CSV") {
            @Override public void actionPerformed(ActionEvent e) {
                exportarCSV();
            }
        }));

        // Refrescar
        tb.add(new JButton(new AbstractAction("Refrescar") {
            @Override public void actionPerformed(ActionEvent e) {
                conCargando(MainFrame.this::aplicarFiltros);
                syncLabel.setText("Última actualización: " +
                        java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            }
        }));

        tb.add(Box.createHorizontalGlue());
        tb.add(syncLabel);

        return tb;
    }

    private void registrarAtajos() {
        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "refrescar");
        rp.getActionMap().put("refrescar",
                new AbstractAction(){ public void actionPerformed(ActionEvent e){ aplicarFiltros(); }});

        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control F"), "focusBuscar");
        rp.getActionMap().put("focusBuscar",
                new AbstractAction(){ public void actionPerformed(ActionEvent e){ buscarField.requestFocusInWindow(); }});
    }

    private void conCargando(Runnable r) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try { r.run(); } finally { setCursor(Cursor.getDefaultCursor()); }
    }

    private void exportarCSV() {
        if (inventarioTable == null || inventarioTable.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No hay datos para exportar.", "Aviso",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Nombre sugerido
        String sugerido = "inventario_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date()) + ".csv";

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(sugerido));
        int res = chooser.showSaveDialog(this);
        if (res != javax.swing.JFileChooser.APPROVE_OPTION) return;

        java.io.File file = chooser.getSelectedFile();
        // asegurar extensión .csv
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new java.io.File(file.getParentFile(), file.getName() + ".csv");
        }

        final String sep = detectarSeparadorCSV(); // ";" o ","

        setLoading(true);
        try (java.io.BufferedWriter bw = java.nio.file.Files.newBufferedWriter(
                file.toPath(),
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

            // Escribir BOM UTF-8 para que Excel respete acentos
            bw.write('\uFEFF');

            javax.swing.table.TableModel model = inventarioTable.getModel();

            // Encabezados
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (c > 0) bw.write(sep);
                bw.write(escapeCsv(String.valueOf(model.getColumnName(c)), sep));
            }
            bw.write("\r\n");

            // Filas
            int rowCount = model.getRowCount();
            int colCount = model.getColumnCount();
            for (int r = 0; r < rowCount; r++) {
                for (int c = 0; c < colCount; c++) {
                    if (c > 0) bw.write(sep);
                    Object val = model.getValueAt(r, c);
                    bw.write(escapeCsv(val == null ? "" : String.valueOf(val), sep));
                }
                bw.write("\r\n");
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setLoading(false);
        }

        JOptionPane.showMessageDialog(this, "Inventario exportado correctamente.",
                "Éxito", JOptionPane.INFORMATION_MESSAGE);
    }

    private void initLoadingOverlay() {
        loadingGlass = new JPanel(new GridBagLayout());
        loadingGlass.setOpaque(false);

        JPanel badge = new JPanel(new GridBagLayout());
        badge.setBackground(new Color(0,0,0,140));
        badge.setBorder(BorderFactory.createEmptyBorder(16,24,16,24));

        loadingLabel = new JLabel("Cargando…");
        loadingLabel.setForeground(Color.WHITE);
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(Font.BOLD, 14f));
        badge.add(loadingLabel);

        loadingGlass.add(badge, new GridBagConstraints());
        // La glassPane existe en JFrame; la configuramos pero la dejamos oculta
        setGlassPane(loadingGlass);
    }

    private void setLoading(boolean show) {
        // Si aún no se construyó el overlay, no hagas nada para evitar NPE
        if (loadingGlass == null) return;

        loadingGlass.setVisible(show);

        // opcional: deshabilita el botón "Refrescar" mientras carga
        if (refrescarButton != null) {
            refrescarButton.setEnabled(!show);
        }
        loadingGlass.revalidate();
        loadingGlass.repaint();
    }

    private void exportarInventarioCSV() {
        if (inventarioTable == null || inventarioTable.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No hay datos para exportar.", "Aviso",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // nombre sugerido
        String sugerido = "inventario_" +
                new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date()) + ".csv";

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(sugerido));
        int res = chooser.showSaveDialog(this);
        if (res != javax.swing.JFileChooser.APPROVE_OPTION) return;

        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new java.io.File(file.getParentFile(), file.getName() + ".csv");
        }

        final String sep = detectarSeparadorCSV(); // "," o ";"

        setLoading(true);
        try (java.io.BufferedWriter bw = java.nio.file.Files.newBufferedWriter(
                file.toPath(),
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

            // BOM UTF-8 para Excel
            bw.write('\uFEFF');

            javax.swing.table.TableModel model = inventarioTable.getModel();

            // Encabezados
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (c > 0) bw.write(sep); // <<<<< AQUÍ va el separador, no 'c'
                bw.write(escapeCsv(String.valueOf(model.getColumnName(c)), sep));
            }
            bw.write("\r\n");

            // Filas visibles (respeta sort/filtros)
            int rowCount = inventarioTable.getRowCount();
            for (int r = 0; r < rowCount; r++) {
                int modelRow = inventarioTable.convertRowIndexToModel(r);
                for (int c = 0; c < model.getColumnCount(); c++) {
                    if (c > 0) bw.write(sep); // <<<<< separador correcto
                    Object val = model.getValueAt(modelRow, c);
                    bw.write(escapeCsv(val == null ? "" : String.valueOf(val), sep));
                }
                bw.write("\r\n");
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar CSV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setLoading(false);
        }

        JOptionPane.showMessageDialog(this, "Inventario exportado correctamente.",
                "Éxito", JOptionPane.INFORMATION_MESSAGE);
    }

    private String escapeCsv(String s, String sep) {
        if (s == null) return "";
        boolean necesitaComillas = s.contains(sep) || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String out = s.replace("\"", "\"\"");
        return necesitaComillas ? "\"" + out + "\"" : out;
    }

    private void buildLoadingOverlay(JComponent container) {
        // El contenedor donde va la tabla usará OverlayLayout para apilar la "glass"
        container.setLayout(new OverlayLayout(container));

        JPanel glass = new JPanel(new GridBagLayout());
        glass.setOpaque(true);
        // blanco con transparencia
        glass.setBackground(new Color(255, 255, 255, 180));

        JLabel lbl = new JLabel("Actualizando...");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        glass.add(lbl);

        glass.setVisible(false); // oculto por defecto

        // Guarda referencias en los fields de la clase
        this.loadingGlass = glass;
        this.loadingLabel = lbl;

        // Añade el overlay por encima
        container.add(glass);
    }

    private String detectarSeparadorCSV() {
        char decimal = java.text.DecimalFormatSymbols.getInstance().getDecimalSeparator();
        return (decimal == ',') ? ";" : ",";
    }

}
