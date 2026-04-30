package com.healthvault.controller;

import com.healthvault.model.UserModel;
import com.healthvault.service.UserDAO;
import com.healthvault.ui.AdminDashboardView;
import javafx.stage.Stage;

/**
 * Controller for the Admin Dashboard view
 */
public class AdminDashboardController {
    private final Stage stage;
    private final AdminDashboardView view;
    private final UserModel admin;
    private final UserDAO userDAO = UserDAO.getInstance();

    public AdminDashboardController(Stage stage, AdminDashboardView view, UserModel admin) {
        this.stage = stage;
        this.view = view;
        this.admin = admin;
        initController();
    }

    private void initController() {
        // Admin-specific event logic
    }
}
