package com.datmt.pdftools.ui.extractor;

import com.datmt.pdftools.model.PdfBookmark;
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
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Controller for the PDF Extractor tool.
 * Manages the 3-panel layout: pages list, preview, and selected pages.
 */
public class PdfExtractorController {
    private static final Logger logger = LoggerFactory.getLogger(PdfExtractorController.class);
    Task<Void> currentLoadingTask;
    @FXML
    private Button loadButton;
    @FXML
    private Label fileLabel;
    @FXML
    private Label pageCountLabel;
    @FXML
    private VBox pagesListContainer;
    @FXML
    private ScrollPane pagesScrollPane;
    @FXML
    private StackPane previewContainer;
    @FXML
    private ScrollPane previewScrollPane;
    @FXML
    private Label currentPageLabel;
    @FXML
    private Button prevButton, nextButton;
    @FXML
    private VBox selectedPagesList;
    @FXML
    private ScrollPane selectedPagesScrollPane;
    @FXML
    private TextField pageInputField;
    @FXML
    private Button addPagesButton;
    @FXML
    private Label inputHintLabel;
    @FXML
    private Button removeSelectedButton, clearButton;
    @FXML
    private TextField outputFileField;
    @FXML
    private Button browseButton, exportButton;
    @FXML
    private Button selectAllButton, deselectAllButton;
    @FXML
    private Label notificationLabel;
    @FXML
    private TabPane leftTabPane;
    @FXML
    private Tab pagesTab, bookmarksTab;
    @FXML
    private TreeView<PdfBookmark> bookmarkTreeView;
    @FXML
    private Button selectAllBookmarksButton, deselectAllBookmarksButton;
    @FXML
    private Spinner<Integer> levelSpinner;
    @FXML
    private Button selectLevelButton;
    @FXML
    private TextField outputDirField;
    @FXML
    private Button browseDirButton, exportBookmarksButton;
    private PdfService pdfService;
    private Set<Integer> selectedPages;
    private List<PageThumbnailPanel> thumbnailPanels;
    private int currentPreviewPage;
    private ImageView currentImageView;
    private PageThumbnailPanel currentSelectedPanel;
    private List<PdfBookmark> currentBookmarks;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(4); // Limit threads to save RAM
    private final AtomicReference<Object> currentSessionId = new AtomicReference<>(); // Token to track active request

    @FXML
    public void initialize() {
        logger.trace("Initializing PdfExtractorController");
        pdfService = new PdfService();
        selectedPages = new TreeSet<>();
        thumbnailPanels = new ArrayList<>();
        currentBookmarks = new ArrayList<>();
        currentPreviewPage = 0;

        setupEventHandlers();
        setupBookmarkTreeView();
        logger.debug("Controller initialization complete");
    }

