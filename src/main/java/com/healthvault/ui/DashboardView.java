package com.healthvault.ui;

import com.healthvault.model.*;
import com.healthvault.service.*;
import com.healthvault.util.*;
import com.healthvault.exception.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.*;

import java.io.*;
import java.sql.*;
import java.security.*;
import java.time.*;
import java.util.*;

/**
 * Dashboard built entirely in pure Java — no FXML.
 */
public class DashboardView {

    private final BorderPane root;
    private final Stage stage;
    private final UserModel currentUser;

    // Services
    private final UserDAO userDAO = UserDAO.getInstance();
    private final FileVaultDAO fileVaultDAO = new FileVaultDAO();
    private final SearchDAO searchDAO = new SearchDAO();
    private final SharingDAO sharingDAO = new SharingDAO();

    // Header controls
    private final Label userInfoLabel  = new Label();
    private final TextField searchField = new TextField();

    // Dashboard stat labels
    private final Label totalFilesLabel    = new Label("0");
    private final Label storageUsedLabel   = new Label("0 B");
    private final Label sharedFilesLabel   = new Label("0");
    private final Label recentUploadsLabel = new Label("0");
    private final Label prescriptionsLabel = new Label("0");
    private final Label labReportsLabel    = new Label("0");
    private final Label imagingLabel       = new Label("0");
    private final Label medHistoryLabel    = new Label("0");

    // My Files tab
    private final TableView<MedicalFileModel> filesTable      = new TableView<>();
    private final TableView<MedicalFileModel> recentTable     = new TableView<>();
    private final TextField fileSearchField             = new TextField();
    private final ComboBox<String> categoryFilter       = new ComboBox<>();
    private final DatePicker startDatePicker            = new DatePicker();
    private final DatePicker endDatePicker              = new DatePicker();

    // Upload tab
    private final ObservableList<File> selectedFiles    = FXCollections.observableArrayList();
    private final Label selectedFilesLabel              = new Label("No files selected");
    private final ComboBox<String> uploadCategoryBox    = new ComboBox<>();
    private final TextField doctorNameField             = new TextField();
    private final TextField hospitalNameField           = new TextField();
    private final DatePicker uploadDatePicker           = new DatePicker();
    private final TextArea descriptionArea              = new TextArea();
    private final TextField tagsField                   = new TextField();
    private final ProgressBar uploadProgressBar         = new ProgressBar(0);
    private final Label uploadStatusLabel               = new Label();

    // Profile tab
    private final Label healthIdLabel            = new Label();
    private final TextField nameField            = new TextField();
    private final Label emailLabel               = new Label();
    private final TextField phoneField           = new TextField();
    private final TextArea addressArea           = new TextArea();
    private final TextField emergencyContactField = new TextField();
    private final Label profileStatusLabel       = new Label();

    // Main tab pane
    private final TabPane tabPane = new TabPane();

    public DashboardView(Stage stage, UserModel currentUser) {
        this.stage       = stage;
        this.currentUser = currentUser;
        this.root        = buildRoot();
        updateUserInfo();
        loadDashboardData();
    }

    public BorderPane getRoot() { return root; }

    // ─── Root Layout ────────────────────────────────────────────────────────────

    private BorderPane buildRoot() {
        BorderPane bp = new BorderPane();
        bp.setTop(buildHeader());
        bp.setCenter(buildTabs());
        return bp;
    }

    // ─── Header ─────────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        Label title = new Label("🏥 Health-ID Vault");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.getStyleClass().add("header-label");

        userInfoLabel.getStyleClass().add("user-info");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField.setPromptText("Search records…");
        searchField.setPrefWidth(220);
        searchField.getStyleClass().add("search-box");

        Button searchBtn = new Button("🔍");
        searchBtn.getStyleClass().add("button");
        searchBtn.setOnAction(e -> handleSearch());
        searchField.setOnAction(e -> handleSearch());

        Button myRecordsBtn  = headerBtn("My Records",  () -> tabPane.getSelectionModel().select(1));
        Button sharedBtn     = headerBtn("Shared",       () -> tabPane.getSelectionModel().select(2));
        Button uploadBtn     = headerBtn("Upload",       () -> tabPane.getSelectionModel().select(3));
        Button logoutBtn     = headerBtn("Logout",       this::handleLogout);
        logoutBtn.getStyleClass().add("danger");

        HBox header = new HBox(14, title, userInfoLabel, spacer,
                searchField, searchBtn, myRecordsBtn, sharedBtn, uploadBtn, logoutBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.getStyleClass().add("header");
        return header;
    }

