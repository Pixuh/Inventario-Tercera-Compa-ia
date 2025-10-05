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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.util.stream.Collectors;
import javax.swing.border.TitledBorder;
import java.util.Date;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

public class MainFrame extends JFrame implements BodegaObserver {

    private final Map<String, java.util.List<String>> subbodegasPorBodegaOrigen = new HashMap<>();
    private boolean bloqueaEventosOrigen = false;

    private final ProductoDAO productoDAO = new ProductoDAO();
    private Map<String, Integer> bodegaPrincipalMap = new LinkedHashMap<>();
    private Map<String, Integer> subbodegaMap       = new LinkedHashMap<>();

    private JTable inventarioTable;
    private JTextField buscarField;

    private final JLabel syncLabel = new JLabel("Listo");

    private static final int COL_ID = 0, COL_NOMBRE = 1, COL_CANT = 2, COL_FECHA = 3, COL_UBIC = 4, COL_BOD = 5, COL_SUB = 6;
    private static final int LOW_STOCK = 1;

    private JComboBox<String> bodegaPrincipalComboBox, subbodegaComboBox;
    private JComboBox<String> bodegaPrincipalComboBoxActualizar;
    private JComboBox<String> subbodegaComboBoxActualizar;
    private JComboBox<String> bodegaPrincipalComboBoxEliminar;
    private JComboBox<String> subbodegaComboBoxEliminar;
    private JComboBox<String> bodegaDestinoComboBox;
    private JComboBox<String> subbodegaDestinoComboBox;
    private JComboBox<String> productoComboBoxActualizar;
    private JComboBox<String> productoComboBoxEliminar;

    // overlay de carga + botón refrescar como field ---
    private JPanel loadingGlass;
    private JLabel loadingLabel;
    private JButton refrescarButton;  // antes era local en createFiltroPanel

    // Paso 10: exportación CSV
    private JMenuItem exportCsvItem;

    // STATUS & UX
    private JLabel totalLabel = new JLabel("Total: 0");
    private Timer buscarDebounce;                   // debounce de búsqueda

    // === EPP: servicios simples ===
    private final EppService EppService = new EppService();
    private final EppTipoDAO EppTipoDAO = new EppTipoDAO();

    // ==== EPP UI ====
    private JTable eppTable;
    private DefaultTableModel eppModel;
    private JTable eppMovTable;
    private DefaultTableModel eppMovModel;

    private JTextField eppBuscarTxt;
    private JComboBox<String> eppBodegaCbo;
    private JComboBox<String> eppSubbodegaCbo;
    private JComboBox<String> eppTipoCbo;
    private JButton eppBuscarBtn;

    private JLabel eppSelLbl; // muestra info del EPP seleccionado

    private JComboBox<String> eppMoverBodDestCbo;
    private JComboBox<String> eppMoverSubDestCbo;
    private JTextField eppMotivoTxt;
    private JButton eppMoverBtn;

    // mapas para IDs
    private Map<String,Integer> eppBodegaNombreToId = new LinkedHashMap<>();
    private Map<String,Integer> eppSubbodegaNombreToId = new LinkedHashMap<>();
    private Map<String,Integer> eppTipoNombreToId = new LinkedHashMap<>();

    private JComboBox<ProductoUbicacion> productoCombo; // en vez de JComboBox<String>
    private JLabel stockLabel;                           // para mostrar disponibilidad

    public MainFrame() {
        setTitle("Inventario Tercera Compañía San Pedro de la Paz");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initComponents();
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

        tabbedPane.addTab("EPP", createEppTab());

        return tabbedPane;
    }