    private void setupBookmarkTreeView() {
        // Set up the cell factory for the bookmark tree
        bookmarkTreeView.setCellFactory(tv -> new CheckBoxTreeCell<>() {
            @Override
            public void updateItem(PdfBookmark item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    CheckBox checkBox = new CheckBox();
                    checkBox.setSelected(item.isSelected());
                    checkBox.setOnAction(e -> {
                        item.setSelected(checkBox.isSelected());
                        updateBookmarkExportButton();
                    });

                    String displayText = item.getTitle() + " (" + item.getPageRangeString() + ")";
                    setText(displayText);
                    setGraphic(checkBox);
                }
            }
        });
    }

    private static class CheckBoxTreeCell<T> extends TreeCell<T> {
        // Base class for custom tree cell with checkbox
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

            loadPageThumbnails(doc, pagesListContainer, thumbnailPanels);
            updatePreview(0);

            removeSelectedButton.setDisable(selectedPagesList.getChildren().isEmpty());
            clearButton.setDisable(true);

            // Load bookmarks if available
            loadBookmarks();
        });

        loadTask.setOnFailed(event -> {
            logger.error("Failed to load PDF", loadTask.getException());
            showError("Failed to load PDF: " + loadTask.getException().getMessage());
        });

        new Thread(loadTask).start();
    }
    public void loadPageThumbnailsParallel(PdfDocument document, Pane container, List<PageThumbnailPanel> trackerList) {
        // 1. Generate a unique ID for this specific loading session
        Object mySessionId = new Object();
        currentSessionId.set(mySessionId);

        logger.debug("Starting parallel thumbnail generation. Session: {}", mySessionId);

        // 2. Clear UI immediately
        container.getChildren().clear();
        if (trackerList != null) trackerList.clear();

        // 3. Prepare the Map to hold results (Thread-safe)
        Map<Integer, PageThumbnailPanel> resultsMap = new ConcurrentHashMap<>();

        // 4. Create a list of Future tasks
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < document.getPageCount(); i++) {
            final int pageIndex = i;

            // Create an async task for each page
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // A. Check for cancellation (Race Condition Check)
                if (currentSessionId.get() != mySessionId) return;

                try {
                    // B. Heavy Lifting (Render)
                    Image thumbnail = pdfService.renderPageThumbnail(pageIndex);

                    // C. Create Panel (Lightweight, unattached node)
                    PageThumbnailPanel panel = new PageThumbnailPanel(
                            pageIndex + 1,
                            thumbnail,
                            selected -> onPageThumbnailToggled(pageIndex, selected),
                            clickedPage -> updatePreview(pageIndex + 1)
                    );

                    // D. Store in Map
                    resultsMap.put(pageIndex, panel);

                } catch (Exception e) {
                    logger.error("Failed to render page {}", pageIndex, e);
                }
            }, renderExecutor);

            futures.add(future);
        }

        // 5. When ALL tasks are finished...
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRunAsync(() -> {
                    // Race Condition Check: Is this session still valid?
                    if (currentSessionId.get() != mySessionId) {
                        logger.debug("Loading cancelled or superseded. Ignoring results.");
                        return;
                    }

                    // 6. Sort and Extract
                    // We use the Map to get items, but we sort by Index to ensure Page 1 comes before Page 2
                    List<PageThumbnailPanel> sortedPanels = resultsMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey()) // Sort by Page Index (Key)
                            .map(Map.Entry::getValue)
                            .collect(Collectors.toList());

                    // 7. Bulk Update UI (One-shot)
                    Platform.runLater(() -> {
                        // Final Race Condition Check
                        if (currentSessionId.get() == mySessionId) {
                            container.getChildren().setAll(sortedPanels);
                            if (trackerList != null) trackerList.addAll(sortedPanels);
                            logger.info("Finished loading {} thumbnails.", sortedPanels.size());
                        }
                    });
                }, Platform::runLater); // Run the final aggregation on the JavaFX thread strictly if needed, or a worker.
        // Ideally, run sort on background, setAll on FX.
        // Modified above to run `thenRunAsync` on common pool, but internally calls Platform.runLater.
    }
    public void loadPageThumbnails(PdfDocument document, Pane container, List<PageThumbnailPanel> trackerList) {
        // 1. Cancel any previous loading task to prevent race conditions
        if (currentLoadingTask != null && !currentLoadingTask.isDone()) {
            currentLoadingTask.cancel();
        }

        // 2. Clear UI immediately (must be on FX Thread)
        container.getChildren().clear();
        if (trackerList != null) trackerList.clear();

        logger.debug("Starting thumbnail generation for {} pages", document.getPageCount());
        currentLoadingTask = new Task<>() {
            @Override
            protected Void call() {
                final int BATCH_SIZE = 10; // Update UI every 10 items
                List<PageThumbnailPanel> batch = new ArrayList<>(BATCH_SIZE);

                for (int i = 0; i < document.getPageCount(); i++) {
                    // 3. Fail-fast: Stop processing if task is cancelled
                    if (isCancelled()) {
                        logger.debug("Thumbnail loading cancelled.");
                        return null;
                    }

                    final int pageIndex = i;
                    try {
                        // Heavy lifting (IO/Rendering) happens here off-thread
                        Image thumbnail = pdfService.renderPageThumbnail(pageIndex);

                        // Create the panel (nodes can be created off-thread, just not attached)
                        PageThumbnailPanel panel = new PageThumbnailPanel(
                                pageIndex + 1,
                                thumbnail,
                                selected -> onPageThumbnailToggled(pageIndex, selected),
                                unused -> updatePreview(pageIndex)
                        );

                        batch.add(panel);

                        // 4. Batch Updates: Only hit the UI thread when batch is full or at the end
                        if (batch.size() >= BATCH_SIZE || pageIndex == document.getPageCount() - 1) {
                            final List<PageThumbnailPanel> toAdd = new ArrayList<>(batch); // Copy for the closure

                            Platform.runLater(() -> {
                                // Check cancellation again before touching UI
                                if (!isCancelled()) {
                                    container.getChildren().addAll(toAdd);
                                    if (trackerList != null) trackerList.addAll(toAdd);
                                }
                            });

                            batch.clear();

                            // Optional: Small sleep to yield CPU if rendering is extremely aggressive
                            // Thread.sleep(10);
                        }

                    } catch (Exception e) {
                        logger.error("Error rendering page {}", pageIndex, e);
                    }
                }
                return null;
            }
        };

        // 5. Handle success/fail states cleanly
        currentLoadingTask.setOnFailed(e -> logger.error("Thumbnail task failed", currentLoadingTask.getException()));

        // Use a daemon thread so it doesn't prevent app shutdown
        Thread thread = new Thread(currentLoadingTask);
        thread.setDaemon(true);
        thread.start();
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

        // Update visual selection of thumbnail panel
        if (currentSelectedPanel != null) {
            currentSelectedPanel.setPreviewSelected(false);
        }
        if (pageIndex >= 0 && pageIndex < thumbnailPanels.size()) {
            currentSelectedPanel = thumbnailPanels.get(pageIndex);
            currentSelectedPanel.setPreviewSelected(true);
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

        renderTask.setOnSucceeded(e -> {
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

    // ==================== Bookmark Methods ====================

    private void loadBookmarks() {
        currentBookmarks = pdfService.getBookmarks();

        if (currentBookmarks.isEmpty()) {
            logger.debug("No bookmarks found in document");
            bookmarksTab.setDisable(true);
            return;
        }

        logger.info("Loaded {} bookmarks", currentBookmarks.size());
        bookmarksTab.setDisable(false);

        // Build the tree structure
        TreeItem<PdfBookmark> root = new TreeItem<>();
        root.setExpanded(true);

        for (PdfBookmark bookmark : currentBookmarks) {
            TreeItem<PdfBookmark> item = createTreeItem(bookmark);
            root.getChildren().add(item);
        }

        bookmarkTreeView.setRoot(root);

        // Set up level spinner based on max depth
        int maxLevel = getMaxBookmarkLevel(currentBookmarks, 0);
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxLevel + 1, 1);
        levelSpinner.setValueFactory(valueFactory);

        updateBookmarkExportButton();
    }

    private int getMaxBookmarkLevel(List<PdfBookmark> bookmarks, int currentLevel) {
        int maxLevel = currentLevel;
        for (PdfBookmark bookmark : bookmarks) {
            if (bookmark.hasChildren()) {
                int childMax = getMaxBookmarkLevel(bookmark.getChildren(), currentLevel + 1);
                maxLevel = Math.max(maxLevel, childMax);
            }
        }
        return maxLevel;
    }

    private TreeItem<PdfBookmark> createTreeItem(PdfBookmark bookmark) {
        TreeItem<PdfBookmark> item = new TreeItem<>(bookmark);
        item.setExpanded(true);

        for (PdfBookmark child : bookmark.getChildren()) {
            item.getChildren().add(createTreeItem(child));
        }

        return item;
    }

    private void updateBookmarkExportButton() {
        List<PdfBookmark> selected = getSelectedBookmarks();
        boolean hasSelection = !selected.isEmpty();
        boolean hasOutputDir = !outputDirField.getText().trim().isEmpty();

        exportBookmarksButton.setDisable(!hasSelection || !hasOutputDir);

        if (hasSelection) {
            exportBookmarksButton.setText("Export " + selected.size() + " Chapter(s)");
        } else {
            exportBookmarksButton.setText("Export by Chapters");
        }
    }

    private List<PdfBookmark> getSelectedBookmarks() {
        List<PdfBookmark> selected = new ArrayList<>();
        collectSelectedBookmarks(currentBookmarks, selected);
        return selected;
    }

    private void collectSelectedBookmarks(List<PdfBookmark> bookmarks, List<PdfBookmark> selected) {
        for (PdfBookmark bookmark : bookmarks) {
            if (bookmark.isSelected()) {
                selected.add(bookmark);
            }
            collectSelectedBookmarks(bookmark.getChildren(), selected);
        }
    }

    @FXML
    private void onSelectAllBookmarks() {
        logger.info("Selecting all bookmarks");
        setAllBookmarksSelected(currentBookmarks, true);
        refreshBookmarkTree();
        updateBookmarkExportButton();
    }

    @FXML
    private void onDeselectAllBookmarks() {
        logger.info("Deselecting all bookmarks");
        setAllBookmarksSelected(currentBookmarks, false);
        refreshBookmarkTree();
        updateBookmarkExportButton();
    }

    @FXML
    private void onSelectLevel() {
        int targetLevel = levelSpinner.getValue();
        logger.info("Selecting bookmarks at level {}", targetLevel);

        // First deselect all
        setAllBookmarksSelected(currentBookmarks, false);

        // Then select only the target level (0-indexed internally, 1-indexed for user)
        selectBookmarksAtLevel(currentBookmarks, 0, targetLevel - 1);

        refreshBookmarkTree();
        updateBookmarkExportButton();
    }

    private void selectBookmarksAtLevel(List<PdfBookmark> bookmarks, int currentLevel, int targetLevel) {
        for (PdfBookmark bookmark : bookmarks) {
            if (currentLevel == targetLevel) {
                bookmark.setSelected(true);
            }
            if (bookmark.hasChildren()) {
                selectBookmarksAtLevel(bookmark.getChildren(), currentLevel + 1, targetLevel);
            }
        }
    }

    private void setAllBookmarksSelected(List<PdfBookmark> bookmarks, boolean selected) {
        for (PdfBookmark bookmark : bookmarks) {
            bookmark.setSelected(selected);
            setAllBookmarksSelected(bookmark.getChildren(), selected);
        }
    }

    private void refreshBookmarkTree() {
        // Force refresh by rebuilding the tree
        if (bookmarkTreeView.getRoot() != null) {
            TreeItem<PdfBookmark> root = new TreeItem<>();
            root.setExpanded(true);

            for (PdfBookmark bookmark : currentBookmarks) {
                TreeItem<PdfBookmark> item = createTreeItem(bookmark);
                root.getChildren().add(item);
            }

            bookmarkTreeView.setRoot(root);
        }
    }

    @FXML
    private void onBrowseOutputDir() {
        logger.info("User clicked browse for output directory");
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Output Directory");

        Stage stage = (Stage) browseDirButton.getScene().getWindow();
        File selectedDir = dirChooser.showDialog(stage);

        if (selectedDir != null) {
            outputDirField.setText(selectedDir.getAbsolutePath());
            logger.debug("Output directory selected: {}", selectedDir.getAbsolutePath());
            updateBookmarkExportButton();
        }
    }

    @FXML
    private void onExportByBookmarks() {
        logger.info("User clicked export by bookmarks");

        List<PdfBookmark> selected = getSelectedBookmarks();
        if (selected.isEmpty()) {
            logger.warn("No bookmarks selected for export");
            showWarning("Please select bookmarks to export");
            return;
        }

        String outputPath = outputDirField.getText().trim();
        if (outputPath.isEmpty()) {
            logger.warn("No output directory specified");
            showWarning("Please specify an output directory");
            return;
        }

        File outputDir = new File(outputPath);
        logger.info("Starting export of {} bookmarks to: {}", selected.size(), outputDir.getAbsolutePath());

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                logger.debug("Background task: extracting by bookmarks");
                pdfService.extractByBookmarks(selected, outputDir);
                return null;
            }
        };

        exportTask.setOnSucceeded(event -> {
            logger.info("Bookmark export completed successfully");
            showInfo("Exported " + selected.size() + " chapter(s) successfully");
        });

        exportTask.setOnFailed(event -> {
            logger.error("Bookmark export failed", exportTask.getException());
            showError("Export failed: " + exportTask.getException().getMessage());
        });

        new Thread(exportTask).start();
    }
}
