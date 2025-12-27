# PDF Tools - Codebase Reference

## Project Overview
JavaFX desktop application for PDF manipulation. Uses Apache PDFBox for PDF operations.

## Build
```bash
mvn compile    # Compile
mvn package    # Build JAR
```

## Architecture

### UI Structure (3-Panel Layout)
```
BorderPane
├── Top: Toolbar (Load PDF button)
├── Center: HBox with 3 panels
│   ├── Left Panel (220px) - TabPane
│   │   ├── Pages Tab: ScrollPane → VBox (thumbnails)
│   │   └── Bookmarks Tab: TreeView
│   ├── Center Panel - Preview
│   │   └── ScrollPane → StackPane → ImageView
│   └── Right Panel (250px) - Selected pages & export
```

### Key Files

| File | Purpose |
|------|---------|
| `ui/extractor/PdfExtractorController.java` | Main controller (~900 lines) |
| `ui/extractor/components/PageThumbnailPanel.java` | Thumbnail component with checkbox |
| `service/PdfService.java` | PDF operations wrapper |
| `service/PdfRenderService.java` | Page rendering (72 DPI thumbnails, 150 DPI preview) |
| `model/PdfDocument.java` | Document model with page cache |
| `model/PdfBookmark.java` | Bookmark model (title, pageIndex, endPageIndex, level) |

### FXML
- `resources/com/datmt/pdftools/ui/extractor/pdf-extractor.fxml`

## Key Implementation Details

### Thumbnail Lazy Loading (PdfExtractorController)
- `MAX_RENDERED_THUMBNAILS = 100` - Never render more than 100 at once
- `BUFFER_SIZE = 5` - Preload 5 panels above/below viewport
- `placeholderImage` - Gray "Loading..." image for unloaded thumbnails
- `renderedThumbnails` - Set tracking which pages have real images
- `scrollDebounceTimeline` - 150ms debounce for scroll events

**Flow:**
1. `loadPageThumbnails()` - Creates all panels with placeholders instantly
2. `updateVisibleThumbnails()` - Called on scroll, loads visible range
3. `getVisiblePanelRange()` - Uses `localToScene()` bounds to detect visibility
4. `loadThumbnailForPage()` - Async loads single thumbnail
5. `unloadFurthestThumbnails()` - Evicts when >100 rendered

### PageThumbnailPanel
- Fixed size: 100x100px thumbnails
- Methods: `setThumbnail(Image)`, `clearThumbnail(Image placeholder)`
- `getPageIndex()` - 0-based index
- `isImageLoaded()` - Check if real image is set

### Rendering
- Thumbnails: 72 DPI via `PdfRenderService.renderPageToThumbnail()`
- Preview: 150 DPI via `PdfRenderService.renderPageToImage()`
- Uses 4-thread pool: `ExecutorService renderExecutor`

### Bookmarks
- TreeView with CheckBox cells
- Click handler jumps to bookmark page via `jumpToPage()`
- `scrollToThumbnail()` centers the page in thumbnail list

## Common Tasks

### Add new thumbnail feature
1. Modify `PageThumbnailPanel.java` for UI
2. Update `PdfExtractorController.java` for logic

### Modify PDF rendering
1. Check `PdfRenderService.java` for DPI settings
2. `PdfService.java` wraps the render service

### Add export functionality
1. Export logic in `PdfService.extractPages()` and `extractByBookmarks()`
2. UI in controller's `onExport()` and `onExportByBookmarks()`

## Performance & Memory Guidelines

### Resource Cleanup (CRITICAL)
When creating new tools/controllers, ALWAYS implement cleanup:

```java
// In initialize()
setupWindowCloseHandler();

private void setupWindowCloseHandler() {
    Platform.runLater(() -> {
        if (someButton.getScene() != null && someButton.getScene().getWindow() != null) {
            someButton.getScene().getWindow().setOnCloseRequest(event -> cleanup());
        }
    });
}

public void cleanup() {
    renderExecutor.shutdownNow();       // Shutdown thread pools
    pdfService.closeDocument();          // Close PDDocument
    // Clear caches, lists, UI containers
}
```

**Must clean up:**
- `ExecutorService` - call `shutdownNow()`
- `PDDocument` - call `close()` (via PdfService.closeDocument() or JoinerFile.close())
- Image caches - clear lists
- Timeline/animations - call `stop()`

### Bulk Operations Pattern
When updating many UI elements at once, use a flag to prevent N updates:

```java
private boolean isBulkOperation = false;

private void onSelectAll() {
    isBulkOperation = true;
    try {
        for (item : items) {
            item.setSelected(true);  // This triggers listener
        }
    } finally {
        isBulkOperation = false;
    }
    updateUI();  // Single update at end
}

private void onItemChanged() {
    if (isBulkOperation) return;  // Skip during bulk
    updateUI();
}
```

### Large Collections in UI
Don't create N UI nodes for large selections:

```java
if (count > 50) {
    // Show summary: "4150 pages selected" + "1-4150"
    Label summary = new Label(count + " pages selected");
} else {
    // Show individual items only for small counts
    for (item : items) { ... }
}
```

### PDF Page Import
When copying pages between PDDocuments, use `importPage()` not `addPage()`:

```java
// WRONG - corrupts fonts/resources
PDPage page = sourcePdf.getPage(i);
outputDoc.addPage(page);

// CORRECT - properly clones page with resources
PDPage importedPage = outputDoc.importPage(page);
```

### Loading New Documents
Always close previous document before loading new one:

```java
public PdfDocument loadPdf(File file) {
    closeDocument();  // Close previous first
    currentDocument = loader.loadPdf(file);
    return currentDocument;
}
```
