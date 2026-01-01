package com.datmt.pdftools.ui.inserter;

import com.datmt.pdftools.model.JoinerSection;
import com.datmt.pdftools.service.PdfBulkInserterService;
import com.datmt.pdftools.service.PdfBulkInserterService.InsertResult;
import com.datmt.pdftools.service.PdfBulkInserterService.InsertionMode;
import com.datmt.pdftools.service.PdfBulkInserterService.InsertionOptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datmt.pdftools.util.CreditLinkHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the PDF Bulk Inserter tool.
 */
public class PdfBulkInserterController {
    private static final Logger logger = LoggerFactory.getLogger(PdfBulkInserterController.class);

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    // Toolbar
    @FXML private Button selectSourceButton;
    @FXML private Label sourceFileLabel;
    @FXML private Button insertAllButton;

    // Target files
    @FXML private Button addFilesButton;
    @FXML private Button removeFileButton;
    @FXML private Button clearAllButton;
    @FXML private VBox filesContainer;
    @FXML private Label totalTargetsLabel;

    // Source preview
    @FXML private ImageView sourcePreviewImage;
    @FXML private Label noSourceLabel;
    @FXML private Label sourceNameLabel;
    @FXML private Label sourceInfoLabel;

    // Insertion mode
    @FXML private RadioButton atEndRadio;
    @FXML private RadioButton afterPageRadio;
    @FXML private RadioButton everyNPagesRadio;
    @FXML private ToggleGroup insertionModeGroup;
    @FXML private HBox afterPageOptions;
    @FXML private HBox everyNPagesOptions;
    @FXML private Spinner<Integer> afterPageSpinner;
    @FXML private Spinner<Integer> everyNPagesSpinner;
    @FXML private CheckBox insertAtEndIfShorterCheck;

    // Image options
    @FXML private VBox imageOptionsPane;
    @FXML private ComboBox<JoinerSection.PageSize> pageSizeCombo;
    @FXML private ComboBox<JoinerSection.FitOption> fitOptionCombo;

    // Output
    @FXML private TextField suffixField;

    // Progress
    @FXML private VBox progressSection;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressDetailLabel;

    // Results
    @FXML private VBox resultsSection;
    @FXML private VBox resultsContainer;
    @FXML private Label totalSuccessLabel;
    @FXML private Label totalFailedLabel;
    @FXML private Label totalInsertionsLabel;
    @FXML private Hyperlink creditLink;

    // State
    private File sourceFile;
    private boolean sourceIsPdf;
    private PDDocument sourceDocument;
    private final List<TargetFileItem> targetFileItems = new ArrayList<>();
    private TargetFileItem selectedTargetItem;
    private final PdfBulkInserterService inserterService = new PdfBulkInserterService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isInserting = false;

    @FXML
    public void initialize() {
        logger.info("Initializing PDF Bulk Inserter");
        setupWindowCloseHandler();
        setupListeners();
        setupComboBoxes();
        CreditLinkHandler.setup(creditLink);
        updateUI();
    }

    private void setupWindowCloseHandler() {
        Platform.runLater(() -> {
            if (selectSourceButton.getScene() != null && selectSourceButton.getScene().getWindow() != null) {
                selectSourceButton.getScene().getWindow().setOnCloseRequest(event -> cleanup());
            }
        });
    }

