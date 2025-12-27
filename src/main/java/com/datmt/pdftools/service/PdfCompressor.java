package com.datmt.pdftools.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Calendar;

/**
 * Service for compressing PDF files by optimizing images and removing unnecessary data.
 */
public class PdfCompressor {
    private static final Logger logger = LoggerFactory.getLogger(PdfCompressor.class);
    private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());

    /**
     * Compression level presets.
     */
    public enum CompressionLevel {
        LOW(0.85f, 200),      // Best quality, less compression
        MEDIUM(0.65f, 150),   // Balanced
        HIGH(0.45f, 100);     // Smallest size, lower quality

        private final float imageQuality;
        private final int maxDpi;

        CompressionLevel(float imageQuality, int maxDpi) {
            this.imageQuality = imageQuality;
            this.maxDpi = maxDpi;
        }

        public float getImageQuality() {
            return imageQuality;
        }

        public int getMaxDpi() {
            return maxDpi;
        }
    }

    /**
     * Compression options.
     */
    public static class CompressionOptions {
        private CompressionLevel level = CompressionLevel.MEDIUM;
        private float imageQuality = 0.65f;
        private boolean removeMetadata = false;
        private boolean removeBookmarks = false;

        public CompressionLevel getLevel() {
            return level;
        }

        public void setLevel(CompressionLevel level) {
            this.level = level;
            this.imageQuality = level.getImageQuality();
        }

        public float getImageQuality() {
            return imageQuality;
        }

        public void setImageQuality(float imageQuality) {
            this.imageQuality = Math.max(0.1f, Math.min(1.0f, imageQuality));
        }

        public boolean isRemoveMetadata() {
            return removeMetadata;
        }

        public void setRemoveMetadata(boolean removeMetadata) {
            this.removeMetadata = removeMetadata;
        }

        public boolean isRemoveBookmarks() {
            return removeBookmarks;
        }

        public void setRemoveBookmarks(boolean removeBookmarks) {
            this.removeBookmarks = removeBookmarks;
        }
    }

    /**
     * Result of compression operation.
     */
    public static class CompressResult {
        private final File inputFile;
        private final File outputFile;
        private final long originalSize;
        private final long compressedSize;
        private final boolean success;
        private final String errorMessage;

        public CompressResult(File inputFile, File outputFile, long originalSize, long compressedSize) {
            this.inputFile = inputFile;
            this.outputFile = outputFile;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.success = true;
            this.errorMessage = null;
        }

        public CompressResult(File inputFile, String errorMessage) {
            this.inputFile = inputFile;
            this.outputFile = null;
            this.originalSize = inputFile.length();
            this.compressedSize = 0;
            this.success = false;
            this.errorMessage = errorMessage;
        }

        public File getInputFile() {
            return inputFile;
        }

        public File getOutputFile() {
            return outputFile;
        }

        public long getOriginalSize() {
            return originalSize;
        }

        public long getCompressedSize() {
            return compressedSize;
        }

        public double getCompressionRatio() {
            if (originalSize == 0) return 0;
            return 1.0 - ((double) compressedSize / originalSize);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Progress callback interface.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int currentPage, int totalPages, String status);
    }

    /**
     * Compress a PDF file.
     *
     * @param inputFile  The PDF file to compress
     * @param outputFile Where to save the compressed PDF
     * @param options    Compression options
     * @param callback   Progress callback (optional)
     * @return Compression result
     */
    public CompressResult compress(File inputFile, File outputFile, CompressionOptions options,
                                   ProgressCallback callback) {
        logger.info("Compressing PDF: {} with level {} using {} threads",
                inputFile.getName(), options.getLevel(), THREAD_COUNT);
        long originalSize = inputFile.length();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(inputFile))) {
            int totalPages = document.getNumberOfPages();
            AtomicInteger completedPages = new AtomicInteger(0);

            // Create tasks for all pages
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < totalPages; i++) {
                final int pageIndex = i;
                Future<?> future = executor.submit(() -> {
                    try {
                        PDPage page = document.getPage(pageIndex);
                        compressPageImages(document, page, options);

                        int done = completedPages.incrementAndGet();
                        if (callback != null && done % 50 == 0) {
                            callback.onProgress(done, totalPages, "Optimizing pages");
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to compress page {}: {}", pageIndex, e.getMessage());
                    }
                });
                futures.add(future);
            }

            // Wait for all pages to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.warn("Page task failed: {}", e.getMessage());
                }
            }

            if (callback != null) {
                callback.onProgress(totalPages, totalPages, "Pages optimized");
            }

            // Remove metadata if requested
            if (options.isRemoveMetadata()) {
                if (callback != null) {
                    callback.onProgress(totalPages, totalPages, "Removing metadata");
                }
                removeMetadata(document);
            }

            // Remove bookmarks if requested
            if (options.isRemoveBookmarks()) {
                if (callback != null) {
                    callback.onProgress(totalPages, totalPages, "Removing bookmarks");
                }
                document.getDocumentCatalog().setDocumentOutline(null);
            }

            // Save the compressed document
            if (callback != null) {
                callback.onProgress(totalPages, totalPages, "Saving compressed file");
            }
            document.save(outputFile);

            long compressedSize = outputFile.length();
            double reduction = (1.0 - (double) compressedSize / originalSize) * 100;
            logger.info("Compression complete: {} -> {} bytes ({}% reduction)",
                    originalSize, compressedSize, String.format("%.1f", reduction));

            return new CompressResult(inputFile, outputFile, originalSize, compressedSize);

        } catch (Exception e) {
            logger.error("Failed to compress PDF {}: {}", inputFile.getName(), e.getMessage(), e);
            return new CompressResult(inputFile, e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Compress images on a page. Thread-safe with synchronization on document.
     */
    private void compressPageImages(PDDocument document, PDPage page, CompressionOptions options)
            throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) {
            return;
        }

        // Collect image names first (to avoid concurrent modification)
        List<COSName> imageNames = new ArrayList<>();
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xobject = resources.getXObject(name);
            if (xobject instanceof PDImageXObject) {
                imageNames.add(name);
            }
        }

        // Process each image
        for (COSName name : imageNames) {
            try {
                PDXObject xobject = resources.getXObject(name);
                if (xobject instanceof PDImageXObject) {
                    PDImageXObject image = (PDImageXObject) xobject;
                    compressImage(document, resources, name, image, options);
                }
            } catch (Exception e) {
                logger.trace("Skipping image {}: {}", name.getName(), e.getMessage());
            }
        }
    }

    /**
     * Compress a single image. Uses synchronization for thread-safe document modification.
     */
    private void compressImage(PDDocument document, PDResources resources, COSName name,
                               PDImageXObject originalImage, CompressionOptions options) {
        try {
            // Get the image (read operation - can be done outside sync)
            BufferedImage bufferedImage = originalImage.getImage();
            if (bufferedImage == null) {
                return;
            }

            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();

            // Calculate target size based on DPI limit
            int maxDpi = options.getLevel().getMaxDpi();
            float scale = calculateScale(originalImage, maxDpi);

            BufferedImage processedImage = bufferedImage;

            // Downscale if needed (CPU work - done outside sync)
            if (scale < 1.0f) {
                int newWidth = Math.max(1, (int) (width * scale));
                int newHeight = Math.max(1, (int) (height * scale));
                processedImage = resizeImage(bufferedImage, newWidth, newHeight);
                logger.trace("Downscaled image from {}x{} to {}x{}", width, height, newWidth, newHeight);
            }

            // Convert to RGB if needed (CPU work - done outside sync)
            if (processedImage.getColorModel().hasAlpha()) {
                BufferedImage rgbImage = new BufferedImage(
                        processedImage.getWidth(),
                        processedImage.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbImage.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
                g.drawImage(processedImage, 0, 0, null);
                g.dispose();
                processedImage = rgbImage;
            }

            // Synchronized block for document modification
            synchronized (document) {
                // Create compressed JPEG image and replace original
                PDImageXObject compressedImage = JPEGFactory.createFromImage(
                        document, processedImage, options.getImageQuality());
                resources.put(name, compressedImage);
            }

        } catch (Exception e) {
            logger.trace("Skipping image {}: {}", name.getName(), e.getMessage());
            // Keep original image if compression fails
        }
    }

    /**
     * Calculate scale factor based on DPI limit.
     */
    private float calculateScale(PDImageXObject image, int maxDpi) {
        // Estimate current DPI (assuming 72 points = 1 inch on page)
        // This is a rough estimate since we don't have page placement info
        float estimatedDpi = Math.max(image.getWidth(), image.getHeight()) / 8.0f; // rough estimate

        if (estimatedDpi > maxDpi) {
            return (float) maxDpi / estimatedDpi;
        }
        return 1.0f;
    }

    /**
     * Resize an image.
     */
    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height,
                original.getType() == 0 ? BufferedImage.TYPE_INT_RGB : original.getType());
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Remove metadata from the document.
     */
    private void removeMetadata(PDDocument document) {
        // Clear document information
        PDDocumentInformation info = new PDDocumentInformation();
        document.setDocumentInformation(info);

        // Remove XMP metadata
        try {
            document.getDocumentCatalog().setMetadata(null);
        } catch (Exception e) {
            logger.warn("Could not remove XMP metadata: {}", e.getMessage());
        }
    }

    /**
     * Get file size in a human-readable format.
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
