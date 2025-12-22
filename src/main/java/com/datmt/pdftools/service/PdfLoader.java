package com.datmt.pdftools.service;

import com.datmt.pdftools.model.PdfDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Handles loading PDF files from disk.
 */
public class PdfLoader {
    private static final Logger logger = LoggerFactory.getLogger(PdfLoader.class);

    /**
     * Load a PDF file from the filesystem.
     *
     * @param file The PDF file to load
     * @return PdfDocument wrapper containing the loaded document
     * @throws IOException If the file cannot be read
     */
    public PdfDocument loadPdf(File file) throws IOException {
        logger.debug("Loading PDF file: {}", file.getAbsolutePath());

        if (!file.exists()) {
            logger.error("PDF file does not exist: {}", file.getAbsolutePath());
            throw new IOException("File does not exist: " + file.getAbsolutePath());
        }

        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            logger.warn("File does not have .pdf extension: {}", file.getName());
        }

        try {
            logger.trace("Using Loader.loadPDF() with RandomAccessReadBufferedFile");
            PDDocument pdfDocument = Loader.loadPDF(new RandomAccessReadBufferedFile(file));
            logger.info("Successfully loaded PDF: {} with {} pages", file.getName(), pdfDocument.getNumberOfPages());
            return new PdfDocument(file, pdfDocument);
        } catch (IOException e) {
            logger.error("Failed to load PDF file: {}", file.getAbsolutePath(), e);
            throw e;
        }
    }
}
