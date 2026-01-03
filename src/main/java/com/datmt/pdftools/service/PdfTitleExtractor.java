package com.datmt.pdftools.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Service for extracting titles from PDF files.
 * Tries metadata first, then falls back to first-page text extraction.
 */
public class PdfTitleExtractor {
    private static final Logger logger = LoggerFactory.getLogger(PdfTitleExtractor.class);

    // Invalid filename characters for Windows/Linux/Mac
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");
    // Multiple spaces/underscores
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    // Control characters
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1f\\x7f]");

    private static final int MAX_FILENAME_LENGTH = 200;
    private static final int MAX_FIRST_PAGE_CHARS = 500;

    /**
     * Extract title from a PDF file.
     * Tries metadata first, then first-page text.
     *
     * @param pdfFile the PDF file
     * @return extracted title or null if not found
     */
    public String extractTitle(File pdfFile) {
        logger.debug("Extracting title from: {}", pdfFile.getName());

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(pdfFile))) {
            // Try metadata first
            String title = extractFromMetadata(document);
            if (title != null && !title.isBlank()) {
                logger.debug("Found title in metadata: {}", title);
                return title.trim();
            }

            // Fall back to first page text
            title = extractFromFirstPage(document);
            if (title != null && !title.isBlank()) {
                logger.debug("Extracted title from first page: {}", title);
                return title.trim();
            }

            logger.debug("No title found for: {}", pdfFile.getName());
            return null;

        } catch (IOException e) {
            logger.warn("Failed to extract title from {}: {}", pdfFile.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Extract title from PDF metadata.
     */
    private String extractFromMetadata(PDDocument document) {
        PDDocumentInformation info = document.getDocumentInformation();
        if (info != null) {
            String title = info.getTitle();
            if (title != null && !title.isBlank()) {
                return cleanTitle(title);
            }
        }
        return null;
    }

    /**
     * Extract title from first page text.
     * Uses heuristics to identify the title (usually first significant line).
     */
    private String extractFromFirstPage(PDDocument document) {
        try {
            if (document.getNumberOfPages() == 0) {
                return null;
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);

            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                return null;
            }

            // Limit text length for processing
            if (text.length() > MAX_FIRST_PAGE_CHARS) {
                text = text.substring(0, MAX_FIRST_PAGE_CHARS);
            }

            return extractTitleFromText(text);

        } catch (IOException e) {
            logger.debug("Failed to extract text from first page: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract title from raw text using heuristics.
     */
    private String extractTitleFromText(String text) {
        String[] lines = text.split("\\r?\\n");

        StringBuilder titleBuilder = new StringBuilder();
        int consecutiveEmptyLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip empty lines at the beginning
            if (trimmed.isEmpty()) {
                if (titleBuilder.length() > 0) {
                    consecutiveEmptyLines++;
                    // Stop after finding title followed by empty line
                    if (consecutiveEmptyLines >= 1) {
                        break;
                    }
                }
                continue;
            }

            consecutiveEmptyLines = 0;

            // Skip common header patterns (journal names, dates, DOIs, etc.)
            if (isHeaderLine(trimmed)) {
                continue;
            }

            // Skip lines that look like author names (all caps, or "and", "," separated names)
            if (isAuthorLine(trimmed)) {
                if (titleBuilder.length() > 0) {
                    break; // Stop at author line
                }
                continue;
            }

            // Skip abstract indicator
            if (trimmed.toLowerCase().startsWith("abstract")) {
                break;
            }

            // Accumulate title (may span multiple lines)
            if (titleBuilder.length() > 0) {
                titleBuilder.append(" ");
            }
            titleBuilder.append(trimmed);

            // Stop if we have a reasonable title length
            if (titleBuilder.length() > 150) {
                break;
            }
        }

        String title = titleBuilder.toString().trim();

        // Validate title
        if (title.length() < 5) {
            return null; // Too short
        }

        return cleanTitle(title);
    }

    /**
     * Check if a line looks like a header (journal name, date, DOI, etc.)
     */
    private boolean isHeaderLine(String line) {
        String lower = line.toLowerCase();

        // DOI pattern
        if (lower.contains("doi:") || lower.contains("doi.org")) {
            return true;
        }

        // Common journal/publisher patterns
        if (lower.contains("journal") || lower.contains("proceedings") ||
            lower.contains("conference") || lower.contains("volume") ||
            lower.contains("issue") || lower.contains("published") ||
            lower.contains("received") || lower.contains("accepted") ||
            lower.contains("arxiv") || lower.contains("preprint")) {
            return true;
        }

        // Date patterns (year ranges, month-year)
        if (line.matches(".*\\b(19|20)\\d{2}\\b.*") && line.length() < 30) {
            return true;
        }

        // Page numbers
        if (line.matches("^\\d+[-–]\\d+$") || line.matches("^p\\.?\\s*\\d+.*")) {
            return true;
        }

        // Copyright notices
        if (lower.contains("copyright") || lower.contains("©") || lower.contains("all rights reserved")) {
            return true;
        }

        return false;
    }

    /**
     * Check if a line looks like author names.
     */
    private boolean isAuthorLine(String line) {
        // Contains common author separators with multiple names
        if (line.contains(",") && line.split(",").length >= 2) {
            // Check if it looks like "Name1, Name2, Name3"
            String[] parts = line.split(",");
            int nameCount = 0;
            for (String part : parts) {
                String trimmed = part.trim();
                // Simple name heuristic: 1-3 words, capitalized
                if (trimmed.matches("^[A-Z][a-z]+( [A-Z][a-z.]+){0,3}$")) {
                    nameCount++;
                }
            }
            if (nameCount >= 2) {
                return true;
            }
        }

        // "and" between names
        if (line.toLowerCase().contains(" and ") && line.length() < 100) {
            String[] parts = line.toLowerCase().split(" and ");
            if (parts.length >= 2) {
                // Check if both parts look like names
                boolean allNames = true;
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.split("\\s+").length > 4) {
                        allNames = false;
                        break;
                    }
                }
                if (allNames) {
                    return true;
                }
            }
        }

        // Email pattern
        if (line.contains("@") && line.contains(".")) {
            return true;
        }

        // Affiliation patterns (university, institute, department)
        String lower = line.toLowerCase();
        if (lower.contains("university") || lower.contains("institute") ||
            lower.contains("department") || lower.contains("laboratory") ||
            lower.contains("school of")) {
            return true;
        }

        return false;
    }

    /**
     * Clean and sanitize a title for use as a filename.
     */
    private String cleanTitle(String title) {
        if (title == null) {
            return null;
        }

        // Remove control characters
        title = CONTROL_CHARS.matcher(title).replaceAll("");

        // Remove invalid filename characters
        title = INVALID_CHARS.matcher(title).replaceAll(" ");

        // Normalize whitespace
        title = MULTIPLE_SPACES.matcher(title).replaceAll(" ");

        // Trim
        title = title.trim();

        // Remove trailing periods (but keep other punctuation)
        while (title.endsWith(".")) {
            title = title.substring(0, title.length() - 1).trim();
        }

        // Truncate if too long
        if (title.length() > MAX_FILENAME_LENGTH) {
            title = title.substring(0, MAX_FILENAME_LENGTH);
            // Try to cut at word boundary
            int lastSpace = title.lastIndexOf(' ');
            if (lastSpace > MAX_FILENAME_LENGTH - 50) {
                title = title.substring(0, lastSpace);
            }
        }

        return title.trim();
    }

    /**
     * Generate a safe filename from title.
     *
     * @param title extracted title
     * @return filename with .pdf extension
     */
    public String generateFilename(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }

        String filename = cleanTitle(title);
        if (filename == null || filename.isBlank()) {
            return null;
        }

        return filename + ".pdf";
    }

    /**
     * Generate a unique filename, handling duplicates.
     *
     * @param directory target directory
     * @param baseFilename desired filename (with .pdf extension)
     * @return unique filename that doesn't exist in the directory
     */
    public String generateUniqueFilename(File directory, String baseFilename) {
        if (baseFilename == null) {
            return null;
        }

        File targetFile = new File(directory, baseFilename);
        if (!targetFile.exists()) {
            return baseFilename;
        }

        // Extract name without extension
        String nameWithoutExt = baseFilename.substring(0, baseFilename.length() - 4);

        // Try numbered versions
        for (int i = 1; i <= 999; i++) {
            String numberedName = nameWithoutExt + " (" + i + ").pdf";
            targetFile = new File(directory, numberedName);
            if (!targetFile.exists()) {
                return numberedName;
            }
        }

        // Give up after 999 duplicates
        logger.warn("Could not find unique filename for: {}", baseFilename);
        return null;
    }
}
