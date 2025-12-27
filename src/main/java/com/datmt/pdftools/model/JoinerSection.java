package com.datmt.pdftools.model;

/**
 * Represents a section to be merged in the PDF Joiner.
 * Each section references a source file and specifies which pages to include.
 */
public class JoinerSection {

    public enum PageSize {
        A4("A4", 595, 842),
        LETTER("Letter", 612, 792),
        ORIGINAL("Original", 0, 0);

        private final String displayName;
        private final float width;   // in points (72 points = 1 inch)
        private final float height;

        PageSize(String displayName, float width, float height) {
            this.displayName = displayName;
            this.width = width;
            this.height = height;
        }

        public String getDisplayName() {
            return displayName;
        }

        public float getWidth() {
            return width;
        }

        public float getHeight() {
            return height;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum FitOption {
        FIT_TO_PAGE("Fit to page"),
        FILL_PAGE("Fill page"),
        ORIGINAL_SIZE("Original size");

        private final String displayName;

        FitOption(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum Rotation {
        NONE(0),
        CW_90(90),
        CW_180(180),
        CW_270(270);

        private final int degrees;

        Rotation(int degrees) {
            this.degrees = degrees;
        }

        public int getDegrees() {
            return degrees;
        }

        @Override
        public String toString() {
            return degrees + "\u00B0";
        }
    }

    private final JoinerFile sourceFile;
    private int startPage;    // 0-based
    private int endPage;      // 0-based, inclusive

    // Image options (only applicable when sourceFile.isImage())
    private PageSize pageSize = PageSize.A4;
    private FitOption fitOption = FitOption.FIT_TO_PAGE;
    private Rotation rotation = Rotation.NONE;

    /**
     * Create a section that includes all pages from the file.
     */
    public JoinerSection(JoinerFile sourceFile) {
        this.sourceFile = sourceFile;
        this.startPage = 0;
        this.endPage = sourceFile.getPageCount() - 1;
    }

    /**
     * Create a section with a specific page range.
     */
    public JoinerSection(JoinerFile sourceFile, int startPage, int endPage) {
        this.sourceFile = sourceFile;
        this.startPage = Math.max(0, startPage);
        this.endPage = Math.min(endPage, sourceFile.getPageCount() - 1);
    }

    public JoinerFile getSourceFile() {
        return sourceFile;
    }

    public int getStartPage() {
        return startPage;
    }

    public void setStartPage(int startPage) {
        this.startPage = Math.max(0, startPage);
    }

    public int getEndPage() {
        return endPage;
    }

    public void setEndPage(int endPage) {
        this.endPage = Math.min(endPage, sourceFile.getPageCount() - 1);
    }

    /**
     * Get the number of pages in this section.
     */
    public int getPageCount() {
        return endPage - startPage + 1;
    }

    /**
     * Check if this section includes all pages from the source file.
     */
    public boolean isAllPages() {
        return startPage == 0 && endPage == sourceFile.getPageCount() - 1;
    }

    // Image options getters/setters
    public PageSize getPageSize() {
        return pageSize;
    }

    public void setPageSize(PageSize pageSize) {
        this.pageSize = pageSize;
    }

    public FitOption getFitOption() {
        return fitOption;
    }

    public void setFitOption(FitOption fitOption) {
        this.fitOption = fitOption;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    /**
     * Get page range as human-readable string (1-based for display).
     */
    public String getPageRangeString() {
        if (sourceFile.isImage()) {
            return "1 page";
        }
        if (isAllPages()) {
            return "all (" + getPageCount() + " pages)";
        }
        int startDisplay = startPage + 1;
        int endDisplay = endPage + 1;
        if (startDisplay == endDisplay) {
            return "page " + startDisplay;
        }
        return "pages " + startDisplay + "-" + endDisplay;
    }

    @Override
    public String toString() {
        return sourceFile.getFileName() + ": " + getPageRangeString();
    }
}
