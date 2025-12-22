package com.datmt.pdftools.service;

import com.datmt.pdftools.model.PdfDocument;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Handles rendering PDF pages to JavaFX images for display.
 */
public class PdfRenderService {
    private static final Logger logger = LoggerFactory.getLogger(PdfRenderService.class);
    private static final int DEFAULT_DPI = 150;
    private static final int THUMBNAIL_DPI = 72;

    /**
     * Render a page to a JavaFX Image at standard resolution.
     *
     * @param pdfDocument The PDF document
     * @param pageIndex   0-based page index
     * @return JavaFX Image of the rendered page
     * @throws IOException If rendering fails
     */
    public Image renderPageToImage(PdfDocument pdfDocument, int pageIndex) throws IOException {
        logger.trace("Rendering page {} to image at {}DPI", pageIndex, DEFAULT_DPI);
        return renderPageToImage(pdfDocument, pageIndex, DEFAULT_DPI);
    }

    /**
     * Render a page to a JavaFX Image at thumbnail resolution.
     *
     * @param pdfDocument The PDF document
     * @param pageIndex   0-based page index
     * @return JavaFX Image of the rendered page thumbnail
     * @throws IOException If rendering fails
     */
    public Image renderPageToThumbnail(PdfDocument pdfDocument, int pageIndex) throws IOException {
        logger.trace("Rendering page {} to thumbnail at {}DPI", pageIndex, THUMBNAIL_DPI);
        return renderPageToImage(pdfDocument, pageIndex, THUMBNAIL_DPI);
    }

    /**
     * Render a page to a JavaFX Image at specified DPI.
     *
     * @param pdfDocument The PDF document
     * @param pageIndex   0-based page index
     * @param dpi         Resolution in dots per inch
     * @return JavaFX Image of the rendered page
     * @throws IOException If rendering fails
     */
    private Image renderPageToImage(PdfDocument pdfDocument, int pageIndex, int dpi) throws IOException {
        if (pageIndex < 0 || pageIndex >= pdfDocument.getPageCount()) {
            logger.error("Page index out of range: {} (document has {} pages)", pageIndex, pdfDocument.getPageCount());
            throw new IllegalArgumentException("Page index out of range: " + pageIndex);
        }

        logger.debug("Rendering page {} at {}DPI", pageIndex, dpi);

        try {
            PDFRenderer renderer = new PDFRenderer(pdfDocument.getPdfDocument());
            BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
            logger.trace("Successfully rendered page {} to image", pageIndex);
            return fxImage;
        } catch (IOException e) {
            logger.error("Failed to render page {}: {}", pageIndex, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the dimensions of a page.
     *
     * @param pdfDocument The PDF document
     * @param pageIndex   0-based page index
     * @return Array with [width, height] in points
     */
    public double[] getPageDimensions(PdfDocument pdfDocument, int pageIndex) {
        logger.trace("Getting dimensions for page {}", pageIndex);
        try {
            PDPage page = pdfDocument.getPdfDocument().getPage(pageIndex);
            var mediaBox = page.getMediaBox();
            return new double[]{mediaBox.getWidth(), mediaBox.getHeight()};
        } catch (Exception e) {
            logger.error("Failed to get page dimensions for page {}: {}", pageIndex, e.getMessage());
            return new double[]{595, 842}; // Default A4 dimensions
        }
    }
}
