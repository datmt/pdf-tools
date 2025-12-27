package com.datmt.pdftools.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Service for adding and removing password protection from PDF files.
 */
public class PdfSecurityService {
    private static final Logger logger = LoggerFactory.getLogger(PdfSecurityService.class);
    private static final int KEY_LENGTH = 256;

    /**
     * Permission settings for protected PDFs.
     */
    public static class Permissions {
        private boolean canPrint = true;
        private boolean canCopy = true;
        private boolean canModify = false;
        private boolean canFillForms = true;

        public boolean isCanPrint() {
            return canPrint;
        }

        public void setCanPrint(boolean canPrint) {
            this.canPrint = canPrint;
        }

        public boolean isCanCopy() {
            return canCopy;
        }

        public void setCanCopy(boolean canCopy) {
            this.canCopy = canCopy;
        }

        public boolean isCanModify() {
            return canModify;
        }

        public void setCanModify(boolean canModify) {
            this.canModify = canModify;
        }

        public boolean isCanFillForms() {
            return canFillForms;
        }

        public void setCanFillForms(boolean canFillForms) {
            this.canFillForms = canFillForms;
        }

        /**
         * Convert to PDFBox AccessPermission.
         */
        public AccessPermission toAccessPermission() {
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(canPrint);
            ap.setCanPrintFaithful(canPrint);
            ap.setCanExtractContent(canCopy);
            ap.setCanExtractForAccessibility(canCopy);
            ap.setCanModify(canModify);
            ap.setCanModifyAnnotations(canModify);
            ap.setCanFillInForm(canFillForms);
            ap.setCanAssembleDocument(canModify);
            return ap;
        }
    }

    /**
     * Security information about a PDF file.
     */
    public static class SecurityInfo {
        private final boolean isEncrypted;
        private final boolean hasUserPassword;
        private final boolean hasOwnerPassword;
        private final Permissions permissions;

        public SecurityInfo(boolean isEncrypted, boolean hasUserPassword,
                          boolean hasOwnerPassword, Permissions permissions) {
            this.isEncrypted = isEncrypted;
            this.hasUserPassword = hasUserPassword;
            this.hasOwnerPassword = hasOwnerPassword;
            this.permissions = permissions;
        }

        public boolean isEncrypted() {
            return isEncrypted;
        }

        public boolean hasUserPassword() {
            return hasUserPassword;
        }

        public boolean hasOwnerPassword() {
            return hasOwnerPassword;
        }

        public Permissions getPermissions() {
            return permissions;
        }
    }

    /**
     * Check if a PDF file is password protected.
     *
     * @param file The PDF file to check
     * @return true if the file is encrypted
     */
    public boolean isProtected(File file) {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(file))) {
            return doc.isEncrypted();
        } catch (IOException e) {
            // If we can't open it without password, it's likely protected
            return true;
        }
    }

    /**
     * Get security information about a PDF file.
     *
     * @param file     The PDF file
     * @param password Password to open the file (null or empty for no password)
     * @return Security information
     */
    public SecurityInfo getSecurityInfo(File file, String password) throws IOException {
        try (PDDocument doc = loadDocument(file, password)) {
            if (!doc.isEncrypted()) {
                return new SecurityInfo(false, false, false, null);
            }

            AccessPermission ap = doc.getCurrentAccessPermission();
            Permissions perms = new Permissions();
            perms.setCanPrint(ap.canPrint());
            perms.setCanCopy(ap.canExtractContent());
            perms.setCanModify(ap.canModify());
            perms.setCanFillForms(ap.canFillInForm());

            // We can't directly determine if user/owner passwords exist,
            // but if we opened with empty password, there's no user password
            boolean hasUserPassword = password != null && !password.isEmpty();

            return new SecurityInfo(true, hasUserPassword, true, perms);
        }
    }

    /**
     * Add password protection to a PDF file.
     *
     * @param inputFile     The input PDF file
     * @param outputFile    Where to save the protected PDF
     * @param userPassword  Password required to open the document (can be empty)
     * @param ownerPassword Password required to modify permissions
     * @param permissions   Permission settings
     * @throws IOException If protection fails
     */
    public void protectPdf(File inputFile, File outputFile, String userPassword,
                          String ownerPassword, Permissions permissions) throws IOException {
        logger.info("Protecting PDF: {} -> {}", inputFile.getName(), outputFile.getName());

        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(inputFile))) {
            // Create access permissions
            AccessPermission ap = permissions.toAccessPermission();

            // Create protection policy
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    ownerPassword,
                    userPassword != null ? userPassword : "",
                    ap
            );
            policy.setEncryptionKeyLength(KEY_LENGTH);

            // Apply protection
            doc.protect(policy);

            // Save the protected document
            doc.save(outputFile);

            logger.info("Successfully protected PDF: {}", outputFile.getName());
        }
    }

    /**
     * Remove password protection from a PDF file.
     *
     * @param inputFile  The input PDF file
     * @param outputFile Where to save the unprotected PDF
     * @param password   The password to open the document
     * @throws IOException If removal fails
     */
    public void removeProtection(File inputFile, File outputFile, String password) throws IOException {
        logger.info("Removing protection from PDF: {} -> {}", inputFile.getName(), outputFile.getName());

        try (PDDocument doc = loadDocument(inputFile, password)) {
            // Mark all security to be removed
            doc.setAllSecurityToBeRemoved(true);

            // Save the unprotected document
            doc.save(outputFile);

            logger.info("Successfully removed protection from PDF: {}", outputFile.getName());
        }
    }

    /**
     * Verify if a password is correct for a PDF file.
     *
     * @param file     The PDF file
     * @param password The password to verify
     * @return true if the password is correct or the file is not encrypted
     */
    public boolean verifyPassword(File file, String password) {
        try (PDDocument doc = loadDocument(file, password)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Load a document with an optional password.
     */
    private PDDocument loadDocument(File file, String password) throws IOException {
        if (password == null || password.isEmpty()) {
            return Loader.loadPDF(new RandomAccessReadBufferedFile(file));
        } else {
            return Loader.loadPDF(new RandomAccessReadBufferedFile(file), password);
        }
    }
}
