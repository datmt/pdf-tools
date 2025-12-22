package com.datmt.pdftools.ui.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Controller for the main screen where users select which tool to use.
 */
public class MainScreenController {
    private static final Logger logger = LoggerFactory.getLogger(MainScreenController.class);

    public Button extractorButton;

    public void onExtractorClicked() {
        logger.info("User clicked PDF Extractor button");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/com/datmt/pdftools/ui/extractor/pdf-extractor.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1400, 800);
            
            Stage stage = new Stage();
            stage.setTitle("PDF Extractor");
            stage.setScene(scene);
            stage.setMinWidth(1400);
            stage.setMinHeight(800);
            stage.show();
            
            logger.info("PDF Extractor window opened");
        } catch (IOException e) {
            logger.error("Failed to load PDF Extractor window", e);
        }
    }
}
