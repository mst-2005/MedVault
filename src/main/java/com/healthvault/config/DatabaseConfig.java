package com.healthvault.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Database configuration and connection management.
 */
public class DatabaseConfig {
    private static final String CONFIG_FILE = "database.properties";
    private static Properties properties;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                setDefaultProperties();
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            setDefaultProperties();
        }
    }

    private static void setDefaultProperties() {
        // Clean base URL — parameters are added in getConnection()
        properties.setProperty("db.url",      "jdbc:mysql://localhost:3306/health_vault");
        properties.setProperty("db.username", "root");
        properties.setProperty("db.password", "");
        properties.setProperty("db.driver",   "com.mysql.cj.jdbc.Driver");
    }

    /**
     * Get a JDBC connection.
     * The URL stored in db.url must NOT already contain query-string parameters;
     * this method appends the required MySQL flags cleanly.
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName(properties.getProperty("db.driver"));

            String rawUrl  = properties.getProperty("db.url");
            String user    = properties.getProperty("db.username");
            String pass    = properties.getProperty("db.password", "");

            // Strip any pre-existing query string so we never double-up the '?'
            String baseUrl = rawUrl.contains("?") ? rawUrl.substring(0, rawUrl.indexOf('?')) : rawUrl;

            String connectionUrl = baseUrl
                    + "?useSSL=false"
                    + "&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=UTC"
                    + "&createDatabaseIfNotExist=true";

            return DriverManager.getConnection(connectionUrl, user, pass);

        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC Driver not found: " + properties.getProperty("db.driver"), e);
        }
    }

    public static boolean testConnection() {
        try (Connection c = getConnection()) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public static String getDatabaseUrl()      { return properties.getProperty("db.url"); }
    public static String getDatabaseUsername() { return properties.getProperty("db.username"); }

    public static void reloadConfiguration() { loadProperties(); }
}
