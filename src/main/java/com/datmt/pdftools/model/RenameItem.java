package com.datmt.pdftools.model;

import java.io.File;

/**
 * Represents a PDF file to be renamed with its extracted title and rename status.
 */
public class RenameItem {

    public enum RenameStatus {
        PENDING,        // Not yet processed
        READY,          // Title extracted, ready to rename
        NO_TITLE,       // Could not extract title
        SUCCESS,        // Successfully renamed
        SKIPPED,        // Skipped (no change needed or user skipped)
        ERROR           // Error during rename
    }

    private final File originalFile;
    private String extractedTitle;
    private String newFilename;
    private RenameStatus status;
    private String errorMessage;

    public RenameItem(File originalFile) {
        this.originalFile = originalFile;
        this.extractedTitle = null;
        this.newFilename = originalFile.getName();
        this.status = RenameStatus.PENDING;
        this.errorMessage = null;
    }

    public File getOriginalFile() {
        return originalFile;
    }

    public String getOriginalFilename() {
        return originalFile.getName();
    }

    public String getExtractedTitle() {
        return extractedTitle;
    }

    public void setExtractedTitle(String extractedTitle) {
        this.extractedTitle = extractedTitle;
    }

    public String getNewFilename() {
        return newFilename;
    }

    public void setNewFilename(String newFilename) {
        this.newFilename = newFilename;
    }

    public RenameStatus getStatus() {
        return status;
    }

    public void setStatus(RenameStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Check if this item needs renaming (filename would change).
     */
    public boolean needsRename() {
        return newFilename != null && !newFilename.equals(originalFile.getName());
    }

    /**
     * Get the target file after rename.
     */
    public File getTargetFile() {
        return new File(originalFile.getParentFile(), newFilename);
    }

    @Override
    public String toString() {
        return originalFile.getName() + " -> " + newFilename + " [" + status + "]";
    }
}
