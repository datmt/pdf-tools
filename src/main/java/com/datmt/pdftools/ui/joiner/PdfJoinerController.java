package com.datmt.pdftools.ui.joiner;

import com.datmt.pdftools.model.JoinerFile;
import com.datmt.pdftools.model.JoinerSection;
import com.datmt.pdftools.service.PdfMergeService;
import com.datmt.pdftools.ui.joiner.components.FileListItem;
import com.datmt.pdftools.ui.joiner.components.SectionListItem;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datmt.pdftools.util.CreditLinkHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the PDF Joiner tool.
 * Manages the 3-panel layout: file list, section list, and preview.
 */
public class PdfJoinerController {
    private static final Logger logger = LoggerFactory.getLogger(PdfJoinerController.class);
    private static final int THUMBNAIL_SIZE = 80;

    // Toolbar
    @FXML private Button addFilesButton;
    @FXML private Label fileCountLabel;
    @FXML private Button joinButton;
    @FXML private Label notificationLabel;

    // File list panel
    @FXML private ScrollPane fileListScrollPane;
    @FXML private VBox fileListContainer;
    @FXML private TextField pageRangeField;
    @FXML private Button addSectionButton;

    // Section list panel
    @FXML private ScrollPane sectionListScrollPane;
    @FXML private VBox sectionListContainer;
    @FXML private Label totalPagesLabel;
    @FXML private Button clearSectionsButton;

    // Preview panel
    @FXML private ToggleButton gridViewToggle;
    @FXML private ToggleButton singleViewToggle;
    @FXML private ScrollPane gridPreviewScrollPane;
    @FXML private FlowPane gridPreviewContainer;
    @FXML private VBox singlePreviewContainer;
    @FXML private ScrollPane singlePreviewScrollPane;
    @FXML private StackPane singlePreviewPane;
    @FXML private ImageView singlePreviewImage;
    @FXML private Button prevPageButton;
    @FXML private Button nextPageButton;
    @FXML private Label currentPageLabel;

    // Image options
    @FXML private VBox imageOptionsPane;
    @FXML private ComboBox<JoinerSection.PageSize> pageSizeCombo;
    @FXML private ComboBox<JoinerSection.FitOption> fitOptionCombo;
    @FXML private ComboBox<JoinerSection.Rotation> rotationCombo;

    // Output
    @FXML private TextField outputFileField;
    @FXML private Button browseButton;
    @FXML private Hyperlink creditLink;

    // State
    private final PdfMergeService mergeService = new PdfMergeService();
    private final List<JoinerFile> loadedFiles = new ArrayList<>();
    private final List<JoinerSection> sections = new ArrayList<>();
    private final List<FileListItem> fileListItems = new ArrayList<>();
    private final List<SectionListItem> sectionListItems = new ArrayList<>();
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(4);

    private FileListItem selectedFileItem;
    private SectionListItem selectedSectionItem;
    private int currentPreviewPage = 0;
    private boolean isGridView = true;

    @FXML
    public void initialize() {
        logger.trace("Initializing PdfJoinerController");

        setupImageOptionsComboBoxes();
        setupViewToggle();
        setupWindowCloseHandler();
        CreditLinkHandler.setup(creditLink);
        updateUI();

        logger.debug("Controller initialization complete");
    }

    private void setupWindowCloseHandler() {
        // Register cleanup when window is closed
        Platform.runLater(() -> {
            if (addFilesButton.getScene() != null && addFilesButton.getScene().getWindow() != null) {
                addFilesButton.getScene().getWindow().setOnCloseRequest(event -> cleanup());
            }
        });
    }

    /**
     * Clean up resources when the window is closed.
     */
    public void cleanup() {
        logger.info("Cleaning up PdfJoinerController resources");

        // Shut down the executor service
        renderExecutor.shutdownNow();

        // Close all loaded PDF documents
        for (JoinerFile file : loadedFiles) {
            try {
                file.close();
            } catch (Exception e) {
                logger.error("Error closing file: {}", file.getFileName(), e);
            }
        }

        // Clear all lists and caches
        loadedFiles.clear();
        sections.clear();
        fileListItems.clear();
        sectionListItems.clear();
        fileListContainer.getChildren().clear();
        sectionListContainer.getChildren().clear();
        gridPreviewContainer.getChildren().clear();

        logger.info("Cleanup complete");
    }

