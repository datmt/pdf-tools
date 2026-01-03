package com.datmt.pdftools.ui.renamer;

import com.datmt.pdftools.model.RenameItem;
import com.datmt.pdftools.model.RenameItem.RenameStatus;
import com.datmt.pdftools.service.PdfTitleExtractor;
import com.datmt.pdftools.util.CreditLinkHandler;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Controller for the PDF Bulk Renamer tool.
 * Extracts titles from PDFs and renames files accordingly.
 */
public class PdfBulkRenamerController {
    private static final Logger logger = LoggerFactory.getLogger(PdfBulkRenamerController.class);

    @FXML private Button addFilesButton;
    @FXML private Button addFolderButton;
    @FXML private CheckBox includeSubfoldersCheck;
    @FXML private Button removeSelectedButton;
    @FXML private Button clearAllButton;
    @FXML private Label statusLabel;

    @FXML private TableView<RenameItem> fileTable;
    @FXML private TableColumn<RenameItem, Boolean> selectColumn;
    @FXML private TableColumn<RenameItem, String> originalNameColumn;
    @FXML private TableColumn<RenameItem, String> extractedTitleColumn;
    @FXML private TableColumn<RenameItem, String> newFilenameColumn;
    @FXML private TableColumn<RenameItem, RenameStatus> statusColumn;

    @FXML private HBox progressSection;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    @FXML private Label statsLabel;
    @FXML private Button scanButton;
    @FXML private Button renameButton;

    @FXML private HBox resultsSection;
    @FXML private Label resultsLabel;
    @FXML private Hyperlink creditLink;

    private final ObservableList<RenameItem> items = FXCollections.observableArrayList();
    private final Map<RenameItem, SimpleBooleanProperty> selectionMap = new HashMap<>();
    private final PdfTitleExtractor titleExtractor = new PdfTitleExtractor();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isProcessing = false;

    @FXML
    public void initialize() {
        logger.info("Initializing PDF Bulk Renamer");
        setupWindowCloseHandler();
        setupTable();
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

    private void setupTable() {
        fileTable.setItems(items);
        fileTable.setEditable(true);
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Checkbox column for selection
        selectColumn.setCellFactory(col -> new CheckBoxTableCell<>());
        selectColumn.setCellValueFactory(cellData -> {
            RenameItem item = cellData.getValue();
            SimpleBooleanProperty prop = selectionMap.computeIfAbsent(item, k -> new SimpleBooleanProperty(true));
            prop.addListener((obs, oldVal, newVal) -> updateUI());
            return prop;
        });

        // Original filename (read-only)
        originalNameColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getOriginalFilename()));

