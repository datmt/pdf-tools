# PDF Tools Architecture

## Overview

This application is designed as a modular, extensible PDF processing toolkit with a main tool selection screen and
individual tool implementations. The first tool implemented is the **PDF Extractor**.

## Project Structure

```
pdf-tools/
├── src/main/java/com/datmt/pdftools/
│   ├── MainApplication.java              # Entry point
│   │
│   ├── model/
│   │   └── PdfDocument.java              # PDF document model with page caching
│   │
│   ├── service/
│   │   ├── PdfService.java               # Main orchestration (facade pattern)
│   │   ├── PdfLoader.java                # PDF file loading
│   │   ├── PdfExtractor.java             # Page extraction operations
│   │   └── PdfRenderService.java         # PDF page rendering to images
│   │
│   ├── ui/
│   │   ├── controller/
│   │   │   └── MainScreenController.java # Main tool selection screen
│   │   │
│   │   └── extractor/
│   │       ├── PdfExtractorController.java
│   │       └── components/
│   │           └── PageThumbnailPanel.java
│   │
│   └── [Obsolete files to remove]
│       ├── HelloApplication.java
│       ├── HelloController.java
│       └── Launcher.java
│
├── src/main/resources/
│   ├── logback.xml                       # Logging configuration
│   └── com/datmt/pdftools/ui/
│       ├── main-screen.fxml              # Tool selection UI
│       └── extractor/
│           └── pdf-extractor.fxml        # Extractor layout
│
└── pom.xml                               # Maven configuration
```

## Architecture Layers

### 1. **UI Layer** (FXML + Controllers)

- **Purpose:** Display and handle user interactions
- **Responsibilities:**
    - File dialogs and UI events
    - Real-time updates to views
    - User input validation and feedback
- **Threading:** Long operations (PDF loading, rendering) run on background threads
- **Controllers:**
    - `MainScreenController` - Tool selection
    - `PdfExtractorController` - Extractor tool UI logic

### 2. **Service Layer** (Business Logic)

- **Purpose:** Orchestrate PDF operations
- **Responsibilities:**
    - PDF file operations (load, extract, render)
    - Document lifecycle management
    - Thread-safe operations
- **Classes:**
    - `PdfService` - Facade for all PDF operations
    - `PdfLoader` - File loading
    - `PdfExtractor` - Page extraction
    - `PdfRenderService` - Image rendering

### 3. **Model Layer**

- **Purpose:** Represent domain objects
- **Classes:**
    - `PdfDocument` - Wrapper for loaded PDF with caching support

## Key Design Patterns

### Facade Pattern

`PdfService` acts as a single entry point for all PDF operations, hiding complexity from controllers.

```
Controller → PdfService → [Loader, Extractor, RenderService]
```

### Background Tasks (Async Operations)

All long-running operations use JavaFX `Task` to prevent UI blocking:

- PDF loading
- Page rendering
- PDF extraction/export

### Component Composition

Reusable UI components (e.g., `PageThumbnailPanel`) encapsulate thumbnail display and selection logic.

## Logging Strategy

### Configuration

- **File:** `src/main/resources/logback.xml`
- **Output:** Console + Rolling file appenders (logs/ directory)
- **Levels by Package:**
    - `com.datmt.pdftools` → TRACE (verbose)
    - `org.apache.pdfbox` → DEBUG
    - Root → INFO

### Usage Examples

```java
logger.trace("Parsing page input: {}", input);           // Detailed flow
logger.debug("Background task: loading PDF");             // Step details
logger.info("Successfully loaded PDF: {} pages", count);  // Important events
logger.warn("File does not have .pdf extension");         // Unexpected but ok
logger.error("Failed to load PDF", exception);            // Failures with stack trace
```

## PDF Processing Dependencies

### Apache PDFBox 3.0.1

- **Used for:** PDF parsing, rendering, page manipulation
- **Key Classes:**
    - `PDDocument` - Represents a PDF file
    - `PDFRenderer` - Renders pages to BufferedImage
    - `PDPage` - Individual page representation

### JavaFX Swing Integration

- **Used for:** Converting BufferedImage → JavaFX Image
- **Class:** `SwingFXUtils.toFXImage()`

## Extending the Application

### Adding a New Tool

1. **Create UI Package**
   ```
   ui/mytool/
   ├── MyToolController.java
   ├── MyToolView.fxml
   └── components/
       └── [custom components]
   ```

2. **Create FXML Layout**
    - Define UI in `MyToolView.fxml`
    - Reference controller class

3. **Create Controller**
   ```java
   public class MyToolController {
       @FXML
       public void initialize() { /* setup */ }
       
       // Action handlers...
   }
   ```

