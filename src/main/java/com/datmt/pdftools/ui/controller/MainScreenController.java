package com.datmt.pdftools.ui.controller;

import com.datmt.pdftools.util.CreditLinkHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
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
    public Button joinerButton;
    public Button compressorButton;
    public Button securityButton;
    public Button inserterButton;
    @FXML
    private Hyperlink creditLink;

    @FXML
    public void initialize() {
        CreditLinkHandler.setup(creditLink);
    }

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

    public void onJoinerClicked() {
        logger.info("User clicked PDF Joiner button");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/com/datmt/pdftools/ui/joiner/pdf-joiner.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1400, 800);

            Stage stage = new Stage();
            stage.setTitle("PDF Joiner");
            stage.setScene(scene);
            stage.setMinWidth(1400);
            stage.setMinHeight(800);
            stage.show();

            logger.info("PDF Joiner window opened");
        } catch (IOException e) {
            logger.error("Failed to load PDF Joiner window", e);
        }
    }

    public void onCompressorClicked() {
        logger.info("User clicked PDF Compressor button");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/com/datmt/pdftools/ui/compressor/pdf-compressor.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 900, 700);

            Stage stage = new Stage();
            stage.setTitle("PDF Compressor");
            stage.setScene(scene);
            stage.setMinWidth(900);
            stage.setMinHeight(700);
            stage.show();

            logger.info("PDF Compressor window opened");
        } catch (IOException e) {
            logger.error("Failed to load PDF Compressor window", e);
        }
    }

    public void onSecurityClicked() {
        logger.info("User clicked PDF Security button");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/com/datmt/pdftools/ui/security/pdf-security.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 800, 600);

            Stage stage = new Stage();
            stage.setTitle("PDF Security");
            stage.setScene(scene);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.show();

            logger.info("PDF Security window opened");
        } catch (IOException e) {
            logger.error("Failed to load PDF Security window", e);
        }
    }

    public void onInserterClicked() {
        logger.info("User clicked PDF Bulk Inserter button");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/com/datmt/pdftools/ui/inserter/pdf-bulk-inserter.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

            Stage stage = new Stage();
            stage.setTitle("PDF Bulk Inserter");
            stage.setScene(scene);
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.show();

            logger.info("PDF Bulk Inserter window opened");
        } catch (IOException e) {
            logger.error("Failed to load PDF Bulk Inserter window", e);
        }
    }
}
