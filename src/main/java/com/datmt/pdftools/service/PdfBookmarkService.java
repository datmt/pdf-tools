package com.datmt.pdftools.service;

import com.datmt.pdftools.model.PdfBookmark;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting and processing PDF bookmarks (outline/table of contents).
 */
public class PdfBookmarkService {
    private static final Logger logger = LoggerFactory.getLogger(PdfBookmarkService.class);

    /**
     * Check if the document has bookmarks.
     */
    public boolean hasBookmarks(PDDocument document) {
        if (document == null) {
            return false;
        }
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        return outline != null && outline.getFirstChild() != null;
    }

    /**
     * Extract all bookmarks from the document as a tree structure.
     */
    public List<PdfBookmark> extractBookmarks(PDDocument document) {
        List<PdfBookmark> bookmarks = new ArrayList<>();

        if (document == null) {
            return bookmarks;
        }

        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null) {
            logger.debug("Document has no outline/bookmarks");
            return bookmarks;
        }

        int totalPages = document.getNumberOfPages();
        logger.info("Extracting bookmarks from document with {} pages", totalPages);

        // Extract bookmark tree
        extractBookmarksRecursive(outline, document, bookmarks, 0);

        // Calculate page ranges for all bookmarks
        calculatePageRanges(bookmarks, totalPages);

        logger.info("Extracted {} top-level bookmarks", bookmarks.size());
        return bookmarks;
    }

    /**
     * Recursively extract bookmarks from the outline tree.
     */
    private void extractBookmarksRecursive(PDOutlineNode node, PDDocument document,
                                           List<PdfBookmark> bookmarks, int level) {
        PDOutlineItem item = node.getFirstChild();
        while (item != null) {
            String title = item.getTitle();
            int pageIndex = getPageIndex(item, document);

            if (title != null && !title.trim().isEmpty()) {
                PdfBookmark bookmark = new PdfBookmark(title.trim(), pageIndex, level);
                bookmarks.add(bookmark);

                logger.trace("Found bookmark: '{}' at page {} (level {})",
                            title, pageIndex + 1, level);

                // Recursively process children
                if (item.getFirstChild() != null) {
                    extractBookmarksRecursive(item, document, bookmark.getChildren(), level + 1);
                }
            }

            item = item.getNextSibling();
        }
    }

    /**
     * Get the 0-based page index for a bookmark item.
     */
    private int getPageIndex(PDOutlineItem item, PDDocument document) {
        try {
            PDPage page = item.findDestinationPage(document);
            if (page != null) {
                int index = document.getPages().indexOf(page);
                return Math.max(0, index);
            }
        } catch (IOException e) {
            logger.warn("Could not get destination page for bookmark: {}", item.getTitle(), e);
        }
        return 0;
    }

    /**
     * Calculate end page indices for all bookmarks based on the next sibling's start page.
     */
    private void calculatePageRanges(List<PdfBookmark> bookmarks, int totalPages) {
        calculatePageRangesRecursive(bookmarks, totalPages);
    }

    private void calculatePageRangesRecursive(List<PdfBookmark> bookmarks, int totalPages) {
        for (int i = 0; i < bookmarks.size(); i++) {
            PdfBookmark current = bookmarks.get(i);

            // End page is either:
            // 1. Next sibling's start page - 1
            // 2. Last page of document (for last bookmark at this level)
            if (i + 1 < bookmarks.size()) {
                PdfBookmark next = bookmarks.get(i + 1);
                current.setEndPageIndex(Math.max(current.getPageIndex(), next.getPageIndex() - 1));
            } else {
                current.setEndPageIndex(totalPages - 1);
            }

            // Recursively calculate for children
            if (current.hasChildren()) {
                calculatePageRangesRecursive(current.getChildren(), current.getEndPageIndex() + 1);
            }
        }
    }

    /**
     * Flatten the bookmark tree to a list (top-level bookmarks only for splitting).
     */
    public List<PdfBookmark> flattenTopLevel(List<PdfBookmark> bookmarks) {
        return new ArrayList<>(bookmarks);
    }

    /**
     * Flatten all bookmarks including nested ones.
     */
    public List<PdfBookmark> flattenAll(List<PdfBookmark> bookmarks) {
        List<PdfBookmark> flattened = new ArrayList<>();
        flattenRecursive(bookmarks, flattened);
        return flattened;
    }

    private void flattenRecursive(List<PdfBookmark> bookmarks, List<PdfBookmark> flattened) {
        for (PdfBookmark bookmark : bookmarks) {
            flattened.add(bookmark);
            if (bookmark.hasChildren()) {
                flattenRecursive(bookmark.getChildren(), flattened);
            }
        }
    }

    /**
     * Get all selected bookmarks from the tree.
     */
    public List<PdfBookmark> getSelectedBookmarks(List<PdfBookmark> bookmarks) {
        List<PdfBookmark> selected = new ArrayList<>();
        collectSelectedRecursive(bookmarks, selected);
        return selected;
    }

    private void collectSelectedRecursive(List<PdfBookmark> bookmarks, List<PdfBookmark> selected) {
        for (PdfBookmark bookmark : bookmarks) {
            if (bookmark.isSelected()) {
                selected.add(bookmark);
            }
            if (bookmark.hasChildren()) {
                collectSelectedRecursive(bookmark.getChildren(), selected);
            }
        }
    }
}
