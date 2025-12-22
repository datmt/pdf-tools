# Developer Guide

## Project Overview

PDF Tools is a modular JavaFX application for PDF processing. It features:
- **Tool-based architecture** - Easy to add new tools
- **Service-oriented design** - Reusable PDF operations
- **Verbose logging** - Comprehensive debugging information
- **Async rendering** - Responsive UI with background operations

## Getting Started

### Prerequisites
```bash
java -version          # Java 25+
mvn -version          # Maven 3.6+
```

### First Build
```bash
cd pdf-tools
mvn clean compile     # Verify dependencies
mvn clean javafx:run  # Run the application
```

### IDE Setup
- **IntelliJ IDEA:** Open as Maven project
- **Eclipse:** Import as Maven project
- **VS Code:** Install Extension Pack for Java

## Code Organization

### Service Layer (`com.datmt.pdftools.service`)

All PDF operations go here. Create new services for new operations.

#### PdfService (Facade)
```java
// Entry point for all PDF operations
pdfService.loadPdf(file)                    // PdfDocument
pdfService.renderPage(pageIndex)            // Image
pdfService.extractPages(indices, output)    // void
```

**When to add methods:**
- New general-purpose PDF operation
- Used by multiple controllers
- Example: `rotatePage(index, degrees)`

#### Specialized Services
```java
PdfLoader       → File I/O
PdfExtractor    → Page operations
PdfRenderService → Image rendering
```

**When to create new service:**
- Large cohesive group of related operations
- Example: `PdfMerger` for joining operations

### Controller Layer (`com.datmt.pdftools.ui`)

Controller responsibilities:
1. Wire FXML UI elements
2. Handle user events
3. Call PdfService methods
4. Update UI with results
5. Show user feedback (dialogs)

#### Pattern: Async Operations
```java
Task<T> task = new Task<>() {
    @Override
    protected T call() throws Exception {
        // Heavy operation here
        return pdfService.heavyOperation();
    }
};

task.setOnSucceeded(event -> {
    // Update UI on JavaFX thread
    Platform.runLater(() -> updateUI(task.getValue()));
});

task.setOnFailed(event -> {
    logger.error("Operation failed", task.getException());
    showError("Error", task.getException().getMessage());
});

new Thread(task).start();
```

#### Pattern: Input Validation
```java
@FXML
private void onUserInput() {
    String input = inputField.getText().trim();
    logger.info("User input: {}", input);
    
    if (input.isEmpty()) {
        logger.warn("Input validation failed: empty");
        showWarning("Validation", "Please enter something");
        return;
    }
    
    try {
        processInput(input);
    } catch (IllegalArgumentException e) {
        logger.warn("Input validation failed: {}", e.getMessage());
        showWarning("Invalid Input", e.getMessage());
    }
}
```

### FXML Files (`src/main/resources/com/datmt/pdftools/ui`)

Naming: `lowercase-with-dashes.fxml`

**Each FXML file should:**
1. Reference correct controller class
2. Set `fx:id` on elements that code accesses
3. Wire event handlers with `onAction="#methodName"`
4. Use meaningful layout hierarchy (VBox, HBox, etc.)

Example:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<BorderPane xmlns="http://javafx.com/javafx"
            xmlns:fx="http://javafx.com/fxml"
            fx:controller="com.datmt.pdftools.ui.MyToolController">
    
    <top>
        <HBox>
            <Button fx:id="myButton" text="Click Me" onAction="#onButtonClicked"/>
        </HBox>
    </top>
    
</BorderPane>
```

### Model Layer (`com.datmt.pdftools.model`)

**Rules:**
1. No business logic - just data
2. No dependencies on services or UI
3. One class per entity type

Current:
- `PdfDocument` - Wrapper for PDDocument with caching

Future:
- `ExtractionConfig` - Configuration for operations
- `ExportSettings` - User preferences

## Adding a New Tool

### Step 1: Create Package Structure
```
src/main/java/com/datmt/pdftools/ui/mytool/
├── MyToolController.java
└── components/
    └── [custom components]
