package com.datmt.pdftools.ui.compressor;

import com.datmt.pdftools.service.PdfCompressor;
import com.datmt.pdftools.service.PdfCompressor.CompressResult;
import com.datmt.pdftools.service.PdfCompressor.CompressionLevel;
import com.datmt.pdftools.service.PdfCompressor.CompressionOptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datmt.pdftools.util.CreditLinkHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the PDF Compressor tool.
 */
public class PdfCompressorController {
    private static final Logger logger = LoggerFactory.getLogger(PdfCompressorController.class);

    @FXML private Button addFilesButton;
    @FXML private Button removeFileButton;
    @FXML private Button clearAllButton;
    @FXML private Button compressButton;

    @FXML private VBox filesContainer;
    @FXML private Label totalSizeLabel;

    @FXML private RadioButton lowCompressionRadio;
    @FXML private RadioButton mediumCompressionRadio;
    @FXML private RadioButton highCompressionRadio;
    @FXML private ToggleGroup compressionLevelGroup;

    @FXML private Slider imageQualitySlider;
    @FXML private Label imageQualityLabel;

    @FXML private CheckBox removeMetadataCheck;
    @FXML private CheckBox removeBookmarksCheck;
    @FXML private CheckBox addSuffixCheck;
    @FXML private CheckBox overwriteCheck;

    @FXML private VBox progressSection;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressDetailLabel;

    @FXML private VBox resultsSection;
    @FXML private VBox resultsContainer;
    @FXML private Label totalOriginalLabel;
    @FXML private Label totalCompressedLabel;
    @FXML private Label totalSavingsLabel;
    @FXML private Hyperlink creditLink;

    private final List<FileItem> fileItems = new ArrayList<>();
    private FileItem selectedFileItem = null;
    private final PdfCompressor compressor = new PdfCompressor();
    private final ExecutorService compressionExecutor = Executors.newSingleThreadExecutor();
    private boolean isCompressing = false;

    @FXML
    public void initialize() {
        logger.info("Initializing PDF Compressor");
        setupWindowCloseHandler();
        setupListeners();
        CreditLinkHandler.setup(creditLink);
        updateUI();
    }

    private void setupWindowCloseHandler() {
        Platform.runLater(() -> {
            if (addFilesButton.getScene() != null && addFilesButton.getScene().getWindow() != null) {
                addFilesButton.getScene().getWindow().setOnCloseRequest(event -> cleanup());
            }
        });
    }

