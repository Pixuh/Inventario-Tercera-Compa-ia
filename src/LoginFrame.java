import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Year;
import java.util.Arrays;
import java.util.prefs.Preferences;
import com.formdev.flatlaf.FlatClientProperties;

public class LoginFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final JTextField userField;
    private final JPasswordField passField;
    private final JButton loginButton;
    private final JButton exitButton;
    private final JCheckBox showPass;
    private final JCheckBox rememberMe;
    private final JLabel errorLabel;
    private final JLabel footerLabel;
    private final JProgressBar spinner;
    private final char defaultEchoChar;

    private final Preferences prefs = Preferences.userRoot().node("inventario/login");

    private int failCount = 0;
    private long lockUntil = 0L;

    // tamaños
    private static final int FIELD_W = 260;
    private static final int FIELD_H = 36;
    private static final int BTN_W = 260;
    private static final int BTN_H = 40;
    private static final int GAP_Y = 12;

    public LoginFrame() {
        setTitle("Inicio de Sesión");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(new Color(240, 240, 240));

        // ===== LOGO =====
        JPanel logoPanel = new JPanel(new GridBagLayout());
        logoPanel.setBackground(new Color(240, 240, 240));
        logoPanel.setPreferredSize(new Dimension(260, 260));
        JLabel logoLabel = new JLabel();
        ImageIcon logoIcon = loadScaledLogo("/img/logotercera400x400.png", 220, 220);
        if (logoIcon != null) logoLabel.setIcon(logoIcon);
        logoPanel.add(logoLabel, new GridBagConstraints());

        // ===== FORM =====
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(20, 30, 20, 30));
        form.setBackground(Color.WHITE);

        userField = new JTextField();
        sizeField(userField);
        userField.setBorder(compoundFieldBorder());
        userField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Nombre de Usuario");
        userField.setAlignmentX(Component.CENTER_ALIGNMENT);

        passField = new JPasswordField();
        sizeField(passField);
        passField.setBorder(compoundFieldBorder());
        passField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Contraseña");
        passField.setAlignmentX(Component.CENTER_ALIGNMENT);
        defaultEchoChar = passField.getEchoChar();

        // recordar usuario
        rememberMe = new JCheckBox("Recordarme");
        rememberMe.setBackground(Color.WHITE);
        rememberMe.setAlignmentX(Component.CENTER_ALIGNMENT);
        String savedUser = prefs.get("lastUser", "");
        boolean savedRemember = prefs.getBoolean("remember", false);
        userField.setText(savedRemember ? savedUser : "");
        rememberMe.setSelected(savedRemember);

        // mostrar contraseña
        showPass = new JCheckBox("Mostrar contraseña");
        showPass.setBackground(Color.WHITE);
        showPass.setAlignmentX(Component.CENTER_ALIGNMENT);
        showPass.addActionListener(e ->
                passField.setEchoChar(showPass.isSelected() ? (char) 0 : defaultEchoChar)
        );

        // enlace recuperar
        JButton forgotBtn = linkButton("¿Olvidaste tu contraseña?", () ->
                JOptionPane.showMessageDialog(this,
                        "Contacta al administrador para restablecer tu contraseña.",
                        "Recuperación de contraseña",
                        JOptionPane.INFORMATION_MESSAGE)
        );
        forgotBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        // error inline
        errorLabel = new JLabel(" ");
        errorLabel.setForeground(new Color(200, 0, 0));
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // caps lock
        passField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { showCapsWarning(); }
            @Override public void keyReleased(KeyEvent e) { showCapsWarning(); }
        });

        // botón login
        loginButton = new JButton("Iniciar Sesión");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setFocusPainted(false);
        loginButton.setBackground(new Color(57, 106, 252));
        loginButton.setForeground(Color.WHITE);
        loginButton.setOpaque(true);
        loginButton.setBorderPainted(false);
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginButton.setPreferredSize(new Dimension(BTN_W, BTN_H));
        loginButton.setMaximumSize(new Dimension(BTN_W, BTN_H));
        loginButton.setEnabled(false);
        loginButton.addActionListener(e -> startAuth());

        // spinner
        spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setVisible(false);
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        spinner.setMaximumSize(new Dimension(BTN_W, 6));

        // botón salir
        exitButton = new JButton("Salir");
        exitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        exitButton.setPreferredSize(new Dimension(BTN_W, 34));
        exitButton.setMaximumSize(new Dimension(BTN_W, 34));
        exitButton.addActionListener(e -> System.exit(0));

        // footer
        footerLabel = new JLabel();
        String version = getClass().getPackage() != null ? getClass().getPackage().getImplementationVersion() : null;
        if (version == null) version = "v1.02";
        footerLabel.setText("Inventario Tercera • " + version + " • © " + Year.now().getValue());
        footerLabel.setFont(footerLabel.getFont().deriveFont(11f));
        footerLabel.setForeground(new Color(120, 120, 120));
        footerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // enter = login
        getRootPane().setDefaultButton(loginButton);

        // habilitar botón solo con datos
        DocumentListener dl = new DocumentListener() {
            private void check() {
                boolean ok = !userField.getText().trim().isEmpty()
                        && passField.getPassword().length > 0;
                loginButton.setEnabled(ok);
            }
            @Override public void insertUpdate(DocumentEvent e) { check(); }
            @Override public void removeUpdate(DocumentEvent e) { check(); }
            @Override public void changedUpdate(DocumentEvent e) { check(); }
        };
        userField.getDocument().addDocumentListener(dl);
        passField.getDocument().addDocumentListener(dl);

        // ESC cierra
        getRootPane().registerKeyboardAction(
                e -> dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING)),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // ===== layout =====
        form.add(userField);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(passField);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(showPass);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(rememberMe);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(forgotBtn);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(errorLabel);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(loginButton);
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(spinner);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(exitButton);
        form.add(Box.createRigidArea(new Dimension(0, GAP_Y)));
        form.add(footerLabel);

        main.add(logoPanel, BorderLayout.WEST);
        main.add(form, BorderLayout.CENTER);

        add(main);
        pack();
    }

    private void sizeField(JTextField f) {
        f.setPreferredSize(new Dimension(FIELD_W, FIELD_H));
        f.setMaximumSize(new Dimension(FIELD_W, FIELD_H));
        f.setMinimumSize(new Dimension(FIELD_W, FIELD_H));
    }

    private Border compoundFieldBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        );
    }

    private ImageIcon loadScaledLogo(String resourcePath, int w, int h) {
        URL url = getClass().getResource(resourcePath);
        if (url == null) return null;
        Image img = new ImageIcon(url).getImage();
        Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private JButton linkButton(String text, Runnable action) {
        JButton b = new JButton("<HTML><U>" + text + "</U></HTML>");
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setBackground(Color.WHITE);
        b.setForeground(new Color(57, 106, 252));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        spinner.setVisible(busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void showCapsWarning() {
        try {
            boolean caps = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
            if (caps) {
                if (" ".equals(errorLabel.getText()) || errorLabel.getText().isEmpty()) {
                    errorLabel.setText("Bloq Mayús activado");
                }
            } else if ("Bloq Mayús activado".equals(errorLabel.getText())) {
                errorLabel.setText(" ");
            }
        } catch (UnsupportedOperationException ignore) {}
    }

    private void startAuth() {
        final String usuario = userField.getText().trim();
        final char[] password = passField.getPassword();

        if (usuario.isEmpty() || password.length == 0) {
            errorLabel.setText("Complete usuario y contraseña.");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < lockUntil) {
            long secs = Math.max(1, (lockUntil - now) / 1000);
            errorLabel.setText("Bloqueado por intentos fallidos. Intenta en " + secs + " s.");
            return;
        }

        if (rememberMe.isSelected()) {
            prefs.put("lastUser", usuario);
            prefs.putBoolean("remember", true);
        } else {
            prefs.remove("lastUser");
            prefs.putBoolean("remember", false);
        }

        errorLabel.setText(" ");
        setBusy(true);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                final String sql = "SELECT rol FROM usuario WHERE nombre = ? AND contrasena = ?";
                String pwd = new String(password);
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, usuario);
                    ps.setString(2, pwd);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getString("rol");
                        return null;
                    }
                } catch (SQLException ex) {
                    return "ERROR:" + ex.getMessage();
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    String result = get();
                    if (result == null) {
                        failCount++;
                        if (failCount >= 5) {
                            lockUntil = System.currentTimeMillis() + 30_000;
                            failCount = 0;
                            errorLabel.setText("Demasiados intentos. Espera 30 s.");
                        } else {
                            errorLabel.setText("Credenciales inválidas. Intento " + failCount + "/5");
                        }
                        passField.requestFocusInWindow();
                        passField.selectAll();
                        return;
                    }
                    if (result.startsWith("ERROR:")) {
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                "Error en la conexión a la base de datos.\n" + result.substring(6),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    abrirVentanaPorRol(result);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LoginFrame.this,
                            "Ocurrió un error inesperado.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void abrirVentanaPorRol(String rol) {
        dispose();
        if ("ADMIN".equalsIgnoreCase(rol)) {
            new MainFrame().setVisible(true);
        } else if ("USUARIO".equalsIgnoreCase(rol)) {
            new UsuarioFrame().setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this, "Rol desconocido: " + rol,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
