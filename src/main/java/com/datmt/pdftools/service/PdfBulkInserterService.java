package com.datmt.pdftools.service;

import com.datmt.pdftools.model.JoinerSection;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for bulk inserting a PDF or image into multiple target PDFs.
 */
public class PdfBulkInserterService {
    private static final Logger logger = LoggerFactory.getLogger(PdfBulkInserterService.class);
    private static final int THUMBNAIL_DPI = 72;

    /**
     * Insertion mode determining where pages are inserted.
     */
    public enum InsertionMode {
        AT_END,           // Insert after the last page
        AFTER_PAGE,       // Insert after a specific page number
        EVERY_N_PAGES     // Insert every N pages
    }

    /**
     * Options for the insertion operation.
     */
    public static class InsertionOptions {
        private InsertionMode mode = InsertionMode.AT_END;
        private int pageNumber = 1;       // For AFTER_PAGE mode (1-based)
        private int interval = 10;        // For EVERY_N_PAGES mode
        private boolean insertAtEndIfShorter = true;
        private String outputSuffix = "_modified";

        // For image sources only
        private JoinerSection.PageSize pageSize = JoinerSection.PageSize.A4;
        private JoinerSection.FitOption fitOption = JoinerSection.FitOption.FIT_TO_PAGE;

        public InsertionMode getMode() {
            return mode;
        }

        public void setMode(InsertionMode mode) {
            this.mode = mode;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public int getInterval() {
            return interval;
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }

        public boolean isInsertAtEndIfShorter() {
            return insertAtEndIfShorter;
        }

        public void setInsertAtEndIfShorter(boolean insertAtEndIfShorter) {
            this.insertAtEndIfShorter = insertAtEndIfShorter;
        }

        public String getOutputSuffix() {
            return outputSuffix;
        }

        public void setOutputSuffix(String outputSuffix) {
            this.outputSuffix = outputSuffix;
        }

        public JoinerSection.PageSize getPageSize() {
            return pageSize;
        }

        public void setPageSize(JoinerSection.PageSize pageSize) {
            this.pageSize = pageSize;
        }

        public JoinerSection.FitOption getFitOption() {
            return fitOption;
        }

        public void setFitOption(JoinerSection.FitOption fitOption) {
            this.fitOption = fitOption;
        }
    }

    /**
     * Result of an insertion operation.
     */
    public static class InsertResult {
        private final File inputFile;
        private File outputFile;
        private int originalPageCount;
        private int newPageCount;
        private int insertionsPerformed;
        private boolean success;
        private String errorMessage;

        public InsertResult(File inputFile) {
            this.inputFile = inputFile;
        }

        public File getInputFile() {
            return inputFile;
        }

        public File getOutputFile() {
            return outputFile;
        }

        public void setOutputFile(File outputFile) {
            this.outputFile = outputFile;
        }

        public int getOriginalPageCount() {
            return originalPageCount;
        }

        public void setOriginalPageCount(int originalPageCount) {
            this.originalPageCount = originalPageCount;
        }

        public int getNewPageCount() {
            return newPageCount;
        }

        public void setNewPageCount(int newPageCount) {
            this.newPageCount = newPageCount;
        }

        public int getInsertionsPerformed() {
            return insertionsPerformed;
        }

        public void setInsertionsPerformed(int insertionsPerformed) {
            this.insertionsPerformed = insertionsPerformed;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Load a PDF file as source document.
     */
    public PDDocument loadSourcePdf(File file) throws IOException {
        logger.info("Loading source PDF: {}", file.getName());
        return Loader.loadPDF(new RandomAccessReadBufferedFile(file));
    }

    /**
     * Create a single-page PDF document from an image file.
     */
    public PDDocument createPdfFromImage(File imageFile, InsertionOptions options) throws IOException {
        logger.info("Creating PDF from image: {}", imageFile.getName());

        BufferedImage bufferedImage = ImageIO.read(imageFile);
        if (bufferedImage == null) {
            throw new IOException("Failed to read image: " + imageFile.getName());
        }

        PDDocument document = new PDDocument();

        // Determine page size
        PDRectangle pageSize = getPageSize(options.getPageSize(), bufferedImage);
        PDPage page = new PDPage(pageSize);
        document.addPage(page);

        // Calculate image placement
        float[] placement = calculateImagePlacement(bufferedImage, pageSize, options.getFitOption());
        float x = placement[0];
        float y = placement[1];
        float width = placement[2];
        float height = placement[3];

        // Draw the image on the page
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, bufferedImage);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(pdImage, x, y, width, height);
        }

