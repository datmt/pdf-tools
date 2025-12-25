package com.datmt.pdftools.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a PDF bookmark (outline item) with its page range.
 */
public class PdfBookmark {
    private String title;
    private int pageIndex;      // 0-based start page
    private int endPageIndex;   // 0-based end page (inclusive)
    private int level;          // Depth in hierarchy (0 = top level)
    private List<PdfBookmark> children;
    private boolean selected;

    public PdfBookmark(String title, int pageIndex, int level) {
        this.title = title;
        this.pageIndex = pageIndex;
        this.endPageIndex = pageIndex; // Will be calculated later
        this.level = level;
        this.children = new ArrayList<>();
        this.selected = false;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public int getEndPageIndex() {
        return endPageIndex;
    }

    public void setEndPageIndex(int endPageIndex) {
        this.endPageIndex = endPageIndex;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public List<PdfBookmark> getChildren() {
        return children;
    }

    public void addChild(PdfBookmark child) {
        this.children.add(child);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * Get page range as human-readable string (1-based).
     */
    public String getPageRangeString() {
        int startDisplay = pageIndex + 1;
        int endDisplay = endPageIndex + 1;
        if (startDisplay == endDisplay) {
            return "page " + startDisplay;
        }
        return "pages " + startDisplay + "-" + endDisplay;
    }

    /**
     * Get the number of pages in this bookmark's range.
     */
    public int getPageCount() {
        return endPageIndex - pageIndex + 1;
    }

    /**
     * Get a sanitized filename from the bookmark title.
     */
    public String getSanitizedTitle() {
        return title.replaceAll("[^a-zA-Z0-9\\s\\-_]", "")
                    .replaceAll("\\s+", "_")
                    .trim();
    }

    @Override
    public String toString() {
        return title + " (" + getPageRangeString() + ")";
    }
}
