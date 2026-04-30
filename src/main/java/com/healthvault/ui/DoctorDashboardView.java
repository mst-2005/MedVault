package com.healthvault.ui;

import com.healthvault.model.*;
import com.healthvault.service.*;
import com.healthvault.util.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;

import java.io.*;
import java.sql.*;
import java.security.*;
import java.time.*;
import java.util.*;

/**
 * Modern Doctor Dashboard with tabbed navigation: Dashboard (Stats), Search, and Upload.
 */
public class DoctorDashboardView {

    private final Stage stage;
    private final UserModel doctor;
    private final UserDAO userService = UserDAO.getInstance();
    private final FileVaultDAO fileVaultService = new FileVaultDAO();

    private final BorderPane root = new BorderPane();
    private final StackPane mainContentArea = new StackPane();
    private final VBox navContainer = new VBox(8);

    // Stats Labels
    private final Label totalPatientsStat = new Label("0");
    private final Label totalReportsStat   = new Label("0");
    private final Label recentUploadsStat  = new Label("0");

    // Search & Patient State
    private final TextField searchField = new TextField();
    private UserModel selectedPatient = null;
    private final Label activePatientLabel = new Label("No patient selected");
    private final Label activePatientId    = new Label("");

    // Records Table
    private final TableView<MedicalFileModel> recordsTable = new TableView<>();

    // Upload Form Fields
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final TextField descField = new TextField();
    private File selectedFile = null;
    private final Label fileNameLabel = new Label("No file selected");
    private final Label uploadStatus = new Label("");

    public DoctorDashboardView(Stage stage, UserModel doctor) {
        this.stage = stage;
        this.doctor = doctor;
        buildUI();
        showDashboardView(); // Default view
        refreshStats();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root.setStyle("-fx-background-color: #F8FAFC;");

        // ── Sidebar ──────────────────────────────────────────────────────────
        VBox sidebar = new VBox(10);
        sidebar.setPrefWidth(260);
        sidebar.setStyle("-fx-background-color: white; -fx-padding: 30 20; -fx-border-color: #E2E8F0; -fx-border-width: 0 1 0 0;");

        Label logo = new Label("🏥 HealthVault");
        logo.setFont(Font.font("Inter", FontWeight.BOLD, 22));
        logo.setStyle("-fx-text-fill: #1E293B; -fx-padding: 0 0 30 0;");

        refreshNav("Dashboard");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = new Button("🚪 Logout");
        logoutBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold;");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setPrefHeight(45);
        logoutBtn.setOnAction(e -> handleLogout());

        sidebar.getChildren().addAll(logo, navContainer, spacer, logoutBtn);
        root.setLeft(sidebar);

        // ── Top Bar ──────────────────────────────────────────────────────────
        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-padding: 15 40; -fx-border-color: #E2E8F0; -fx-border-width: 0 0 1 0;");
        
        Label welcome = new Label("Welcome, Dr. " + doctor.getName());
        welcome.setFont(Font.font("Inter", FontWeight.BOLD, 16));
        welcome.setStyle("-fx-text-fill: #1E293B;");
        
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        
        Circle avatar = new Circle(16, Color.web("#3B82F6"));
        topBar.getChildren().addAll(welcome, topSpacer, avatar);
        root.setTop(topBar);

        // ── Main Content Area ────────────────────────────────────────────────
        mainContentArea.setPadding(new Insets(30, 40, 30, 40));
        root.setCenter(mainContentArea);
    }

    private void handleLogout() {
        LoginView login = new LoginView(stage);
        stage.getScene().setRoot(login.getRoot());
    }

    private void refreshNav(String activeTab) {
        navContainer.getChildren().clear();
        navContainer.getChildren().addAll(
                createNavBtn("📊  Dashboard", activeTab.equals("Dashboard"), this::showDashboardView),
                createNavBtn("🔍  Search Patient", activeTab.equals("Search"), this::showSearchView),
                createNavBtn("📤  Upload Report", activeTab.equals("Upload"), this::showUploadView),
                createNavBtn("📋  Patient History", activeTab.equals("History"), this::showHistoryView),
                createNavBtn("⚙️  Settings", activeTab.equals("Settings"), () -> {})
        );
    }

    // ─── View Switchers ─────────────────────────────────────────────────────