        return document;
    }

    /**
     * Get the page count of a PDF file without fully loading it.
     */
    public int getPageCount(File pdfFile) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(pdfFile))) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Render a thumbnail for preview.
     */
    public Image renderThumbnail(File file, boolean isPdf, int pageIndex) throws IOException {
        if (isPdf) {
            try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(file))) {
                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, THUMBNAIL_DPI, ImageType.RGB);
                return SwingFXUtils.toFXImage(bufferedImage, null);
            }
        } else {
            // Image file
            return new Image(file.toURI().toString());
        }
    }

    /**
     * Render a thumbnail from an already-loaded document.
     */
    public Image renderThumbnailFromDocument(PDDocument doc, int pageIndex) throws IOException {
        PDFRenderer renderer = new PDFRenderer(doc);
        BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, THUMBNAIL_DPI, ImageType.RGB);
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    /**
     * Insert source pages into a target PDF.
     *
     * @param targetFile Target PDF file
     * @param sourceDoc  Source document (PDF or converted image)
     * @param options    Insertion options
     * @return InsertResult with operation details
     */
    public InsertResult insertIntoTarget(File targetFile, PDDocument sourceDoc, InsertionOptions options) {
        InsertResult result = new InsertResult(targetFile);

        try {
            // Load target document
            try (PDDocument targetDoc = Loader.loadPDF(new RandomAccessReadBufferedFile(targetFile))) {
                int originalPageCount = targetDoc.getNumberOfPages();
                result.setOriginalPageCount(originalPageCount);

                // Calculate insertion positions
                List<Integer> positions = calculateInsertionPositions(originalPageCount, options);

                if (positions.isEmpty()) {
                    result.setSuccess(true);
                    result.setInsertionsPerformed(0);
                    result.setNewPageCount(originalPageCount);
                    result.setOutputFile(targetFile);
                    return result;
                }

                // Create new document with inserted pages
                try (PDDocument newDoc = new PDDocument()) {
                    int sourcePageCount = sourceDoc.getNumberOfPages();
                    int insertionsPerformed = 0;
                    int positionIndex = 0;
                    int currentPosition = positions.get(positionIndex);

                    // Copy pages, inserting source pages at specified positions
                    for (int i = 0; i < originalPageCount; i++) {
                        // Import the target page
                        PDPage targetPage = targetDoc.getPage(i);
                        newDoc.importPage(targetPage);

                        // Check if we need to insert after this page (1-based position)
                        if (positionIndex < positions.size() && (i + 1) == currentPosition) {
                            // Insert all source pages
                            for (int j = 0; j < sourcePageCount; j++) {
                                PDPage sourcePage = sourceDoc.getPage(j);
                                newDoc.importPage(sourcePage);
                            }
                            insertionsPerformed++;

                            // Move to next position
                            positionIndex++;
                            if (positionIndex < positions.size()) {
                                currentPosition = positions.get(positionIndex);
                            }
                        }
                    }

                    // Handle AT_END mode or remaining positions at end
                    while (positionIndex < positions.size()) {
                        for (int j = 0; j < sourcePageCount; j++) {
                            PDPage sourcePage = sourceDoc.getPage(j);
                            newDoc.importPage(sourcePage);
                        }
                        insertionsPerformed++;
                        positionIndex++;
                    }

                    // Generate output file path
                    File outputFile = generateOutputFile(targetFile, options.getOutputSuffix());
                    result.setOutputFile(outputFile);

                    // Save the new document
                    newDoc.save(outputFile);

                    result.setSuccess(true);
                    result.setInsertionsPerformed(insertionsPerformed);
                    result.setNewPageCount(newDoc.getNumberOfPages());

                    logger.info("Inserted {} time(s) into {}: {} -> {} pages",
                            insertionsPerformed, targetFile.getName(),
                            originalPageCount, result.getNewPageCount());
                }
            }
        } catch (IOException e) {
            logger.error("Error inserting into {}: {}", targetFile.getName(), e.getMessage());
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Calculate the positions (1-based page numbers) after which to insert.
     */
    private List<Integer> calculateInsertionPositions(int targetPageCount, InsertionOptions options) {
        List<Integer> positions = new ArrayList<>();

        switch (options.getMode()) {
            case AT_END:
                positions.add(targetPageCount);
                break;

            case AFTER_PAGE:
                int afterPage = options.getPageNumber();
                if (afterPage <= targetPageCount) {
                    positions.add(afterPage);
                } else if (options.isInsertAtEndIfShorter()) {
                    positions.add(targetPageCount);
                }
                // If PDF is shorter and insertAtEndIfShorter is false, positions stays empty
                break;

            case EVERY_N_PAGES:
                int interval = options.getInterval();
                for (int i = interval; i <= targetPageCount; i += interval) {
                    positions.add(i);
                }
                // Optionally add at end if there's a remainder
                if (targetPageCount % interval != 0 && options.isInsertAtEndIfShorter()) {
                    positions.add(targetPageCount);
                }
                break;
        }

        // Sort positions (they should already be sorted, but ensure)
        Collections.sort(positions);

        logger.debug("Calculated insertion positions for {} pages with mode {}: {}",
                targetPageCount, options.getMode(), positions);

        return positions;
    }

    /**
     * Generate output file path with suffix.
     */
    private File generateOutputFile(File inputFile, String suffix) {
        String name = inputFile.getName();
        int dotIndex = name.lastIndexOf('.');
        String baseName = dotIndex > 0 ? name.substring(0, dotIndex) : name;
        String extension = dotIndex > 0 ? name.substring(dotIndex) : ".pdf";

        return new File(inputFile.getParentFile(), baseName + suffix + extension);
    }

    /**
     * Determine the page size based on options.
     */
    private PDRectangle getPageSize(JoinerSection.PageSize sizeOption, BufferedImage image) {
        if (sizeOption == JoinerSection.PageSize.ORIGINAL) {
            return new PDRectangle(image.getWidth(), image.getHeight());
        }
        return new PDRectangle(sizeOption.getWidth(), sizeOption.getHeight());
    }

    /**
     * Calculate image placement based on fit option.
     *
     * @return float[] {x, y, width, height}
     */
    private float[] calculateImagePlacement(BufferedImage image, PDRectangle pageSize, JoinerSection.FitOption fitOption) {
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();
        float imgWidth = image.getWidth();
        float imgHeight = image.getHeight();

        float x, y, width, height;

        switch (fitOption) {
            case FILL_PAGE:
                float fillScale = Math.max(pageWidth / imgWidth, pageHeight / imgHeight);
                width = imgWidth * fillScale;
                height = imgHeight * fillScale;
                x = (pageWidth - width) / 2;
                y = (pageHeight - height) / 2;
                break;

            case ORIGINAL_SIZE:
                width = imgWidth;
                height = imgHeight;
                x = (pageWidth - width) / 2;
                y = (pageHeight - height) / 2;
                break;

            case FIT_TO_PAGE:
            default:
                float fitScale = Math.min(pageWidth / imgWidth, pageHeight / imgHeight);
                width = imgWidth * fitScale;
                height = imgHeight * fitScale;
                x = (pageWidth - width) / 2;
                y = (pageHeight - height) / 2;
                break;
        }

        return new float[]{x, y, width, height};
    }
}
