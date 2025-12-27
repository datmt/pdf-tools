package com.datmt.pdftools.service;

import com.datmt.pdftools.model.PdfBookmark;
import com.datmt.pdftools.model.PdfDocument;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Main service orchestrating all PDF operations.
 * Provides a unified interface for UI controllers to interact with PDF functionality.
 */
public class PdfService {
    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

    private final PdfLoader loader;
    private final PdfExtractor extractor;
    private final PdfRenderService renderService;
    private final PdfBookmarkService bookmarkService;

    private PdfDocument currentDocument;
    private List<PdfBookmark> currentBookmarks;

    public PdfService() {
        logger.trace("Initializing PdfService");
        this.loader = new PdfLoader();
        this.extractor = new PdfExtractor();
        this.renderService = new PdfRenderService();
        this.bookmarkService = new PdfBookmarkService();
        this.currentDocument = null;
        this.currentBookmarks = new ArrayList<>();
    }

    /**
     * Load a PDF file.
     *
     * @param file The PDF file to load
     * @return The loaded PdfDocument
     * @throws IOException If loading fails
     */
    public PdfDocument loadPdf(File file) throws IOException {
        logger.info("PdfService.loadPdf() called for: {}", file.getName());
        currentDocument = loader.loadPdf(file);

        // Extract bookmarks from the loaded document
        currentBookmarks = bookmarkService.extractBookmarks(currentDocument.getPdfDocument());
        logger.info("Loaded document with {} bookmarks", currentBookmarks.size());

        return currentDocument;
    }

    /**
     * Get the currently loaded document.
     *
     * @return The PdfDocument or null if no document is loaded
     */
    public PdfDocument getCurrentDocument() {
        return currentDocument;
    }

    /**
     * Check if a document is currently loaded.
     *
     * @return true if document is loaded
     */
    public boolean isDocumentLoaded() {
        return currentDocument != null;
    }

    /**
     * Render a page to a JavaFX Image.
     *
     * @param pageIndex 0-based page index
     * @return JavaFX Image of the page
     * @throws IOException If rendering fails
     */
    public Image renderPage(int pageIndex) throws IOException {
        if (!isDocumentLoaded()) {
            logger.error("Cannot render page: no document loaded");
            throw new IllegalStateException("No PDF document loaded");
        }
        logger.debug("Rendering page {} from current document", pageIndex);
        return renderService.renderPageToImage(currentDocument, pageIndex);
    }

    /**
     * Render a page thumbnail.
     *
     * @param pageIndex 0-based page index
     * @return JavaFX Image thumbnail of the page
     * @throws IOException If rendering fails
     */
    public Image renderPageThumbnail(int pageIndex) throws IOException {
        if (!isDocumentLoaded()) {
            logger.error("Cannot render thumbnail: no document loaded");
            throw new IllegalStateException("No PDF document loaded");
        }
        logger.debug("Rendering thumbnail for page {}", pageIndex);
        return renderService.renderPageToThumbnail(currentDocument, pageIndex);
    }

    /**
     * Get page dimensions.
     *
     * @param pageIndex 0-based page index
     * @return [width, height] in points
     */
    public double[] getPageDimensions(int pageIndex) {
        if (!isDocumentLoaded()) {
            logger.warn("Cannot get dimensions: no document loaded");
            return new double[]{595, 842};
        }
        return renderService.getPageDimensions(currentDocument, pageIndex);
    }

    /**
     * Extract pages to a new PDF file.
     *
     * @param pageIndices Set of 0-based page indices to extract
     * @param outputFile  Where to save the extracted PDF
     * @throws IOException If extraction fails
     */
    public void extractPages(Set<Integer> pageIndices, File outputFile) throws IOException {
        extractPages(pageIndices, new java.util.HashMap<>(), outputFile);
    }

    /**
     * Extract pages to a new PDF file with optional rotations.
     *
     * @param pageIndices Set of 0-based page indices to extract
     * @param rotations   Map of page index to rotation in degrees (0, 90, 180, 270)
     * @param outputFile  Where to save the extracted PDF
     * @throws IOException If extraction fails
     */
    public void extractPages(Set<Integer> pageIndices, java.util.Map<Integer, Integer> rotations, File outputFile) throws IOException {
        if (!isDocumentLoaded()) {
            logger.error("Cannot extract pages: no document loaded");
            throw new IllegalStateException("No PDF document loaded");
        }
        logger.info("Extracting {} pages to: {} with {} rotations", pageIndices.size(), outputFile.getAbsolutePath(), rotations.size());
        extractor.extractPages(currentDocument, pageIndices, rotations, outputFile);
    }

    /**
     * Extract a single page to a new PDF file.
     *
     * @param pageIndex  0-based page index
     * @param outputFile Where to save the extracted PDF
     * @throws IOException If extraction fails
     */
    public void extractSinglePage(int pageIndex, File outputFile) throws IOException {
        if (!isDocumentLoaded()) {
            logger.error("Cannot extract page: no document loaded");
            throw new IllegalStateException("No PDF document loaded");
        }
        logger.info("Extracting single page {} to: {}", pageIndex, outputFile.getAbsolutePath());
        extractor.extractSinglePage(currentDocument, pageIndex, outputFile);
    }

    /**
     * Extract a page range to a new PDF file.
     *
     * @param startPage  0-based start page (inclusive)
     * @param endPage    0-based end page (inclusive)
     * @param outputFile Where to save the extracted PDF
     * @throws IOException If extraction fails
     */
    public void extractPageRange(int startPage, int endPage, File outputFile) throws IOException {
        if (!isDocumentLoaded()) {
            logger.error("Cannot extract range: no document loaded");
            throw new IllegalStateException("No PDF document loaded");
        }
        logger.info("Extracting page range {} to {} to: {}", startPage, endPage, outputFile.getAbsolutePath());
        extractor.extractPageRange(currentDocument, startPage, endPage, outputFile);
    }

    /**
     * Check if the current document has bookmarks.
     *
     * @return true if document has bookmarks
     */
    public boolean hasBookmarks() {
        return !currentBookmarks.isEmpty();
    }

    /**
     * Get bookmarks from the current document.
     *
     * @return List of top-level bookmarks (may have children)
     */
    public List<PdfBookmark> getBookmarks() {
        return currentBookmarks;
    }

    /**
     * Get the bookmark service for advanced operations.
     *
     * @return The PdfBookmarkService instance
     */
    public PdfBookmarkService getBookmarkService() {
        return bookmarkService;
    }

    /**
     * Extract pages for selected bookmarks into separate files.
     *
     * @param bookmarks List of selected bookmarks to extract
     * @param outputDir Directory to save the extracted PDFs
     * @throws IOException If extraction fails
     */
    public void extractByBookmarks(List<PdfBookmark> bookmarks, File outputDir) throws IOException {
        if (!isDocumentLoaded()) {
            logger.error("Cannot extract by bookmarks: no document loaded");
            throw new IllegalStateException("No PDF document loaded");
        }
        logger.info("Extracting {} bookmarks to: {}", bookmarks.size(), outputDir.getAbsolutePath());
        extractor.extractByBookmarks(currentDocument, bookmarks, outputDir);
    }

    /**
     * Close the current document and clean up resources.
     */
    public void closeDocument() {
        if (currentDocument != null) {
            logger.info("Closing document: {}", currentDocument.getSourceFile().getName());
            try {
                currentDocument.getPdfDocument().close();
                currentDocument.clearCache();
                currentDocument = null;
                currentBookmarks = new ArrayList<>();
            } catch (Exception e) {
                logger.error("Error closing document: {}", e.getMessage(), e);
            }
        }
    }
}