```

### Step 2: Create FXML Layout
```xml
<!-- src/main/resources/com/datmt/pdftools/ui/mytool/my-tool.fxml -->
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<BorderPane xmlns="http://javafx.com/javafx"
            xmlns:fx="http://javafx.com/fxml"
            fx:controller="com.datmt.pdftools.ui.mytool.MyToolController">
    <center>
        <Label text="My Tool"/>
    </center>
</BorderPane>
```

### Step 3: Create Controller
```java
package com.datmt.pdftools.ui.mytool;

import com.datmt.pdftools.service.PdfService;
import javafx.fxml.FXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyToolController {
    private static final Logger logger = LoggerFactory.getLogger(MyToolController.class);
    
    private PdfService pdfService;
    
    @FXML
    public void initialize() {
        logger.trace("Initializing MyToolController");
        pdfService = new PdfService();
    }
}
```

### Step 4: Add Button to MainScreenController
```java
@FXML
private void onMyToolClicked() {
    logger.info("User clicked My Tool button");
    try {
        FXMLLoader fxmlLoader = new FXMLLoader(
            getClass().getResource("/com/datmt/pdftools/ui/mytool/my-tool.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 700);
        Stage stage = new Stage();
        stage.setTitle("My Tool");
        stage.setScene(scene);
        stage.show();
        logger.info("My Tool window opened");
    } catch (IOException e) {
        logger.error("Failed to load My Tool", e);
    }
}
```

### Step 5: Update Module Info
```java
opens com.datmt.pdftools.ui.mytool to javafx.fxml;
exports com.datmt.pdftools.ui.mytool;
```

## Adding New PDF Operations

### Option 1: Add to PdfService
For small operations:

```java
public void myOperation(int pageIndex, File output) throws IOException {
    if (!isDocumentLoaded()) {
        logger.error("Cannot perform operation: no document loaded");
        throw new IllegalStateException("No PDF document loaded");
    }
    logger.info("Performing myOperation on page {}", pageIndex);
    // implementation
}
```

### Option 2: Create Specialized Service
For larger related operations:

```java
package com.datmt.pdftools.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfWatermarker {
    private static final Logger logger = LoggerFactory.getLogger(PdfWatermarker.class);
    
    public void addWatermark(PdfDocument doc, String text, File output) throws IOException {
        logger.debug("Adding watermark to PDF: {}", text);
        // implementation
        logger.info("Watermark added successfully");
    }
}
```

Then add to PdfService:
```java
private PdfWatermarker watermarker = new PdfWatermarker();

public void addWatermark(String text, File output) throws IOException {
    if (!isDocumentLoaded()) {
        throw new IllegalStateException("No PDF document loaded");
    }
    watermarker.addWatermark(currentDocument, text, output);
}
```

## Logging Best Practices

### Log Levels

| Level | Usage | Example |
|-------|-------|---------|
| TRACE | Detailed flow, low-level details | Parsing inputs, loop iterations |
| DEBUG | Step-by-step operations, state changes | Rendering page 5, loading service |
| INFO | Important events, user actions | File saved, PDF loaded, operation completed |
| WARN | Unexpected but recoverable | Invalid input, deprecated API |
| ERROR | Failures that affect functionality | File not found, IO error |

### Logging Patterns

**Method entry (TRACE):**
```java
logger.trace("Parsing page input: {}", input);
```

**Validation failures (WARN):**
```java
if (pageIndex < 0) {
    logger.warn("Invalid page index: {}", pageIndex);
    throw new IllegalArgumentException(...);
}
```

**Success/completion (INFO):**
```java
logger.info("Successfully extracted {} pages to: {}", 
    pageIndices.size(), outputFile.getAbsolutePath());
```

**Exceptions (ERROR):**
```java
catch (IOException e) {
    logger.error("Failed to load PDF: {}", filePath, e);
    throw e;
}
```

### Multi-line Context

For complex operations, log the state:
```java
logger.debug("Starting extract operation");
logger.debug("  Source: {}", sourceFile.getName());
logger.debug("  Pages: {}", selectedPages);
logger.debug("  Output: {}", outputFile.getAbsolutePath());
// ... perform operation ...
logger.info("Extraction completed successfully");
```

## Testing Approach

### Unit Testing Services
Services can be tested independently:

```java
// Example test structure
public class PdfExtractorTest {
    private PdfExtractor extractor;
    private PdfDocument testDocument;
    
    @Before
    public void setUp() {
        extractor = new PdfExtractor();
        // Load test PDF
    }
    
    @Test
    public void testExtractSinglePage() {
        // Assert
    }
}
```

### Integration Testing Controllers
Load FXML and verify UI behavior.

### Manual Testing
1. Load various PDF sizes
2. Test edge cases (1 page, 100 pages)
3. Verify error handling
4. Check logs for verbose output

## Performance Considerations

### Page Rendering
- Happens on background thread
- Results cached in PdfDocument
- Thumbnail (72 DPI) vs full (150 DPI)
- Use `SwingFXUtils.toFXImage()` for conversion

### Memory Usage
- PDFBox keeps entire document in memory
- Page images cached but can be cleared
- For large PDFs (100+ pages), consider:
  - Lazy thumbnail loading
  - Cache eviction strategy
  - Streaming extraction

### Concurrency
- Each window has own PdfService instance
- Operations use JavaFX Task for threading
- No shared mutable state

## Common Mistakes

### 1. Blocking UI Thread
**Wrong:**
```java
Image image = pdfService.renderPage(0);  // Blocks!
imageView.setImage(image);
```

**Right:**
```java
Task<Image> task = new Task<>() {
    @Override
    protected Image call() throws Exception {
        return pdfService.renderPage(0);
    }
};
task.setOnSucceeded(e -> imageView.setImage(task.getValue()));
new Thread(task).start();
```

### 2. Not Handling Exceptions
**Wrong:**
```java
try {
    pdfService.extractPages(pages, file);
} catch (IOException e) {
    // Silently ignore!
}
```

**Right:**
```java
try {
    pdfService.extractPages(pages, file);
} catch (IOException e) {
    logger.error("Extraction failed", e);
    showError("Export Failed", e.getMessage());
}
```

### 3. Missing Null Checks
**Wrong:**
```java
pdfService.extractPages(pages, file);  // What if no document loaded?
```

**Right:**
```java
if (!pdfService.isDocumentLoaded()) {
    logger.warn("No document loaded for extraction");
    showWarning("No Document", "Please load a PDF first");
    return;
}
pdfService.extractPages(pages, file);
```

### 4. Hard-Coded Values
**Wrong:**
```java
double[] dims = {595, 842};  // A4?
```

**Right:**
```java
double[] dims = pdfService.getPageDimensions(pageIndex);
```

## Building and Debugging

### Build with Details
```bash
mvn clean compile -X              # Verbose
mvn clean javafx:run -e           # Show errors
```

### Running with Different Memory
```bash
mvn javafx:run -Dexec.args="-Xmx2G"
```

### Checking Logs
```bash
tail -f logs/pdf-tools.log        # Follow log
grep ERROR logs/pdf-tools.log      # Find errors
```

### Debug Mode (with IDE)
1. Set breakpoints in code
2. Run with debug configuration
3. Step through code
4. Check variables and logs

## Useful Resources

- **JavaFX Documentation:** https://docs.oracle.com/javase/8/javafx/
- **Apache PDFBox:** https://pdfbox.apache.org/
- **Logback:** http://logback.qos.ch/
- **SLF4J:** http://www.slf4j.org/

## Code Review Checklist

Before committing:

- [ ] Logging added at appropriate levels
- [ ] Error handling complete (try-catch + log)
- [ ] No UI blocking (async operations use Task)
- [ ] FXML references match controller fields
- [ ] No hardcoded paths or values
- [ ] Null checks where needed
- [ ] Resource cleanup (close files, clear caches)
- [ ] Module-info.java updated if adding packages

## Getting Help

1. **Check logs first:** `logs/pdf-tools.log`
2. **Add logging:** Enable TRACE level for detailed flow
3. **Review architecture:** See ARCHITECTURE.md
4. **Test in isolation:** Use services directly without UI
5. **Ask questions:** Check documentation and code comments

## Quick Reference

### Create New Tool
```
ui/toolname/ → Create controller → Create FXML → Add to MainScreen
```

### Add PDF Operation
```
service/PdfNewThing.java → Inject to PdfService → Call from controller
```

### Debug Issue
```
Enable logging → Run operation → Check logs/pdf-tools.log → Add breakpoints
```

### Run Application
```
mvn clean javafx:run
```