4. **Update MainScreenController**
   ```java
   @FXML
   private void onMyToolClicked() {
       FXMLLoader fxmlLoader = new FXMLLoader(
           getClass().getResource("/com/datmt/pdftools/ui/mytool/MyToolView.fxml"));
       Scene scene = new Scene(fxmlLoader.load(), width, height);
       Stage stage = new Stage();
       stage.setScene(scene);
       stage.show();
   }
   ```

5. **Reuse PdfService**
   ```java
   private PdfService pdfService = new PdfService();
   
   // Access common operations
   pdfService.loadPdf(file);
   pdfService.renderPage(pageIndex);
   ```

### Adding New PDF Operations

1. **Create new service class** (if needed)
   ```java
   public class PdfMerger {
       public void mergeDocuments(List<File> files, File output) { /* ... */ }
   }
   ```

2. **Add method to PdfService**
   ```java
   private PdfMerger merger = new PdfMerger();
   
   public void mergeFiles(List<File> files, File output) throws IOException {
       logger.info("Merging {} files", files.size());
       merger.mergeDocuments(files, output);
   }
   ```

3. **Call from controller**
   ```java
   pdfService.mergeFiles(selectedFiles, outputFile);
   ```

## PDF Extractor Tool Details

### Three-Panel Layout

```
┌─────────────────────────────────────────────────────────┐
│ [Load PDF] ................... [10 pages]                │
├──────────────┬─────────────────────────────┬─────────────┤
│   Pages      │                             │  Extract    │
│              │        Preview              │  Options    │
│  [1][2][3]   │   ┌──────────────────────┐  │             │
│  [4][5][6]   │   │  Page Image Display  │  │ [Selected]  │
│  [7][8][9]   │   │                      │  │ Pages: 1,3,5│
│  [10]        │   └──────────────────────┘  │             │
│              │   [< Prev] [Next >]         │ Input: 1,3-5│
│ [Sel All]    │                             │ [Add]       │
│ [Desel All]  │                             │             │
│              │                             │ [Remove]    │
│              │                             │ [Clear]     │
│              │                             │             │
│              │                             │ Output: ...│
│              │                             │ [Browse]   │
│              │                             │ [Export]   │
└──────────────┴─────────────────────────────┴─────────────┘
```

### Features

- **Page Selection:** Individual pages, ranges (1-5), or mixed (1,3,5-7)
- **Page Preview:** Click thumbnail or use navigation buttons
- **Visual Feedback:** Selected pages listed on right panel
- **Batch Export:** Extract multiple pages in one operation

### Input Format

Pages can be specified as:

- **Single:** `1, 3, 5`
- **Range:** `1-10`
- **Mixed:** `1,3,5-10,15`

(1-based indexing for user, internally converted to 0-based)

## Running the Application

### Development

```bash
mvn clean javafx:run
```

### Building

```bash
mvn clean package
```

### Logs Location

```
./logs/pdf-tools.log
```

## Dependencies

### Core

- JavaFX 21.0.6 (UI framework)
- Apache PDFBox 3.0.1 (PDF processing)

### Logging

- Logback 1.5.3
- SLF4J 2.0.11

### Build

- Maven 3.x
- Java 25+

## Thread Safety

### Background Task Pattern

```java
Task<PdfDocument> loadTask = new Task<>() {
    @Override
    protected PdfDocument call() throws Exception {
        // Heavy operation on background thread
        return pdfService.loadPdf(file);
    }
};

loadTask.

setOnSucceeded(event ->{
        // Update UI on JavaFX thread
        Platform.

runLater(() ->

updateUI());
        });

        new

Thread(loadTask).

start();
```

### Key Points

- Rendering happens on background thread
- UI updates use `Platform.runLater()`
- PdfService is stateful but used sequentially per window
- Each window has its own PdfService instance

## Error Handling

### Logging Levels

- **IOException during PDF operations** → WARN/ERROR + user alert
- **Invalid page indices** → WARN (input validation) + user alert
- **UI state issues** → DEBUG logs only (expected conditions)

### User Feedback

- Dialog boxes for errors, warnings, info
- Console and file logs for debugging
- Graceful degradation (disable UI elements when not applicable)

## Future Enhancements

1. **PDF Joiner Tool**
    - Merge multiple PDFs
    - Reorder pages from multiple files
    - Reuse page thumbnail component

2. **PDF Splitter Tool**
    - Auto-detect split points
    - Define split ranges
    - Batch operations

3. **Image to PDF Tool**
    - Load multiple images
    - Arrange in custom order
    - Adjust spacing/margins

4. **Shared Features**
    - Recent files manager
    - Batch operations queue
    - Settings/preferences dialog
    - Progress indicators for long operations

## Code Quality

- **Verbose Logging:** Every significant operation logged at appropriate level
- **Defensive Programming:** Input validation at UI layer
- **Resource Management:** Try-with-resources, proper cleanup
- **Separation of Concerns:** Clear responsibility boundaries
- **Testability:** Services can be tested independently
