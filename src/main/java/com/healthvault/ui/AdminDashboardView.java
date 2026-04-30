package com.healthvault.ui;

import com.healthvault.config.*;
import com.healthvault.model.*;
import com.healthvault.service.*;
import com.healthvault.exception.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;

import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Enhanced Admin Dashboard with professional analytics and detailed access logs.
 */
public class AdminDashboardView {

    private final Stage stage;
    private final UserModel admin;
    private final BorderPane root = new BorderPane();
    private final StackPane mainContentArea = new StackPane();
    private final VBox navContainer = new VBox(8);
    private final UserDAO userService = UserDAO.getInstance();

    // Stats
    private final Label totalUsersStat = new Label("0");
    private final Label activeDoctorsStat = new Label("0");
    private final Label pendingApprovalsStat = new Label("0");

    public AdminDashboardView(Stage stage, UserModel admin) {
        this.stage = stage;
        this.admin = admin;
        buildUI();
        showDashboard();
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
        
        Label title = new Label("Admin Dashboard");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 18));
        
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        
        Circle avatar = new Circle(16, Color.web("#3B82F6"));
        topBar.getChildren().addAll(title, topSpacer, avatar);
        root.setTop(topBar);

        mainContentArea.setPadding(new Insets(30, 40, 30, 40));
        root.setCenter(mainContentArea);
    }

    private void refreshNav(String activeTab) {
        navContainer.getChildren().clear();
        navContainer.getChildren().addAll(
                createNavBtn("📊  Dashboard", activeTab.equals("Dashboard"), this::showDashboard),
                createNavBtn("👥  Manage Users", activeTab.equals("Users"), this::showUsers),
                createNavBtn("👨‍⚕️  Doctor Approvals", activeTab.equals("Approvals"), () -> {}),
                createNavBtn("📜  Access Logs", activeTab.equals("Logs"), this::showAccessLogs),
                createNavBtn("🛡️  Security", activeTab.equals("Security"), () -> {}),
                createNavBtn("📈  Analytics", activeTab.equals("Analytics"), () -> {})
        );
    }

    // ─── Views ───────────────────────────────────────────────────────────────

    private void showDashboard() {
        refreshNav("Dashboard");
        VBox view = new VBox(30);
        
        Label head = new Label("Admin Dashboard");
        head.setFont(Font.font("Inter", FontWeight.BOLD, 28));
        Label sub = new Label("Monitor and manage the Health-ID Vault system");
        sub.setStyle("-fx-text-fill: #64748B;");

        HBox statsRow = new HBox(20);
        statsRow.getChildren().addAll(
            createStatCard("Total Users", totalUsersStat, "👤", "+12% from last month"),
            createStatCard("Active Doctors", activeDoctorsStat, "👨‍⚕️", "Verified professionals"),
            createStatCard("Pending Approvals", pendingApprovalsStat, "📈", "Doctor registrations")
        );
        for (javafx.scene.Node node : statsRow.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        HBox chartsRow = new HBox(20);
        VBox userGrowth = buildUserGrowthChart();
        VBox accessActivity = buildAccessActivityChart();
        HBox.setHgrow(userGrowth, Priority.ALWAYS);
        HBox.setHgrow(accessActivity, Priority.ALWAYS);
        chartsRow.getChildren().addAll(userGrowth, accessActivity);

        view.getChildren().addAll(new VBox(5, head, sub), statsRow, chartsRow);
        mainContentArea.getChildren().setAll(new ScrollPane(view) {{ setFitToWidth(true); setStyle("-fx-background-color: transparent; -fx-background: transparent;"); }});
    }

    private VBox buildUserGrowthChart() {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 25; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        
        Label t = new Label("UserModel Growth");
        t.setFont(Font.font("Inter", FontWeight.BOLD, 16));
        Label s = new Label("Monthly user registration trends");
        s.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13;");

        javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
        javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
        javafx.scene.chart.LineChart<String, Number> lineChart = new javafx.scene.chart.LineChart<>(xAxis, yAxis);
        lineChart.setLegendVisible(false);
        lineChart.setPrefHeight(250);
        lineChart.setCreateSymbols(true);

        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        
        String sql = "SELECT DATE_FORMAT(created_at, '%b') as month, COUNT(*) as count FROM users " +
                     "GROUP BY month ORDER BY MIN(created_at)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                series.getData().add(new javafx.scene.chart.XYChart.Data<>(rs.getString("month"), rs.getInt("count")));
            }
        } catch (SQLException e) { 
            showAlert(Alert.AlertType.ERROR, "Data Error", "Failed to load user growth stats: " + e.getMessage());
        }

        lineChart.getData().add(series);
        card.getChildren().addAll(t, s, lineChart);
        return card;
    }

    private VBox buildAccessActivityChart() {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 25; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        
        Label t = new Label("Access Activity");
        t.setFont(Font.font("Inter", FontWeight.BOLD, 16));
        Label s = new Label("Weekly file views and uploads");
        s.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13;");

        javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
        javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
        javafx.scene.chart.BarChart<String, Number> barChart = new javafx.scene.chart.BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(250);

        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        
        String sql = "SELECT DATE_FORMAT(created_at, '%a') as day, COUNT(*) as count FROM audit_logs " +
                     "WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
                     "GROUP BY day ORDER BY MIN(created_at)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                series.getData().add(new javafx.scene.chart.XYChart.Data<>(rs.getString("day"), rs.getInt("count")));
            }
        } catch (SQLException e) { 
            showAlert(Alert.AlertType.ERROR, "Data Error", "Failed to load activity activity: " + e.getMessage());
        }

        barChart.getData().add(series);
        card.getChildren().addAll(t, s, barChart);
        return card;
    }

    private void showAccessLogs() {
        refreshNav("Logs");
        VBox view = new VBox(20);
        
        Label title = new Label("🛡️ Access Logs");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 22));
        Label sub = new Label("Complete audit trail of all system activities");
        sub.setStyle("-fx-text-fill: #64748B;");

        TextField searchLogs = new TextField();
        searchLogs.setPromptText("Search logs by user, action, or file...");
        searchLogs.setPrefHeight(40);
        searchLogs.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #E2E8F0;");

        TableView<AuditLogEntry> table = new TableView<>();
        
        TableColumn<AuditLogEntry, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        
        TableColumn<AuditLogEntry, String> userCol = new TableColumn<>("UserModel");
        userCol.setCellValueFactory(new PropertyValueFactory<>("userEmail"));
        
        TableColumn<AuditLogEntry, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setCellFactory(column -> new TableCell<AuditLogEntry, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); }
                else {
                    Label l = new Label(item);
                    l.setPadding(new Insets(2, 8, 2, 8));
                    l.setStyle("-fx-background-radius: 4; -fx-font-size: 10; -fx-font-weight: bold;");
                    if (item.equals("DOCTOR")) l.setStyle(l.getStyle() + "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF;");
                    else if (item.equals("PATIENT")) l.setStyle(l.getStyle() + "-fx-background-color: #ECFDF5; -fx-text-fill: #065F46;");
                    else l.setStyle(l.getStyle() + "-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B;");
                    setGraphic(l);
                }
            }
        });

        TableColumn<AuditLogEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        
        TableColumn<AuditLogEntry, String> targetCol = new TableColumn<>("Patient/Target");
        targetCol.setCellValueFactory(new PropertyValueFactory<>("target"));
        
        TableColumn<AuditLogEntry, String> detailCol = new TableColumn<>("File/Details");
        detailCol.setCellValueFactory(new PropertyValueFactory<>("details"));

        table.getColumns().addAll(timeCol, userCol, typeCol, actionCol, targetCol, detailCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(600);
        table.setItems(FXCollections.observableArrayList(loadDetailedLogs()));

        view.getChildren().addAll(new VBox(5, title, sub), searchLogs, table);
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        mainContentArea.getChildren().setAll(scroll);
    }

    private void showUsers() {
        refreshNav("Users");
        VBox view = new VBox(20);
        
        Label title = new Label("👥 UserModel Management");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 22));
        Label sub = new Label("Manage patients, doctors, and their access credentials");
        sub.setStyle("-fx-text-fill: #64748B;");

        HBox actionsRow = new HBox(10);
        Button addBtn = new Button("➕ Add UserModel");
        addBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 6; -fx-cursor: hand;");
        Button delBtn = new Button("🗑️ Delete Selected");
        delBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 6; -fx-cursor: hand;");
        Button pwdBtn = new Button("🔑 Change Password");
        pwdBtn.setStyle("-fx-background-color: #F59E0B; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 6; -fx-cursor: hand;");
        actionsRow.getChildren().addAll(addBtn, delBtn, pwdBtn);

        TableView<UserModel> table = new TableView<>();
        
        TableColumn<UserModel, String> idCol = new TableColumn<>("Health ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("healthId"));
        TableColumn<UserModel, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<UserModel, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        TableColumn<UserModel, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("userType"));
        
        table.getColumns().addAll(idCol, nameCol, emailCol, roleCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);
        
        Runnable loadData = () -> {
            table.setItems(FXCollections.observableArrayList(UserDAO.getInstance().getAllUsers()));
            refreshStats(); // Update stats whenever user list changes
        };
        loadData.run();

        addBtn.setOnAction(e -> {
            showAddUserDialog(userService, loadData);
        });

        delBtn.setOnAction(e -> {
            UserModel selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No UserModel Selected", "Please select a user to delete.");
                return;
            }
            if (selected.getId() == admin.getId()) {
                showAlert(Alert.AlertType.ERROR, "Action Denied", "You cannot delete your own admin account.");
                return;
            }
            
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete " + selected.getName() + "?");
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        if (userService.deleteUser(selected.getId())) {
                            showAlert(Alert.AlertType.INFORMATION, "Success", "UserModel deleted successfully.");
                            loadData.run();
                        }
                    } catch (UserModelException ex) {
                        showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
                    }
                }
            });
        });

        pwdBtn.setOnAction(e -> {
            UserModel selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No UserModel Selected", "Please select a user to change password.");
                return;
            }
            showChangePasswordDialog(userService, selected);
        });

        view.getChildren().addAll(new VBox(5, title, sub), actionsRow, table);
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        mainContentArea.getChildren().setAll(scroll);
    }

    private void showAddUserDialog(UserDAO userService, Runnable onSuccess) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add New UserModel");
        dialog.setHeaderText("Enter details for the new user.");
        
        VBox form = new VBox(10);
        TextField nameFld = new TextField(); nameFld.setPromptText("Full Name");
        TextField emailFld = new TextField(); emailFld.setPromptText("Email");
        PasswordField pwdFld = new PasswordField(); pwdFld.setPromptText("Password (min 8 chars, strong)");
        ComboBox<UserModel.UserType> roleCombo = new ComboBox<>(FXCollections.observableArrayList(UserModel.UserType.PATIENT, UserModel.UserType.DOCTOR));
        roleCombo.setValue(UserModel.UserType.PATIENT);
        
        form.getChildren().addAll(new Label("Name:"), nameFld, new Label("Email:"), emailFld, 
                                  new Label("Password:"), pwdFld, new Label("Role:"), roleCombo);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Prevent dialog from closing if registration fails
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                // For demo, using dummy data for phone, DOB, address, emergency contact
                userService.registerUser(nameFld.getText(), emailFld.getText(), pwdFld.getText(), 
                        roleCombo.getValue(), "0000000000", LocalDate.now().minusYears(25), "N/A", "N/A");
                showAlert(Alert.AlertType.INFORMATION, "Success", "UserModel registered successfully.");
                onSuccess.run();
            } catch (InvalidUserModelException ex) {
                showAlert(Alert.AlertType.ERROR, "Validation Error", ex.getMessage());
                event.consume(); // Keep dialog open
            } catch (UserModelException ex) {
                showAlert(Alert.AlertType.ERROR, "System Error", ex.getMessage());
                event.consume(); // Keep dialog open
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Registration Failed", "An unexpected error occurred: " + ex.getMessage());
                event.consume(); // Keep dialog open
            }
        });
        
        dialog.showAndWait();
    }

    private void showChangePasswordDialog(UserDAO userService, UserModel user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Set a new password for " + user.getName());
        
        VBox form = new VBox(10);
        PasswordField pwdFld = new PasswordField(); pwdFld.setPromptText("New Password (min 8 chars, strong)");
        
        form.getChildren().addAll(new Label("New Password:"), pwdFld);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                if (userService.adminChangePassword(user.getId(), pwdFld.getText())) {
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Password updated successfully.");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Failed", "Failed to change password. Ensure it meets strength requirements.");
                }
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ─── Logic ───────────────────────────────────────────────────────────────

    private void refreshStats() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql1 = "SELECT COUNT(*) FROM users";
            try (PreparedStatement stmt = conn.prepareStatement(sql1); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) totalUsersStat.setText(String.format("%,d", rs.getInt(1)));
            }
            String sql2 = "SELECT COUNT(*) FROM users WHERE user_type = 'DOCTOR'";
            try (PreparedStatement stmt = conn.prepareStatement(sql2); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) activeDoctorsStat.setText(String.format("%,d", rs.getInt(1)));
            }
            pendingApprovalsStat.setText("15"); // Mock pending registrations
        } catch (SQLException e) { 
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to load system statistics: " + e.getMessage());
        }
    }

    private List<AuditLogEntry> loadDetailedLogs() {
        List<AuditLogEntry> logs = new ArrayList<>();
        String sql = "SELECT al.*, u.email, u.user_type FROM audit_logs al " +
                     "LEFT JOIN users u ON al.user_id = u.id ORDER BY al.created_at DESC LIMIT 50";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                logs.add(new AuditLogEntry(
                    rs.getTimestamp("created_at").toString(),
                    rs.getString("email") != null ? rs.getString("email") : "SYSTEM",
                    rs.getString("user_type") != null ? rs.getString("user_type") : "ADMIN",
                    rs.getString("action_type"),
                    "Patient/Vault", // Simplified target
                    rs.getString("details")
                ));
            }
        } catch (SQLException e) { 
            showAlert(Alert.AlertType.ERROR, "Data Error", "Failed to load detailed audit logs: " + e.getMessage());
        }
        return logs;
    }

    private void handleLogout() {
        LoginView login = new LoginView(stage);
        stage.getScene().setRoot(login.getRoot());
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private VBox createStatCard(String title, Label val, String icon, String subText) {
        VBox card = new VBox(15);
        card.setPrefWidth(300);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 25; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        
        HBox head = new HBox(10);
        Label i = new Label(icon);
        i.setStyle("-fx-text-fill: #3B82F6; -fx-font-size: 18;");
        Label t = new Label(title);
        t.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        t.setStyle("-fx-text-fill: #1E293B;");
        head.getChildren().addAll(i, t);

        val.setFont(Font.font("Inter", FontWeight.BOLD, 32));
        val.setStyle("-fx-text-fill: #2563EB;");

        Label sub = new Label(subText);
        sub.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12;");

        card.getChildren().addAll(head, val, sub);
        return card;
    }

    private VBox buildChartPlaceholder(String title, String subtitle) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 25; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 10, 0, 0, 5);");
        
        Label t = new Label(title);
        t.setFont(Font.font("Inter", FontWeight.BOLD, 16));
        Label s = new Label(subtitle);
        s.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13;");
        
        Pane chartArea = new Pane();
        chartArea.setPrefHeight(200);
        Line x = new Line(0, 180, 400, 180); x.setStroke(Color.LIGHTGRAY);
        Line y = new Line(0, 0, 0, 180); y.setStroke(Color.LIGHTGRAY);
        chartArea.getChildren().addAll(x, y);

        card.getChildren().addAll(t, s, chartArea);
        return card;
    }

    private VBox createNavBtn(String text, boolean active, Runnable action) {
        Button btn = new Button(text);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(45);
        String base = "-fx-padding: 0 20; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold;";
        if (active) btn.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; " + base);
        else btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #475569; " + base);
        
        if (!active) {
            btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #1E293B; " + base));
            btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #475569; " + base));
        }
        btn.setOnAction(e -> action.run());
        return new VBox(btn);
    }

    public static class AuditLogEntry {
        private final SimpleStringProperty timestamp;
        private final SimpleStringProperty userEmail;
        private final SimpleStringProperty type;
        private final SimpleStringProperty action;
        private final SimpleStringProperty target;
        private final SimpleStringProperty details;

        public AuditLogEntry(String t, String u, String type, String a, String target, String d) {
            this.timestamp = new SimpleStringProperty(t);
            this.userEmail = new SimpleStringProperty(u);
            this.type = new SimpleStringProperty(type);
            this.action = new SimpleStringProperty(a);
            this.target = new SimpleStringProperty(target);
            this.details = new SimpleStringProperty(d);
        }

        public String getTimestamp() { return timestamp.get(); }
        public String getUserEmail() { return userEmail.get(); }
        public String getType() { return type.get(); }
        public String getAction() { return action.get(); }
        public String getTarget() { return target.get(); }
        public String getDetails() { return details.get(); }
    }
}