    private void setupListeners() {
        // Insertion mode changes
        insertionModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            updateModeOptionsVisibility();
        });
    }

    private void setupComboBoxes() {
        pageSizeCombo.getItems().addAll(JoinerSection.PageSize.values());
        pageSizeCombo.setValue(JoinerSection.PageSize.A4);

        fitOptionCombo.getItems().addAll(JoinerSection.FitOption.values());
        fitOptionCombo.setValue(JoinerSection.FitOption.FIT_TO_PAGE);
    }

    private void updateModeOptionsVisibility() {
        afterPageOptions.setDisable(!afterPageRadio.isSelected());
        everyNPagesOptions.setDisable(!everyNPagesRadio.isSelected());
    }

    @FXML
    private void onSelectSource() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF or Image to Insert");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF and Image Files", "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        File file = fileChooser.showOpenDialog(selectSourceButton.getScene().getWindow());
        if (file != null) {
            loadSource(file);
        }
    }

    private void loadSource(File file) {
        // Close previous source document
        closeSourceDocument();

        sourceFile = file;
        String fileName = file.getName().toLowerCase();
        sourceIsPdf = fileName.endsWith(".pdf");

        // Show/hide image options
        imageOptionsPane.setVisible(!sourceIsPdf);
        imageOptionsPane.setManaged(!sourceIsPdf);

        // Load preview
        executor.submit(() -> {
            try {
                Image thumbnail;
                int pageCount;

                if (sourceIsPdf) {
                    sourceDocument = inserterService.loadSourcePdf(file);
                    pageCount = sourceDocument.getNumberOfPages();
                    thumbnail = inserterService.renderThumbnailFromDocument(sourceDocument, 0);
                } else {
                    // Image source - create PDF from it (we'll create fresh each insertion)
                    pageCount = 1;
                    thumbnail = inserterService.renderThumbnail(file, false, 0);
                }

                final int finalPageCount = pageCount;
                final Image finalThumbnail = thumbnail;

                Platform.runLater(() -> {
                    sourcePreviewImage.setImage(finalThumbnail);
                    noSourceLabel.setVisible(false);
                    sourceNameLabel.setText(file.getName());
                    sourceInfoLabel.setText(sourceIsPdf ? finalPageCount + " page(s)" : "1 image");
                    sourceFileLabel.setText(file.getName());
                    updateUI();
                });
            } catch (IOException e) {
                logger.error("Error loading source: {}", e.getMessage());
                Platform.runLater(() -> {
                    showError("Failed to load source", e.getMessage());
                    clearSource();
                });
            }
        });
    }

    private void closeSourceDocument() {
        if (sourceDocument != null) {
            try {
                sourceDocument.close();
            } catch (IOException e) {
                logger.error("Error closing source document: {}", e.getMessage());
            }
            sourceDocument = null;
        }
    }

    private void clearSource() {
        closeSourceDocument();
        sourceFile = null;
        sourcePreviewImage.setImage(null);
        noSourceLabel.setVisible(true);
        sourceNameLabel.setText("-");
        sourceInfoLabel.setText("-");
        sourceFileLabel.setText("No source selected");
        imageOptionsPane.setVisible(false);
        imageOptionsPane.setManaged(false);
        updateUI();
    }

    @FXML
    private void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Target PDF Files");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        List<File> files = fileChooser.showOpenMultipleDialog(addFilesButton.getScene().getWindow());
        if (files != null) {
            for (File file : files) {
                addTargetFile(file);
            }
            updateUI();
        }
    }

    private void addTargetFile(File file) {
        // Check if file already added
        for (TargetFileItem item : targetFileItems) {
            if (item.getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                logger.debug("File already added: {}", file.getName());
                return;
            }
        }

        // Don't add the source file as a target
        if (sourceFile != null && sourceFile.getAbsolutePath().equals(file.getAbsolutePath())) {
            logger.debug("Cannot add source file as target: {}", file.getName());
            return;
        }

        // Get page count asynchronously
        executor.submit(() -> {
            try {
                int pageCount = inserterService.getPageCount(file);
                Platform.runLater(() -> {
                    TargetFileItem item = new TargetFileItem(file, pageCount, this::onTargetSelected, this::onTargetRemove);
                    targetFileItems.add(item);
                    filesContainer.getChildren().add(item.getNode());
                    updateUI();
                    logger.info("Added target file: {} ({} pages)", file.getName(), pageCount);
                });
            } catch (IOException e) {
                logger.error("Error loading target file: {}", e.getMessage());
                Platform.runLater(() -> showError("Failed to load", file.getName() + ": " + e.getMessage()));
            }
        });
    }

    private void onTargetSelected(TargetFileItem item) {
        if (selectedTargetItem != null) {
            selectedTargetItem.setSelected(false);
        }
        selectedTargetItem = item;
        item.setSelected(true);
        updateUI();
    }

    private void onTargetRemove(TargetFileItem item) {
        targetFileItems.remove(item);
        filesContainer.getChildren().remove(item.getNode());
        if (selectedTargetItem == item) {
            selectedTargetItem = null;
        }
        updateUI();
    }

    @FXML
    private void onRemoveFile() {
        if (selectedTargetItem != null) {
            onTargetRemove(selectedTargetItem);
        }
    }

    @FXML
    private void onClearAll() {
        targetFileItems.clear();
        filesContainer.getChildren().clear();
        selectedTargetItem = null;
        resultsSection.setVisible(false);
        resultsSection.setManaged(false);
        resultsContainer.getChildren().clear();
        updateUI();
    }

    @FXML
    private void onInsertAll() {
        if (sourceFile == null || targetFileItems.isEmpty() || isInserting) {
            return;
        }

        // Build options
        InsertionOptions options = buildOptions();

        // Start insertion
        isInserting = true;
        updateUI();

        progressSection.setVisible(true);
        progressSection.setManaged(true);
        progressBar.setProgress(0);
        resultsSection.setVisible(false);
        resultsSection.setManaged(false);
        resultsContainer.getChildren().clear();

        List<File> targetFiles = new ArrayList<>();
        for (TargetFileItem item : targetFileItems) {
            targetFiles.add(item.getFile());
        }

        int totalFiles = targetFiles.size();

        executor.submit(() -> {
            List<InsertResult> allResults = new ArrayList<>();
            PDDocument sourceDoc = null;

            try {
                // Load/create source document
                if (sourceIsPdf) {
                    // Reload the source PDF fresh for the operation
                    sourceDoc = inserterService.loadSourcePdf(sourceFile);
                } else {
                    sourceDoc = inserterService.createPdfFromImage(sourceFile, options);
                }

                final PDDocument finalSourceDoc = sourceDoc;

                for (int i = 0; i < totalFiles; i++) {
                    File targetFile = targetFiles.get(i);
                    final int fileIndex = i;

                    Platform.runLater(() -> {
                        progressLabel.setText("Processing: " + targetFile.getName() +
                                " (" + (fileIndex + 1) + "/" + totalFiles + ")");
                        progressBar.setProgress((double) fileIndex / totalFiles);
                    });

                    InsertResult result = inserterService.insertIntoTarget(targetFile, finalSourceDoc, options);
                    allResults.add(result);

                    final InsertResult finalResult = result;
                    Platform.runLater(() -> {
                        if (finalResult.isSuccess()) {
                            progressDetailLabel.setText(String.format("Inserted %d time(s), %d -> %d pages",
                                    finalResult.getInsertionsPerformed(),
                                    finalResult.getOriginalPageCount(),
                                    finalResult.getNewPageCount()));
                        } else {
                            progressDetailLabel.setText("Error: " + finalResult.getErrorMessage());
                        }
                    });
                }

            } catch (IOException e) {
                logger.error("Error during insertion: {}", e.getMessage());
                Platform.runLater(() -> showError("Insertion Error", e.getMessage()));
            } finally {
                // Close the source doc used for this operation
                if (sourceDoc != null && sourceDoc != sourceDocument) {
                    try {
                        sourceDoc.close();
                    } catch (IOException e) {
                        logger.error("Error closing source: {}", e.getMessage());
                    }
                }
            }

            Platform.runLater(() -> {
                showResults(allResults);
                isInserting = false;
                progressSection.setVisible(false);
                progressSection.setManaged(false);
                updateUI();
            });
        });
    }

    private InsertionOptions buildOptions() {
        InsertionOptions options = new InsertionOptions();

        if (atEndRadio.isSelected()) {
            options.setMode(InsertionMode.AT_END);
        } else if (afterPageRadio.isSelected()) {
            options.setMode(InsertionMode.AFTER_PAGE);
            options.setPageNumber(afterPageSpinner.getValue());
        } else if (everyNPagesRadio.isSelected()) {
            options.setMode(InsertionMode.EVERY_N_PAGES);
            options.setInterval(everyNPagesSpinner.getValue());
        }

        options.setInsertAtEndIfShorter(insertAtEndIfShorterCheck.isSelected());
        options.setOutputSuffix(suffixField.getText());

        // Image options
        options.setPageSize(pageSizeCombo.getValue());
        options.setFitOption(fitOptionCombo.getValue());

        return options;
    }

    private void showResults(List<InsertResult> results) {
        resultsContainer.getChildren().clear();

        int successCount = 0;
        int failedCount = 0;
        int totalInsertions = 0;

        for (InsertResult result : results) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(result.getInputFile().getName());
            nameLabel.setMaxWidth(200);
            nameLabel.setStyle("-fx-font-size: 11;");

            if (result.isSuccess()) {
                successCount++;
                totalInsertions += result.getInsertionsPerformed();

                String statusText = String.format("%d -> %d pages (+%d)",
                        result.getOriginalPageCount(),
                        result.getNewPageCount(),
                        result.getInsertionsPerformed());

                Label statusLabel = new Label(statusText);
                statusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #4CAF50;");

                row.getChildren().addAll(nameLabel, statusLabel);
            } else {
                failedCount++;
                Label errorLabel = new Label("Failed: " + result.getErrorMessage());
                errorLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #cc0000;");
                row.getChildren().addAll(nameLabel, errorLabel);
            }

            resultsContainer.getChildren().add(row);
        }

        // Update totals
        totalSuccessLabel.setText("Success: " + successCount);
        totalFailedLabel.setText("Failed: " + failedCount);
        totalFailedLabel.setStyle(failedCount > 0 ? "-fx-font-size: 13; -fx-text-fill: #cc0000;" : "-fx-font-size: 13;");
        totalInsertionsLabel.setText("Total insertions: " + totalInsertions);

        resultsSection.setVisible(true);
        resultsSection.setManaged(true);

        logger.info("Insertion complete: {}/{} files successful, {} total insertions",
                successCount, results.size(), totalInsertions);
    }

    private void updateUI() {
        boolean hasSource = sourceFile != null;
        boolean hasTargets = !targetFileItems.isEmpty();
        boolean hasSelection = selectedTargetItem != null;

        insertAllButton.setDisable(!hasSource || !hasTargets || isInserting);
        removeFileButton.setDisable(!hasSelection || isInserting);
        clearAllButton.setDisable(!hasTargets || isInserting);
        addFilesButton.setDisable(isInserting);
        selectSourceButton.setDisable(isInserting);

        // Update total targets
        int totalPages = 0;
        for (TargetFileItem item : targetFileItems) {
            totalPages += item.getPageCount();
        }
        totalTargetsLabel.setText(String.format("Total: %d files, %d pages",
                targetFileItems.size(), totalPages));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void cleanup() {
        logger.info("Cleaning up PDF Bulk Inserter");
        executor.shutdownNow();
        closeSourceDocument();
        targetFileItems.clear();
        filesContainer.getChildren().clear();
    }

    /**
     * Represents a target file item in the list.
     */
    private static class TargetFileItem {
        private final File file;
        private final int pageCount;
        private final HBox node;
        private boolean selected = false;

        public TargetFileItem(File file, int pageCount,
                              java.util.function.Consumer<TargetFileItem> onSelect,
                              java.util.function.Consumer<TargetFileItem> onRemove) {
            this.file = file;
            this.pageCount = pageCount;
            this.node = createNode(onSelect, onRemove);
        }

        private HBox createNode(java.util.function.Consumer<TargetFileItem> onSelect,
                                java.util.function.Consumer<TargetFileItem> onRemove) {
            HBox box = new HBox(10);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setStyle("-fx-padding: 8; -fx-background-color: white; -fx-border-color: #e0e0e0; " +
                    "-fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");

            Label nameLabel = new Label(file.getName());
            nameLabel.setStyle("-fx-font-size: 13;");
            nameLabel.setMaxWidth(250);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Label pagesLabel = new Label(pageCount + " pages");
            pagesLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666666;");

            Button removeBtn = new Button("X");
            removeBtn.setStyle("-fx-font-size: 10; -fx-padding: 2 6; -fx-background-color: #ff5252; " +
                    "-fx-text-fill: white; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> onRemove.accept(this));

            box.getChildren().addAll(nameLabel, pagesLabel, removeBtn);

            box.setOnMouseClicked(e -> onSelect.accept(this));

            return box;
        }

        public File getFile() {
            return file;
        }

        public int getPageCount() {
            return pageCount;
        }

        public HBox getNode() {
            return node;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            if (selected) {
                node.setStyle("-fx-padding: 8; -fx-background-color: #e3f2fd; -fx-border-color: #2196F3; " +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
            } else {
                node.setStyle("-fx-padding: 8; -fx-background-color: white; -fx-border-color: #e0e0e0; " +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
            }
        }
    }
}
