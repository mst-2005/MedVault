package com.healthvault.controller;

import com.healthvault.model.UserModel;
import com.healthvault.service.FileVaultDAO;
import com.healthvault.ui.DoctorDashboardView;
import javafx.stage.Stage;

/**
 * Controller for the Doctor Dashboard view
 */
public class DoctorDashboardController {
    private final Stage stage;
    private final DoctorDashboardView view;
    private final UserModel doctor;
    private final FileVaultDAO fileVaultDAO = new FileVaultDAO();

    public DoctorDashboardController(Stage stage, DoctorDashboardView view, UserModel doctor) {
        this.stage = stage;
        this.view = view;
        this.doctor = doctor;
        initController();
    }

    private void initController() {
        // Doctor-specific event logic
    }
}
