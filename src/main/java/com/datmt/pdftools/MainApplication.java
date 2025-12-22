package com.datmt.pdftools;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main application entry point for PDF Tools.
 */
public class MainApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApplication.class);

    @Override
    public void start(Stage stage) throws IOException {
        logger.info("=== PDF Tools Application Starting ===");
        logger.debug("JavaFX Version: {}", System.getProperty("javafx.version"));
        logger.debug("Java Version: {}", System.getProperty("java.version"));

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/datmt/pdftools/ui/main-screen.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);
            
            stage.setTitle("PDF Tools");
            stage.setScene(scene);
            
            logger.info("Main window initialized");
            stage.show();
            
            stage.setOnCloseRequest(event -> {
                logger.info("Application closing");
            });
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw e;
        }
    }

    public static void main(String[] args) {
        logger.trace("main() method called");
        launch();
    }
}
