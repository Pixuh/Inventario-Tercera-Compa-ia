import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    // Información de conexión a la base de datos
    private static final String URL = "jdbc:postgresql://3.86.100.202:5432/inventario_bomberos";
    private static final String USER = "postgres";
    private static final String PASSWORD = "admin123";

    // Método para obtener la conexión a la base de datos
    public static Connection getConnection() throws SQLException {
        try {
            // Registrar el driver de PostgreSQL
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("El driver de PostgreSQL no está disponible en el classpath.", e);
        }

        // Retornar la conexión
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Método para cerrar una conexión (opcional para facilitar el manejo de recursos)
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error al cerrar la conexión: " + e.getMessage());
            }
        }
    }
}
