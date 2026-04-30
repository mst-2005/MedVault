package com.healthvault.ui;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;

/**
 * Pure View for the Login screen, focusing on UI layout and exposing controls.
 */
public class LoginView {

    private final VBox root;
    private final Stage stage;

    // Form controls
    private final TextField emailField      = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton         = new Button("Login Securely");
    private final Label  statusLabel         = new Label();
    private final ToggleGroup roleGroup      = new ToggleGroup();

    public LoginView(Stage stage) {
        this.stage = stage;
        this.root  = buildRoot();
    }

    public VBox getRoot() { return root; }
    public Button getLoginButton() { return loginButton; }
    public String getEmail() { return emailField.getText().trim(); }
    public String getPassword() { return passwordField.getText(); }
    public String getSelectedRole() { 
        RadioButton rb = (RadioButton) roleGroup.getSelectedToggle();
        return rb != null ? rb.getText().toUpperCase() : "";
    }

    private VBox buildRoot() {
        VBox container = new VBox(20);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(40));
        container.setStyle("-fx-background-color: #F8F9FA;"); 

        VBox card = new VBox(25);
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(450);
        card.setPadding(new Insets(30));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 20, 0, 0, 10);");

        // Header
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        Label title = new Label("Secure Health-ID Vault");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        Label subtitle = new Label("Login to access your secure medical records");
        subtitle.setFont(Font.font("Segoe UI", 14));
        subtitle.setTextFill(Color.web("#6B7280"));
        header.getChildren().addAll(title, subtitle);

        // Form
        VBox form = new VBox(15);
        
        emailField.setPromptText("Email or Health-ID");
        emailField.setPrefHeight(45);
        emailField.setStyle("-fx-background-radius: 8;");
        
        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(45);
        passwordField.setStyle("-fx-background-radius: 8;");

        HBox roles = new HBox(20);
        roles.setAlignment(Pos.CENTER);
        RadioButton r1 = new RadioButton("PATIENT"); r1.setToggleGroup(roleGroup); r1.setSelected(true);
        RadioButton r2 = new RadioButton("DOCTOR"); r2.setToggleGroup(roleGroup);
        RadioButton r3 = new RadioButton("ADMIN"); r3.setToggleGroup(roleGroup);
        roles.getChildren().addAll(r1, r2, r3);

        loginButton.setPrefWidth(Double.MAX_VALUE);
        loginButton.setPrefHeight(45);
        loginButton.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");

        statusLabel.setWrapText(true);
        statusLabel.setAlignment(Pos.CENTER);

        form.getChildren().addAll(
            new Label("Identity"), emailField,
            new Label("Secret Key"), passwordField,
            new Label("Access Role"), roles,
            loginButton, statusLabel
        );

        card.getChildren().addAll(header, form);
        container.getChildren().add(card);
        
        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return new VBox(scroll);
    }

    public void showStatus(String message, String type) {
        statusLabel.setText(message);
        if ("error".equals(type)) {
            statusLabel.setTextFill(Color.web("#DC2626"));
        } else {
            statusLabel.setTextFill(Color.web("#059669"));
        }
    }

    public void clearStatus() {
        statusLabel.setText("");
    }
}
