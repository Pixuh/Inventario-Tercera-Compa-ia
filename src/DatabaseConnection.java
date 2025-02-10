import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {

    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://3.86.100.202:5432/inventario_bomberos");
        config.setUsername("postgres");
        config.setPassword("admin123");
        // Configuraciones adicionales:
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);       // Número máximo de conexiones en el pool
        config.setMinimumIdle(2);            // Conexiones mínimas inactivas
        config.setConnectionTimeout(30000);  // Tiempo máximo de espera (ms)
        config.setIdleTimeout(600000);       // Tiempo máximo que una conexión puede estar inactiva (ms)
        config.setMaxLifetime(1800000);      // Vida máxima de una conexión (ms)

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Método para cerrar el dataSource al finalizar la aplicación (opcional)
    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
