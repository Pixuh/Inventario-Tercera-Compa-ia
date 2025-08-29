import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginFrame extends JFrame {
    private final JTextField userField;
    private final JPasswordField passField;

    public LoginFrame() {
        setTitle("Inicio de Sesión");
        setSize(450, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(240, 240, 240));

        JPanel logoPanel = new JPanel(new GridBagLayout());
        logoPanel.setBackground(new Color(240, 240, 240));
        JLabel logoLabel = new JLabel();
        ImageIcon logo = new ImageIcon(getClass().getResource("/img/logotercera400x400.png"));
        Image scaledLogo = logo.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
        logoLabel.setIcon(new ImageIcon(scaledLogo));
        logoPanel.add(logoLabel, new GridBagConstraints());

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        formPanel.setBackground(Color.WHITE);

        JLabel userLabel = new JLabel("Nombre de Usuario:");
        userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        userField = new JTextField();
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        userField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JLabel passLabel = new JLabel("Contraseña:");
        passLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        passField = new JPasswordField();
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        passField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JButton loginButton = new JButton("Iniciar Sesión");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setFocusPainted(false);
        loginButton.setBackground(new Color(57, 106, 252));
        loginButton.setForeground(Color.WHITE);
        loginButton.setOpaque(true);
        loginButton.setBorderPainted(false);
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        loginButton.setMaximumSize(new Dimension(150, 40));

        loginButton.addActionListener(e -> {
            String nombreUsuario = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            authenticate(nombreUsuario, password, loginButton);
        });

        formPanel.add(userLabel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        formPanel.add(userField);
        formPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        formPanel.add(passLabel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        formPanel.add(passField);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        formPanel.add(loginButton);

        mainPanel.add(logoPanel, BorderLayout.WEST);
        mainPanel.add(formPanel, BorderLayout.CENTER);

        add(mainPanel);
    }

    private void authenticate(String nombreUsuario, String password, JButton loginButton) {
        if (nombreUsuario.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Por favor complete todos los campos.",
                    "Advertencia", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        SwingUtilities.invokeLater(() -> {
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                conn = DatabaseConnection.getConnection();
                stmt = conn.prepareStatement("SELECT rol FROM usuario WHERE nombre = ? AND contrasena = ?");
                stmt.setString(1, nombreUsuario);
                stmt.setString(2, password);

                rs = stmt.executeQuery();
                if (rs.next()) {
                    String rol = rs.getString("rol");
                    dispose();
                    abrirVentanaPorRol(rol);
                } else {
                    JOptionPane.showMessageDialog(null, "Usuario o contraseña incorrectos.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error en la conexión a la base de datos.");
            } finally {
                try {
                    if (rs != null) rs.close();
                    if (stmt != null) stmt.close();
                    if (conn != null) DatabaseConnection.closeConnection(conn);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                loginButton.setEnabled(true);
            }
        });
    }

    private void abrirVentanaPorRol(String rol) {
        if ("ADMIN".equalsIgnoreCase(rol)) {
            new MainFrame().setVisible(true);
        } else if ("USUARIO".equalsIgnoreCase(rol)) {
            new UsuarioFrame().setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this, "Rol desconocido: " + rol, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}
