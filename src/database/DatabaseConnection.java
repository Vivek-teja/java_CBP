package database;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Manages JDBC connections to MySQL for the Traffic Management System.
 * Loads connection configuration from an external database.properties file
 * or environment variables without hardcoding credentials in source code.
 *
 * Implements multi-tier failsafe driver loading:
 * 1. Direct module binding (mysql.connector.j)
 * 2. Standard Class.forName reflection
 * 3. Dynamic JAR ClassLoader with DriverShim for Eclipse modular isolation
 * 4. Direct Driver.connect() invocation (completely immune to DriverManager "No suitable driver found")
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

    private static volatile Driver registeredDriver = null;

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

        // 4. Multi-tier driver resolution
        ensureDriverRegistered();
        initialized = true;

        // 5. Test connection
        try (Connection conn = getConnection()) {
            available = (conn != null && !conn.isClosed());
            lastErrorMessage = "";
            System.out.println("[DatabaseConnection] MySQL connection established successfully to " + dbUrl);
        } catch (SQLException e) {
            available = false;
            lastErrorMessage = e.getMessage();
            System.err.println("[DatabaseConnection] Warning: Cannot connect to MySQL (" + e.getMessage() + "). Simulation will continue in in-memory mode.");
        } catch (Throwable t) {
            available = false;
            lastErrorMessage = t.getMessage();
            System.err.println("[DatabaseConnection] Warning: Unexpected database error: " + t.getMessage());
        }
    }

    /**
     * Resolves and registers the MySQL JDBC Driver across any modular, Eclipse, or Classpath configuration.
     */
    private static void ensureDriverRegistered() {
        if (registeredDriver != null) return;

        // Tier 1: Direct instantiation (if mysql.connector.j is required in module-info)
        try {
            registeredDriver = new com.mysql.cj.jdbc.Driver();
            DriverManager.registerDriver(registeredDriver);
            return;
        } catch (Throwable ignored) {
        }

        // Tier 2: Reflection via Class.forName
        try {
            Class<?> clazz = Class.forName(dbDriver);
            registeredDriver = (Driver) clazz.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(registeredDriver);
            return;
        } catch (Throwable ignored) {
        }

        // Tier 3: Dynamic lookup from local lib/ directory using URLClassLoader & DriverShim
        String[] candidatePaths = {
            "lib/mysql-connector-j-9.2.0.jar",
            "../lib/mysql-connector-j-9.2.0.jar",
            "mysql-connector-j-9.2.0.jar"
        };

        for (String p : candidatePaths) {
            File jar = new File(p);
            if (jar.exists()) {
                try {
                    java.net.URLClassLoader ucl = new java.net.URLClassLoader(
                        new java.net.URL[] { jar.toURI().toURL() },
                        DatabaseConnection.class.getClassLoader()
                    );
                    Class<?> clazz = Class.forName(dbDriver, true, ucl);
                    Driver raw = (Driver) clazz.getDeclaredConstructor().newInstance();
                    registeredDriver = new DriverShim(raw);
                    DriverManager.registerDriver(registeredDriver);
                    System.out.println("[DatabaseConnection] Dynamically loaded MySQL driver from: " + jar.getAbsolutePath());
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Wrapper shim to register foreign-classloader drivers with DriverManager safely.
     */
    public static class DriverShim implements Driver {
        private final Driver driver;
        public DriverShim(Driver d) { this.driver = d; }
        @Override public boolean acceptsURL(String u) throws SQLException { return driver.acceptsURL(u); }
        @Override public Connection connect(String u, Properties p) throws SQLException { return driver.connect(u, p); }
        @Override public int getMajorVersion() { return driver.getMajorVersion(); }
        @Override public int getMinorVersion() { return driver.getMinorVersion(); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException { return driver.getPropertyInfo(u, p); }
        @Override public boolean jdbcCompliant() { return driver.jdbcCompliant(); }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return driver.getParentLogger(); }
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
     * Obtains a new JDBC connection from the driver.
     * Uses direct driver invocation to bypass any DriverManager "No suitable driver found" limitations.
     * Callers must close this connection when finished (use try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            loadConfiguration();
        }

        // Direct connection attempt using registeredDriver instance
        if (registeredDriver != null) {
            try {
                Properties info = new Properties();
                if (dbUser != null) info.put("user", dbUser);
                if (dbPassword != null) info.put("password", dbPassword);
                Connection conn = registeredDriver.connect(dbUrl, info);
                if (conn != null && !conn.isClosed()) {
                    return conn;
                }
            } catch (SQLException ex) {
                // If it was an authentication/network error, rethrow; otherwise fall back to DriverManager
                if (!ex.getMessage().contains("No suitable driver")) {
                    throw ex;
                }
            }
        }

        // Standard DriverManager fallback
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