    private void showDashboardView() {
        refreshNav("Dashboard");
        refreshStats();
        
        VBox view = new VBox(30);
        Label title = new Label("Analytics Overview");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 24));
        
        HBox statsRow = new HBox(20);
        statsRow.getChildren().addAll(
            createStatCard("Total Patients Served", totalPatientsStat, "#3B82F6"),
            createStatCard("Reports Uploaded", totalReportsStat, "#10B981"),
            createStatCard("Recent Activity (30d)", recentUploadsStat, "#F59E0B")
        );
        for (javafx.scene.Node node : statsRow.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        
        view.getChildren().addAll(title, statsRow);
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        mainContentArea.getChildren().setAll(scroll);
    }

        VBox chartCard = new VBox(15);
        chartCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 25; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        Label chartTitle = new Label("Reports Growth (Monthly)");
        chartTitle.setFont(Font.font("Inter", FontWeight.BOLD, 16));
        
        javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
        javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
        javafx.scene.chart.LineChart<String, Number> lineChart = new javafx.scene.chart.LineChart<>(xAxis, yAxis);
        lineChart.setLegendVisible(false);
        lineChart.setPrefHeight(300);
        lineChart.setCreateSymbols(true);

        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        String sql = "SELECT DATE_FORMAT(created_at, '%b') as month, COUNT(*) as count FROM medical_files " +
                     "WHERE doctor_name LIKE ? GROUP BY month ORDER BY MIN(created_at)";
        try (Connection conn = com.healthvault.config.DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + doctor.getName() + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    series.getData().add(new javafx.scene.chart.XYChart.Data<>(rs.getString("month"), rs.getInt("count")));
                }
            }
        } catch (SQLException e) { 
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Data Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to load report analytics: " + e.getMessage());
            alert.showAndWait();
        }
        
        lineChart.getData().add(series);
        chartCard.getChildren().addAll(chartTitle, lineChart);
        view.getChildren().add(chartCard);

        mainContentArea.getChildren().setAll(view);
    }

    private void showSearchView() {
        refreshNav("Search");
        VBox view = new VBox(25);
        
        Label title = new Label("Search & Manage Patients");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 24));

        VBox searchCard = new VBox(15);
        searchCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 25; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        
        searchField.setPromptText("Enter Health-ID (e.g. HV-2024-10001)");
        searchField.setPrefHeight(45);
        searchField.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #E2E8F0; -fx-padding: 0 15;");
        
        Button searchBtn = new Button("🔍  Search Patient");
        searchBtn.setMaxWidth(Double.MAX_VALUE);
        searchBtn.setPrefHeight(45);
        searchBtn.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand;");
        searchBtn.setOnAction(e -> handleSearch());

        searchCard.getChildren().addAll(new Label("Patient Health-ID"), searchField, searchBtn);

        VBox resultCard = new VBox(15);
        resultCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 25; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        
        activePatientLabel.setFont(Font.font("Inter", FontWeight.BOLD, 18));
        activePatientId.setStyle("-fx-text-fill: #64748B;");
        
        setupRecordsTable();
        resultCard.getChildren().addAll(new HBox(10, activePatientLabel, activePatientId), recordsTable);

        view.getChildren().addAll(title, searchCard, resultCard);
        mainContentArea.getChildren().setAll(view);
    }

    private void showUploadView() {
        refreshNav("Upload");
        VBox view = new VBox(25);
        
        Label title = new Label("Upload Medical Record");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 24));

        VBox uploadCard = new VBox(20);
        uploadCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 30; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        
        if (selectedPatient == null) {
            Label warn = new Label("⚠️ Please search and select a patient first in the 'Search' tab.");
            warn.setStyle("-fx-text-fill: #F59E0B; -fx-font-weight: bold;");
            uploadCard.getChildren().add(warn);
        } else {
            Label patInfo = new Label("Uploading for: " + selectedPatient.getName() + " (" + selectedPatient.getHealthId() + ")");
            patInfo.setStyle("-fx-text-fill: #3B82F6; -fx-font-weight: bold;");
            uploadCard.getChildren().add(patInfo);
        }

        GridPane grid = new GridPane();
        grid.setHgap(30); grid.setVgap(20);
        
        categoryBox.setItems(FXCollections.observableArrayList("PRESCRIPTION", "LAB_REPORT", "IMAGING", "VACCINATION", "OTHER"));
        categoryBox.setPromptText("Select Record Category");
        categoryBox.setPrefWidth(Double.MAX_VALUE);
        categoryBox.setPrefHeight(45);
        categoryBox.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #E2E8F0; -fx-padding: 0 5;");
        
        datePicker.setPrefHeight(45);
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.getEditor().setStyle("-fx-background-radius: 10 0 0 10; -fx-border-radius: 10 0 0 10;");
        datePicker.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #E2E8F0;");
        
        descField.setPromptText("Enter a brief description of the report...");
        descField.setPrefHeight(45);
        descField.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #E2E8F0; -fx-padding: 0 15;");
        
        Button chooseFile = new Button("📂  Browse Files");
        chooseFile.setPrefHeight(45);
        chooseFile.setPrefWidth(180);
        chooseFile.setStyle("-fx-background-color: white; -fx-border-color: #3B82F6; -fx-text-fill: #3B82F6; -fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold;");
        chooseFile.setOnAction(e -> handleSelectFile());
        
        Label catLbl = new Label("Category"); catLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");
        Label dateLbl = new Label("Report Date"); dateLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");
        Label descLbl = new Label("Description"); descLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");
        Label fileLbl = new Label("Document"); fileLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        grid.add(catLbl, 0, 0); grid.add(categoryBox, 1, 0);
        grid.add(dateLbl, 0, 1); grid.add(datePicker, 1, 1);
        grid.add(descLbl, 0, 2); grid.add(descField, 1, 2);
        grid.add(fileLbl, 0, 3); grid.add(new HBox(15, chooseFile, fileNameLabel), 1, 3);
        fileNameLabel.setStyle("-fx-text-fill: #64748B; -fx-font-style: italic;");
        fileNameLabel.setAlignment(Pos.CENTER_LEFT);
        fileNameLabel.setPrefHeight(45);

        Button uploadBtn = new Button("🚀  Securely Encrypt & Upload");
        uploadBtn.setPrefHeight(55);
        uploadBtn.setMaxWidth(Double.MAX_VALUE);
        uploadBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16; -fx-background-radius: 12; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(16, 185, 129, 0.2), 10, 0, 0, 5);");
        uploadBtn.setOnAction(e -> handleUpload());

        uploadCard.getChildren().addAll(grid, uploadBtn, uploadStatus);
        view.getChildren().addAll(title, uploadCard);
        mainContentArea.getChildren().setAll(view);
    }

    private void showHistoryView() {
        refreshNav("History");
        VBox view = new VBox(25);
        
        Label title = new Label("Recent Activity Logs");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 24));
        Label sub = new Label("Audit trail of your interactions with patient records");
        sub.setStyle("-fx-text-fill: #64748B;");

        TableView<AdminDashboardView.AuditLogEntry> logTable = new TableView<>();
        
        TableColumn<AdminDashboardView.AuditLogEntry, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setPrefWidth(180);
        
        TableColumn<AdminDashboardView.AuditLogEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        actionCol.setPrefWidth(120);
        
        TableColumn<AdminDashboardView.AuditLogEntry, String> detailCol = new TableColumn<>("Activity Details");
        detailCol.setCellValueFactory(new PropertyValueFactory<>("details"));
        
        logTable.getColumns().addAll(timeCol, actionCol, detailCol);
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        logTable.setPrefHeight(600);
        logTable.setPlaceholder(new Label("No recent activity logs found."));

        // Load logs for THIS doctor
        List<AdminDashboardView.AuditLogEntry> logs = loadDoctorSpecificLogs();
        logTable.setItems(FXCollections.observableArrayList(logs));

        view.getChildren().addAll(new VBox(5, title, sub), logTable);
        mainContentArea.getChildren().setAll(view);
    }

    private List<AdminDashboardView.AuditLogEntry> loadDoctorSpecificLogs() {
        List<AdminDashboardView.AuditLogEntry> logs = new ArrayList<>();
        String sql = "SELECT al.* FROM audit_logs al WHERE user_id = ? ORDER BY timestamp DESC LIMIT 50";
        try (Connection conn = com.healthvault.config.DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, doctor.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(new AdminDashboardView.AuditLogEntry(
                        rs.getTimestamp("timestamp").toString(),
                        doctor.getEmail(),
                        "DOCTOR",
                        rs.getString("action"),
                        "System",
                        rs.getString("details")
                    ));
                }
            }
        } catch (SQLException e) { 
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to load activity logs: " + e.getMessage());
            alert.showAndWait();
        }
        return logs;
    }

    // ─── Logic ───────────────────────────────────────────────────────────────

    private void setupRecordsTable() {
        recordsTable.getColumns().clear();
        TableColumn<MedicalFileModel, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("originalFileName"));
        TableColumn<MedicalFileModel, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        TableColumn<MedicalFileModel, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("uploadDate"));
        
        recordsTable.getColumns().addAll(nameCol, catCol, dateCol);
        recordsTable.setPrefHeight(300);
        recordsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        recordsTable.setPlaceholder(new Label("No patient selected or no records found."));
    }

    private void handleSearch() {
        String healthId = searchField.getText().trim();
        if (healthId.isEmpty()) return;

        Optional<UserModel> patient = userService.getUserByHealthId(healthId);
        if (patient.isPresent()) {
            selectedPatient = patient.get();
            activePatientLabel.setText(selectedPatient.getName());
            activePatientId.setText("(" + selectedPatient.getHealthId() + ")");
            List<MedicalFileModel> files = fileVaultService.getUserFiles(selectedPatient.getId(), null, null, null, null);
            recordsTable.setItems(FXCollections.observableArrayList(files));
        } else {
            selectedPatient = null;
            activePatientLabel.setText("Patient Not Found");
            activePatientId.setText("");
            recordsTable.setItems(FXCollections.observableArrayList());
        }
    }

    private void handleSelectFile() {
        FileChooser fc = new FileChooser();
        selectedFile = fc.showOpenDialog(stage);
        if (selectedFile != null) fileNameLabel.setText(selectedFile.getName());
    }

    private void handleUpload() {
        if (selectedPatient == null) {
            uploadStatus.setText("❌ Error: Select a patient first!");
            uploadStatus.setTextFill(Color.RED);
            return;
        }
        if (selectedFile == null || categoryBox.getValue() == null) {
            uploadStatus.setText("❌ Error: File and category are required.");
            uploadStatus.setTextFill(Color.RED);
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Security Check");
        dialog.setHeaderText("Enter your password to encrypt this record");
        dialog.setContentText("Password:");
        
        dialog.showAndWait().ifPresent(password -> {
            try {
                KeyManagementDAO kms = new KeyManagementDAO();
                if (!kms.hasUserKey(doctor.getId())) kms.generateUserKey(doctor.getId(), password);
                
                javax.crypto.SecretKey key;
                try {
                    key = kms.getUserKey(doctor.getId(), password);
                } catch (Exception e) {
                    if (e.getMessage() != null && (e.getMessage().contains("padding") || e.toString().contains("BadPaddingException"))) {
                        kms.deleteUserKey(doctor.getId());
                        kms.generateUserKey(doctor.getId(), password);
                        key = kms.getUserKey(doctor.getId(), password);
                    } else throw e;
                }

                fileVaultService.uploadFileForPatient(
                        selectedPatient.getId(), selectedFile,
                        MedicalFileModel.FileCategory.valueOf(categoryBox.getValue()),
                        descField.getText(), doctor.getName(), null,
                        datePicker.getValue(), null, key
                );

                uploadStatus.setText("✅ Record successfully encrypted and uploaded!");
                uploadStatus.setTextFill(Color.web("#10B981"));
                refreshStats();
                
            } catch (Exception ex) {
                uploadStatus.setText("❌ Error: " + ex.getMessage());
                uploadStatus.setTextFill(Color.RED);
            }
        });
    }

    private void refreshStats() {
        FileVaultDAO.DoctorStatistics stats = fileVaultService.getDoctorStatistics(doctor.getName());
        totalReportsStat.setText(String.valueOf(stats.getReportsUploaded()));
        totalPatientsStat.setText(String.valueOf(stats.getPatientsServed()));
        recentUploadsStat.setText(String.valueOf(stats.getRecentUploads()));
    }

    private VBox createNavBtn(String text, boolean active, Runnable action) {
        Button btn = new Button(text);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(45);
        
        String textColor = active ? "white" : "#475569";
        String bgColor = active ? "#3B82F6" : "transparent";
        
        btn.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor + "; " +
                    "-fx-padding: 0 20; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold;");
        
        if (!active) {
            btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #1E293B; -fx-padding: 0 20; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold;"));
            btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #475569; -fx-padding: 0 20; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold;"));
        }
        
        btn.setOnAction(e -> action.run());
        return new VBox(btn);
    }

    private VBox createStatCard(String title, Label val, String color) {
        VBox card = new VBox(10);
        card.setPrefWidth(260);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 20; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #64748B; -fx-font-size: 14;");
        val.setFont(Font.font("Inter", FontWeight.BOLD, 28));
        val.setStyle("-fx-text-fill: " + color + ";");
        card.getChildren().addAll(t, val);
        return card;
    }
}