        // Extracted title (read-only)
        extractedTitleColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getExtractedTitle() != null ?
                                cellData.getValue().getExtractedTitle() : "(no title found)"));
        extractedTitleColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("(no title found)")) {
                        setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // New filename (editable)
        newFilenameColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNewFilename()));
        newFilenameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        newFilenameColumn.setOnEditCommit(event -> {
            RenameItem item = event.getRowValue();
            String newValue = event.getNewValue();
            if (newValue != null && !newValue.isBlank()) {
                // Ensure .pdf extension
                if (!newValue.toLowerCase().endsWith(".pdf")) {
                    newValue = newValue + ".pdf";
                }
                item.setNewFilename(newValue);
                if (item.getStatus() == RenameStatus.NO_TITLE) {
                    item.setStatus(RenameStatus.READY);
                }
            }
            fileTable.refresh();
            updateUI();
        });

        // Status column with colored display
        statusColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getStatus()));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(RenameStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    switch (status) {
                        case PENDING -> {
                            setText("Pending");
                            setStyle("-fx-text-fill: #888888;");
                        }
                        case READY -> {
                            setText("Ready");
                            setStyle("-fx-text-fill: #2196F3;");
                        }
                        case NO_TITLE -> {
                            setText("No Title");
                            setStyle("-fx-text-fill: #ff9800;");
                        }
                        case SUCCESS -> {
                            setText("Renamed");
                            setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                        }
                        case SKIPPED -> {
                            setText("Skipped");
                            setStyle("-fx-text-fill: #9e9e9e;");
                        }
                        case ERROR -> {
                            setText("Error");
                            setStyle("-fx-text-fill: #f44336;");
                        }
                    }
                }
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

    @FXML
    private void onAddFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Folder with PDF Files");

        File directory = dirChooser.showDialog(addFolderButton.getScene().getWindow());
        if (directory != null) {
            addFilesFromDirectory(directory, includeSubfoldersCheck.isSelected());
            updateUI();
        }
    }

    private void addFilesFromDirectory(File directory, boolean includeSubfolders) {
        logger.info("Scanning directory: {} (subfolders: {})", directory.getAbsolutePath(), includeSubfolders);

        try {
            int maxDepth = includeSubfolders ? Integer.MAX_VALUE : 1;
            try (Stream<Path> paths = Files.walk(directory.toPath(), maxDepth)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                        .map(Path::toFile)
                        .forEach(this::addFile);
            }
        } catch (IOException e) {
            logger.error("Failed to scan directory: {}", e.getMessage());
            showError("Failed to scan directory: " + e.getMessage());
        }
    }

    private void addFile(File file) {
        // Check if file already added
        for (RenameItem item : items) {
            if (item.getOriginalFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                logger.debug("File already added: {}", file.getName());
                return;
            }
        }

        RenameItem item = new RenameItem(file);
        items.add(item);
        selectionMap.put(item, new SimpleBooleanProperty(true));
        logger.debug("Added file: {}", file.getName());
    }

    @FXML
    private void onRemoveSelected() {
        List<RenameItem> toRemove = new ArrayList<>();
        for (RenameItem item : items) {
            SimpleBooleanProperty selected = selectionMap.get(item);
            if (selected != null && selected.get()) {
                toRemove.add(item);
            }
        }

        items.removeAll(toRemove);
        for (RenameItem item : toRemove) {
            selectionMap.remove(item);
        }
        updateUI();
    }

    @FXML
    private void onClearAll() {
        items.clear();
        selectionMap.clear();
        resultsSection.setVisible(false);
        resultsSection.setManaged(false);
        updateUI();
    }

    @FXML
    private void onScanTitles() {
        if (items.isEmpty() || isProcessing) {
            return;
        }

        isProcessing = true;
        updateUI();

        progressSection.setVisible(true);
        progressSection.setManaged(true);
        progressBar.setProgress(0);

        List<RenameItem> itemsToScan = new ArrayList<>(items);
        int total = itemsToScan.size();

        executor.submit(() -> {
            for (int i = 0; i < total; i++) {
                RenameItem item = itemsToScan.get(i);
                final int index = i;

                Platform.runLater(() -> {
                    progressLabel.setText("Scanning: " + item.getOriginalFilename() +
                            " (" + (index + 1) + "/" + total + ")");
                    progressBar.setProgress((double) index / total);
                });

                // Extract title
                String title = titleExtractor.extractTitle(item.getOriginalFile());

                if (title != null && !title.isBlank()) {
                    String filename = titleExtractor.generateFilename(title);
                    String uniqueFilename = titleExtractor.generateUniqueFilename(
                            item.getOriginalFile().getParentFile(), filename);

                    item.setExtractedTitle(title);
                    if (uniqueFilename != null) {
                        item.setNewFilename(uniqueFilename);
                        item.setStatus(RenameStatus.READY);
                    } else {
                        item.setNewFilename(item.getOriginalFilename());
                        item.setStatus(RenameStatus.NO_TITLE);
                        item.setErrorMessage("Could not generate unique filename");
                    }
                } else {
                    item.setExtractedTitle(null);
                    item.setNewFilename(item.getOriginalFilename());
                    item.setStatus(RenameStatus.NO_TITLE);
                }
            }

            Platform.runLater(() -> {
                progressSection.setVisible(false);
                progressSection.setManaged(false);
                isProcessing = false;
                fileTable.refresh();
                updateUI();
                logger.info("Title scanning complete: {} files processed", total);
            });
        });
    }

    @FXML
    private void onRenameAll() {
        if (items.isEmpty() || isProcessing) {
            return;
        }

        // Count items ready for rename
        List<RenameItem> itemsToRename = new ArrayList<>();
        for (RenameItem item : items) {
            SimpleBooleanProperty selected = selectionMap.get(item);
            if (selected != null && selected.get() && item.getStatus() == RenameStatus.READY && item.needsRename()) {
                itemsToRename.add(item);
            }
        }

        if (itemsToRename.isEmpty()) {
            showError("No files are ready to rename. Click 'Scan Titles' first.");
            return;
        }

        // Confirm rename
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Rename");
        confirm.setHeaderText("Rename " + itemsToRename.size() + " files?");
        confirm.setContentText("This will rename the selected PDF files based on their extracted titles.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        isProcessing = true;
        updateUI();

        progressSection.setVisible(true);
        progressSection.setManaged(true);
        progressBar.setProgress(0);

        int total = itemsToRename.size();

        executor.submit(() -> {
            int successCount = 0;
            int errorCount = 0;

            for (int i = 0; i < total; i++) {
                RenameItem item = itemsToRename.get(i);
                final int index = i;

                Platform.runLater(() -> {
                    progressLabel.setText("Renaming: " + item.getOriginalFilename() +
                            " (" + (index + 1) + "/" + total + ")");
                    progressBar.setProgress((double) index / total);
                });

                try {
                    File targetFile = item.getTargetFile();

                    // Check if target already exists (shouldn't happen with unique filename, but be safe)
                    if (targetFile.exists()) {
                        String unique = titleExtractor.generateUniqueFilename(
                                item.getOriginalFile().getParentFile(), item.getNewFilename());
                        if (unique != null) {
                            item.setNewFilename(unique);
                            targetFile = item.getTargetFile();
                        }
                    }

                    // Perform rename
                    if (item.getOriginalFile().renameTo(targetFile)) {
                        item.setStatus(RenameStatus.SUCCESS);
                        successCount++;
                        logger.info("Renamed: {} -> {}", item.getOriginalFilename(), item.getNewFilename());
                    } else {
                        item.setStatus(RenameStatus.ERROR);
                        item.setErrorMessage("Rename failed");
                        errorCount++;
                        logger.warn("Failed to rename: {}", item.getOriginalFilename());
                    }
                } catch (Exception e) {
                    item.setStatus(RenameStatus.ERROR);
                    item.setErrorMessage(e.getMessage());
                    errorCount++;
                    logger.error("Error renaming {}: {}", item.getOriginalFilename(), e.getMessage());
                }
            }

            final int finalSuccess = successCount;
            final int finalError = errorCount;

            Platform.runLater(() -> {
                progressSection.setVisible(false);
                progressSection.setManaged(false);
                isProcessing = false;
                fileTable.refresh();
                updateUI();

                // Show results
                resultsSection.setVisible(true);
                resultsSection.setManaged(true);
                if (finalError == 0) {
                    resultsLabel.setText("Successfully renamed " + finalSuccess + " files!");
                    resultsSection.setStyle("-fx-padding: 15; -fx-background-color: #e8f5e9; " +
                            "-fx-border-color: #4CAF50; -fx-border-radius: 4;");
                    resultsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #2e7d32;");
                } else {
                    resultsLabel.setText("Renamed " + finalSuccess + " files, " + finalError + " errors");
                    resultsSection.setStyle("-fx-padding: 15; -fx-background-color: #fff3e0; " +
                            "-fx-border-color: #ff9800; -fx-border-radius: 4;");
                    resultsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e65100;");
                }

                logger.info("Rename complete: {} success, {} errors", finalSuccess, finalError);
            });
        });
    }

    private void updateUI() {
        boolean hasFiles = !items.isEmpty();

        int selectedCount = 0;
        int readyCount = 0;
        for (RenameItem item : items) {
            SimpleBooleanProperty selected = selectionMap.get(item);
            if (selected != null && selected.get()) {
                selectedCount++;
                if (item.getStatus() == RenameStatus.READY && item.needsRename()) {
                    readyCount++;
                }
            }
        }

        addFilesButton.setDisable(isProcessing);
        addFolderButton.setDisable(isProcessing);
        removeSelectedButton.setDisable(!hasFiles || isProcessing);
        clearAllButton.setDisable(!hasFiles || isProcessing);
        scanButton.setDisable(!hasFiles || isProcessing);
        renameButton.setDisable(readyCount == 0 || isProcessing);

        if (isProcessing) {
            statusLabel.setText("Processing...");
        } else if (hasFiles) {
            statusLabel.setText(items.size() + " files loaded");
        } else {
            statusLabel.setText("No files loaded");
        }

        statsLabel.setText(items.size() + " files | " + readyCount + " ready to rename");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void cleanup() {
        logger.info("Cleaning up PDF Bulk Renamer");
        executor.shutdownNow();
        items.clear();
        selectionMap.clear();
    }
}
