import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ProductoFormGUI extends JFrame {
    private JTextField idField;
    private JTextField nombreField;
    private JTextField cantidadField;
    private JTextField fechaIngresoField;
    private JButton   fechaIngresoButton;
    private JTextField ubicacionField;

    // NUEVOS METADATOS
    private JTextField marcaField;
    private JTextField valorField; // BigDecimal como texto
    private JComboBox<String> estadoCombo;     // p.ej. SERVICIO / EN_MANTENCION / FUERA_DE_SERVICIO / DADO_DE_BAJA
    private JComboBox<String> tipoMaterialCombo; // Estructural / ERA / Rescate / EPP
    private JTextArea  observacionArea;
    private JTextField fechaVencField;
    private JButton   fechaVencButton;

    private JCheckBox requiereMantencionCheck;
    private JSpinner  frecuenciaMantencionSpinner; // meses
    private JTextField proximaMantField; // calculada opcional
    private JButton    calcularProximaMantButton;

    private JCheckBox esEppCheck;
    private JCheckBox esSerializableCheck;

    private JComboBox<String> bodegaComboBox;
    private JComboBox<String> subbodegaComboBox;
    private Map<String, Integer> bodegaMap = new HashMap<>();
    private Map<String, Integer> subbodegaMap = new HashMap<>();
    private JCheckBox aplicaVencCheck = new JCheckBox("Aplica vencimiento");

    private final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");

    public ProductoFormGUI() {
        setTitle("Formulario de Producto");
        setSize(560, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Container container = getContentPane();
        // Subo las filas para que quepa todo (no cambio a GridBag para no “refactorizar”)
        container.setLayout(new GridLayout(0, 2, 6, 6));

        // --- Campos base ---
        idField = new JTextField();
        idField.setEnabled(false);
        generarIdAutomatico();

        nombreField = new JTextField();
        cantidadField = new JTextField();

        fechaIngresoField = new JTextField();
        fechaIngresoButton = new JButton("Seleccionar Fecha Ingreso");
        fechaIngresoButton.addActionListener(e -> {
            JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
            JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
            dateSpinner.setEditor(timeEditor);
            if (!fechaIngresoField.getText().trim().isEmpty()) {
                try { dateSpinner.setValue(DF.parse(fechaIngresoField.getText().trim())); } catch (Exception ignored) {}
            }
            int result = JOptionPane.showOptionDialog(null, dateSpinner, "Seleccione Fecha de Ingreso",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (result == JOptionPane.OK_OPTION) {
                Date selectedDate = (Date) dateSpinner.getValue();
                fechaIngresoField.setText(DF.format(selectedDate));
            }
        });

        ubicacionField = new JTextField();

        // --- Metadatos nuevos ---
        marcaField = new JTextField();
        valorField = new JTextField(); // Se parsea a BigDecimal
        estadoCombo = new JComboBox<>(new String[]{
                "SERVICIO", "EN_MANTENCION", "FUERA_DE_SERVICIO", "DADO_DE_BAJA"
        });
        tipoMaterialCombo = new JComboBox<>(new String[]{
                "Estructural", "ERA", "Rescate", "EPP"
        });

        observacionArea = new JTextArea(3, 20);
        observacionArea.setLineWrap(true);
        observacionArea.setWrapStyleWord(true);
        JScrollPane obsScroll = new JScrollPane(observacionArea);

        fechaVencField = new JTextField();
        fechaVencButton = new JButton("Seleccionar Vencimiento");
        fechaVencButton.addActionListener(e -> {
            JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
            JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
            dateSpinner.setEditor(timeEditor);
            if (!fechaVencField.getText().trim().isEmpty()) {
                try { dateSpinner.setValue(DF.parse(fechaVencField.getText().trim())); } catch (Exception ignored) {}
            }
            int result = JOptionPane.showOptionDialog(null, dateSpinner, "Seleccione Fecha de Vencimiento",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (result == JOptionPane.OK_OPTION) {
                Date selectedDate = (Date) dateSpinner.getValue();
                fechaVencField.setText(DF.format(selectedDate));
            }
        });

        requiereMantencionCheck = new JCheckBox("Requiere Mantención");
        frecuenciaMantencionSpinner = new JSpinner(new SpinnerNumberModel(12, 1, 120, 1));
        frecuenciaMantencionSpinner.setEnabled(false);
        requiereMantencionCheck.addActionListener(e ->
                frecuenciaMantencionSpinner.setEnabled(requiereMantencionCheck.isSelected()));

        proximaMantField = new JTextField();
        proximaMantField.setToolTipText("Opcional. Puedes calcular automáticamente.");
        calcularProximaMantButton = new JButton("Calcular Próx. Mant.");
        calcularProximaMantButton.addActionListener(e -> calcularProximaMantencion());

        esEppCheck = new JCheckBox("Es EPP");
        esSerializableCheck = new JCheckBox("Es Serializable");

        // --- Bodegas ---
        bodegaComboBox = new JComboBox<>();
        bodegaComboBox.addActionListener(e -> actualizarSubbodegas());
        actualizarListaBodegas();

        subbodegaComboBox = new JComboBox<>();

        // --- Form UI ---
        container.add(new JLabel("ID:"));
        container.add(idField);

        container.add(new JLabel("Nombre:"));
        container.add(nombreField);

        container.add(new JLabel("Cantidad:"));
        container.add(cantidadField);

        container.add(new JLabel("Fecha Ingreso (yyyy-MM-dd):"));
        container.add(fechaIngresoField);
        container.add(new JLabel(""));
        container.add(fechaIngresoButton);

        container.add(new JLabel("Ubicación:"));
        container.add(ubicacionField);

        // Metadatos
        container.add(new JLabel("Marca:"));
        container.add(marcaField);

        container.add(new JLabel("Valor ($):"));
        container.add(valorField);

        container.add(new JLabel("Estado del Producto:"));
        container.add(estadoCombo);

        container.add(new JLabel("Tipo de Material:"));
        container.add(tipoMaterialCombo);

        container.add(new JLabel("Observación:"));
        container.add(obsScroll);

        container.add(new JLabel("Vencimiento (opcional):"));
        container.add(fechaVencField);
        container.add(new JLabel(""));
        container.add(fechaVencButton);

        container.add(requiereMantencionCheck);
        container.add(new JLabel("Frecuencia Mant. (meses):"));
        container.add(frecuenciaMantencionSpinner);
        container.add(new JLabel(""));

        container.add(new JLabel("Próxima Mant. (opcional):"));
        container.add(proximaMantField);
        container.add(new JLabel(""));
        container.add(calcularProximaMantButton);

        container.add(esEppCheck);
        container.add(esSerializableCheck);

        container.add(new JLabel("Bodega:"));
        container.add(bodegaComboBox);

        container.add(new JLabel("Subbodega:"));
        container.add(subbodegaComboBox);

        JButton submitButton = new JButton("Agregar Producto");
        submitButton.addActionListener(e -> agregarProducto());
        container.add(new JLabel(""));
        container.add(submitButton);
    }

    private void calcularProximaMantencion() {
        try {
            // Base: fechaIngreso ó hoy si está vacío
            Date base;
            if (!fechaIngresoField.getText().trim().isEmpty()) {
                base = DF.parse(fechaIngresoField.getText().trim());
            } else {
                base = new Date();
            }
            int meses = (Integer) frecuenciaMantencionSpinner.getValue();
            Calendar cal = Calendar.getInstance();
            cal.setTime(base);
            cal.add(Calendar.MONTH, meses);
            proximaMantField.setText(DF.format(cal.getTime()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo calcular la próxima mantención: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void agregarProducto() {
        try {
            // ==== Validaciones mínimas ====
            String nombre = nombreField.getText().trim();
            if (nombre.isEmpty()) throw new IllegalArgumentException("Ingrese un nombre de producto.");

            int cantidad = Integer.parseInt(cantidadField.getText().trim());
            if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");

            String txtFechaIng = fechaIngresoField.getText().trim();
            if (txtFechaIng.isEmpty()) throw new IllegalArgumentException("Ingrese la fecha de ingreso.");
            Date fechaIngreso = DF.parse(txtFechaIng);

            String ubicacion = ubicacionField.getText().trim();
            if (ubicacion.isEmpty()) throw new IllegalArgumentException("Ingrese la ubicación.");

            String bodegaNom = (String) bodegaComboBox.getSelectedItem();
            String subbNom   = (String) subbodegaComboBox.getSelectedItem();
            if (bodegaNom == null || subbNom == null)
                throw new IllegalArgumentException("Debe seleccionar bodega y subbodega.");

            Integer idBodegaPrincipal = bodegaMap.get(bodegaNom);
            Integer idSubbodega       = subbodegaMap.get(subbNom);
            if (idBodegaPrincipal == null || idSubbodega == null)
                throw new IllegalArgumentException("No se pudo resolver la bodega/subbodega seleccionada.");

            // ==== Metadatos opcionales ====
            String marca = vacioANull(marcaField.getText());

            BigDecimal valor = null;
            String valTxt = valorField.getText().trim();
            if (!valTxt.isEmpty()) {
                try {
                    valor = new BigDecimal(valTxt);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Valor referencial inválido. Use sólo números (y opcionalmente decimales).");
                }
            }

            String estadoProducto = (String) estadoCombo.getSelectedItem();      // cat. visual
            String tipoMaterial   = (String) tipoMaterialCombo.getSelectedItem(); // cat. visual
            String observacion    = vacioANull(observacionArea.getText());

            // ==== Vencimiento ====
            Boolean aplicaVenc = aplicaVencCheck.isSelected();   // <-- checkbox "Aplica vencimiento"
            Date fechaVenc = null;
            String txtVenc = fechaVencField.getText().trim();
            if (aplicaVenc && !txtVenc.isEmpty()) {
                fechaVenc = DF.parse(txtVenc);
            }
            // Si NO aplica vencimiento, NO mandamos fechaVenc (queda null) y aplicaVenc = false.

            // ==== Mantención ====
            Boolean requiereMant = requiereMantencionCheck.isSelected();
            Integer freqMeses    = requiereMant ? (Integer) frecuenciaMantencionSpinner.getValue() : null;

            Date proximaMant = null;
            String txtProx = proximaMantField.getText().trim();
            if (!txtProx.isEmpty()) {
                proximaMant = DF.parse(txtProx);
            } else if (requiereMant) {
                // calcular por defecto si marcó requiere y no ingresó fecha
                Calendar cal = Calendar.getInstance();
                cal.setTime(fechaIngreso);
                cal.add(Calendar.MONTH, (freqMeses != null ? freqMeses : 12));
                proximaMant = cal.getTime();
            }

            // ==== Flags ====
            Boolean esEpp          = esEppCheck.isSelected();
            Boolean esSerializable = esSerializableCheck.isSelected();

            int idProducto = Integer.parseInt(idField.getText().trim());

            // ==== Construcción del DTO (usa tu constructor extendido) ====
            ProductoForm producto = new ProductoForm(
                    idProducto,
                    nombre,
                    cantidad,
                    fechaIngreso,
                    ubicacion,
                    idBodegaPrincipal,
                    idSubbodega,
                    /* nombres legibles UI */ null, null,
                    /* metadatos */           marca, valor, estadoProducto, tipoMaterial, observacion,
                    /* venc/mtto */           fechaVenc, requiereMant, freqMeses, proximaMant,
                    /* flags */               esEpp, esSerializable,
                    /* aplica_vencimiento */  aplicaVenc
            );

            // ==== Persistencia ====
            ProductoDAO dao = new ProductoDAO();

            // Si ya existe ese producto en la misma ubicación, ofrece sumar cantidad
            ProductoForm existente = dao.obtenerProductoPorNombreYBodega(nombre, idBodegaPrincipal, idSubbodega);
            if (existente != null) {
                int opt = JOptionPane.showConfirmDialog(
                        this,
                        "El producto ya existe en esta bodega/subbodega.\n¿Desea sumar la cantidad ingresada?",
                        "Producto existente",
                        JOptionPane.YES_NO_OPTION
                );
                if (opt == JOptionPane.YES_OPTION) {
                    dao.actualizarCantidadProducto(existente.getId(), idBodegaPrincipal, idSubbodega, cantidad);
                    JOptionPane.showMessageDialog(this, "Cantidad actualizada.");
                } else {
                    JOptionPane.showMessageDialog(this, "No se realizó ninguna acción.");
                }
            } else {
                dao.agregarProducto(producto);
                JOptionPane.showMessageDialog(this, "Producto agregado correctamente.");
            }

            // ==== Reset UI ====
            limpiarCampos();
            generarIdAutomatico();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "La cantidad/valor no es válido.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al agregar producto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String vacioANull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void actualizarListaBodegas() {
        try {
            BodegaDAO bodegaDAO = new BodegaDAO();
            Map<Integer, String> bodegas = bodegaDAO.obtenerBodegasPrincipalesConIds();
            bodegaComboBox.removeAllItems();
            bodegaMap.clear();
            for (Map.Entry<Integer,String> e : bodegas.entrySet()) {
                bodegaMap.put(e.getValue(), e.getKey());
                bodegaComboBox.addItem(e.getValue());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar bodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarSubbodegas() {
        try {
            BodegaDAO bodegaDAO = new BodegaDAO();
            String nombreBodega = (String) bodegaComboBox.getSelectedItem();
            if (nombreBodega != null) {
                Integer idBodegaPrincipal = bodegaMap.get(nombreBodega);
                if (idBodegaPrincipal == null) return;

                List<String> subbodegas = bodegaDAO.obtenerSubbodegasPorBodegaPrincipal(idBodegaPrincipal);

                subbodegaComboBox.removeAllItems();
                subbodegaMap.clear();
                for (String nombreSubbodega : subbodegas) {
                    subbodegaComboBox.addItem(nombreSubbodega);
                    // Nota: si hay subbodegas homónimas en distintas bodegas, sería mejor un método que
                    // devuelva (id, nombre) filtrado por bodega. Por ahora mantenemos tu flujo.
                    subbodegaMap.put(nombreSubbodega, bodegaDAO.obtenerIdSubbodegaPorNombre(nombreSubbodega));
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al cargar subbodegas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void limpiarCampos() {
        nombreField.setText("");
        cantidadField.setText("");
        fechaIngresoField.setText("");
        ubicacionField.setText("");

        marcaField.setText("");
        valorField.setText("");
        estadoCombo.setSelectedIndex(0);
        tipoMaterialCombo.setSelectedIndex(0);
        observacionArea.setText("");
        fechaVencField.setText("");
        requiereMantencionCheck.setSelected(false);
        frecuenciaMantencionSpinner.setValue(12);
        frecuenciaMantencionSpinner.setEnabled(false);
        proximaMantField.setText("");
        esEppCheck.setSelected(false);
        esSerializableCheck.setSelected(false);

        bodegaComboBox.setSelectedIndex(-1);
        subbodegaComboBox.removeAllItems();
    }

    private void generarIdAutomatico() {
        try {
            ProductoDAO productoDAO = new ProductoDAO();
            int nuevoId = productoDAO.obtenerSiguienteId();
            idField.setText(String.valueOf(nuevoId));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al generar ID: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ProductoFormGUI form = new ProductoFormGUI();
            form.setVisible(true);
        });
    }

    private static BigDecimal parseBigDecimalOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            // admite coma o punto
            t = t.replace(",", ".");
            return new BigDecimal(t);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor inválido. Use solo números (con punto o coma para decimales).");
        }
    }

}
