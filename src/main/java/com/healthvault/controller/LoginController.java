package com.healthvault.controller;

import com.healthvault.model.UserModel;
import com.healthvault.service.UserDAO;
import com.healthvault.ui.LoginView;
import com.healthvault.ui.DashboardView;
import com.healthvault.ui.AdminDashboardView;
import com.healthvault.ui.DoctorDashboardView;
import com.healthvault.util.AuditLoggerUtil;
import com.healthvault.exception.AuthenticationException;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Controller for the Login view
 */
public class LoginController {
    private final Stage stage;
    private final LoginView view;
    private final UserDAO userDAO = UserDAO.getInstance();

    public LoginController(Stage stage, LoginView view) {
        this.stage = stage;
        this.view = view;
        initController();
    }

    private void initController() {
        view.getLoginButton().setOnAction(e -> handleLogin());
    }

    private void handleLogin() {
        String email = view.getEmail();
        String password = view.getPassword();
        String selectedRole = view.getSelectedRole();

        view.clearStatus();

        if (email.isEmpty() || password.isEmpty()) {
            view.showStatus("Please enter email and password", "error");
            return;
        }

        try {
            UserModel user = userDAO.authenticateUser(email, password);
            
            boolean roleValid = false;
            if (selectedRole.equals("ADMIN") && user.getEmail().contains("admin")) {
                roleValid = true;
            } else if (!selectedRole.equals("ADMIN") && user.getUserType().name().equals(selectedRole)) {
                roleValid = true;
            }
            
            if (!roleValid) {
                view.showStatus("Access denied: Please select your correct role", "error");
                AuditLoggerUtil.logAuthenticationEvent(email, "LOGIN_ROLE_MISMATCH", null, false);
                return;
            }

            view.showStatus("Login successful! Redirecting...", "success");
            AuditLoggerUtil.logAuthenticationEvent(email, "LOGIN", null, true);
            navigateToDashboard(user);

        } catch (AuthenticationException e) {
            view.showStatus(e.getMessage(), "error");
        } catch (Exception e) {
            view.showStatus("An error occurred during login", "error");
            e.printStackTrace();
        }
    }

    private void navigateToDashboard(UserModel user) {
        Scene scene;
        if (user.getEmail().contains("admin")) {
            AdminDashboardView adminView = new AdminDashboardView(stage, user);
            new com.healthvault.controller.AdminDashboardController(stage, adminView, user);
            scene = new Scene(adminView.getRoot(), 1200, 800);
        } else if (user.getUserType() == UserModel.UserType.DOCTOR) {
            DoctorDashboardView doctorView = new DoctorDashboardView(stage, user);
            new com.healthvault.controller.DoctorDashboardController(stage, doctorView, user);
            scene = new Scene(doctorView.getRoot(), 1200, 800);
        } else {
            DashboardView dashboardView = new DashboardView(stage, user);
            new com.healthvault.controller.DashboardController(stage, dashboardView, user);
            scene = new Scene(dashboardView.getRoot(), 1100, 750);
        }
        
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.centerOnScreen();
    }
}
