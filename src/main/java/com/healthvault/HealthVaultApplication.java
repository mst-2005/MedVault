package com.healthvault;

import com.healthvault.config.DatabaseConfig;
import com.healthvault.service.UserDAO;
import com.healthvault.ui.LoginView;
import com.healthvault.util.AuditLoggerUtil;
import com.healthvault.util.HealthIdUtil;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Main application class for Cryptographically Secure Health-ID Vault.
 * All UI is built programmatically in pure Java — no FXML.
 */
public class HealthVaultApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(HealthVaultApplication.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            initializeDatabase();
            
            // Initialize Health ID sequence from database
            UserDAO userService = UserDAO.getInstance();
            HealthIdUtil.setInitialSequence(userService.getMaxHealthIdSequence());

            LoginView loginView = new LoginView(primaryStage);
            new com.healthvault.controller.LoginController(primaryStage, loginView);
            
            Scene scene = new Scene(loginView.getRoot(), 860, 620);
            String cssPath = getClass().getResource("/css/styles.css").toExternalForm();
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }

            primaryStage.setTitle("Health-ID Vault – Secure Medical Records");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true); // Allow zooming/resizing
            primaryStage.setMinWidth(600);
            primaryStage.setMinHeight(500);
            primaryStage.show();

            AuditLoggerUtil.logSystemEvent("APPLICATION_STARTUP");
            logger.info("Health-ID Vault started successfully");

        } catch (SQLException e) {
            logger.error("Database initialization failed", e);
            String troubleshootingMsg = "Failed to connect to the database.\n\n" +
                "Error Details: " + e.getMessage() + "\n\n" +
                "Would you like to open the Troubleshooting Guide?";
            
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Database Connection Error");
            alert.setHeaderText(null);
            alert.setContentText(troubleshootingMsg);
            
            javafx.scene.control.ButtonType openGuide = new javafx.scene.control.ButtonType("Open Troubleshooting Guide");
            javafx.scene.control.ButtonType close = new javafx.scene.control.ButtonType("Close", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            
            alert.getButtonTypes().setAll(openGuide, close);
            
            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == openGuide) {
                com.healthvault.ui.TroubleshootingView.show(null, com.healthvault.ui.TroubleshootingView.Section.DATABASE);
            }
        }
    }

    private void initializeDatabase() throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (connection == null) {
                throw new SQLException("Failed to establish database connection");
            }
            logger.info("Database connection established successfully");
        }
    }

    private void showErrorDialog(String title, String message) {
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        AuditLoggerUtil.logSystemEvent("APPLICATION_SHUTDOWN");
        logger.info("Health-ID Vault shutdown");
    }

    public static void main(String[] args) {
        try {
            launch(args);
        } catch (Exception e) {
            System.err.println("❌ Critical Error: Application failed to launch.");
            System.err.println("\nPossible JavaFX / Runtime Issues:");
            System.err.println("1. Use the provided execution scripts: run.sh (macOS/Linux) or compile_and_run.bat (Windows).");
            System.err.println("2. Ensure your JDK version is 17 or higher (Required for JavaFX 21).");
            System.err.println("3. Verify that your system supports the JavaFX graphics pipeline.");
            System.err.println("\nTechnical Details:");
            e.printStackTrace();
        }
    }
}
