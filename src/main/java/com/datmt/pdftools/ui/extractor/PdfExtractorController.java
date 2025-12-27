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

import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

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
    private static final int MAX_RENDERED_THUMBNAILS = 100;
    private static final int BUFFER_SIZE = 5; // Extra panels above/below viewport to preload
    private static final int PANEL_HEIGHT = 130; // Approximate height of each thumbnail panel

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
    @FXML
    private Button rotateLeftButton, rotateRightButton;
    private PdfService pdfService;
    private Set<Integer> selectedPages;
    private List<PageThumbnailPanel> thumbnailPanels;
    private int currentPreviewPage;
    private ImageView currentImageView;
    private PageThumbnailPanel currentSelectedPanel;
    private List<PdfBookmark> currentBookmarks;
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(4); // Limit threads to save RAM
    private final AtomicReference<Object> currentSessionId = new AtomicReference<>(); // Token to track active request

    // Lazy loading state
    private Image placeholderImage;
    private Set<Integer> renderedThumbnails = new HashSet<>();
    private Timeline scrollDebounceTimeline;

    // Page rotation tracking (pageIndex -> rotation in degrees: 0, 90, 180, 270)
    private Map<Integer, Integer> pageRotations = new HashMap<>();

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
        setupLazyLoading();
        logger.debug("Controller initialization complete");
    }

    private void setupLazyLoading() {
        // Create placeholder image
        placeholderImage = createPlaceholderImage();

        // Set up scroll listener with debouncing (150ms to handle slider dragging)
        scrollDebounceTimeline = new Timeline(new KeyFrame(Duration.millis(150), e -> updateVisibleThumbnails()));
        scrollDebounceTimeline.setCycleCount(1);

        pagesScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            // Restart debounce timer on each scroll event
            scrollDebounceTimeline.stop();
            scrollDebounceTimeline.playFromStart();
        });

        // Also listen to viewport changes (window resize)
        pagesScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            scrollDebounceTimeline.stop();
            scrollDebounceTimeline.playFromStart();
        });
    }

    private Image createPlaceholderImage() {
        int size = 100;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Gray background
        gc.setFill(Color.rgb(230, 230, 230));
        gc.fillRect(0, 0, size, size);

        // Border
        gc.setStroke(Color.rgb(200, 200, 200));
        gc.strokeRect(0, 0, size, size);

        // Loading text
        gc.setFill(Color.rgb(150, 150, 150));
        gc.setFont(Font.font(12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Loading...", size / 2, size / 2);

        return canvas.snapshot(null, null);
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

        // Add click handler to jump to bookmark page
        bookmarkTreeView.setOnMouseClicked(event -> {
            TreeItem<PdfBookmark> selectedItem = bookmarkTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getValue() != null) {
                PdfBookmark bookmark = selectedItem.getValue();
                int pageIndex = bookmark.getPageIndex();
                logger.debug("Bookmark clicked: '{}', jumping to page {}", bookmark.getTitle(), pageIndex + 1);
                jumpToPage(pageIndex);
            }
        });
    }

    private void jumpToPage(int pageIndex) {
        if (!pdfService.isDocumentLoaded() || pageIndex < 0 || pageIndex >= thumbnailPanels.size()) {
            return;
        }

        // Update the preview
        updatePreview(pageIndex);

        // Scroll the thumbnail list to show this page
        scrollToThumbnail(pageIndex);
    }

    private void scrollToThumbnail(int pageIndex) {
        if (thumbnailPanels.isEmpty() || pageIndex < 0 || pageIndex >= thumbnailPanels.size()) {
            return;
        }

        // Calculate scroll position to center the target panel
        double contentHeight = pagesListContainer.getHeight();
        double viewportHeight = pagesScrollPane.getViewportBounds().getHeight();

        if (contentHeight <= viewportHeight) {
            // All content fits, no scrolling needed
            return;
        }

        // Get the target panel's position
        PageThumbnailPanel targetPanel = thumbnailPanels.get(pageIndex);
        double panelY = targetPanel.getBoundsInParent().getMinY();

        // Calculate scroll value to center the panel
        double scrollY = panelY - (viewportHeight / 2) + (PANEL_HEIGHT / 2);
        double scrollValue = Math.max(0, Math.min(1, scrollY / (contentHeight - viewportHeight)));

        pagesScrollPane.setVvalue(scrollValue);
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
            pageRotations.clear();
            currentPreviewPage = 0;

            loadPageThumbnails(doc, pagesListContainer, thumbnailPanels);
            updatePreview(0);

            removeSelectedButton.setDisable(selectedPagesList.getChildren().isEmpty());
            clearButton.setDisable(true);

            // Enable rotation buttons
            rotateLeftButton.setDisable(false);
            rotateRightButton.setDisable(false);

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
                            clickedPage -> updatePreview(pageIndex)
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
        // Clear previous state
        container.getChildren().clear();
        if (trackerList != null) trackerList.clear();
        renderedThumbnails.clear();

        logger.debug("Creating {} placeholder panels for lazy loading", document.getPageCount());

        // Create all panels with placeholder images immediately (fast, no rendering)
        for (int i = 0; i < document.getPageCount(); i++) {
            final int pageIndex = i;
            PageThumbnailPanel panel = new PageThumbnailPanel(
                    pageIndex + 1,
                    placeholderImage,
                    selected -> onPageThumbnailToggled(pageIndex, selected),
                    unused -> updatePreview(pageIndex)
            );
            container.getChildren().add(panel);
            if (trackerList != null) trackerList.add(panel);
        }

        logger.debug("Created {} placeholder panels, triggering initial lazy load", document.getPageCount());

        // Trigger initial visibility check after layout is complete
        Platform.runLater(() -> {
            // Small delay to ensure layout is calculated
            Timeline initialLoad = new Timeline(new KeyFrame(Duration.millis(100), e -> updateVisibleThumbnails()));
            initialLoad.play();
        });
    }

    private void updateVisibleThumbnails() {
        if (thumbnailPanels.isEmpty() || !pdfService.isDocumentLoaded()) {
            return;
        }

        int[] visibleRange = getVisiblePanelRange();
        int firstVisible = visibleRange[0];
        int lastVisible = visibleRange[1];

        logger.trace("Visible range: {} to {}, currently rendered: {}", firstVisible, lastVisible, renderedThumbnails.size());

        // Determine which panels need to be loaded
        Set<Integer> toLoad = new HashSet<>();
        for (int i = firstVisible; i <= lastVisible; i++) {
            if (i >= 0 && i < thumbnailPanels.size() && !renderedThumbnails.contains(i)) {
                toLoad.add(i);
            }
        }

        // If we would exceed the limit, unload furthest non-visible thumbnails first
        int willBeRendered = renderedThumbnails.size() + toLoad.size();
        if (willBeRendered > MAX_RENDERED_THUMBNAILS) {
            int toUnload = willBeRendered - MAX_RENDERED_THUMBNAILS;
            unloadFurthestThumbnails(firstVisible, lastVisible, toUnload);
        }

        // Load visible thumbnails
        for (int pageIndex : toLoad) {
            loadThumbnailForPage(pageIndex);
        }
    }

    private int[] getVisiblePanelRange() {
        if (thumbnailPanels.isEmpty()) {
            return new int[]{0, 0};
        }

        // Get viewport bounds in scene coordinates
        Bounds viewportBoundsInScene = pagesScrollPane.getViewportBounds();
        Bounds scrollPaneBoundsInScene = pagesScrollPane.localToScene(pagesScrollPane.getBoundsInLocal());

        if (scrollPaneBoundsInScene == null) {
            return new int[]{0, Math.min(BUFFER_SIZE * 2, thumbnailPanels.size() - 1)};
        }

        double viewportTop = scrollPaneBoundsInScene.getMinY();
        double viewportBottom = scrollPaneBoundsInScene.getMaxY();

        int firstVisible = -1;
        int lastVisible = -1;

        // Find panels that intersect with the viewport
        for (int i = 0; i < thumbnailPanels.size(); i++) {
            PageThumbnailPanel panel = thumbnailPanels.get(i);
            Bounds panelBoundsInScene = panel.localToScene(panel.getBoundsInLocal());

            if (panelBoundsInScene == null) continue;

            double panelTop = panelBoundsInScene.getMinY();
            double panelBottom = panelBoundsInScene.getMaxY();

            // Check if panel intersects with viewport
            boolean isVisible = panelBottom >= viewportTop && panelTop <= viewportBottom;

            if (isVisible) {
                if (firstVisible == -1) {
                    firstVisible = i;
                }
                lastVisible = i;
            } else if (firstVisible != -1) {
                // We've passed the visible area, no need to continue
                break;
            }
        }

        // If no panels visible yet (layout not ready), default to first few
        if (firstVisible == -1) {
            firstVisible = 0;
            lastVisible = Math.min(BUFFER_SIZE * 2, thumbnailPanels.size() - 1);
        }

        // Add buffer
        firstVisible = Math.max(0, firstVisible - BUFFER_SIZE);
        lastVisible = Math.min(thumbnailPanels.size() - 1, lastVisible + BUFFER_SIZE);

        return new int[]{firstVisible, lastVisible};
    }

    private void loadThumbnailForPage(int pageIndex) {
        if (renderedThumbnails.contains(pageIndex) || pageIndex < 0 || pageIndex >= thumbnailPanels.size()) {
            return;
        }

        // Mark as pending to avoid duplicate loads
        renderedThumbnails.add(pageIndex);

        CompletableFuture.runAsync(() -> {
            try {
                Image thumbnail = pdfService.renderPageThumbnail(pageIndex);
                Platform.runLater(() -> {
                    if (pageIndex < thumbnailPanels.size()) {
                        thumbnailPanels.get(pageIndex).setThumbnail(thumbnail);
                        logger.trace("Loaded thumbnail for page {}", pageIndex);
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to load thumbnail for page {}", pageIndex, e);
                // Remove from rendered set so it can be retried
                renderedThumbnails.remove(pageIndex);
            }
        }, renderExecutor);
    }

    private void unloadFurthestThumbnails(int firstVisible, int lastVisible, int count) {
        // Find rendered thumbnails that are furthest from the visible range
        List<Integer> candidates = new ArrayList<>(renderedThumbnails);

        // Sort by distance from visible range (furthest first)
        int center = (firstVisible + lastVisible) / 2;
        candidates.sort((a, b) -> {
            int distA = Math.min(Math.abs(a - firstVisible), Math.abs(a - lastVisible));
            int distB = Math.min(Math.abs(b - firstVisible), Math.abs(b - lastVisible));
            return Integer.compare(distB, distA); // Descending order (furthest first)
        });

        // Unload the furthest ones (but not those in visible range)
        int unloaded = 0;
        for (int pageIndex : candidates) {
            if (unloaded >= count) break;
            if (pageIndex < firstVisible || pageIndex > lastVisible) {
                if (pageIndex >= 0 && pageIndex < thumbnailPanels.size()) {
                    thumbnailPanels.get(pageIndex).clearThumbnail(placeholderImage);
                    renderedThumbnails.remove(pageIndex);
                    unloaded++;
                    logger.trace("Unloaded thumbnail for page {}", pageIndex);
                }
            }
        }
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
            if (image == null) {
                logger.warn("Rendered image is null for page {}", pageIndex);
                return;
            }
            previewContainer.getChildren().clear();
            currentImageView = new ImageView(image);
            currentImageView.setPreserveRatio(true);

            // Apply rotation if any
            int rotation = pageRotations.getOrDefault(pageIndex, 0);
            currentImageView.setRotate(rotation);

            // Use container width if available, otherwise use image width
            double containerWidth = previewContainer.getWidth();
            if (containerWidth > 20) {
                currentImageView.setFitWidth(containerWidth - 20);
            } else {
                // Fallback: bind to container width for when layout is calculated
                currentImageView.fitWidthProperty().bind(
                    previewContainer.widthProperty().subtract(20)
                );
            }

            previewContainer.getChildren().add(currentImageView);
            logger.trace("Page {} preview rendered and displayed with rotation {}", pageIndex, rotation);
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

        // Collect rotations for selected pages
        Map<Integer, Integer> selectedRotations = new HashMap<>();
        for (int pageIndex : selectedPages) {
            int rotation = pageRotations.getOrDefault(pageIndex, 0);
            if (rotation != 0) {
                selectedRotations.put(pageIndex, rotation);
            }
        }

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                logger.debug("Background task: extracting pages with {} rotations", selectedRotations.size());
                pdfService.extractPages(new HashSet<>(selectedPages), selectedRotations, outputFile);
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

    @FXML
    private void onRotateLeft() {
        rotateSelectedPages(-90);
    }

    @FXML
    private void onRotateRight() {
        rotateSelectedPages(90);
    }

    private void rotateSelectedPages(int degrees) {
        if (!pdfService.isDocumentLoaded()) {
            return;
        }

        // Get pages to rotate: selected pages, or current preview page if none selected
        Set<Integer> pagesToRotate = new HashSet<>(selectedPages);
        if (pagesToRotate.isEmpty() && currentPreviewPage >= 0) {
            pagesToRotate.add(currentPreviewPage);
        }

        if (pagesToRotate.isEmpty()) {
            showWarning("Please select pages to rotate");
            return;
        }

        logger.info("Rotating {} pages by {} degrees", pagesToRotate.size(), degrees);

        for (int pageIndex : pagesToRotate) {
            int currentRotation = pageRotations.getOrDefault(pageIndex, 0);
            int newRotation = (currentRotation + degrees + 360) % 360;
            pageRotations.put(pageIndex, newRotation);
            logger.debug("Page {} rotation: {} -> {}", pageIndex + 1, currentRotation, newRotation);

            // Update thumbnail display
            updateThumbnailRotation(pageIndex);
        }

        // Refresh preview if current page was rotated
        if (pagesToRotate.contains(currentPreviewPage)) {
            updatePreview(currentPreviewPage);
        }

        showInfo("Rotated " + pagesToRotate.size() + " page(s)");
    }

    private void updateThumbnailRotation(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= thumbnailPanels.size()) {
            return;
        }

        PageThumbnailPanel panel = thumbnailPanels.get(pageIndex);
        int rotation = pageRotations.getOrDefault(pageIndex, 0);
        panel.setRotation(rotation);
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