    private void setupImageOptionsComboBoxes() {
        pageSizeCombo.getItems().addAll(JoinerSection.PageSize.values());
        pageSizeCombo.setValue(JoinerSection.PageSize.A4);
        pageSizeCombo.setOnAction(e -> onImageOptionChanged());

        fitOptionCombo.getItems().addAll(JoinerSection.FitOption.values());
        fitOptionCombo.setValue(JoinerSection.FitOption.FIT_TO_PAGE);
        fitOptionCombo.setOnAction(e -> onImageOptionChanged());

        rotationCombo.getItems().addAll(JoinerSection.Rotation.values());
        rotationCombo.setValue(JoinerSection.Rotation.NONE);
        rotationCombo.setOnAction(e -> onImageOptionChanged());
    }

    private void setupViewToggle() {
        gridViewToggle.setSelected(true);
        singleViewToggle.setSelected(false);
    }

    private void updateUI() {
        boolean hasFiles = !loadedFiles.isEmpty();
        boolean hasSections = !sections.isEmpty();
        boolean hasSelectedFile = selectedFileItem != null;

        fileCountLabel.setText(hasFiles ? loadedFiles.size() + " file(s)" : "No files loaded");
        addSectionButton.setDisable(!hasSelectedFile);
        clearSectionsButton.setDisable(!hasSections);
        joinButton.setDisable(!hasSections || outputFileField.getText().trim().isEmpty());

        updateTotalPagesLabel();
    }

    private void updateTotalPagesLabel() {
        int totalPages = sections.stream().mapToInt(JoinerSection::getPageCount).sum();
        totalPagesLabel.setText(totalPages + " pages");
    }

    // ==================== File Management ====================