    private JPanel createVerInventarioPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Modelo NO editable
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"ID", "Nombre", "Cantidad", "Fecha Ingreso", "Ubicación", "Bodega Principal", "Subbodega"}, 0
        ) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return switch (col) {
                    case COL_ID, COL_CANT -> Integer.class;
                    case COL_FECHA -> java.util.Date.class;
                    default -> String.class;
                };
            }
        };

        // Tabla
        inventarioTable = new JTable(model);
        inventarioTable.setRowHeight(22);
        inventarioTable.setFillsViewportHeight(true);
        inventarioTable.setAutoCreateRowSorter(true);

        // Renderers
        inventarioTable.getColumnModel().getColumn(COL_ID).setCellRenderer(new CenterRenderer());
        inventarioTable.getColumnModel().getColumn(COL_CANT).setCellRenderer(new QuantityRenderer());
        inventarioTable.getColumnModel().getColumn(COL_FECHA).setCellRenderer(new DateRenderer());

        // Anchuras cómodas
        int[] widths = {50, 220, 80, 110, 140, 150, 120};
        for (int i = 0; i < widths.length; i++) {
            inventarioTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Sorters adecuados (por fecha y por número)
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        sorter.setComparator(COL_FECHA, (a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return -1;
            if (b == null) return 1;
            return ((Date) a).compareTo((Date) b);
        });
        sorter.setComparator(COL_ID, (a,b) -> Integer.compare((Integer)a,(Integer)b));
        sorter.setComparator(COL_CANT, (a,b) -> Integer.compare((Integer)a,(Integer)b));
        inventarioTable.setRowSorter(sorter);

        // Tooltips ricos por fila
        inventarioTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = inventarioTable.rowAtPoint(e.getPoint());
                if (row < 0) { inventarioTable.setToolTipText(null); return; }
                int mr = inventarioTable.convertRowIndexToModel(row);
                String ubic = String.valueOf(model.getValueAt(mr, COL_UBIC));
                String bod  = String.valueOf(model.getValueAt(mr, COL_BOD));
                String sub  = String.valueOf(model.getValueAt(mr, COL_SUB));
                inventarioTable.setToolTipText(
                        "<html><b>Ubicación:</b> " + escapeHtml(ubic) +
                                "<br><b>Bodega:</b> " + escapeHtml(bod) +
                                "<br><b>Subbodega:</b> " + escapeHtml(sub) + "</html>");
            }
        });

        // Centro con overlay
        JPanel center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(inventarioTable), BorderLayout.CENTER);

        panel.add(createFiltroPanel(), BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);

        // Overlay de carga (ya lo usas en otras partes)
        buildLoadingOverlay(center);

        // Menú contextual (copiar y columnas)
        installColumnVisibilityMenu(inventarioTable);

        // Carga inicial
        cargarDatosTabla();

        return panel;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private JPanel createAgregarProductoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 10, 8, 10);
        int row = 0;

        // ===== Campos básicos =====
        JTextField nombreField    = new JTextField(24);
        JTextField cantidadField  = new JTextField(10);
        JTextField ubicacionField = new JTextField(20);

        JSpinner ingresoSpin = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        ingresoSpin.setEditor(new JSpinner.DateEditor(ingresoSpin, "yyyy-MM-dd"));

        JComboBox<String> bodegaCbo    = new JComboBox<>();
        JComboBox<String> subbodegaCbo = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaCbo);
        bodegaCbo.addActionListener(e -> actualizarSubbodegasEn(bodegaCbo, subbodegaCbo));

        // ===== Opcionales (metadatos) =====
        JTextField marcaField  = new JTextField(20);
        JTextField valorField  = new JTextField(12);     // valor_referencial (BigDecimal)

        JCheckBox aplicaVencChk = new JCheckBox("Aplica vencimiento");
        JSpinner vencSpin = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        vencSpin.setEditor(new JSpinner.DateEditor(vencSpin, "yyyy-MM-dd"));
        vencSpin.setEnabled(false);
        aplicaVencChk.addActionListener(e -> vencSpin.setEnabled(aplicaVencChk.isSelected()));

        JCheckBox reqMantChk = new JCheckBox("Requiere mantención");
        JTextField frecMantField = new JTextField(6);    // meses (Integer)
        frecMantField.setEnabled(false);
        JSpinner proxMantSpin = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        proxMantSpin.setEditor(new JSpinner.DateEditor(proxMantSpin, "yyyy-MM-dd"));
        proxMantSpin.setEnabled(false);
        reqMantChk.addActionListener(e -> {
            boolean on = reqMantChk.isSelected();
            frecMantField.setEnabled(on);
            proxMantSpin.setEnabled(on);
        });

        JTextArea obsArea = new JTextArea(3, 24);
        obsArea.setLineWrap(true);
        obsArea.setWrapStyleWord(true);
        JScrollPane obsScroll = new JScrollPane(obsArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JCheckBox eppChk   = new JCheckBox("Es EPP");
        JCheckBox serChk   = new JCheckBox("Es serializable");

        // ===== Layout =====
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1; panel.add(nombreField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1; panel.add(cantidadField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Fecha de Ingreso:"), gbc);
        gbc.gridx = 1; panel.add(ingresoSpin, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Ubicación:"), gbc);
        gbc.gridx = 1; panel.add(ubicacionField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Bodega Principal:"), gbc);
        gbc.gridx = 1; panel.add(bodegaCbo, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Subbodega:"), gbc);
        gbc.gridx = 1; panel.add(subbodegaCbo, gbc); row++;

        // ——— Opcionales
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Marca (opcional):"), gbc);
        gbc.gridx = 1; panel.add(marcaField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Valor referencial (opcional):"), gbc);
        gbc.gridx = 1; panel.add(valorField, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(aplicaVencChk, gbc);
        gbc.gridx = 1; panel.add(vencSpin, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(reqMantChk, gbc);
        JPanel mantPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        mantPanel.add(new JLabel("Frecuencia (meses):"));
        mantPanel.add(frecMantField);
        mantPanel.add(new JLabel("Próxima mantención:"));
        mantPanel.add(proxMantSpin);
        gbc.gridx = 1; panel.add(mantPanel, gbc); row++;

        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Observación (opcional):"), gbc);
        gbc.gridx = 1; panel.add(obsScroll, gbc); row++;

        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        flags.add(eppChk);
        flags.add(serChk);
        gbc.gridx = 0; gbc.gridy = row; panel.add(new JLabel("Flags:"), gbc);
        gbc.gridx = 1; panel.add(flags, gbc); row++;

        // ===== Botón =====
        JButton agregarButton = new JButton("Agregar Producto");
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        panel.add(agregarButton, gbc);

        // ===== Acción del botón =====
        agregarButton.addActionListener(e -> {
            try {
                // Validaciones básicas
                String nombre = nombreField.getText().trim();
                if (nombre.isEmpty()) throw new IllegalArgumentException("Ingrese un nombre de producto.");

                int cantidad;
                try {
                    cantidad = Integer.parseInt(cantidadField.getText().trim());
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("La cantidad debe ser un número entero.");
                }
                if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");

                String bodNom = (String) bodegaCbo.getSelectedItem();
                String subNom = (String) subbodegaCbo.getSelectedItem();
                if (bodNom == null || bodNom.isEmpty()) throw new IllegalArgumentException("Seleccione una bodega.");
                if (subNom == null || subNom.isEmpty() || "No hay subbodegas disponibles".equalsIgnoreCase(subNom))
                    throw new IllegalArgumentException("Seleccione una subbodega válida.");

                // Resolver IDs por nombre (vía BodegaDAO)
                BodegaDAO bodegaDAO = new BodegaDAO();
                int idBodega = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(bodNom);
                int idSub    = bodegaDAO.obtenerIdSubbodegaPorNombre(subNom);

                // Construir ProductoForm (básicos)
                ProductoForm p = new ProductoForm(
                        0,
                        nombre,
                        cantidad,
                        getDateFromSpinner(ingresoSpin),
                        ubicacionField.getText().trim(),
                        idBodega,
                        idSub
                );

                // ===== Asignar opcionales =====
                p.setMarca(marcaField.getText().trim().isEmpty() ? null : marcaField.getText().trim());
                p.setValor(parseNullableBig(valorField.getText()));                // -> valor_referencial
                p.setObservacion(obsArea.getText().trim().isEmpty() ? null : obsArea.getText().trim());

                if (aplicaVencChk.isSelected()) {
                    p.setFechaVencimiento(getDateFromSpinner(vencSpin));           // DAO setea aplica_vencimiento=true si hay fecha
                } else {
                    p.setFechaVencimiento(null);
                }

                if (reqMantChk.isSelected()) {
                    p.setRequiereMantencion(Boolean.TRUE);
                    p.setFrecuenciaMantencionMeses(parseNullableInt(frecMantField.getText()));
                    p.setProximaMantencion(getDateFromSpinner(proxMantSpin));
                } else {
                    p.setRequiereMantencion(Boolean.FALSE);
                    p.setFrecuenciaMantencionMeses(null);
                    p.setProximaMantencion(null);
                }

                p.setEsEpp(eppChk.isSelected());
                p.setEsSerializable(serChk.isSelected());

                // Guardar
                productoDAO.agregarProducto(p);

                JOptionPane.showMessageDialog(this, "Producto agregado correctamente.");
                cargarDatosTabla();                   // refresca la grilla
                // si usas combos en otras pestañas:
                if (productoComboBoxActualizar != null) cargarProductosEnComboBox(productoComboBoxActualizar);
                if (productoComboBoxEliminar   != null) cargarProductosEnComboBox(productoComboBoxEliminar);

                // Limpiar formulario
                nombreField.setText("");
                cantidadField.setText("");
                ubicacionField.setText("");
                marcaField.setText("");
                valorField.setText("");
                obsArea.setText("");
                aplicaVencChk.setSelected(false);
                vencSpin.setEnabled(false);
                reqMantChk.setSelected(false);
                frecMantField.setText("");
                frecMantField.setEnabled(false);
                proxMantSpin.setEnabled(false);
                eppChk.setSelected(false);
                serChk.setSelected(false);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error al agregar producto: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

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

        // === Controles ===
        productoComboBoxEliminar = new JComboBox<>();
        JTextField cantidadField = new JTextField(20);

        bodegaPrincipalComboBoxEliminar = new JComboBox<>();
        subbodegaComboBoxEliminar = new JComboBox<>();

        // Llenar bodegas y encadenar subbodegas
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxEliminar);
        bodegaPrincipalComboBoxEliminar.addActionListener(e -> {
            actualizarSubbodegasEn(bodegaPrincipalComboBoxEliminar, subbodegaComboBoxEliminar);
            recargarProductosParaEliminar(); // << carga productos según nueva subbodega
        });

        // Cuando cambia subbodega, recargamos productos
        subbodegaComboBoxEliminar.addActionListener(e -> recargarProductosParaEliminar());

        // Botón eliminar
        JButton eliminarButton = new JButton("Eliminar");
        eliminarButton.addActionListener(e -> {
            try {
                String productoNombre = (String) productoComboBoxEliminar.getSelectedItem();
                String nombreBodega   = (String) bodegaPrincipalComboBoxEliminar.getSelectedItem();
                String nombreSub      = (String) subbodegaComboBoxEliminar.getSelectedItem();

                if (productoNombre == null || productoNombre.startsWith("No hay"))
                    throw new IllegalArgumentException("Seleccione un producto.");
                if (nombreBodega == null || nombreSub == null ||
                        "No hay subbodegas disponibles".equalsIgnoreCase(nombreSub))
                    throw new IllegalArgumentException("Seleccione bodega y subbodega válidas.");

                int cant = Integer.parseInt(cantidadField.getText().trim());
                if (cant <= 0) throw new IllegalArgumentException("Ingrese una cantidad mayor a 0.");

                BodegaDAO bdao = new BodegaDAO();
                int idBod = bdao.obtenerIdBodegaPrincipalPorNombre(nombreBodega);
                int idSub = bdao.obtenerIdSubbodegaPorNombre(nombreSub);

                int idProducto = productoDAO.obtenerIdProductoPorNombre(productoNombre);

                // Ejecutar eliminación
                productoDAO.eliminarCantidadProducto(idProducto, idBod, idSub, cant);

                JOptionPane.showMessageDialog(this, "Producto eliminado correctamente.");

                // Refrescar tabla principal
                cargarDatosTabla();
                // Volver a cargar el combo con el stock restante
                recargarProductosParaEliminar();

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "La cantidad debe ser numérica.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al eliminar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // === Layout ===
        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Producto:"), gbc);
        gbc.gridx = 1; panel.add(productoComboBoxEliminar, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx = 1; panel.add(cantidadField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Bodega:"), gbc);
        gbc.gridx = 1; panel.add(bodegaPrincipalComboBoxEliminar, gbc);

        gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Subbodega:"), gbc);
        gbc.gridx = 1; panel.add(subbodegaComboBoxEliminar, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; panel.add(eliminarButton, gbc);

        // Estado inicial: selecionar primera bodega -> subbodega -> cargar productos
        if (bodegaPrincipalComboBoxEliminar.getItemCount() > 0) {
            bodegaPrincipalComboBoxEliminar.setSelectedIndex(0);
            actualizarSubbodegasEn(bodegaPrincipalComboBoxEliminar, subbodegaComboBoxEliminar);
            recargarProductosParaEliminar();
        }

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
        model.setRowCount(0);

        long total = 0;
        for (ProductoForm p : productos) {
            // p.getCantidad() es int, no necesita null-check
            total += p.getCantidad();

            // IMPORTANTE: la columna de fecha recibe un java.util.Date real
            model.addRow(new Object[]{
                    p.getId(),
                    p.getNombre(),
                    p.getCantidad(),
                    p.getFechaIngreso(), // Date
                    p.getUbicacion(),
                    (p.getNombreBodegaPrincipal() != null ? p.getNombreBodegaPrincipal() : "Desconocida"),
                    (p.getNombreSubbodega() != null ? p.getNombreSubbodega() : "Desconocida")
            });
        }

        totalLabel.setText("Total: " + total);
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

            BodegaDAO bodegaDAO = new BodegaDAO();

            int idBodega = bodegaDAO.obtenerIdBodegaPrincipalPorNombre(nombreBodega);

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
        statusBar.add(new JLabel("  |  Versión: v1.03"));
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

        // === EPP: Abrir gestión y Crear ítems ===
        tb.addSeparator();
        tb.add(new JButton(new AbstractAction("Abrir EPP…") {
            @Override public void actionPerformed(ActionEvent e) {
                Integer idProd = getProductoSeleccionadoId();
                if (idProd == null) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Seleccione un producto en la tabla.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (!isProductoEpp(idProd)) {
                    JOptionPane.showMessageDialog(MainFrame.this, "El producto seleccionado no está marcado como EPP.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                abrirGestionEpp(idProd);
            }
        }));
        tb.add(new JButton(new AbstractAction("Crear ítems…") {
            @Override public void actionPerformed(ActionEvent e) {
                Integer idProd = getProductoSeleccionadoId();
                if (idProd == null) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Seleccione un producto en la tabla.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (!isProductoEpp(idProd)) {
                    JOptionPane.showMessageDialog(MainFrame.this, "El producto seleccionado no está marcado como EPP.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                mostrarDialogoCrearItems(idProd);
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

    private Integer getProductoSeleccionadoId() {
        if (inventarioTable == null) return null;
        int viewRow = inventarioTable.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = inventarioTable.convertRowIndexToModel(viewRow);
        Object val = inventarioTable.getModel().getValueAt(modelRow, 0);
        if (val == null) return null;
        try { return Integer.parseInt(String.valueOf(val)); } catch (NumberFormatException e) { return null; }
    }

    private boolean isProductoEpp(int idProducto) {
        String sql = "SELECT es_epp FROM producto WHERE idproducto = ?";
        try (java.sql.Connection con = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean(1);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo verificar si el producto es EPP: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    private void abrirGestionEpp(int idProducto) {
        try {
            EppFrame dlg = new EppFrame(this, idProducto, EppService); // << usa la instancia
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo abrir la gestión EPP: " + t.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void mostrarDialogoCrearItems(int idProducto) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int r=0;
        JTextField prefijoField = new JTextField("EPP", 12);
        JSpinner cantidadSpin = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
        JTextField tallaField = new JTextField("", 8);

        JComboBox<String> tipoCombo = new JComboBox<>();
        JComboBox<String> subtipoCombo = new JComboBox<>();

        JComboBox<String> bodegaCombo = new JComboBox<>();
        JComboBox<String> subbodegaCombo = new JComboBox<>();

        // Cargar tipos/subtipos desde epp_tipo
        Map<String, java.util.List<String>> mapaTipoSub = cargarMapaTipoSubtipo();
        // llenar tipo
        for (String t : mapaTipoSub.keySet()) tipoCombo.addItem(t);
        tipoCombo.addActionListener(ev -> {
            subtipoCombo.removeAllItems();
            String tSel = (String) tipoCombo.getSelectedItem();
            if (tSel != null) {
                for (String s : mapaTipoSub.getOrDefault(tSel, java.util.Collections.emptyList())) {
                    subtipoCombo.addItem(s);
                }
            }
        });
        if (tipoCombo.getItemCount()>0) tipoCombo.setSelectedIndex(0);

        // Cargar bodegas y subbodegas
        cargarBodegasPrincipalesEnComboBox(bodegaCombo);
        bodegaCombo.addActionListener(ev -> actualizarSubbodegasEn(bodegaCombo, subbodegaCombo));
        if (bodegaCombo.getItemCount()>0) {
            bodegaCombo.setSelectedIndex(0);
            actualizarSubbodegasEn(bodegaCombo, subbodegaCombo);
        }

        // Layout
        gbc.gridx=0; gbc.gridy=r; panel.add(new JLabel("Prefijo código:"), gbc);
        gbc.gridx=1; panel.add(prefijoField, gbc); r++;

        gbc.gridx=0; gbc.gridy=r; panel.add(new JLabel("Cantidad:"), gbc);
        gbc.gridx=1; panel.add(cantidadSpin, gbc); r++;

        gbc.gridx=0; gbc.gridy=r; panel.add(new JLabel("Talla (opcional):"), gbc);
        gbc.gridx=1; panel.add(tallaField, gbc); r++;

        gbc.gridx=0; gbc.gridy=r; panel.add(new JLabel("Tipo EPP:"), gbc);
        gbc.gridx=1; panel.add(tipoCombo, gbc); r++;

        gbc.gridx=0; gbc.gridy=r; panel.add(new JLabel("Subtipo:"), gbc);
        gbc.gridx=1; panel.add(subtipoCombo, gbc); r++;

        gbc.gridx=0; gbc.gridy=r; panel.add(new JLabel("Bodega:"), gbc);
        gbc.gridx=1; panel.add(bodegaCombo, gbc); r++;

        gbc.gridx=0; gbc.gridy=r; panel.add(new JLabel("Subbodega:"), gbc);
        gbc.gridx=1; panel.add(subbodegaCombo, gbc); r++;

        int op = JOptionPane.showConfirmDialog(this, panel, "Crear ítems EPP", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (op != JOptionPane.OK_OPTION) return;

        // Validaciones y ejecución
        try {
            String prefijo = prefijoField.getText().trim();
            if (prefijo.isEmpty()) prefijo = "EPP";
            int cant = (Integer) cantidadSpin.getValue();

            String tipoSel = (String) tipoCombo.getSelectedItem();
            String subtipoSel = (String) subtipoCombo.getSelectedItem();
            if (tipoSel == null || subtipoSel == null) throw new IllegalArgumentException("Seleccione tipo y subtipo.");

            // Resolver id_tipo
            Integer idTipo = EppTipoDAO.getIdTipo(tipoSel, subtipoSel);
            if (idTipo == null) throw new IllegalStateException("No se encontró el tipo/subtipo seleccionado en epp_tipo.");

            // Resolver bodegas por nombre
            BodegaDAO bdao = new BodegaDAO();
            String bNom  = (String) bodegaCombo.getSelectedItem();
            String sNom  = (String) subbodegaCombo.getSelectedItem();
            if (bNom == null || sNom == null || sNom.equalsIgnoreCase("No hay subbodegas disponibles"))
                throw new IllegalArgumentException("Seleccione bodega y subbodega válidas.");

            int idBod = bdao.obtenerIdBodegaPrincipalPorNombre(bNom);
            int idSub = bdao.obtenerIdSubbodegaPorNombre(sNom);

            // Crear lote
            int creados = EppService.crearLote(idProducto, idTipo, prefijo, cant, tallaField.getText().trim(), idBod, idSub);
            JOptionPane.showMessageDialog(this, "Se crearon " + creados + " ítem(s) EPP.", "Éxito", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al crear ítems: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Map<String, java.util.List<String>> cargarMapaTipoSubtipo() {
        Map<String, java.util.List<String>> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT tipo, subtipo FROM epp_tipo ORDER BY tipo, subtipo";
        try (java.sql.Connection con = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String tipo = rs.getString(1);
                String sub  = rs.getString(2);
                map.computeIfAbsent(tipo, k -> new java.util.ArrayList<>()).add(sub);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar tipos EPP: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return map;
    }

    private JPanel createEppTab() {
        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        // ======= FILTROS (arriba) =======
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        eppBuscarTxt = new JTextField(18);
        eppBodegaCbo = new JComboBox<>();
        eppSubbodegaCbo = new JComboBox<>();
        eppTipoCbo = new JComboBox<>();
        eppBuscarBtn = new JButton("Buscar");

        filtros.add(new JLabel("Texto:"));
        filtros.add(eppBuscarTxt);

        filtros.add(new JLabel("Bodega:"));
        filtros.add(eppBodegaCbo);

        filtros.add(new JLabel("Subbodega:"));
        filtros.add(eppSubbodegaCbo);

        filtros.add(new JLabel("Tipo:"));
        filtros.add(eppTipoCbo);

        filtros.add(eppBuscarBtn);

        // cargar combos (bodegas/tipos)
        llenarComboBodegasEpp(eppBodegaCbo, eppBodegaNombreToId);
        eppBodegaCbo.insertItemAt("Todas", 0);
        eppBodegaCbo.setSelectedIndex(0);
        eppSubbodegaCbo.addItem("Todas");
        llenarComboTiposEpp();

        // Cambio de bodega => subbodegas
        eppBodegaCbo.addActionListener(e -> {
            String bSel = (String) eppBodegaCbo.getSelectedItem();
            eppSubbodegaCbo.removeAllItems();
            eppSubbodegaCbo.addItem("Todas");
            eppSubbodegaNombreToId.clear();
            if (bSel != null && !"Todas".equals(bSel)) {
                try {
                    int idBod = eppBodegaNombreToId.get(bSel);
                    Map<String,Integer> nombreId = new BodegaDAO().obtenerSubbodegasPorIdPrincipal(idBod);
                    for (Map.Entry<String,Integer> en : nombreId.entrySet()) {
                        eppSubbodegaCbo.addItem(en.getKey());
                        eppSubbodegaNombreToId.put(en.getKey(), en.getValue());
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "Error subbodegas: " + ex.getMessage());
                }
            }
        });

        // ======= ALTA RÁPIDA EPP (izquierda) =======
        JPanel quickAdd = new JPanel(new GridBagLayout());
        quickAdd.setBorder(new TitledBorder("Agregar EPP"));
        GridBagConstraints gq = new GridBagConstraints();
        gq.insets = new Insets(6,6,6,6);
        gq.fill = GridBagConstraints.HORIZONTAL;
        gq.gridx = 0; gq.gridy = 0;

        JComboBox<String> qaTipoCbo   = new JComboBox<>();
        JComboBox<String> qaTallaCbo  = new JComboBox<>(new String[]{"","XS","S","M","L","XL","XXL"});
        JTextField        qaSerieTxt  = new JTextField(14);
        JComboBox<String> qaEstadoCbo = new JComboBox<>(new String[]{"Nuevo","Usado","En reparación"});
        JSpinner          qaCantSpin  = new JSpinner(new SpinnerNumberModel(1,1,1000,1));
        JComboBox<String> qaBodCbo    = new JComboBox<>();
        JComboBox<String> qaSubCbo    = new JComboBox<>();
        JButton           qaAddBtn    = new JButton("Agregar EPP");

        // Tipos desde epp_tipo (map local nombre -> id)
        Map<String,Integer> qaTipoNombreToId = new LinkedHashMap<>();
        try {
            for (String[] t : new EppDAO().listarTipos()) { // [id, tipo, subtipo]
                int id = Integer.parseInt(t[0]);
                String nom = t[1] + " / " + t[2];
                qaTipoCbo.addItem(nom);
                qaTipoNombreToId.put(nom, id);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar los tipos EPP: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        // Bodega/Subbodega por defecto
        llenarComboBodegasEpp(qaBodCbo, null);
        qaBodCbo.addActionListener(e -> actualizarSubbodegasEn(qaBodCbo, qaSubCbo));
        if (qaBodCbo.getItemCount()>0) {
            qaBodCbo.setSelectedIndex(0);
            actualizarSubbodegasEn(qaBodCbo, qaSubCbo);
        }

        // Serie -> bloquea cantidad
        qaSerieTxt.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void upd(){
                boolean has = !qaSerieTxt.getText().trim().isEmpty();
                qaCantSpin.setValue(1);
                qaCantSpin.setEnabled(!has);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e){upd();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){upd();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){upd();}
        });

        // Layout quick-add
        quickAdd.add(new JLabel("Tipo:"), gq);               gq.gridx=1; quickAdd.add(qaTipoCbo, gq);
        gq.gridx=0; gq.gridy++; quickAdd.add(new JLabel("Talla:"), gq);      gq.gridx=1; quickAdd.add(qaTallaCbo, gq);
        gq.gridx=0; gq.gridy++; quickAdd.add(new JLabel("Serie (opcional):"), gq); gq.gridx=1; quickAdd.add(qaSerieTxt, gq);
        gq.gridx=0; gq.gridy++; quickAdd.add(new JLabel("Estado:"), gq);     gq.gridx=1; quickAdd.add(qaEstadoCbo, gq);
        gq.gridx=0; gq.gridy++; quickAdd.add(new JLabel("Cantidad:"), gq);   gq.gridx=1; quickAdd.add(qaCantSpin, gq);
        gq.gridx=0; gq.gridy++; quickAdd.add(new JLabel("Bodega:"), gq);     gq.gridx=1; quickAdd.add(qaBodCbo, gq);
        gq.gridx=0; gq.gridy++; quickAdd.add(new JLabel("Subbodega:"), gq);  gq.gridx=1; quickAdd.add(qaSubCbo, gq);
        gq.gridx=0; gq.gridy++; gq.gridwidth=2; quickAdd.add(qaAddBtn, gq);

        // Acción alta rápida
        qaAddBtn.addActionListener(e -> {
            try {
                String tipoNom = (String) qaTipoCbo.getSelectedItem();
                if (tipoNom == null) throw new IllegalArgumentException("Seleccione el tipo de EPP.");
                Integer idTipo = qaTipoNombreToId.get(tipoNom);

                String talla  = (String) qaTallaCbo.getSelectedItem();
                String serie  = qaSerieTxt.getText().trim();
                String estado = (String) qaEstadoCbo.getSelectedItem();

                String bNom = (String) qaBodCbo.getSelectedItem();
                String sNom = (String) qaSubCbo.getSelectedItem();
                if (bNom == null || sNom == null || "No hay subbodegas disponibles".equalsIgnoreCase(String.valueOf(sNom)))
                    throw new IllegalArgumentException("Seleccione bodega y subbodega válidas.");

                BodegaDAO bdao = new BodegaDAO();
                int idBod = bdao.obtenerIdBodegaPrincipalPorNombre(bNom);
                int idSub = bdao.obtenerIdSubbodegaPorNombre(sNom);

                int cantidad = (Integer) qaCantSpin.getValue();
                boolean serializable = !serie.isBlank();
                if (serializable) cantidad = 1; // regla

                // Requiere EppService.agregarEppSimple(idTipo, talla, serie, estado, idBod, idSub, cantidad)
                EppService.agregarEppSimple(idTipo, talla, serie, estado, idBod, idSub, cantidad);

                JOptionPane.showMessageDialog(this, "EPP agregado correctamente.");
                qaSerieTxt.setText("");
                qaCantSpin.setValue(1);
                buscarEppYRefrescarTabla();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo agregar EPP: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // ======= TABLA EPP (centro) =======
        eppModel = new DefaultTableModel(
                new Object[]{"ID","Producto","Tipo","Código","Serie","Talla","Bodega","Subbodega","Estado"},0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        eppTable = new JTable(eppModel);
        eppTable.setRowHeight(22);
        eppTable.setAutoCreateRowSorter(true);
        JScrollPane spEpp = new JScrollPane(eppTable);

        // ======= PANEL MOVER (derecha) =======
        JPanel moverPanel = new JPanel(new GridBagLayout());
        moverPanel.setBorder(new TitledBorder("Mover EPP seleccionado"));
        GridBagConstraints gm = new GridBagConstraints();
        gm.insets = new Insets(6,6,6,6);
        gm.fill = GridBagConstraints.HORIZONTAL;
        gm.gridx=0; gm.gridy=0;

        eppSelLbl = new JLabel("Sin selección");
        gm.gridwidth = 2; moverPanel.add(eppSelLbl, gm); gm.gridwidth = 1;

        gm.gridy++; gm.gridx=0; moverPanel.add(new JLabel("Bodega destino:"), gm);
        gm.gridx=1; eppMoverBodDestCbo = new JComboBox<>(); llenarComboBodegasEpp(eppMoverBodDestCbo, null);
        moverPanel.add(eppMoverBodDestCbo, gm);

        gm.gridy++; gm.gridx=0; moverPanel.add(new JLabel("Subbodega destino:"), gm);
        gm.gridx=1; eppMoverSubDestCbo = new JComboBox<>(); moverPanel.add(eppMoverSubDestCbo, gm);
        eppMoverBodDestCbo.addActionListener(e -> actualizarSubbodegasDestino());

        gm.gridy++; gm.gridx=0; moverPanel.add(new JLabel("Motivo:"), gm);
        gm.gridx=1; eppMotivoTxt = new JTextField(18); moverPanel.add(eppMotivoTxt, gm);

        gm.gridy++; gm.gridx=0; gm.gridwidth=2; eppMoverBtn = new JButton("Mover"); moverPanel.add(eppMoverBtn, gm);

        // ======= HISTORIAL (abajo) =======
        eppMovModel = new DefaultTableModel(
                new Object[]{"Fecha","Desde Bodega","Desde Subbodega","Hacia Bodega","Hacia Subbodega","Motivo"},0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        eppMovTable = new JTable(eppMovModel);
        eppMovTable.setRowHeight(22);
        JScrollPane spMov = new JScrollPane(eppMovTable);
        spMov.setBorder(new TitledBorder("Historial de movimientos"));

        // ======= Layout central (tabla + historial) =======
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spEpp, spMov);
        split.setResizeWeight(0.6);

        // ======= Listeners =======
        eppBuscarBtn.addActionListener(e -> buscarEppYRefrescarTabla());
        eppBuscarTxt.addActionListener(e -> buscarEppYRefrescarTabla());
        eppTable.getSelectionModel().addListSelectionListener(ev -> { if (!ev.getValueIsAdjusting()) onEppSeleccionado(); });
        eppMoverBtn.addActionListener(e -> onMoverEpp());

        // ======= Ensamble =======
        root.add(filtros, BorderLayout.NORTH);
        root.add(quickAdd, BorderLayout.WEST);     // alta rápida al costado
        root.add(split, BorderLayout.CENTER);
        root.add(moverPanel, BorderLayout.EAST);

        // Carga inicial
        buscarEppYRefrescarTabla();
        return root;
    }

    private void llenarComboBodegasEpp(JComboBox<String> cbo, Map<String,Integer> targetMap) {
        try {
            cbo.removeAllItems();
            Map<Integer,String> idNombre = new BodegaDAO().obtenerBodegasPrincipalesConIds();
            // mantener orden por nombre
            List<Map.Entry<Integer,String>> lst = idNombre.entrySet().stream()
                    .sorted((a,b) -> a.getValue().compareToIgnoreCase(b.getValue()))
                    .collect(Collectors.toList());
            for (Map.Entry<Integer,String> e : lst) {
                cbo.addItem(e.getValue());
                if (targetMap != null) targetMap.put(e.getValue(), e.getKey());
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error bodegas: " + ex.getMessage());
        }
    }

    private void llenarComboTiposEpp() {
        eppTipoCbo.removeAllItems();
        eppTipoCbo.addItem("Todos");
        eppTipoNombreToId.clear();
        try {
            EppDAO dao = new EppDAO();
            for (String[] t : dao.listarTipos()) {
                int id = Integer.parseInt(t[0]);
                String nom = t[1] + " / " + t[2];
                eppTipoCbo.addItem(nom);
                eppTipoNombreToId.put(nom, id);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error tipos EPP: " + ex.getMessage());
        }
    }

    private void actualizarSubbodegasDestino() {
        try {
            eppMoverSubDestCbo.removeAllItems();
            String bod = (String) eppMoverBodDestCbo.getSelectedItem();
            if (bod == null) return;
            int idBod = new BodegaDAO().obtenerIdBodegaPrincipalPorNombre(bod);
            Map<String,Integer> map = new BodegaDAO().obtenerSubbodegasPorIdPrincipal(idBod);
            for (String n : map.keySet()) eppMoverSubDestCbo.addItem(n);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error subbodegas destino: " + ex.getMessage());
        }
    }

    private void buscarEppYRefrescarTabla() {
        setLoading(true);
        new SwingWorker<List<EppDAO.EppItem>, Void>() {
            @Override protected List<EppDAO.EppItem> doInBackground() throws Exception {
                String texto = eppBuscarTxt.getText().trim();
                Integer idB = null, idS = null, idT = null;

                String bod = (String) eppBodegaCbo.getSelectedItem();
                if (bod != null && !"Todas".equals(bod)) idB = eppBodegaNombreToId.get(bod);

                String sub = (String) eppSubbodegaCbo.getSelectedItem();
                if (sub != null && !"Todas".equals(sub)) idS = eppSubbodegaNombreToId.get(sub);

                String tipNom = (String) eppTipoCbo.getSelectedItem();
                if (tipNom != null && !"Todos".equals(tipNom)) idT = eppTipoNombreToId.get(tipNom);

                return new EppDAO().listar(texto, idB, idS, idT, 200, 0);
            }
            @Override protected void done() {
                try {
                    List<EppDAO.EppItem> data = get();
                    eppModel.setRowCount(0);
                    for (EppDAO.EppItem e : data) {
                        String bNom = new BodegaDAO().obtenerNombreBodegaPorId(e.idBodegaPrincipal);
                        String sNom = new BodegaDAO().obtenerNombreSubbodegaPorId(e.idSubbodega);
                        eppModel.addRow(new Object[]{
                                e.idEpp, e.idProducto, e.idTipo, e.codigoInterno, e.numeroSerie, e.talla,
                                bNom, sNom, e.estado
                        });
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Error al buscar EPP: " + ex.getMessage());
                } finally {
                    setLoading(false);
                }
            }
        }.execute();
    }

    private Integer eppSeleccionadoId() {
        int r = eppTable.getSelectedRow();
        if (r < 0) return null;
        int mr = eppTable.convertRowIndexToModel(r);
        Object val = eppModel.getValueAt(mr, 0);
        return (val == null) ? null : Integer.parseInt(val.toString());
    }

    private void onEppSeleccionado() {
        Integer id = eppSeleccionadoId();
        if (id == null) {
            eppSelLbl.setText("Sin selección");
            eppMovModel.setRowCount(0);
            return;
        }
        // label
        String cod = String.valueOf(eppModel.getValueAt(eppTable.convertRowIndexToModel(eppTable.getSelectedRow()), 3));
        String serie = String.valueOf(eppModel.getValueAt(eppTable.convertRowIndexToModel(eppTable.getSelectedRow()), 4));
        eppSelLbl.setText("EPP #" + id + "  •  Código: " + cod + "  •  Serie: " + serie);

        cargarHistorialMov(id);
    }

    private void cargarHistorialMov(int idEpp) {
        setLoading(true);
        new SwingWorker<List<EppDAO.MovimientoEpp>, Void>() {
            @Override protected List<EppDAO.MovimientoEpp> doInBackground() throws Exception {
                return new EppDAO().listarMovimientos(idEpp, 100, 0);
            }
            @Override protected void done() {
                try {
                    List<EppDAO.MovimientoEpp> movs = get();
                    eppMovModel.setRowCount(0);
                    BodegaDAO bDao = new BodegaDAO();
                    for (EppDAO.MovimientoEpp m : movs) {
                        String dB = bDao.obtenerNombreBodegaPorId(m.desdeBodega);
                        String dS = bDao.obtenerNombreSubbodegaPorId(m.desdeSubbodega);
                        String hB = bDao.obtenerNombreBodegaPorId(m.haciaBodega);
                        String hS = bDao.obtenerNombreSubbodegaPorId(m.haciaSubbodega);
                        eppMovModel.addRow(new Object[]{
                                m.fecha, dB, dS, hB, hS, m.motivo
                        });
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Error historial: " + ex.getMessage());
                } finally { setLoading(false); }
            }
        }.execute();
    }

    private void onMoverEpp() {
        Integer id = eppSeleccionadoId();
        if (id == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un EPP primero.");
            return;
        }
        try {
            String bodDestNom = (String) eppMoverBodDestCbo.getSelectedItem();
            String subDestNom = (String) eppMoverSubDestCbo.getSelectedItem();
            if (bodDestNom == null || subDestNom == null) {
                JOptionPane.showMessageDialog(this, "Selecciona bodega y subbodega destino.");
                return;
            }
            BodegaDAO bDao = new BodegaDAO();
            int idBod = bDao.obtenerIdBodegaPrincipalPorNombre(bodDestNom);
            int idSub = bDao.obtenerIdSubbodegaPorNombre(subDestNom);

            String motivo = eppMotivoTxt.getText().trim();
            if (motivo.isEmpty()) motivo = "TRASLADO";

            new EppDAO().moverItem(id, idBod, idSub, motivo);

            JOptionPane.showMessageDialog(this, "EPP movido correctamente.");

            // refrescar tabla/historial
            buscarEppYRefrescarTabla();
            // re-seleccionar (simple: limpiar selección)
            eppTable.clearSelection();
            eppSelLbl.setText("Sin selección");
            eppMovModel.setRowCount(0);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al mover EPP: " + ex.getMessage());
        }
    }

    private Integer parseNullableInt(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return Integer.valueOf(s);
    }

    private java.math.BigDecimal parseNullableBig(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        // admite coma decimal
        return new java.math.BigDecimal(s.replace(',', '.'));
    }

    private Date getDateFromSpinner(JSpinner spinner) {
        return (Date) spinner.getValue();
    }

    private static class RightRenderer extends DefaultTableCellRenderer {
        @Override protected void setValue(Object value) {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(value == null ? "" : String.valueOf(value));
        }
    }

    private static class CenterRenderer extends DefaultTableCellRenderer {
        @Override protected void setValue(Object value) {
            setHorizontalAlignment(SwingConstants.CENTER);
            setText(value == null ? "" : String.valueOf(value));
        }
    }

    private static class DateRenderer extends DefaultTableCellRenderer {
        private final java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd");
        @Override protected void setValue(Object value) {
            setHorizontalAlignment(SwingConstants.CENTER);
            if (value instanceof java.util.Date) setText(fmt.format((Date) value));
            else setText(String.valueOf(value));
        }
    }

    private static class QuantityRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
            setHorizontalAlignment(SwingConstants.CENTER);
            // zebra
            if (!s) comp.setBackground((r % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE);
            // bajo stock
            try {
                int cantidad = Integer.parseInt(String.valueOf(v));
                setFont(getFont().deriveFont(cantidad <= LOW_STOCK ? Font.BOLD : Font.PLAIN));
                setForeground(cantidad <= LOW_STOCK ? new Color(180, 0, 0) : Color.BLACK);
            } catch (Exception ignored) {
                setForeground(Color.BLACK);
            }
            return comp;
        }
    }

    private void installColumnVisibilityMenu(JTable table) {
        final JPopupMenu pm = new JPopupMenu();

        // Copiar fila
        JMenuItem copy = new JMenuItem("Copiar fila");
        copy.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) return;
            int mr = table.convertRowIndexToModel(r);
            TableModel tm = table.getModel();
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < tm.getColumnCount(); c++) {
                if (c > 0) sb.append('\t');
                sb.append(String.valueOf(tm.getValueAt(mr, c)));
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(sb.toString()), null);
        });
        pm.add(copy);
        pm.addSeparator();

        // Ocultar / mostrar
        JMenu colsMenu = new JMenu("Columnas…");
        pm.add(colsMenu);

        final TableColumnModel tcm = table.getColumnModel();
        final JTableHeader header = table.getTableHeader();
        // aquí guardamos las columnas ocultas por nombre
        final java.util.Map<String, TableColumn> hidden = new java.util.LinkedHashMap<>();

        // checkboxes para cada columna visible
        for (int i = 0; i < tcm.getColumnCount(); i++) {
            final String name = header.getColumnModel().getColumn(i).getHeaderValue().toString();
            final JCheckBoxMenuItem chk = new JCheckBoxMenuItem(name, true);
            chk.addActionListener(ev -> {
                if (chk.isSelected()) {
                    // mostrar si estaba oculta
                    TableColumn col = hidden.remove(name);
                    if (col != null) tcm.addColumn(col);
                } else {
                    // ocultar si está visible
                    int idx = header.getColumnModel().getColumnIndex(name);
                    TableColumn col = tcm.getColumn(idx);
                    hidden.put(name, col);
                    tcm.removeColumn(col);
                }
            });
            colsMenu.add(chk);
        }

        JMenu showMenu = new JMenu("Mostrar ocultas");
        pm.add(showMenu);

        pm.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                showMenu.removeAll();
                if (hidden.isEmpty()) {
                    JMenuItem none = new JMenuItem("(ninguna)");
                    none.setEnabled(false);
                    showMenu.add(none);
                    return;
                }
                for (String name : new java.util.ArrayList<>(hidden.keySet())) {
                    JMenuItem mi = new JMenuItem(name);
                    mi.addActionListener(ae -> {
                        TableColumn col = hidden.remove(name);
                        if (col != null) tcm.addColumn(col);
                        // marcar el checkbox correspondiente como seleccionado
                        for (int i = 0; i < colsMenu.getItemCount(); i++) {
                            if (colsMenu.getItem(i) instanceof JCheckBoxMenuItem chk) {
                                if (name.equals(chk.getText())) chk.setSelected(true);
                            }
                        }
                    });
                    showMenu.add(mi);
                }
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        table.setComponentPopupMenu(pm);
    }

    private void recargarProductosParaEliminar() {
        try {
            String bNom = (String) bodegaPrincipalComboBoxEliminar.getSelectedItem();
            String sNom = (String) subbodegaComboBoxEliminar.getSelectedItem();

            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();

            if (bNom == null || sNom == null ||
                    "No hay subbodegas disponibles".equalsIgnoreCase(String.valueOf(sNom))) {
                model.addElement("No hay productos disponibles");
                productoComboBoxEliminar.setModel(model);
                productoComboBoxEliminar.setEnabled(false);
                return;
            }

            BodegaDAO bdao = new BodegaDAO();
            int idBod = bdao.obtenerIdBodegaPrincipalPorNombre(bNom);
            int idSub = bdao.obtenerIdSubbodegaPorNombre(sNom);

            List<String> nombres = productoDAO.obtenerProductosConStockEn(idBod, idSub);

            if (nombres == null || nombres.isEmpty()) {
                model.addElement("No hay productos disponibles");
                productoComboBoxEliminar.setModel(model);
                productoComboBoxEliminar.setEnabled(false);
            } else {
                for (String n : nombres) model.addElement(n);
                productoComboBoxEliminar.setModel(model);
                productoComboBoxEliminar.setEnabled(true);
            }
        } catch (Exception ex) {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            model.addElement("Error al cargar");
            productoComboBoxEliminar.setModel(model);
            productoComboBoxEliminar.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Error al cargar productos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createEppQuickAddPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("Agregar EPP"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,6,6,6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0;

        // Campos
        JComboBox<String> tipoCbo   = new JComboBox<>();
        JComboBox<String> tallaCbo  = new JComboBox<>(new String[]{"", "XS","S","M","L","XL","XXL"});
        JTextField serieTxt         = new JTextField(14);
        JComboBox<String> estadoCbo = new JComboBox<>(new String[]{"Nuevo","Usado","En reparación"});
        JSpinner cantidadSpin       = new JSpinner(new SpinnerNumberModel(1,1,1000,1));
        JComboBox<String> bodegaCbo = new JComboBox<>();
        JComboBox<String> subbCbo   = new JComboBox<>();
        JButton agregarBtn          = new JButton("Agregar EPP");

        // Cargar combos existentes (reusamos tus helpers)
        // Tipos desde epp_tipo
        tipoCbo.removeAllItems();
        Map<String,Integer> tipoNombreToIdLocal = new LinkedHashMap<>();
        try {
            for (String[] t : new EppDAO().listarTipos()) {          // id, tipo, subtipo
                int id = Integer.parseInt(t[0]);
                String nom = t[1] + " / " + t[2];
                tipoCbo.addItem(nom);
                tipoNombreToIdLocal.put(nom, id);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar los tipos EPP: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        // Bodega/Subbodega por defecto (reusa tus métodos)
        llenarComboBodegasEpp(bodegaCbo, null);
        bodegaCbo.addActionListener(e -> actualizarSubbodegasEn(bodegaCbo, subbCbo));
        if (bodegaCbo.getItemCount()>0) {
            bodegaCbo.setSelectedIndex(0);
            actualizarSubbodegasEn(bodegaCbo, subbCbo);
        }

        // Serie -> bloquea cantidad (serializable => 1)
        serieTxt.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void upd(){
                boolean has = !serieTxt.getText().trim().isEmpty();
                cantidadSpin.setValue(1);
                cantidadSpin.setEnabled(!has);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e){upd();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){upd();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){upd();}
        });

        // Layout
        p.add(new JLabel("Tipo:"), g);         g.gridx=1; p.add(tipoCbo, g);
        g.gridx=0; g.gridy++; p.add(new JLabel("Talla:"), g); g.gridx=1; p.add(tallaCbo, g);
        g.gridx=0; g.gridy++; p.add(new JLabel("Serie (opcional):"), g); g.gridx=1; p.add(serieTxt, g);
        g.gridx=0; g.gridy++; p.add(new JLabel("Estado:"), g); g.gridx=1; p.add(estadoCbo, g);
        g.gridx=0; g.gridy++; p.add(new JLabel("Cantidad:"), g); g.gridx=1; p.add(cantidadSpin, g);
        g.gridx=0; g.gridy++; p.add(new JLabel("Bodega:"), g); g.gridx=1; p.add(bodegaCbo, g);
        g.gridx=0; g.gridy++; p.add(new JLabel("Subbodega:"), g); g.gridx=1; p.add(subbCbo, g);
        g.gridx=0; g.gridy++; g.gridwidth=2; p.add(agregarBtn, g);

        // Acción agregar
        agregarBtn.addActionListener(e -> {
            try {
                String tipoNom = (String) tipoCbo.getSelectedItem();
                if (tipoNom == null) throw new IllegalArgumentException("Seleccione el tipo de EPP.");
                Integer idTipo = tipoNombreToIdLocal.get(tipoNom);

                String talla   = (String) tallaCbo.getSelectedItem();
                String serie   = serieTxt.getText().trim();
                String estado  = (String) estadoCbo.getSelectedItem();

                String bNom    = (String) bodegaCbo.getSelectedItem();
                String sNom    = (String) subbCbo.getSelectedItem();
                if (bNom == null || sNom == null || "No hay subbodegas disponibles".equalsIgnoreCase(String.valueOf(sNom)))
                    throw new IllegalArgumentException("Seleccione bodega y subbodega válidas.");

                BodegaDAO bdao = new BodegaDAO();
                int idBod = bdao.obtenerIdBodegaPrincipalPorNombre(bNom);
                int idSub = bdao.obtenerIdSubbodegaPorNombre(sNom);

                int cantidad = (Integer) cantidadSpin.getValue();
                boolean serializable = !serie.isBlank();
                if (serializable) cantidad = 1; // regla

                // Llama al servicio (ver parte 2)
                EppService.agregarEppSimple(idTipo, talla, serie, estado, idBod, idSub, cantidad);

                JOptionPane.showMessageDialog(this, "EPP agregado correctamente.");
                // Limpieza rápida
                serieTxt.setText("");
                cantidadSpin.setValue(1);
                // refresca la tabla de la pestaña
                buscarEppYRefrescarTabla();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo agregar EPP: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return p;
    }

}