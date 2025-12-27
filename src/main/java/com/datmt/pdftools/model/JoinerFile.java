package com.datmt.pdftools.model;

import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a file (PDF or image) loaded into the PDF Joiner.
 */
public class JoinerFile {

    public enum FileType {
        PDF,
        IMAGE
    }

    private final File sourceFile;
    private final FileType fileType;
    private final int pageCount;
    private PDDocument pdfDocument;  // Only for PDF files
    private Image image;             // Only for image files
    private final List<Image> thumbnailCache;

    /**
     * Create a JoinerFile for a PDF document.
     */
    public JoinerFile(File sourceFile, PDDocument pdfDocument) {
        this.sourceFile = sourceFile;
        this.fileType = FileType.PDF;
        this.pdfDocument = pdfDocument;
        this.pageCount = pdfDocument.getNumberOfPages();
        this.thumbnailCache = new ArrayList<>();
    }

    /**
     * Create a JoinerFile for an image.
     */
    public JoinerFile(File sourceFile, Image image) {
        this.sourceFile = sourceFile;
        this.fileType = FileType.IMAGE;
        this.image = image;
        this.pageCount = 1;  // Images are always 1 page
        this.thumbnailCache = new ArrayList<>();
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public String getFileName() {
        return sourceFile.getName();
    }

    public FileType getFileType() {
        return fileType;
    }

    public boolean isPdf() {
        return fileType == FileType.PDF;
    }

    public boolean isImage() {
        return fileType == FileType.IMAGE;
    }

    public int getPageCount() {
        return pageCount;
    }

    public PDDocument getPdfDocument() {
        return pdfDocument;
    }

    public Image getImage() {
        return image;
    }

    public void cacheThumbnail(int pageIndex, Image thumbnail) {
        while (thumbnailCache.size() <= pageIndex) {
            thumbnailCache.add(null);
        }
        thumbnailCache.set(pageIndex, thumbnail);
    }

    public Image getCachedThumbnail(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < thumbnailCache.size()) {
            return thumbnailCache.get(pageIndex);
        }
        return null;
    }

    public void clearCache() {
        thumbnailCache.clear();
    }

    /**
     * Close the PDF document if this is a PDF file.
     */
    public void close() throws Exception {
        if (pdfDocument != null) {
            pdfDocument.close();
            pdfDocument = null;
        }
    }

    @Override
    public String toString() {
        return getFileName() + " (" + pageCount + " page" + (pageCount > 1 ? "s" : "") + ")";
    }
}
