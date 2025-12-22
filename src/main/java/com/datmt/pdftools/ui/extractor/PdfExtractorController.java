package com.datmt.pdftools.ui.extractor;

import com.datmt.pdftools.model.PdfDocument;
import com.datmt.pdftools.service.PdfService;
import com.datmt.pdftools.ui.extractor.components.PageThumbnailPanel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Controller for the PDF Extractor tool.
 * Manages the 3-panel layout: pages list, preview, and selected pages.
 */
public class PdfExtractorController {
    private static final Logger logger = LoggerFactory.getLogger(PdfExtractorController.class);

    @FXML private Button loadButton;
    @FXML private Label fileLabel;
    @FXML private Label pageCountLabel;

    @FXML private VBox pagesListContainer;
    @FXML private ScrollPane pagesScrollPane;
    @FXML private StackPane previewContainer;
    @FXML private ScrollPane previewScrollPane;
    @FXML private Label currentPageLabel;
    @FXML private Button prevButton, nextButton;

    @FXML private VBox selectedPagesList;
    @FXML private ScrollPane selectedPagesScrollPane;
    @FXML private TextField pageInputField;
    @FXML private Button addPagesButton;
    @FXML private Label inputHintLabel;
    @FXML private Button removeSelectedButton, clearButton;

    @FXML private TextField outputFileField;
    @FXML private Button browseButton, exportButton;
    @FXML private Button selectAllButton, deselectAllButton;
    @FXML private Label notificationLabel;

    private PdfService pdfService;
    private Set<Integer> selectedPages;
    private List<PageThumbnailPanel> thumbnailPanels;
    private int currentPreviewPage;
    private ImageView currentImageView;

    @FXML
    public void initialize() {
        logger.trace("Initializing PdfExtractorController");
        pdfService = new PdfService();
        selectedPages = new TreeSet<>();
        thumbnailPanels = new ArrayList<>();
        currentPreviewPage = 0;
        
        setupEventHandlers();
        logger.debug("Controller initialization complete");
    }

    private void setupEventHandlers() {
        logger.trace("Setting up event handlers");
        pageInputField.setOnAction(event -> onAddPages());
    }

