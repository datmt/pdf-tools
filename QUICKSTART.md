# Quick Start Guide

## Prerequisites
- Java 21 or higher
- Maven 3.6+

## Building

```bash
mvn clean package
```

## Running

### Development Mode
```bash
mvn clean javafx:run
```

### Building Distribution
```bash
mvn clean package javafx:jlink
```

## Using the PDF Extractor

### 1. Load a PDF
- Click "PDF Extractor" from the main screen
- Click "Load PDF" button
- Select a PDF file

### 2. Select Pages
**Option A: Input Field**
- Enter page numbers in the right panel input field
- Format examples:
  - `1,3,5` - Individual pages
  - `1-10` - Page range
  - `1,3,5-10` - Mixed
- Click "Add"

**Option B: Thumbnail Selection**
- Use "Select All" / "Deselect All" buttons
- (Checkbox selection on thumbnails to be added)

**Option C: Preview Navigation**
- Click "Previous" / "Next" to browse pages
- Center panel shows current page preview

### 3. Review Selection
- Right panel shows all selected pages
- Click "Remove Selected" or "Clear All" to modify

### 4. Export
- Click "Browse" to choose output file location
- Click "Export PDF" to save

## Logging

Logs are written to:
- **Console:** Real-time output
- **File:** `./logs/pdf-tools.log` (rolling logs up to 100MB)

To change log level, edit `src/main/resources/logback.xml`:

```xml
<logger name="com.datmt.pdftools" level="DEBUG" additivity="false">
```

Valid levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`

## Example Session

```
1. Start app: mvn javafx:run
2. Click "PDF Extractor"
3. Load sample.pdf (10 pages)
4. Enter "1-5" → Click "Add"
5. Enter "8,10" → Click "Add"
6. Browse → Select "output.pdf"
7. Click "Export PDF"
8. Result: output.pdf contains pages 1-5 and 8,10
```

## Project Structure for Developers

```
src/main/java/com/datmt/pdftools/
├── service/           ← PDF operations (reusable)
├── model/             ← Data models
└── ui/                ← UI controllers & FXML
    ├── controller/    ← Main screen
    └── extractor/     ← Extractor tool
```

See `ARCHITECTURE.md` for detailed design documentation.

## Common Issues

### "PDF file does not exist"
- Check file path is correct
- Ensure file has read permissions

### "Invalid page index"
- Check page numbers don't exceed document page count
- Remember input is 1-based (pages 1 to N)

### "Out of memory" with large PDFs
- Increase JVM memory: `mvn javafx:run -Dexec.args="-Xmx2G"`
- Note: Page rendering is done on-demand and cached

## Tips

- Use thumbnail view to quickly browse documents
- Page input supports unlimited entries: `1-5,10,15-20,25`
- Logs help debug issues - check `logs/pdf-tools.log`
- Each new tool is isolated, reuses PdfService for common operations
