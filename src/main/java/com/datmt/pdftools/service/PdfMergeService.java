package com.datmt.pdftools.service;

import com.datmt.pdftools.model.JoinerFile;
import com.datmt.pdftools.model.JoinerSection;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.Loader;
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
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Service for merging PDFs and images into a single PDF document.
 */
public class PdfMergeService {
    private static final Logger logger = LoggerFactory.getLogger(PdfMergeService.class);
    private static final int THUMBNAIL_DPI = 72;
    private static final int PREVIEW_DPI = 150;

    /**
     * Load a PDF file into a JoinerFile.
     */
    public JoinerFile loadPdfFile(File file) throws IOException {
        logger.info("Loading PDF file: {}", file.getName());
        PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(file));
        return new JoinerFile(file, document);
    }

    /**
     * Load an image file into a JoinerFile.
     */
    public JoinerFile loadImageFile(File file) throws IOException {
        logger.info("Loading image file: {}", file.getName());
        Image image = new Image(file.toURI().toString());
        if (image.isError()) {
            throw new IOException("Failed to load image: " + file.getName());
        }
        return new JoinerFile(file, image);
    }

    /**
     * Render a thumbnail for a PDF page.
     */
    public Image renderPdfThumbnail(JoinerFile joinerFile, int pageIndex) throws IOException {
        if (!joinerFile.isPdf()) {
            throw new IllegalArgumentException("File is not a PDF");
        }
        logger.trace("Rendering PDF thumbnail for page {}", pageIndex);
        PDFRenderer renderer = new PDFRenderer(joinerFile.getPdfDocument());
        BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, THUMBNAIL_DPI, ImageType.RGB);
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    /**
     * Render a preview image for a PDF page.
     */
    public Image renderPdfPreview(JoinerFile joinerFile, int pageIndex) throws IOException {
        if (!joinerFile.isPdf()) {
            throw new IllegalArgumentException("File is not a PDF");
        }
        logger.trace("Rendering PDF preview for page {}", pageIndex);
        PDFRenderer renderer = new PDFRenderer(joinerFile.getPdfDocument());
        BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, PREVIEW_DPI, ImageType.RGB);
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    /**
     * Create a thumbnail from an image file.
     */
    public Image createImageThumbnail(JoinerFile joinerFile, int maxSize) {
        if (!joinerFile.isImage()) {
            throw new IllegalArgumentException("File is not an image");
        }
        Image original = joinerFile.getImage();
        double scale = Math.min(maxSize / original.getWidth(), maxSize / original.getHeight());
        if (scale >= 1.0) {
            return original;
        }
        // Return the original, the UI will scale it
        return original;
    }

    /**
     * Merge multiple sections into a single PDF file.
     *
     * @param sections   List of sections to merge
     * @param outputFile Output PDF file
     * @param callback   Progress callback (optional)
     */
    public void mergeSections(List<JoinerSection> sections, File outputFile, ProgressCallback callback) throws IOException {
        logger.info("Merging {} sections to: {}", sections.size(), outputFile.getAbsolutePath());

        int totalPages = sections.stream().mapToInt(JoinerSection::getPageCount).sum();
        int processedPages = 0;

        try (PDDocument outputDoc = new PDDocument()) {
            for (JoinerSection section : sections) {
                JoinerFile sourceFile = section.getSourceFile();
                logger.debug("Processing section from: {} (pages {}-{})",
                        sourceFile.getFileName(), section.getStartPage() + 1, section.getEndPage() + 1);

                if (sourceFile.isPdf()) {
                    // Import PDF pages (importPage clones the page with all resources including fonts)
                    PDDocument sourcePdf = sourceFile.getPdfDocument();
                    int sectionRotation = section.getRotation().getDegrees();

                    for (int i = section.getStartPage(); i <= section.getEndPage(); i++) {
                        PDPage page = sourcePdf.getPage(i);
                        PDPage importedPage = outputDoc.importPage(page);

                        // Apply rotation if specified for this section
                        if (sectionRotation != 0) {
                            int currentRotation = importedPage.getRotation();
                            int newRotation = (currentRotation + sectionRotation) % 360;
                            importedPage.setRotation(newRotation);
                            logger.debug("Applied rotation {} to page {} (total: {})", sectionRotation, i, newRotation);
                        }

                        processedPages++;
                        if (callback != null) {
                            callback.onProgress(processedPages, totalPages);
                        }
                    }
                } else if (sourceFile.isImage()) {
                    // Convert image to PDF page
                    addImageAsPage(outputDoc, sourceFile, section);
                    processedPages++;
                    if (callback != null) {
                        callback.onProgress(processedPages, totalPages);
                    }
                }
            }

            outputDoc.save(outputFile);
            logger.info("Successfully merged {} pages into: {}", totalPages, outputFile.getAbsolutePath());
        }
    }

    /**
     * Add an image as a PDF page.
     */
    private void addImageAsPage(PDDocument document, JoinerFile imageFile, JoinerSection section) throws IOException {
        logger.debug("Adding image as page: {}", imageFile.getFileName());

        // Load the original image
        BufferedImage originalImage = ImageIO.read(imageFile.getSourceFile());
        if (originalImage == null) {
            throw new IOException("Failed to read image: " + imageFile.getFileName());
        }

        // Apply rotation if needed
        BufferedImage processedImage = applyRotation(originalImage, section.getRotation());

        // Determine page size
        PDRectangle pageSize = getPageSize(section, processedImage);
        PDPage page = new PDPage(pageSize);
        document.addPage(page);

        // Calculate image placement
        float[] placement = calculateImagePlacement(processedImage, pageSize, section.getFitOption());
        float x = placement[0];
        float y = placement[1];
        float width = placement[2];
        float height = placement[3];

        // Draw the image on the page
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, processedImage);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(pdImage, x, y, width, height);
        }
    }

    /**
     * Apply rotation to an image.
     */
    private BufferedImage applyRotation(BufferedImage image, JoinerSection.Rotation rotation) {
        if (rotation == JoinerSection.Rotation.NONE) {
            return image;
        }

        double radians = Math.toRadians(rotation.getDegrees());
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int w = image.getWidth();
        int h = image.getHeight();
        int newW = (int) Math.floor(w * cos + h * sin);
        int newH = (int) Math.floor(h * cos + w * sin);

        AffineTransform transform = new AffineTransform();
        transform.translate((newW - w) / 2.0, (newH - h) / 2.0);
        transform.rotate(radians, w / 2.0, h / 2.0);

        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage rotated = new BufferedImage(newW, newH, image.getType());
        op.filter(image, rotated);
        return rotated;
    }

    /**
     * Determine the page size based on section settings.
     */
    private PDRectangle getPageSize(JoinerSection section, BufferedImage image) {
        JoinerSection.PageSize sizeOption = section.getPageSize();

        if (sizeOption == JoinerSection.PageSize.ORIGINAL) {
            // Use image dimensions at 72 DPI
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
                // Scale to fill the entire page (may crop)
                float fillScale = Math.max(pageWidth / imgWidth, pageHeight / imgHeight);
                width = imgWidth * fillScale;
                height = imgHeight * fillScale;
                x = (pageWidth - width) / 2;
                y = (pageHeight - height) / 2;
                break;

            case ORIGINAL_SIZE:
                // Use original size, centered
                width = imgWidth;
                height = imgHeight;
                x = (pageWidth - width) / 2;
                y = (pageHeight - height) / 2;
                break;

            case FIT_TO_PAGE:
            default:
                // Scale to fit within page (maintains aspect ratio)
                float fitScale = Math.min(pageWidth / imgWidth, pageHeight / imgHeight);
                width = imgWidth * fitScale;
                height = imgHeight * fitScale;
                x = (pageWidth - width) / 2;
                y = (pageHeight - height) / 2;
                break;
        }

        return new float[]{x, y, width, height};
    }

    /**
     * Progress callback interface.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }
}
