package com.healthvault.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A dedicated window to display the README.md troubleshooting section
 * with automatic scrolling to relevant issues.
 */
public class TroubleshootingView {

    public enum Section {
        DATABASE("Database Connection Failed"),
        JAVAFX("JavaFX Startup Issues"),
        ENCRYPTION("Encryption Errors");

        private final String header;
        Section(String header) { this.header = header; }
        public String getHeader() { return header; }
    }

    public static void show(Stage owner, Section targetSection) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Health-ID Vault – Troubleshooting Guide");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f8f9fa;");

        Label title = new Label("System Troubleshooting Guide");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #2c3e50;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);

        TextFlow textFlow = new TextFlow();
        textFlow.setPadding(new Insets(10));
        textFlow.setLineSpacing(5);
        textFlow.setStyle("-fx-background-color: white;");

        // Load README content
        loadReadmeContent(textFlow, targetSection, scrollPane);

        scrollPane.setContent(textFlow);

        Button closeBtn = new Button("Close Guide");
        closeBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20;");
        closeBtn.setOnAction(e -> stage.close());

        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, scrollPane, footer);

        Scene scene = new Scene(root, 600, 500);
        stage.setScene(scene);
        stage.show();
    }

    private static void loadReadmeContent(TextFlow flow, Section target, ScrollPane scroll) {
        try (BufferedReader reader = new BufferedReader(new FileReader("README.md"))) {
            String line;
            boolean inTroubleshooting = false;
            double targetY = 0;
            double currentY = 0;

            while ((line = reader.readLine()) != null) {
                Text textNode = new Text(line + "\n");
                
                if (line.contains("## 🆘 Support")) {
                    inTroubleshooting = true;
                }

                if (inTroubleshooting) {
                    if (line.contains(target.getHeader())) {
                        textNode.setFill(javafx.scene.paint.Color.RED);
                        textNode.setFont(Font.font("System", FontWeight.BOLD, 14));
                        // Mark target for auto-scroll (simplification: we'll just remember the node)
                        targetY = currentY;
                    } else if (line.startsWith("###") || line.startsWith("##")) {
                        textNode.setFont(Font.font("System", FontWeight.BOLD, 13));
                        textNode.setFill(javafx.scene.paint.Color.DARKBLUE);
                    } else if (line.startsWith("> [!")) {
                         textNode.setFill(javafx.scene.paint.Color.ORANGE);
                         textNode.setFont(Font.font("System", FontPosture.ITALIC, 12));
                    }
                    
                    flow.getChildren().add(textNode);
                    // Approximate height for scrolling
                    currentY += 20; 
                }
            }

            // Auto-scroll logic after layout
            final double finalY = targetY;
            javafx.application.Platform.runLater(() -> {
                double totalHeight = flow.getBoundsInLocal().getHeight();
                if (totalHeight > 0) {
                    scroll.setVvalue(finalY / totalHeight);
                }
            });

        } catch (IOException e) {
            flow.getChildren().add(new Text("Error loading README.md: " + e.getMessage()));
        }
    }
}