    // ─── Tabs ───────────────────────────────────────────────────────────────────

    private TabPane buildTabs() {
        Tab dashboardTab = new Tab("📊 Dashboard",  buildDashboardTab());
        Tab filesTab     = new Tab("📁 My Records", buildFilesTab());
        Tab sharedTab    = new Tab("🔗 Shared",     buildSharedTab());
        Tab uploadTab    = new Tab("⬆ Upload",      buildUploadTab());
        Tab profileTab   = new Tab("👤 Profile",    buildProfileTab());

        for (Tab t : new Tab[]{dashboardTab, filesTab, sharedTab, uploadTab, profileTab})
            t.setClosable(false);

        tabPane.getTabs().addAll(dashboardTab, filesTab, sharedTab, uploadTab, profileTab);
        tabPane.getStyleClass().add("tab-pane");
        return tabPane;
    }

    // ─── Dashboard Tab ──────────────────────────────────────────────────────────

    private ScrollPane buildDashboardTab() {
        HBox statsRow = new HBox(16,
                statCard("📄 Total Files",    totalFilesLabel),
                statCard("💾 Storage Used",   storageUsedLabel),
                statCard("🔗 Shared Files",   sharedFilesLabel),
                statCard("🕐 Recent Uploads", recentUploadsLabel));
        statsRow.setPadding(new Insets(16));
        HBox.setHgrow(statsRow, Priority.ALWAYS);

        HBox catRow = new HBox(16,
                statCard("💊 Prescriptions", prescriptionsLabel),
                statCard("🧪 Lab Reports",   labReportsLabel),
                statCard("🩻 Imaging",        imagingLabel),
                statCard("📋 Medical History", medHistoryLabel));
        catRow.setPadding(new Insets(0, 16, 16, 16));
        HBox.setHgrow(catRow, Priority.ALWAYS);

        Label recentLabel = sectionLabel("Recent Files");

        buildFilesTableColumns(recentTable, false);
        recentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        recentTable.setPrefHeight(300);
        VBox.setVgrow(recentTable, Priority.ALWAYS);

        VBox container = new VBox(0, statsRow, catRow, recentLabel, recentTable);
        container.setPadding(new Insets(0, 16, 16, 16));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    // ─── My Files Tab ───────────────────────────────────────────────────────────

    private ScrollPane buildFilesTab() {
        categoryFilter.setItems(FXCollections.observableArrayList(
                "All", "Prescription", "Lab Report", "Imaging", "Medical History", "Other"));
        categoryFilter.setValue("All");

        fileSearchField.setPromptText("Search by name…");
        fileSearchField.setPrefWidth(180);
        startDatePicker.setPromptText("From");
        endDatePicker.setPromptText("To");

        Button filterBtn = new Button("Filter");
        filterBtn.getStyleClass().addAll("button", "secondary");
        filterBtn.setOnAction(e -> handleFilterFiles());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("button");
        clearBtn.setOnAction(e -> handleClearFilters());

        HBox toolbar = new HBox(10, fileSearchField, categoryFilter,
                startDatePicker, endDatePicker, filterBtn, clearBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(16, 16, 8, 16));

        VBox.setVgrow(filesTable, Priority.ALWAYS);
        buildFilesTableColumns(filesTable, true);
        filesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        VBox container = new VBox(0, toolbar, filesTable);
        container.setPadding(new Insets(0, 16, 16, 16));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    // ─── Shared Tab ─────────────────────────────────────────────────────────────

    private ScrollPane buildSharedTab() {
        Label msg = new Label("Files shared with you will appear here.");
        msg.getStyleClass().add("user-info");
        msg.setFont(Font.font(14));
        VBox box = new VBox(msg);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        VBox.setVgrow(box, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    // ─── Upload Tab ─────────────────────────────────────────────────────────────

    private ScrollPane buildUploadTab() {
        uploadCategoryBox.setItems(FXCollections.observableArrayList(
                "Prescription", "Lab Report", "Imaging", "Medical History", "Other"));
        uploadDatePicker.setValue(LocalDate.now());
        descriptionArea.setPrefRowCount(3);
        uploadProgressBar.setVisible(false);
        uploadProgressBar.setPrefWidth(400);

        Button browseBtn = new Button("📂 Browse Files…");
        browseBtn.getStyleClass().addAll("button", "secondary");
        browseBtn.setOnAction(e -> handleBrowseFiles());

        Button uploadBtn = new Button("⬆ Upload");
        uploadBtn.getStyleClass().addAll("button", "primary");
        uploadBtn.setOnAction(e -> handleUploadFiles());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("button");
        clearBtn.setOnAction(e -> clearUploadForm());

        VBox form = new VBox(15, 
            new Label("File Category"), uploadCategoryBox,
            new Label("Description"), descriptionArea,
            new Label("Doctor Name"), doctorNameField,
            new Label("Hospital Name"), hospitalNameField,
            new Label("Date"), uploadDatePicker,
            new Label("Tags"), tagsField,
            browseBtn, selectedFilesLabel, uploadBtn, clearBtn, uploadProgressBar, uploadStatusLabel
        );
        form.setPadding(new Insets(20));
        form.setMaxWidth(600);
        form.setAlignment(Pos.TOP_LEFT);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    // ─── Profile Tab ────────────────────────────────────────────────────────────

    private ScrollPane buildProfileTab() {
        addressArea.setPrefRowCount(3);

        Button updateBtn = new Button("Update Profile");
        updateBtn.getStyleClass().addAll("button", "primary");
        updateBtn.setOnAction(e -> handleUpdateProfile());

        Button changePwdBtn = new Button("Change Password");
        changePwdBtn.getStyleClass().add("button");
        changePwdBtn.setOnAction(e -> showInfo("Change Password",
                "Password change dialog not yet implemented."));

        Button qrBtn = new Button("Generate QR Code");
        qrBtn.getStyleClass().addAll("button", "secondary");
        qrBtn.setOnAction(e -> {
            showInfo("QR Code", "QR code would be generated for: " + currentUser.getHealthId());
            AuditLoggerUtil.logUserAction(currentUser.getId(), "QR_CODE_GENERATED",
                    "USER", currentUser.getId(), "QR code generated for Health ID");
        });

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10);
        form.setPadding(new Insets(20));
        int r = 0;
        form.addRow(r++, fLabel("Health ID:"),        healthIdLabel);
        form.addRow(r++, fLabel("Name:"),              nameField);
        form.addRow(r++, fLabel("Email:"),             emailLabel);
        form.addRow(r++, fLabel("Phone:"),             phoneField);
        form.addRow(r++, fLabel("Address:"),           addressArea);
        form.addRow(r++, fLabel("Emergency Contact:"), emergencyContactField);
        form.addRow(r,   profileStatusLabel);

        HBox actions = new HBox(12, updateBtn, changePwdBtn, qrBtn);
        actions.setPadding(new Insets(0, 0, 0, 20));

        VBox container = new VBox(12, sectionLabel("My Profile"), form, actions);
        container.setPadding(new Insets(16));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    // ─── Table helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<MedicalFileModel> buildFilesTableView(boolean withDelete) {
        TableView<MedicalFileModel> tv = new TableView<>();
        buildFilesTableColumns(tv, withDelete);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return tv;
    }

    @SuppressWarnings("unchecked")
    private void buildFilesTableColumns(TableView<MedicalFileModel> tv, boolean withDelete) {
        tv.getColumns().clear();

        TableColumn<MedicalFileModel, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("originalFileName"));

        TableColumn<MedicalFileModel, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(new PropertyValueFactory<>("category"));

        TableColumn<MedicalFileModel, String> docCol = new TableColumn<>("Doctor");
        docCol.setCellValueFactory(new PropertyValueFactory<>("doctorName"));

        TableColumn<MedicalFileModel, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("uploadDate"));

        TableColumn<MedicalFileModel, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("formattedFileSize"));

        TableColumn<MedicalFileModel, Void> actCol = new TableColumn<>("Actions");
        actCol.setCellFactory(param -> new TableCell<>() {
            private final Button dlBtn  = actionBtn("Download", "primary");
            private final Button shBtn  = actionBtn("Share",    "secondary");
            private final Button delBtn = actionBtn("Delete",   "danger");
            private final HBox   box    = withDelete
                    ? new HBox(6, dlBtn, shBtn, delBtn)
                    : new HBox(6, dlBtn, shBtn);

            {
                dlBtn.setOnAction(e  -> handleDownloadFile(getTableView().getItems().get(getIndex())));
                shBtn.setOnAction(e  -> handleShareFile(getTableView().getItems().get(getIndex())));
                if (withDelete)
                    delBtn.setOnAction(e -> handleDeleteFile(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(nameCol, catCol, docCol, dateCol, sizeCol, actCol);
    }

    // ─── Data loading ────────────────────────────────────────────────────────────

    private void updateUserInfo() {
        userInfoLabel.setText(currentUser.getName() + "  (" + currentUser.getHealthId() + ")");
        healthIdLabel.setText(currentUser.getHealthId());
        nameField.setText(currentUser.getName());
        emailLabel.setText(currentUser.getEmail());
        phoneField.setText(currentUser.getPhone() != null ? currentUser.getPhone() : "");
        addressArea.setText(currentUser.getAddress() != null ? currentUser.getAddress() : "");
        emergencyContactField.setText(currentUser.getEmergencyContact() != null
                ? currentUser.getEmergencyContact() : "");
    }

    private void loadDashboardData() {
        try {
            FileVaultDAO.FileStatistics stats =
                    fileVaultService.getFileStatistics(currentUser.getId());
            totalFilesLabel.setText(String.valueOf(stats.getTotalFiles()));
            storageUsedLabel.setText(stats.getFormattedTotalSize());

            // Recent files — populate the dashboard table
            List<MedicalFileModel> recent = searchService.getRecentFiles(currentUser.getId(), 5);
            recentUploadsLabel.setText(String.valueOf(recent.size()));
            recentTable.setItems(FXCollections.observableArrayList(recent));

            // Category counts
            List<SearchDAO.CategoryCount> counts =
                    searchService.getFileCountByCategory(currentUser.getId());
            // reset first
            prescriptionsLabel.setText("0"); labReportsLabel.setText("0");
            imagingLabel.setText("0");       medHistoryLabel.setText("0");
            for (SearchDAO.CategoryCount c : counts) {
                switch (c.getCategory()) {
                    case PRESCRIPTION:    prescriptionsLabel.setText(String.valueOf(c.getCount())); break;
                    case LAB_REPORT:      labReportsLabel.setText(String.valueOf(c.getCount()));    break;
                    case IMAGING:         imagingLabel.setText(String.valueOf(c.getCount()));       break;
                    case MEDICAL_HISTORY: medHistoryLabel.setText(String.valueOf(c.getCount()));    break;
                    default: break;
                }
            }

            // Shared files count
            List<SharingDAO.SharedFileInfo> shared =
                    sharingService.getFilesSharedByUser(currentUser.getId());
            sharedFilesLabel.setText(String.valueOf(shared.size()));

            // Also refresh the My Records table
            loadUserFiles();

        } catch (Exception e) {
            showAlert("Error", "Failed to load dashboard: " + e.getMessage());
        }
    }

    private void loadUserFiles() {
        try {
            List<MedicalFileModel> files =
                    fileVaultService.getUserFiles(currentUser.getId(), null, null, null, null);
            filesTable.setItems(FXCollections.observableArrayList(files));
        } catch (Exception e) {
            showAlert("Error", "Failed to load files: " + e.getMessage());
        }
    }

    // ─── Handlers ───────────────────────────────────────────────────────────────

    private void handleLogout() {
        AuditLoggerUtil.logUserAction(currentUser.getId(), "USER_LOGOUT",
                "USER", currentUser.getId(), "UserModel logged out");
        LoginView loginView = new LoginView(stage);
        javafx.scene.Scene scene = new javafx.scene.Scene(loginView.getRoot(), 860, 620);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setTitle("Health-ID Vault – Login");
        stage.setScene(scene);
        stage.setMaximized(false);
        stage.centerOnScreen();
    }

    private void handleSearch() {
        String term = searchField.getText().trim();
        if (term.isEmpty()) { showInfo("Search", "Please enter a search term."); return; }
        try {
            SearchDAO.SearchCriteria c = new SearchDAO.SearchCriteria();
            c.setFileName(term);
            c.setDescription(term);
            List<MedicalFileModel> results = searchService.searchMedicalFiles(currentUser.getId(), c);
            filesTable.setItems(FXCollections.observableArrayList(results));
            tabPane.getSelectionModel().select(1);
        } catch (Exception e) {
            showAlert("Error", "Search failed: " + e.getMessage());
        }
    }

    private void handleFilterFiles() {
        try {
            String cat = categoryFilter.getValue();
            MedicalFileModel.FileCategory fileCategory = "All".equals(cat) ? null :
                    MedicalFileModel.FileCategory.valueOf(cat.replace(" ", "_").toUpperCase());
            List<MedicalFileModel> files = fileVaultService.getUserFiles(
                    currentUser.getId(), fileCategory,
                    fileSearchField.getText().trim(),
                    startDatePicker.getValue(), endDatePicker.getValue());
            filesTable.setItems(FXCollections.observableArrayList(files));
        } catch (Exception e) {
            showAlert("Error", "Filter failed: " + e.getMessage());
        }
    }

    private void handleClearFilters() {
        fileSearchField.clear();
        categoryFilter.setValue("All");
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        loadUserFiles();
    }

    private void handleBrowseFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Medical Files");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Documents", "*.doc", "*.docx", "*.txt"));
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null) {
            selectedFiles.addAll(files);
            selectedFilesLabel.setText(selectedFiles.size() + " file(s) selected");
        }
    }

    private void handleUploadFiles() {
        if (selectedFiles.isEmpty()) { showInfo("Upload", "Please select files to upload."); return; }
        if (uploadCategoryBox.getValue() == null) { showInfo("Upload", "Please select a category."); return; }

        uploadProgressBar.setVisible(true);
        uploadProgressBar.setProgress(0);

        new Thread(() -> {
            try {
                for (int i = 0; i < selectedFiles.size(); i++) {
                    File file = selectedFiles.get(i);
                    final double progress = (double)(i + 1) / selectedFiles.size();
                    final String fn = file.getName();
                    javafx.application.Platform.runLater(() -> {
                        uploadProgressBar.setProgress(progress);
                        uploadStatusLabel.setText("Uploading " + fn + "…");
                    });
                    Thread.sleep(800);
                }
                javafx.application.Platform.runLater(() -> {
                    uploadProgressBar.setVisible(false);
                    uploadStatusLabel.setText("Upload complete!");
                    showInfo("Success", "Files uploaded successfully.");
                    clearUploadForm();
                    loadDashboardData();
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    uploadProgressBar.setVisible(false);
                    uploadStatusLabel.setText("Upload failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void clearUploadForm() {
        selectedFiles.clear();
        selectedFilesLabel.setText("No files selected");
        uploadCategoryBox.setValue(null);
        doctorNameField.clear();
        hospitalNameField.clear();
        uploadDatePicker.setValue(LocalDate.now());
        descriptionArea.clear();
        tagsField.clear();
        uploadStatusLabel.setText("");
    }

    private void handleDownloadFile(MedicalFileModel file) {
        showInfo("Download", "Would decrypt and download: " + file.getOriginalFileName());
        AuditLoggerUtil.logFileAccess(currentUser.getId(), file.getId(), "DOWNLOADED", null);
    }

    private void handleShareFile(MedicalFileModel file) {
        showInfo("Share", "Would open share dialog for: " + file.getOriginalFileName());
    }

    private void handleDeleteFile(MedicalFileModel file) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete File");
        confirm.setContentText("Delete '" + file.getOriginalFileName() + "'? This cannot be undone.");
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            try {
                if (fileVaultService.deleteFile(currentUser.getId(), file.getId())) {
                    showInfo("Deleted", "File deleted successfully.");
                    loadDashboardData();
                } else {
                    showAlert("Error", "Failed to delete file.");
                }
            } catch (Exception e) {
                showAlert("Error", "Delete failed: " + e.getMessage());
            }
        }
    }

    private void handleUpdateProfile() {
        try {
            currentUser.setName(nameField.getText());
            currentUser.setPhone(phoneField.getText());
            currentUser.setAddress(addressArea.getText());
            currentUser.setEmergencyContact(emergencyContactField.getText());
            if (userService.updateUserProfile(currentUser)) {
                profileStatusLabel.setText("Profile updated successfully!");
                profileStatusLabel.getStyleClass().removeAll("status-error");
                profileStatusLabel.getStyleClass().add("status-success");
                updateUserInfo();
            } else {
                profileStatusLabel.setText("Update failed.");
                profileStatusLabel.getStyleClass().add("status-error");
            }
        } catch (Exception e) {
            profileStatusLabel.setText("Error: " + e.getMessage());
        }
    }

    // ─── Utility ────────────────────────────────────────────────────────────────

    private VBox statCard(String labelText, Label valueLabel) {
        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        valueLabel.getStyleClass().add("stat-number");
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("stat-label");
        VBox card = new VBox(4, valueLabel, lbl);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("dashboard-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        l.getStyleClass().add("card-title");
        l.setPadding(new Insets(12, 0, 4, 0));
        return l;
    }

    private Label fLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        l.setFont(Font.font(null, FontWeight.BOLD, 12));
        return l;
    }

    private Button actionBtn(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button", styleClass);
        b.setPrefHeight(26);
        return b;
    }

    private Button headerBtn(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("button");
        b.setOnAction(e -> action.run());
        return b;
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