    @FXML
    private void onLoadPdf() {
        logger.info("User clicked Load PDF");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        
        Stage stage = (Stage) loadButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            loadPdfFile(selectedFile);
        }
    }

    private void loadPdfFile(File file) {
        logger.info("Loading PDF file: {}", file.getAbsolutePath());
        
        Task<PdfDocument> loadTask = new Task<>() {
            @Override
            protected PdfDocument call() throws Exception {
                logger.debug("Background task: loading PDF");
                return pdfService.loadPdf(file);
            }
        };

        loadTask.setOnSucceeded(event -> {
            PdfDocument doc = loadTask.getValue();
            logger.info("PDF loaded successfully: {} pages", doc.getPageCount());
            
            fileLabel.setText(file.getName());
            pageCountLabel.setText(doc.getPageCount() + " pages");
            
            selectedPages.clear();
            selectedPagesList.getChildren().clear();
            currentPreviewPage = 0;
            
            loadPageThumbnails(doc);
            updatePreview(0);
            
            removeSelectedButton.setDisable(selectedPagesList.getChildren().isEmpty());
            clearButton.setDisable(true);
        });

        loadTask.setOnFailed(event -> {
            logger.error("Failed to load PDF", loadTask.getException());
            showError("Failed to load PDF: " + loadTask.getException().getMessage());
        });

        new Thread(loadTask).start();
    }

    private void loadPageThumbnails(PdfDocument document) {
        logger.debug("Loading page thumbnails for {} pages", document.getPageCount());
        
        pagesListContainer.getChildren().clear();
        thumbnailPanels.clear();
        
        Task<Void> thumbnailTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i < document.getPageCount(); i++) {
                    final int pageIndex = i;
                    logger.trace("Rendering thumbnail for page {}", pageIndex);
                    
                    try {
                        Image thumbnail = pdfService.renderPageThumbnail(pageIndex);
                        Platform.runLater(() -> {
                            PageThumbnailPanel panel = new PageThumbnailPanel(
                                    pageIndex + 1,
                                    thumbnail,
                                    selected -> onPageThumbnailToggled(pageIndex, selected)
                            );
                            thumbnailPanels.add(panel);
                            // Insert at correct index to maintain order
                            pagesListContainer.getChildren().add(pageIndex, panel);
                            logger.trace("Thumbnail for page {} added to UI at index {}", pageIndex, pageIndex);
                        });
                    } catch (Exception e) {
                        logger.warn("Failed to render thumbnail for page {}: {}", pageIndex, e.getMessage());
                    }
                }
                return null;
            }
        };

        new Thread(thumbnailTask).start();
    }

    private void onPageThumbnailToggled(int pageIndex, boolean selected) {
        logger.debug("Page {} thumbnail toggled, selected: {}", pageIndex, selected);
        if (selected) {
            selectedPages.add(pageIndex);
            logger.trace("Page {} added to selection", pageIndex);
        } else {
            selectedPages.remove(pageIndex);
            logger.trace("Page {} removed from selection", pageIndex);
        }
        updateSelectedPagesList();
    }

    private void updatePreview(int pageIndex) {
        logger.debug("Updating preview for page {}", pageIndex);
        
        if (!pdfService.isDocumentLoaded()) {
            logger.warn("Cannot update preview: no document loaded");
            return;
        }

        currentPreviewPage = pageIndex;
        currentPageLabel.setText("Page " + (pageIndex + 1));
        prevButton.setDisable(pageIndex == 0);
        nextButton.setDisable(pageIndex >= pdfService.getCurrentDocument().getPageCount() - 1);

        Task<Image> renderTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                logger.trace("Background task: rendering page {} for preview", pageIndex);
                return pdfService.renderPage(pageIndex);
            }
        };

        renderTask.setOnSucceeded(event -> {
            Image image = renderTask.getValue();
            previewContainer.getChildren().clear();
            currentImageView = new ImageView(image);
            currentImageView.setPreserveRatio(true);
            currentImageView.setFitWidth(previewContainer.getWidth() - 20);
            previewContainer.getChildren().add(currentImageView);
            logger.trace("Page {} preview rendered and displayed", pageIndex);
        });

        renderTask.setOnFailed(event -> {
            logger.error("Failed to render page preview", renderTask.getException());
            showError("Failed to render page preview");
        });

        new Thread(renderTask).start();
    }

    @FXML
    private void onPreviousPage() {
        if (currentPreviewPage > 0) {
            logger.debug("Moving to previous page from {}", currentPreviewPage);
            updatePreview(currentPreviewPage - 1);
        }
    }

    @FXML
    private void onNextPage() {
        int pageCount = pdfService.getCurrentDocument().getPageCount();
        if (currentPreviewPage < pageCount - 1) {
            logger.debug("Moving to next page from {}", currentPreviewPage);
            updatePreview(currentPreviewPage + 1);
        }
    }

    @FXML
    private void onAddPages() {
        String input = pageInputField.getText().trim();
        logger.info("User entered page selection: {}", input);
        
        if (input.isEmpty()) {
            logger.warn("Empty page input");
            showWarning("Please enter page numbers");
            return;
        }

        try {
            Set<Integer> pagesToAdd = parsePageInput(input);
            logger.debug("Parsed {} pages from input", pagesToAdd.size());
            
            selectedPages.addAll(pagesToAdd);
            updateSelectedPagesList();
            pageInputField.clear();
            
            logger.info("Selected pages updated: {}", selectedPages);
            showInfo("Added " + pagesToAdd.size() + " page(s)");
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid page input: {}", e.getMessage());
            showWarning(e.getMessage());
        }
    }

    private Set<Integer> parsePageInput(String input) throws IllegalArgumentException {
        logger.trace("Parsing page input: {}", input);
        Set<Integer> pages = new TreeSet<>();
        int pageCount = pdfService.getCurrentDocument().getPageCount();

        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();
            
            if (part.contains("-")) {
                // Range: e.g., "5-10"
                String[] range = part.split("-");
                if (range.length != 2) {
                    throw new IllegalArgumentException("Invalid range format: " + part);
                }
                
                try {
                    int start = Integer.parseInt(range[0].trim()) - 1; // Convert to 0-based
                    int end = Integer.parseInt(range[1].trim()) - 1;
                    
                    if (start < 0 || end >= pageCount || start > end) {
                        throw new IllegalArgumentException("Invalid range: " + part);
                    }
                    
                    for (int i = start; i <= end; i++) {
                        pages.add(i);
                    }
                    logger.trace("Added range: {} to {} (0-based)", start, end);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid range: " + part);
                }
            } else {
                // Single page
                try {
                    int pageNum = Integer.parseInt(part) - 1; // Convert to 0-based
                    if (pageNum < 0 || pageNum >= pageCount) {
                        throw new IllegalArgumentException("Page out of range: " + (pageNum + 1));
                    }
                    pages.add(pageNum);
                    logger.trace("Added page: {}", pageNum);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid page number: " + part);
                }
            }
        }

        return pages;
    }

    private void updateSelectedPagesList() {
        logger.debug("Updating selected pages list with {} pages", selectedPages.size());
        selectedPagesList.getChildren().clear();

        for (Integer pageNum : selectedPages) {
            Label label = new Label((pageNum + 1) + "");
            label.setStyle("-fx-padding: 5; -fx-border-color: #cccccc; -fx-border-radius: 3;");
            selectedPagesList.getChildren().add(label);
        }

        removeSelectedButton.setDisable(selectedPagesList.getChildren().isEmpty());
        clearButton.setDisable(selectedPagesList.getChildren().isEmpty());
        exportButton.setDisable(selectedPagesList.getChildren().isEmpty());
    }

    @FXML
    private void onSelectAll() {
        if (!pdfService.isDocumentLoaded()) return;
        
        logger.info("User selected all pages");
        int pageCount = pdfService.getCurrentDocument().getPageCount();
        selectedPages.addAll(java.util.stream.IntStream.range(0, pageCount).boxed().collect(Collectors.toSet()));
        
        // Update all thumbnail checkboxes
        for (PageThumbnailPanel panel : thumbnailPanels) {
            panel.setSelected(true);
        }
        
        updateSelectedPagesList();
    }

    @FXML
    private void onDeselectAll() {
        logger.info("User deselected all pages");
        selectedPages.clear();
        
        // Update all thumbnail checkboxes
        for (PageThumbnailPanel panel : thumbnailPanels) {
            panel.setSelected(false);
        }
        
        updateSelectedPagesList();
    }

    @FXML
    private void onRemoveSelected() {
        logger.warn("Remove selected not yet implemented");
        showWarning("This feature will be implemented soon");
    }

    @FXML
    private void onClearAll() {
        logger.info("User cleared all selected pages");
        selectedPages.clear();
        updateSelectedPagesList();
    }

    @FXML
    private void onBrowseOutput() {
        logger.info("User clicked browse for output file");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Extracted PDF As");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("extracted.pdf");

        Stage stage = (Stage) browseButton.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(stage);

        if (selectedFile != null) {
            outputFileField.setText(selectedFile.getAbsolutePath());
            logger.debug("Output file selected: {}", selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void onExport() {
        logger.info("User clicked export");
        
        if (selectedPages.isEmpty()) {
            logger.warn("No pages selected for export");
            showWarning("Please select pages to export");
            return;
        }

        String outputPath = outputFileField.getText().trim();
        if (outputPath.isEmpty()) {
            logger.warn("No output file specified");
            showWarning("Please specify an output file");
            return;
        }

        File outputFile = new File(outputPath);
        logger.info("Starting export to: {} with {} pages", outputFile.getAbsolutePath(), selectedPages.size());

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                logger.debug("Background task: extracting pages");
                pdfService.extractPages(new HashSet<>(selectedPages), outputFile);
                return null;
            }
        };

        exportTask.setOnSucceeded(event -> {
            logger.info("Export completed successfully");
            showInfo("PDF exported successfully");
        });

        exportTask.setOnFailed(event -> {
            logger.error("Export failed", exportTask.getException());
            showError("Export failed: " + exportTask.getException().getMessage());
        });

        new Thread(exportTask).start();
    }

    private void showError(String message) {
        logger.warn("Showing error notification: {}", message);
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
