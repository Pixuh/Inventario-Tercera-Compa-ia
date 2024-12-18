import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GestionBodegasFrame extends JFrame {
    private BodegaDAO bodegaDAO = new BodegaDAO(); // DAO para manejar operaciones con bodegas
    private JComboBox<String> bodegaPrincipalComboBox; // ComboBox para bodegas principales
    private JComboBox<String> subbodegaComboBox; // ComboBox para subbodegas
    private JComboBox<String> bodegaPrincipalComboBoxCrearSubbodega; // ComboBox para crear subbodegas
    private JComboBox<String> eliminarBodegaComboBox; // ComboBox para eliminar bodegas
    private List<BodegaObserver> observers = new ArrayList<>(); // Lista de observadores

    // Constructor que recibe el MainFrame como observador
    public GestionBodegasFrame(MainFrame mainFrame) {
        agregarObservador(mainFrame); // Registra el MainFrame como observador
        setTitle("Gestión de Bodegas");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // Crear pestañas
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Crear Bodega Principal", createBodegaPrincipalPanel());
        tabbedPane.addTab("Crear Subbodega", createSubbodegaPanel());
        tabbedPane.addTab("Eliminar Bodega", createEliminarBodegaPanel());

        // Agregar pestañas al frame
        add(tabbedPane);

        // Actualizar bodegas al iniciar
        refrescarBodegas();
    }


    // Panel para crear bodegas principales
    private JPanel createBodegaPrincipalPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel nombreLabel = new JLabel("Nombre de la Bodega:");
        JTextField nombreField = new JTextField(20);

        JLabel capacidadLabel = new JLabel("Capacidad:");
        JTextField capacidadField = new JTextField(20);

        JButton crearButton = new JButton("Crear Bodega");
        crearButton.addActionListener(e -> {
            try {
                String nombre = nombreField.getText().trim();
                int capacidad = Integer.parseInt(capacidadField.getText().trim());

                if (nombre.isEmpty()) {
                    throw new IllegalArgumentException("El nombre de la bodega es obligatorio.");
                }

                // Crear bodega en la base de datos
                bodegaDAO.crearBodegaPrincipal(nombre, capacidad);
                JOptionPane.showMessageDialog(this, "Bodega Principal creada correctamente.");

                // Notificar a los observadores
                notificarObservadores();

                // Limpiar campos
                nombreField.setText("");
                capacidadField.setText("");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Ingrese un número válido para la capacidad.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al crear bodega: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(nombreLabel, gbc);
        gbc.gridx = 1;
        panel.add(nombreField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(capacidadLabel, gbc);
        gbc.gridx = 1;
        panel.add(capacidadField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(crearButton, gbc);

        return panel;
    }

    // Panel para crear subbodegas
    private JPanel createSubbodegaPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel nombreLabel = new JLabel("Nombre de la Subbodega:");
        JTextField nombreField = new JTextField(20);

        JLabel capacidadLabel = new JLabel("Capacidad:");
        JTextField capacidadField = new JTextField(20);

        JLabel bodegaPrincipalLabel = new JLabel("Bodega Principal:");
        bodegaPrincipalComboBoxCrearSubbodega = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxCrearSubbodega);

        JButton crearButton = new JButton("Crear Subbodega");
        crearButton.addActionListener(e -> {
            try {
                String nombre = nombreField.getText().trim();
                int capacidad = Integer.parseInt(capacidadField.getText().trim());
                String bodegaPrincipal = (String) bodegaPrincipalComboBoxCrearSubbodega.getSelectedItem();

                if (bodegaPrincipal == null || bodegaPrincipal.isEmpty()) {
                    throw new IllegalArgumentException("Debe seleccionar una bodega principal.");
                }

                int idBodegaPrincipal = obtenerIdBodegaPrincipal(bodegaPrincipal);
                bodegaDAO.crearSubbodega(nombre, capacidad, idBodegaPrincipal);

                JOptionPane.showMessageDialog(this, "Subbodega creada correctamente.");

                // Notificar a los observadores
                notificarObservadores();

                // Limpiar campos
                nombreField.setText("");
                capacidadField.setText("");
                bodegaPrincipalComboBoxCrearSubbodega.setSelectedIndex(0);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Ingrese un número válido para la capacidad.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al crear subbodega: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(nombreLabel, gbc);
        gbc.gridx = 1;
        panel.add(nombreField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(capacidadLabel, gbc);
        gbc.gridx = 1;
        panel.add(capacidadField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(bodegaPrincipalLabel, gbc);
        gbc.gridx = 1;
        panel.add(bodegaPrincipalComboBoxCrearSubbodega, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(crearButton, gbc);

        return panel;
    }

    // Panel para eliminar bodegas
    private JPanel createEliminarBodegaPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel bodegaPrincipalLabel = new JLabel("Seleccionar Bodega Principal:");
        eliminarBodegaComboBox = new JComboBox<>();
        cargarBodegasPrincipalesEnComboBox(eliminarBodegaComboBox);

        JButton eliminarButton = new JButton("Eliminar Bodega");
        eliminarButton.addActionListener(e -> {
            try {
                String bodegaSeleccionada = (String) eliminarBodegaComboBox.getSelectedItem();

                if (bodegaSeleccionada == null || bodegaSeleccionada.isEmpty()) {
                    throw new IllegalArgumentException("Debe seleccionar una bodega principal.");
                }

                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "¿Está seguro de eliminar esta bodega principal? Esto eliminará también sus subbodegas.",
                        "Confirmación",
                        JOptionPane.YES_NO_OPTION
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    bodegaDAO.eliminarBodegaPrincipal(bodegaSeleccionada);
                    JOptionPane.showMessageDialog(this, "Bodega eliminada correctamente.");

                    // Notificar a los observadores
                    notificarObservadores();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al eliminar la bodega: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(bodegaPrincipalLabel, gbc);
        gbc.gridx = 1;
        panel.add(eliminarBodegaComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        panel.add(eliminarButton, gbc);

        return panel;
    }

    // Refresca los ComboBox de bodegas principales
    public void refrescarBodegas() {
        cargarBodegasPrincipalesEnComboBox(bodegaPrincipalComboBoxCrearSubbodega);
        cargarBodegasPrincipalesEnComboBox(eliminarBodegaComboBox);
    }

    // Cargar bodegas principales en un ComboBox
    private void cargarBodegasPrincipalesEnComboBox(JComboBox<String> comboBox) {
        try {
            comboBox.removeAllItems(); // Limpia el ComboBox
            List<String> nombresBodegas = bodegaDAO.obtenerNombresBodegasPrincipales(); // Obtiene las bodegas principales
            for (String nombre : nombresBodegas) {
                comboBox.addItem(nombre); // Añade cada bodega al ComboBox
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al cargar las bodegas principales: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Obtener ID de bodega principal por nombre
    private int obtenerIdBodegaPrincipal(String nombreBodega) throws SQLException {
        return bodegaDAO.obtenerIdBodegaPrincipal(nombreBodega);
    }

    // Agregar un observador
    public void agregarObservador(BodegaObserver observer) {
        observers.add(observer);
    }

    // Notificar a los observadores
    private void notificarObservadores() {
        for (BodegaObserver observer : observers) {
            observer.actualizarBodegas();
        }
    }
}
