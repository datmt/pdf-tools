# Quick Reference Card

## Build & Run

```bash
# Development
mvn clean javafx:run

# Build only
mvn clean compile

# Full build
mvn clean package
```

## File Locations

| What             | Where                                                   |
|------------------|---------------------------------------------------------|
| Main entry point | `src/main/java/com/datmt/pdftools/MainApplication.java` |
| PDF operations   | `src/main/java/com/datmt/pdftools/service/`             |
| UI controllers   | `src/main/java/com/datmt/pdftools/ui/`                  |
| FXML layouts     | `src/main/resources/com/datmt/pdftools/ui/`             |
| Logs             | `./logs/pdf-tools.log`                                  |
| Config           | `src/main/resources/logback.xml`                        |

## Adding a Tool

```
1. Create: src/main/java/com/datmt/pdftools/ui/TOOLNAME/
2. Create: TOOLNAME.fxml in src/main/resources/...
3. Create: TOOLNAMEController.java
4. Add button to MainScreenController.java
5. Update module-info.java (opens/exports)
6. Build: mvn clean compile
```

## Adding PDF Operation

```java
// Option 1: Small operation
public void myOp() throws Exception {
    logger.info("Starting myOp");
    // code
}

// Option 2: Related operations (new service)
public class PdfMyThing {
    private static final Logger logger = ...;

    public void operation() throws Exception { ...}
}

// Then add to PdfService:
private PdfMyThing myThing = new PdfMyThing();

public void operation() throws IOException {
    if (!isDocumentLoaded()) throw new IllegalStateException(...);
    myThing.operation();
}
```

## Logging

```java
logger.trace("Detailed: {}",value);           // Flow details
logger.

debug("Operation step: {}",state);     // Debug info
logger.

info("Success: operation completed");   // Important event
logger.

warn("Warning: unusual state");         // Recoverable issue
logger.

error("Failed: {}",message, exception);// Failure
```

## UI Patterns

### Load File Async

```java
Task<PdfDocument> task = new Task<>() {
    @Override
    protected PdfDocument call() throws Exception {
        return pdfService.loadPdf(file);
    }
};
task.

setOnSucceeded(e ->

updateUI(task.getValue()));
        task.

setOnFailed(e ->

showError("Error",task.getException().

getMessage()));
        new

Thread(task).

start();
```

### Show Alert

```java
Alert alert = new Alert(Alert.AlertType.INFORMATION);
alert.

setTitle("Title");
alert.

setHeaderText(null);
alert.

setContentText("Message");
alert.

showAndWait();
```

### Input with Validation

```java
String input = inputField.getText().trim();
if(input.

isEmpty()){
        logger.

warn("Input validation failed: empty");

showWarning("Error","Please enter something");
    return;
            }
// Process input
```

## Service Usage

```java
// Inject in controller
private PdfService pdfService = new PdfService();

// Core operations
pdfService.

loadPdf(file)                // PdfDocument
pdfService.

renderPage(idx)              // Image
pdfService.

renderPageThumbnail(idx)     // Image
pdfService.

getPageDimensions(idx)       // double[]
pdfService.

extractPages(pages, file)    // void
pdfService.

closeDocument()              // void
pdfService.

isDocumentLoaded()           // boolean
```

## Key Classes

| Class                | Purpose                |
|----------------------|------------------------|
| `PdfService`         | Main facade - use this |
| `PdfDocument`        | Loaded PDF wrapper     |
| `PdfLoader`          | File loading           |
| `PdfExtractor`       | Page extraction        |
| `PdfRenderService`   | Image rendering        |
| `PageThumbnailPanel` | Reusable component     |

## File Naming

| Type              | Style      | Example                      |
|-------------------|------------|------------------------------|
| Java classes      | PascalCase | `PdfService.java`            |
| FXML files        | kebab-case | `pdf-extractor.fxml`         |
| Packages          | lowercase  | `com.datmt.pdftools.service` |
| Variables/methods | camelCase  | `pdfService`, `loadPdf()`    |

## Module System

**In module-info.java:**

```java
requires javafx.controls;           // Require module
opens com.
datmt.pdftools.ui to
javafx.fxml;  // Open for FXML
exports com.datmt.pdftools.service; // Export package
```

## Common Errors & Solutions

| Error                | Solution                                           |
|----------------------|----------------------------------------------------|
| "FXML not found"     | Check path in FXMLLoader                           |
| "@FXML not working"  | Add to module-info: `opens package to javafx.fxml` |
| "No document loaded" | Call `pdfService.loadPdf(file)` first              |
| "UI freezes"         | Use `Task` for long operations                     |
| "Page out of range"  | Check: `pageIndex >= 0 && pageIndex < pageCount`   |

## Documentation Files

| File                       | Read If                   |
|----------------------------|---------------------------|
| `ARCHITECTURE.md`          | Want to understand design |
| `QUICKSTART.md`            | Want to use the app       |
| `DEVELOPER_GUIDE.md`       | Want to add code          |
| `IMPLEMENTATION_STATUS.md` | Want to see what's done   |
| `SUMMARY.md`               | Want overview of project  |

## Environment

```
Java:   25+
Maven:  3.6+
JavaFX: 21.0.6
PDFBox: 3.0.1
```

## Shortcuts

| Command                         | What It Does     |
|---------------------------------|------------------|
| `mvn javafx:run`                | Build and run    |
| `mvn clean compile`             | Check for errors |
| `mvn clean package`             | Build JAR        |
| `tail -f logs/pdf-tools.log`    | Watch logs       |
| `grep ERROR logs/pdf-tools.log` | Find errors      |

## Three-Panel Layout (Extractor)

```
┌──────────────┬─────────────────────┬──────────────┐
│   PAGES      │      PREVIEW        │   EXTRACT    │
│ (Thumbnails) │  (Current Page)     │ (Selection)  │
│              │                     │              │
│   [1]        │  ┌────────────────┐ │ Pages: 1,3,5 │
│   [2]        │  │  Page Image    │ │              │
│   [3]        │  │                │ │ Input: 1-5   │
│   ...        │  └────────────────┘ │ [Add]        │
│              │  [< Prev] [Next >]  │ [Remove]     │
│              │                     │ [Clear]      │
│              │                     │              │
│              │                     │ [Export]     │
└──────────────┴─────────────────────┴──────────────┘
```

## Next Steps

1. Run: `mvn clean javafx:run`
2. Test: Load PDF → Select pages → Export
3. Check: `logs/pdf-tools.log` for verbose output
4. When ready: Implement features from IMPLEMENTATION_STATUS.md

## Resources

- Code: `/src/main/java/com/datmt/pdftools/`
- Docs: ARCHITECTURE.md, DEVELOPER_GUIDE.md
- Logs: `./logs/pdf-tools.log`
- Config: `src/main/resources/logback.xml`
