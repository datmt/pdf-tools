package com.datmt.pdftools.model;

import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a loaded PDF document.
 * Holds the PDF document, metadata, and cached page images.
 */
public class PdfDocument {
    private File sourceFile;
    private PDDocument pdfDocument;
    private int pageCount;
    private List<Image> pageImageCache;

    public PdfDocument(File sourceFile, PDDocument pdfDocument) {
        this.sourceFile = sourceFile;
        this.pdfDocument = pdfDocument;
        this.pageCount = pdfDocument.getNumberOfPages();
        this.pageImageCache = new ArrayList<>();
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public PDDocument getPdfDocument() {
        return pdfDocument;
    }

    public int getPageCount() {
        return pageCount;
    }

    public List<Image> getPageImageCache() {
        return pageImageCache;
    }

    public void cachePageImage(int pageIndex, Image image) {
        // Ensure list is large enough
        while (pageImageCache.size() <= pageIndex) {
            pageImageCache.add(null);
        }
        pageImageCache.set(pageIndex, image);
    }

    public Image getCachedPageImage(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < pageImageCache.size()) {
            return pageImageCache.get(pageIndex);
        }
        return null;
    }

    public void clearCache() {
        pageImageCache.clear();
    }
}