    private void setupListeners() {
        // Compression level changes update the quality slider
        compressionLevelGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == lowCompressionRadio) {
                imageQualitySlider.setValue(85);
            } else if (newVal == mediumCompressionRadio) {
                imageQualitySlider.setValue(65);
            } else if (newVal == highCompressionRadio) {
                imageQualitySlider.setValue(45);
            }
        });

        // Quality slider updates label
        imageQualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            imageQualityLabel.setText(String.format("%.0f%%", newVal.doubleValue()));
        });

        // Overwrite and suffix are mutually exclusive
        overwriteCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                addSuffixCheck.setSelected(false);
            }
        });
        addSuffixCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                overwriteCheck.setSelected(false);
            }
        });
    }

    @FXML
    private void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF Files");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        List<File> files = fileChooser.showOpenMultipleDialog(addFilesButton.getScene().getWindow());
        if (files != null) {
            for (File file : files) {
                addFile(file);
            }
            updateUI();
        }
    }

    private void addFile(File file) {
        // Check if file already added
        for (FileItem item : fileItems) {
            if (item.getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                logger.debug("File already added: {}", file.getName());
                return;
            }
        }

        FileItem item = new FileItem(file, this::onFileSelected, this::onFileRemove);
        fileItems.add(item);
        filesContainer.getChildren().add(item.getNode());
        logger.info("Added file: {} ({})", file.getName(), PdfCompressor.formatFileSize(file.length()));
    }

    private void onFileSelected(FileItem item) {
        if (selectedFileItem != null) {
            selectedFileItem.setSelected(false);
        }
        selectedFileItem = item;
        item.setSelected(true);
        updateUI();
    }

    private void onFileRemove(FileItem item) {
        fileItems.remove(item);
        filesContainer.getChildren().remove(item.getNode());
        if (selectedFileItem == item) {
            selectedFileItem = null;
        }
        updateUI();
    }

    @FXML
    private void onRemoveFile() {
        if (selectedFileItem != null) {
            onFileRemove(selectedFileItem);
        }
    }

    @FXML
    private void onClearAll() {
        fileItems.clear();
        filesContainer.getChildren().clear();
        selectedFileItem = null;
        resultsSection.setVisible(false);
        resultsSection.setManaged(false);
        resultsContainer.getChildren().clear();
        updateUI();
    }

    @FXML
    private void onCompress() {
        if (fileItems.isEmpty() || isCompressing) {
            return;
        }

        // Build options
        CompressionOptions options = new CompressionOptions();
        if (lowCompressionRadio.isSelected()) {
            options.setLevel(CompressionLevel.LOW);
        } else if (highCompressionRadio.isSelected()) {
            options.setLevel(CompressionLevel.HIGH);
        } else {
            options.setLevel(CompressionLevel.MEDIUM);
        }
        options.setImageQuality((float) imageQualitySlider.getValue() / 100f);
        options.setRemoveMetadata(removeMetadataCheck.isSelected());
        options.setRemoveBookmarks(removeBookmarksCheck.isSelected());

        boolean addSuffix = addSuffixCheck.isSelected();
        boolean overwrite = overwriteCheck.isSelected();

        // Start compression
        isCompressing = true;
        updateUI();

        progressSection.setVisible(true);
        progressSection.setManaged(true);
        progressBar.setProgress(0);
        resultsSection.setVisible(false);
        resultsSection.setManaged(false);
        resultsContainer.getChildren().clear();

        List<File> filesToCompress = new ArrayList<>();
        for (FileItem item : fileItems) {
            filesToCompress.add(item.getFile());
        }

        int totalFiles = filesToCompress.size();

        // Process files sequentially (page-level parallelism is inside PdfCompressor)
        compressionExecutor.submit(() -> {
            List<CompressResult> allResults = new ArrayList<>();

            for (int i = 0; i < totalFiles; i++) {
                File inputFile = filesToCompress.get(i);
                final int fileIndex = i;

                Platform.runLater(() -> {
                    progressLabel.setText("Compressing: " + inputFile.getName() +
                            " (" + (fileIndex + 1) + "/" + totalFiles + ")");
                    progressBar.setProgress((double) fileIndex / totalFiles);
                });

                logger.info("Starting compression of: {}", inputFile.getName());

                // Determine output file
                File outputFile;
                if (overwrite) {
                    outputFile = new File(inputFile.getParent(),
                            inputFile.getName().replace(".pdf", "_temp_compress.pdf"));
                } else {
                    outputFile = new File(inputFile.getParent(),
                            inputFile.getName().replace(".pdf", "_compressed.pdf"));
                }

                // Compress with progress callback
                CompressResult result = compressor.compress(inputFile, outputFile, options,
                        (page, total, status) -> Platform.runLater(() ->
                                progressDetailLabel.setText(status + " - page " + page + "/" + total)));

                // If overwrite mode and successful, replace original
                if (overwrite && result.isSuccess()) {
                    try {
                        if (inputFile.delete() && outputFile.renameTo(inputFile)) {
                            result = new CompressResult(inputFile, inputFile,
                                    result.getOriginalSize(), result.getCompressedSize());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to replace original file: {}", e.getMessage());
                    }
                }

                allResults.add(result);
                logger.info("Finished compression of: {} (success={})", inputFile.getName(), result.isSuccess());
            }

            Platform.runLater(() -> {
                showResults(allResults);
                isCompressing = false;
                progressSection.setVisible(false);
                progressSection.setManaged(false);
                updateUI();
            });
        });
    }

    private void showResults(List<CompressResult> results) {
        resultsContainer.getChildren().clear();

        long totalOriginal = 0;
        long totalCompressed = 0;
        int successCount = 0;

        for (CompressResult result : results) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(result.getInputFile().getName());
            nameLabel.setMaxWidth(200);
            nameLabel.setStyle("-fx-font-size: 11;");

            if (result.isSuccess()) {
                successCount++;
                totalOriginal += result.getOriginalSize();
                totalCompressed += result.getCompressedSize();

                String sizeText = String.format("%s -> %s (-%,.0f%%)",
                        PdfCompressor.formatFileSize(result.getOriginalSize()),
                        PdfCompressor.formatFileSize(result.getCompressedSize()),
                        result.getCompressionRatio() * 100);

                Label sizeLabel = new Label(sizeText);
                sizeLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #4CAF50;");

                row.getChildren().addAll(nameLabel, sizeLabel);
            } else {
                Label errorLabel = new Label("Failed: " + result.getErrorMessage());
                errorLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #cc0000;");
                row.getChildren().addAll(nameLabel, errorLabel);
            }

            resultsContainer.getChildren().add(row);
        }

        // Update totals
        totalOriginalLabel.setText("Original: " + PdfCompressor.formatFileSize(totalOriginal));
        totalCompressedLabel.setText("Compressed: " + PdfCompressor.formatFileSize(totalCompressed));

        if (totalOriginal > 0) {
            double savings = (1.0 - (double) totalCompressed / totalOriginal) * 100;
            totalSavingsLabel.setText(String.format("Saved: %.1f%%", savings));
        } else {
            totalSavingsLabel.setText("Saved: 0%");
        }

        resultsSection.setVisible(true);
        resultsSection.setManaged(true);

        logger.info("Compression complete: {}/{} files successful", successCount, results.size());
    }

    private void updateUI() {
        boolean hasFiles = !fileItems.isEmpty();
        boolean hasSelection = selectedFileItem != null;

        removeFileButton.setDisable(!hasSelection || isCompressing);
        clearAllButton.setDisable(!hasFiles || isCompressing);
        compressButton.setDisable(!hasFiles || isCompressing);
        addFilesButton.setDisable(isCompressing);

        // Update total size
        long totalSize = 0;
        for (FileItem item : fileItems) {
            totalSize += item.getFile().length();
        }
        totalSizeLabel.setText(String.format("Total: %d files, %s",
                fileItems.size(), PdfCompressor.formatFileSize(totalSize)));
    }

    public void cleanup() {
        logger.info("Cleaning up PDF Compressor");
        compressionExecutor.shutdownNow();
        fileItems.clear();
        filesContainer.getChildren().clear();
    }

    /**
     * Represents a file item in the list.
     */
    private static class FileItem {
        private final File file;
        private final HBox node;
        private boolean selected = false;

        public FileItem(File file, java.util.function.Consumer<FileItem> onSelect,
                       java.util.function.Consumer<FileItem> onRemove) {
            this.file = file;
            this.node = createNode(onSelect, onRemove);
        }

        private HBox createNode(java.util.function.Consumer<FileItem> onSelect,
                               java.util.function.Consumer<FileItem> onRemove) {
            HBox box = new HBox(10);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setStyle("-fx-padding: 8; -fx-background-color: white; -fx-border-color: #e0e0e0; " +
                    "-fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");

            Label nameLabel = new Label(file.getName());
            nameLabel.setStyle("-fx-font-size: 13;");
            nameLabel.setMaxWidth(250);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Label sizeLabel = new Label(PdfCompressor.formatFileSize(file.length()));
            sizeLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666666;");

            Button removeBtn = new Button("X");
            removeBtn.setStyle("-fx-font-size: 10; -fx-padding: 2 6; -fx-background-color: #ff5252; " +
                    "-fx-text-fill: white; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> onRemove.accept(this));

            box.getChildren().addAll(nameLabel, sizeLabel, removeBtn);

            box.setOnMouseClicked(e -> onSelect.accept(this));

            return box;
        }

        public File getFile() {
            return file;
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
