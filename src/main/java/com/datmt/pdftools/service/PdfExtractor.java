package com.datmt.pdftools.service;

import com.datmt.pdftools.model.PdfBookmark;
import com.datmt.pdftools.model.PdfDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Handles PDF page extraction and export operations.
 */
public class PdfExtractor {
    private static final Logger logger = LoggerFactory.getLogger(PdfExtractor.class);

    /**
     * Extract specified pages from a PDF document and save to a new file.
     *
     * @param sourceDocument The source PDF document
     * @param pageIndices    Set of 0-based page indices to extract
     * @param outputFile     Where to save the extracted PDF
     * @throws IOException If extraction or file writing fails
     */
    public void extractPages(PdfDocument sourceDocument, Set<Integer> pageIndices, File outputFile) throws IOException {
        logger.debug("Extracting pages {} from document", pageIndices);
        logger.trace("Output file: {}", outputFile.getAbsolutePath());

        PDDocument sourcePdf = sourceDocument.getPdfDocument();
        int pageCount = sourceDocument.getPageCount();

        // Validate page indices
        for (int index : pageIndices) {
            if (index < 0 || index >= pageCount) {
                logger.error("Invalid page index: {} (document has {} pages)", index, pageCount);
                throw new IllegalArgumentException("Page index out of range: " + index);
            }
        }

        logger.info("Creating new PDF with {} pages from {} selected pages", pageIndices.size(), pageCount);

        try (PDDocument newDocument = new PDDocument()) {
            // Sort indices to maintain order
            List<Integer> sortedIndices = pageIndices.stream()
                    .sorted()
                    .toList();

            for (Integer pageIndex : sortedIndices) {
                logger.trace("Adding page {} to new document", pageIndex);
                PDPage page = sourcePdf.getPage(pageIndex);
                newDocument.addPage(page);
            }

            newDocument.save(outputFile);
            logger.info("Successfully extracted {} pages to: {}", pageIndices.size(), outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to extract pages to file: {}", outputFile.getAbsolutePath(), e);
            throw e;
        }
    }

    /**
     * Extract a single page from a PDF document.
     *
     * @param sourceDocument The source PDF document
     * @param pageIndex      0-based page index
     * @param outputFile     Where to save the extracted PDF
     * @throws IOException If extraction or file writing fails
     */
    public void extractSinglePage(PdfDocument sourceDocument, int pageIndex, File outputFile) throws IOException {
        logger.debug("Extracting single page: {} from document", pageIndex);
        Set<Integer> pages = new TreeSet<>();
        pages.add(pageIndex);
        extractPages(sourceDocument, pages, outputFile);
    }

    /**
     * Extract a range of pages from a PDF document.
     *
     * @param sourceDocument The source PDF document
     * @param startPage      0-based start page index (inclusive)
     * @param endPage        0-based end page index (inclusive)
     * @param outputFile     Where to save the extracted PDF
     * @throws IOException If extraction or file writing fails
     */
    public void extractPageRange(PdfDocument sourceDocument, int startPage, int endPage, File outputFile) throws IOException {
        logger.debug("Extracting page range: {} to {}", startPage, endPage);

        Set<Integer> pages = new TreeSet<>();
        for (int i = startPage; i <= endPage; i++) {
            pages.add(i);
        }
        extractPages(sourceDocument, pages, outputFile);
    }

    /**
     * Extract pages for each bookmark into separate files.
     *
     * @param sourceDocument The source PDF document
     * @param bookmarks      List of bookmarks to extract
     * @param outputDir      Directory to save the extracted PDFs
     * @throws IOException If extraction or file writing fails
     */
    public void extractByBookmarks(PdfDocument sourceDocument, List<PdfBookmark> bookmarks, File outputDir) throws IOException {
        logger.info("Extracting {} bookmarks to directory: {}", bookmarks.size(), outputDir.getAbsolutePath());

        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
            }
        }

        String baseName = sourceDocument.getSourceFile().getName();
        // Remove .pdf extension
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        int bookmarkIndex = 1;
        for (PdfBookmark bookmark : bookmarks) {
            String sanitizedTitle = bookmark.getSanitizedTitle();
            if (sanitizedTitle.isEmpty()) {
                sanitizedTitle = "Chapter_" + bookmarkIndex;
            }

            // Create filename: baseName_index_title.pdf
            String fileName = String.format("%s_%02d_%s.pdf", baseName, bookmarkIndex, sanitizedTitle);
            File outputFile = new File(outputDir, fileName);

            logger.debug("Extracting bookmark '{}' (pages {}-{}) to {}",
                    bookmark.getTitle(),
                    bookmark.getPageIndex() + 1,
                    bookmark.getEndPageIndex() + 1,
                    outputFile.getName());

            // Extract the page range for this bookmark
            extractPageRange(sourceDocument, bookmark.getPageIndex(), bookmark.getEndPageIndex(), outputFile);

            bookmarkIndex++;
        }

        logger.info("Successfully extracted {} bookmarks", bookmarks.size());
    }
}
