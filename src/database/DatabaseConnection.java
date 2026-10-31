package database;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages JDBC connections to MySQL for the Traffic Management System.
 * Loads connection configuration from an external database.properties file
 * or environment variables without hardcoding credentials in source code.
 *
 * Gracefully detects database availability and falls back to offline mode
 * without crashing or interrupting simulation physics.
 */
public class DatabaseConnection {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/traffic_management_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_RSCRIPT_PATH = "/usr/local/bin/Rscript";

    private static String dbUrl = DEFAULT_URL;
    private static String dbUser = DEFAULT_USER;
    private static String dbPassword = DEFAULT_PASSWORD;
    private static String dbDriver = DEFAULT_DRIVER;
    private static String rScriptPath = DEFAULT_RSCRIPT_PATH;

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static String lastErrorMessage = "";

    static {
        loadConfiguration();
    }

    /**
     * Loads database configuration from external database.properties file or environment variables.
     */
    public static synchronized void loadConfiguration() {
        Properties props = new Properties();

        // 1. Try to load from project root database.properties file
        File propFile = new File("database.properties");
        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                props.load(in);
            } catch (Exception e) {
                System.err.println("[DatabaseConnection] Notice: Could not read database.properties file: " + e.getMessage());
            }
        } else {
            // Try classpath
            try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream("database.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Read properties with defaults
        dbUrl = props.getProperty("db.url", DEFAULT_URL);
        dbUser = props.getProperty("db.user", DEFAULT_USER);
        dbPassword = props.getProperty("db.password", DEFAULT_PASSWORD);
        dbDriver = props.getProperty("db.driver", DEFAULT_DRIVER);
        rScriptPath = props.getProperty("r.script.path", DEFAULT_RSCRIPT_PATH);

        // 3. Environment variable overrides (if present)
        String envUrl = System.getenv("DB_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) dbUrl = envUrl.trim();

        String envUser = System.getenv("DB_USER");
        if (envUser != null && !envUser.trim().isEmpty()) dbUser = envUser.trim();

        String envPass = System.getenv("DB_PASSWORD");
        if (envPass != null) dbPassword = envPass;

        String envRscript = System.getenv("RSCRIPT_PATH");
        if (envRscript != null && !envRscript.trim().isEmpty()) rScriptPath = envRscript.trim();

        // 4. Test driver loading and connection
        try {
            Class.forName(dbDriver);
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                available = (conn != null && !conn.isClosed());
                lastErrorMessage = "";
                System.out.println("[DatabaseConnection] MySQL connection established successfully to " + dbUrl);
            }
        } catch (ClassNotFoundException e) {
            available = false;
            lastErrorMessage = "JDBC Driver class not found: " + dbDriver;
            System.err.println("[DatabaseConnection] Warning: " + lastErrorMessage + ". Simulation will continue in in-memory mode.");
        } catch (SQLException e) {
            available = false;
            lastErrorMessage = e.getMessage();
            System.err.println("[DatabaseConnection] Warning: Cannot connect to MySQL (" + e.getMessage() + "). Simulation will continue in in-memory mode.");
        } catch (Throwable t) {
            available = false;
            lastErrorMessage = t.getMessage();
            System.err.println("[DatabaseConnection] Warning: Unexpected database error: " + t.getMessage());
        }

        initialized = true;
    }

    /**
     * Returns true if MySQL connection is active and available.
     */
    public static boolean isAvailable() {
        if (!initialized) {
            loadConfiguration();
        }
        return available;
    }

    /**
     * Obtains a new JDBC connection from DriverManager.
     * Callers must close this connection when finished (use try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            loadConfiguration();
        }
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Returns the configured path to the Rscript executable.
     */
    public static String getRScriptPath() {
        return rScriptPath;
    }

    public static String getDbUrl() {
        return dbUrl;
    }

    public static String getDbUser() {
        return dbUser;
    }

    public static String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * Safely closes an AutoCloseable resource (Connection, Statement, ResultSet).
     */
    public static void closeQuietly(AutoCloseable res) {
        if (res != null) {
            try {
                res.close();
            } catch (Exception ignored) {
            }
        }
    }
}