    @FXML
    private void onAddFiles() {
        logger.info("User clicked Add Files");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF or Image Files");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported", "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        Stage stage = (Stage) addFilesButton.getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            loadFiles(selectedFiles);
        }
    }

    private void loadFiles(List<File> files) {
        logger.info("Loading {} files", files.size());

        for (File file : files) {
            Task<JoinerFile> loadTask = new Task<>() {
                @Override
                protected JoinerFile call() throws Exception {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".pdf")) {
                        return mergeService.loadPdfFile(file);
                    } else {
                        return mergeService.loadImageFile(file);
                    }
                }
            };

            loadTask.setOnSucceeded(event -> {
                JoinerFile joinerFile = loadTask.getValue();
                loadedFiles.add(joinerFile);

                FileListItem item = new FileListItem(joinerFile,
                        this::onFileSelected,
                        this::onFileRemoved);
                fileListItems.add(item);
                fileListContainer.getChildren().add(item);

                // Load thumbnail
                loadFileThumbnail(item, joinerFile);

                updateUI();
                showInfo("Loaded: " + joinerFile.getFileName());
            });

            loadTask.setOnFailed(event -> {
                logger.error("Failed to load file: {}", file.getName(), loadTask.getException());
                showError("Failed to load: " + file.getName());
            });

            new Thread(loadTask).start();
        }
    }

    private void loadFileThumbnail(FileListItem item, JoinerFile joinerFile) {
        CompletableFuture.runAsync(() -> {
            try {
                Image thumbnail;
                if (joinerFile.isPdf()) {
                    thumbnail = mergeService.renderPdfThumbnail(joinerFile, 0);
                } else {
                    thumbnail = joinerFile.getImage();
                }
                Platform.runLater(() -> item.setThumbnail(thumbnail));
            } catch (Exception e) {
                logger.error("Failed to load thumbnail for: {}", joinerFile.getFileName(), e);
            }
        }, renderExecutor);
    }

    private void onFileSelected(FileListItem item) {
        logger.debug("File selected: {}", item.getJoinerFile().getFileName());

        // Deselect previous
        if (selectedFileItem != null) {
            selectedFileItem.setFileSelected(false);
        }

        selectedFileItem = item;
        item.setFileSelected(true);

        // Update page range field default
        pageRangeField.setText("all");

        // Show/hide image options
        boolean isImage = item.getJoinerFile().isImage();
        imageOptionsPane.setVisible(isImage);
        imageOptionsPane.setManaged(isImage);

        updateUI();
    }

    private void onFileRemoved(FileListItem item) {
        logger.info("Removing file: {}", item.getJoinerFile().getFileName());

        JoinerFile joinerFile = item.getJoinerFile();

        // Remove related sections
        sections.removeIf(s -> s.getSourceFile() == joinerFile);
        rebuildSectionList();

        // Remove from file list
        loadedFiles.remove(joinerFile);
        fileListItems.remove(item);
        fileListContainer.getChildren().remove(item);

        // Close document
        try {
            joinerFile.close();
        } catch (Exception e) {
            logger.error("Error closing file: {}", joinerFile.getFileName(), e);
        }

        if (selectedFileItem == item) {
            selectedFileItem = null;
        }

        updateUI();
    }

    // ==================== Section Management ====================

    @FXML
    private void onAddSection() {
        if (selectedFileItem == null) {
            showWarning("Please select a file first");
            return;
        }

        JoinerFile joinerFile = selectedFileItem.getJoinerFile();
        String rangeInput = pageRangeField.getText().trim();

        logger.info("Adding section from {} with range: {}", joinerFile.getFileName(), rangeInput);

        try {
            JoinerSection section = createSection(joinerFile, rangeInput);

            // Apply image options if applicable
            if (joinerFile.isImage()) {
                section.setPageSize(pageSizeCombo.getValue());
                section.setFitOption(fitOptionCombo.getValue());
                section.setRotation(rotationCombo.getValue());
            }

            sections.add(section);
            addSectionToList(section, sections.size());

            updateUI();
            showInfo("Added section: " + section.toString());
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        }
    }

    private JoinerSection createSection(JoinerFile joinerFile, String rangeInput) {
        if (rangeInput.isEmpty() || rangeInput.equalsIgnoreCase("all")) {
            return new JoinerSection(joinerFile);
        }

        // Parse range like "1-5" or "3"
        if (rangeInput.contains("-")) {
            String[] parts = rangeInput.split("-");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid range format: " + rangeInput);
            }
            int start = Integer.parseInt(parts[0].trim()) - 1;
            int end = Integer.parseInt(parts[1].trim()) - 1;

            if (start < 0 || end >= joinerFile.getPageCount() || start > end) {
                throw new IllegalArgumentException("Invalid page range: " + rangeInput);
            }

            return new JoinerSection(joinerFile, start, end);
        } else {
            int page = Integer.parseInt(rangeInput.trim()) - 1;
            if (page < 0 || page >= joinerFile.getPageCount()) {
                throw new IllegalArgumentException("Invalid page number: " + rangeInput);
            }
            return new JoinerSection(joinerFile, page, page);
        }
    }

    private void addSectionToList(JoinerSection section, int index) {
        SectionListItem item = new SectionListItem(section, index,
                this::onSectionSelected,
                this::onSectionMoveUp,
                this::onSectionMoveDown,
                this::onSectionRotateLeft,
                this::onSectionRotateRight,
                this::onSectionRemoved);
        sectionListItems.add(item);
        sectionListContainer.getChildren().add(item);
    }

    private void rebuildSectionList() {
        sectionListContainer.getChildren().clear();
        sectionListItems.clear();
        selectedSectionItem = null;

        for (int i = 0; i < sections.size(); i++) {
            addSectionToList(sections.get(i), i + 1);
        }

        updateUI();
    }

    private void onSectionSelected(SectionListItem item) {
        logger.debug("Section selected: {}", item.getSection().toString());

        // Deselect previous
        if (selectedSectionItem != null) {
            selectedSectionItem.setSectionSelected(false);
        }

        selectedSectionItem = item;
        item.setSectionSelected(true);

        // Show/hide image options based on section's source file
        boolean isImage = item.getSection().getSourceFile().isImage();
        imageOptionsPane.setVisible(isImage);
        imageOptionsPane.setManaged(isImage);

        if (isImage) {
            // Load current image options
            JoinerSection section = item.getSection();
            pageSizeCombo.setValue(section.getPageSize());
            fitOptionCombo.setValue(section.getFitOption());
            rotationCombo.setValue(section.getRotation());
        }

        // Update preview
        currentPreviewPage = 0;
        updatePreview();
    }

    private void onSectionMoveUp(SectionListItem item) {
        int index = sectionListItems.indexOf(item);
        if (index > 0) {
            logger.debug("Moving section up from index {}", index);
            JoinerSection section = sections.remove(index);
            sections.add(index - 1, section);
            rebuildSectionList();
        }
    }

    private void onSectionMoveDown(SectionListItem item) {
        int index = sectionListItems.indexOf(item);
        if (index < sections.size() - 1) {
            logger.debug("Moving section down from index {}", index);
            JoinerSection section = sections.remove(index);
            sections.add(index + 1, section);
            rebuildSectionList();
        }
    }

    private void onSectionRemoved(SectionListItem item) {
        logger.info("Removing section: {}", item.getSection().toString());
        int index = sectionListItems.indexOf(item);
        sections.remove(index);
        rebuildSectionList();
    }

    private void onSectionRotateLeft(SectionListItem item) {
        rotateSectionBy(item, -90);
    }

    private void onSectionRotateRight(SectionListItem item) {
        rotateSectionBy(item, 90);
    }

    private void rotateSectionBy(SectionListItem item, int degrees) {
        JoinerSection section = item.getSection();
        JoinerSection.Rotation currentRotation = section.getRotation();
        int currentDegrees = currentRotation.getDegrees();
        int newDegrees = (currentDegrees + degrees + 360) % 360;

        // Find matching rotation enum
        JoinerSection.Rotation newRotation = JoinerSection.Rotation.NONE;
        for (JoinerSection.Rotation r : JoinerSection.Rotation.values()) {
            if (r.getDegrees() == newDegrees) {
                newRotation = r;
                break;
            }
        }

        section.setRotation(newRotation);
        logger.debug("Section rotation changed: {} -> {}", currentDegrees, newDegrees);

        // Find the section index to re-select after rebuild
        int sectionIndex = sections.indexOf(section);

        // Rebuild to update display
        rebuildSectionList();

        // Re-select the section and update preview
        if (sectionIndex >= 0 && sectionIndex < sectionListItems.size()) {
            SectionListItem newItem = sectionListItems.get(sectionIndex);
            onSectionSelected(newItem);
        }

        showInfo("Rotated section to " + newDegrees + "\u00B0");
    }

    @FXML
    private void onClearSections() {
        logger.info("Clearing all sections");
        sections.clear();
        rebuildSectionList();
        gridPreviewContainer.getChildren().clear();
    }

    // ==================== Image Options ====================

    private void onImageOptionChanged() {
        if (selectedSectionItem != null && selectedSectionItem.getSection().getSourceFile().isImage()) {
            JoinerSection section = selectedSectionItem.getSection();
            section.setPageSize(pageSizeCombo.getValue());
            section.setFitOption(fitOptionCombo.getValue());
            section.setRotation(rotationCombo.getValue());
            logger.debug("Updated image options for section: {}", section.toString());
        }
    }

    // ==================== Preview ====================

    @FXML
    private void onToggleGridView() {
        isGridView = true;
        gridViewToggle.setSelected(true);
        singleViewToggle.setSelected(false);

        gridPreviewScrollPane.setVisible(true);
        gridPreviewScrollPane.setManaged(true);
        singlePreviewContainer.setVisible(false);
        singlePreviewContainer.setManaged(false);

        updatePreview();
    }

    @FXML
    private void onToggleSingleView() {
        isGridView = false;
        gridViewToggle.setSelected(false);
        singleViewToggle.setSelected(true);

        gridPreviewScrollPane.setVisible(false);
        gridPreviewScrollPane.setManaged(false);
        singlePreviewContainer.setVisible(true);
        singlePreviewContainer.setManaged(true);

        updatePreview();
    }

    private void updatePreview() {
        if (selectedSectionItem == null) {
            gridPreviewContainer.getChildren().clear();
            return;
        }

        JoinerSection section = selectedSectionItem.getSection();

        if (isGridView) {
            updateGridPreview(section);
        } else {
            updateSinglePreview(section);
        }
    }

    private void updateGridPreview(JoinerSection section) {
        gridPreviewContainer.getChildren().clear();
        JoinerFile sourceFile = section.getSourceFile();
        int rotation = section.getRotation().getDegrees();

        for (int i = section.getStartPage(); i <= section.getEndPage(); i++) {
            final int pageIndex = i;
            ImageView thumb = new ImageView();
            thumb.setPreserveRatio(true);
            thumb.setFitWidth(THUMBNAIL_SIZE);
            thumb.setFitHeight(THUMBNAIL_SIZE);
            thumb.setRotate(rotation);  // Apply section rotation

            VBox container = new VBox(5);
            container.setStyle("-fx-padding: 5; -fx-border-color: #e0e0e0; -fx-border-radius: 3;");
            Label pageLabel = new Label("Page " + (pageIndex + 1));
            pageLabel.setStyle("-fx-font-size: 10;");
            container.getChildren().addAll(thumb, pageLabel);

            gridPreviewContainer.getChildren().add(container);

            // Load thumbnail async
            CompletableFuture.runAsync(() -> {
                try {
                    Image image;
                    if (sourceFile.isPdf()) {
                        image = mergeService.renderPdfThumbnail(sourceFile, pageIndex);
                    } else {
                        image = sourceFile.getImage();
                    }
                    Platform.runLater(() -> thumb.setImage(image));
                } catch (Exception e) {
                    logger.error("Failed to load preview thumbnail", e);
                }
            }, renderExecutor);
        }
    }

    private void updateSinglePreview(JoinerSection section) {
        JoinerFile sourceFile = section.getSourceFile();
        int pageCount = section.getPageCount();

        // Clamp current page
        currentPreviewPage = Math.max(0, Math.min(currentPreviewPage, pageCount - 1));

        int actualPage = section.getStartPage() + currentPreviewPage;
        currentPageLabel.setText("Page " + (currentPreviewPage + 1) + " of " + pageCount);
        prevPageButton.setDisable(currentPreviewPage == 0);
        nextPageButton.setDisable(currentPreviewPage >= pageCount - 1);

        // Load preview
        Task<Image> previewTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                if (sourceFile.isPdf()) {
                    return mergeService.renderPdfPreview(sourceFile, actualPage);
                } else {
                    return sourceFile.getImage();
                }
            }
        };

        previewTask.setOnSucceeded(e -> {
            Image image = previewTask.getValue();
            singlePreviewImage.setImage(image);
            singlePreviewImage.setFitWidth(singlePreviewPane.getWidth() - 20);
            singlePreviewImage.setRotate(section.getRotation().getDegrees());  // Apply section rotation
        });

        previewTask.setOnFailed(e -> {
            logger.error("Failed to render preview", previewTask.getException());
        });

        new Thread(previewTask).start();
    }

    @FXML
    private void onPreviousPage() {
        if (currentPreviewPage > 0) {
            currentPreviewPage--;
            updatePreview();
        }
    }

    @FXML
    private void onNextPage() {
        if (selectedSectionItem != null) {
            int pageCount = selectedSectionItem.getSection().getPageCount();
            if (currentPreviewPage < pageCount - 1) {
                currentPreviewPage++;
                updatePreview();
            }
        }
    }

    // ==================== Output and Join ====================

    @FXML
    private void onBrowseOutput() {
        logger.info("User clicked browse for output file");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Joined PDF As");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("joined.pdf");

        Stage stage = (Stage) browseButton.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(stage);

        if (selectedFile != null) {
            outputFileField.setText(selectedFile.getAbsolutePath());
            updateUI();
        }
    }

    @FXML
    private void onJoin() {
        if (sections.isEmpty()) {
            showWarning("Please add sections to join");
            return;
        }

        String outputPath = outputFileField.getText().trim();
        if (outputPath.isEmpty()) {
            showWarning("Please specify an output file");
            return;
        }

        File outputFile = new File(outputPath);
        logger.info("Starting join operation with {} sections to: {}", sections.size(), outputFile.getAbsolutePath());

        joinButton.setDisable(true);

        Task<Void> joinTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                mergeService.mergeSections(sections, outputFile, (current, total) -> {
                    Platform.runLater(() -> {
                        joinButton.setText("Joining... " + current + "/" + total);
                    });
                });
                return null;
            }
        };

        joinTask.setOnSucceeded(event -> {
            logger.info("Join completed successfully");
            joinButton.setText("Join PDF");
            joinButton.setDisable(false);
            showInfo("PDF joined successfully: " + outputFile.getName());
        });

        joinTask.setOnFailed(event -> {
            logger.error("Join failed", joinTask.getException());
            joinButton.setText("Join PDF");
            joinButton.setDisable(false);
            showError("Join failed: " + joinTask.getException().getMessage());
        });

        new Thread(joinTask).start();
    }

    // ==================== Notifications ====================

    private void showError(String message) {
        logger.error("Showing error notification: {}", message);
        showNotification(message, "#d32f2f");
    }

    private void showWarning(String message) {
        logger.warn("Showing warning notification: {}", message);
        showNotification(message, "#f57c00");
    }

    private void showInfo(String message) {
        logger.info("Showing info notification: {}", message);
        showNotification(message, "#388e3c");
    }

    private void showNotification(String message, String bgColor) {
        Platform.runLater(() -> {
            notificationLabel.setText(message);
            notificationLabel.setStyle("-fx-padding: 8; -fx-font-size: 12; -fx-text-fill: white; -fx-background-color: " + bgColor + ";");
            notificationLabel.setVisible(true);

            // Auto-hide after 5 seconds
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
                notificationLabel.setVisible(false);
            }));
            timeline.play();
        });
    }
}
