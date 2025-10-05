import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {
    private static HikariDataSource dataSource;

    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://200.35.159.169:5432/inventariodb_new");
            //config.setJdbcUrl("jdbc:postgresql://200.35.159.169:5432/inventariodb");
            config.setUsername("inventario_app");
            config.setPassword("admin123");

//            config.setJdbcUrl("jdbc:postgresql://localhost:5432/inventariodb");
//            config.setUsername("postgres");
//            config.setPassword("admin123");

            // Configuraciones de rendimiento del pool
            config.setDriverClassName("org.postgresql.Driver");
            config.setMaximumPoolSize(20); // Número máximo de conexiones simultáneas
            config.setMinimumIdle(5); // Conexiones mínimas inactivas
            config.setConnectionTimeout(10000); // Tiempo máximo de espera para obtener una conexión (10s)
            config.setIdleTimeout(60000); // Tiempo máximo que una conexión puede estar inactiva (1 min)
            config.setMaxLifetime(300000); // Vida máxima de una conexión en el pool (5 min)
            config.setLeakDetectionThreshold(10000); // Detección de conexiones que se quedan abiertas (10s)

            // Prueba de conexión para validar las conexiones
            config.setConnectionTestQuery("SELECT 1");

            // Inicialización del pool
            dataSource = new HikariDataSource(config);

        } catch (Exception e) {
            throw new RuntimeException("Error al inicializar el pool de conexiones", e);
        }
    }

    // Método para obtener una conexión desde el pool
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Método para cerrar una conexión específica
    public static void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close(); // Devuelve la conexión al pool
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Cierra el pool de conexiones al finalizar
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
