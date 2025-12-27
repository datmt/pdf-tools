package com.datmt.pdftools.ui.security;

import com.datmt.pdftools.service.PdfSecurityService;
import com.datmt.pdftools.service.PdfSecurityService.Permissions;
import com.datmt.pdftools.service.PdfSecurityService.SecurityInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the PDF Security tool.
 */
public class PdfSecurityController {
    private static final Logger logger = LoggerFactory.getLogger(PdfSecurityController.class);

    @FXML private Button selectFileButton;
    @FXML private Label fileNameLabel;
    @FXML private Label statusLabel;

    // Protect section
    @FXML private PasswordField userPasswordField;
    @FXML private PasswordField ownerPasswordField;
    @FXML private CheckBox allowPrintingCheck;
    @FXML private CheckBox allowCopyingCheck;
    @FXML private CheckBox allowEditingCheck;
    @FXML private CheckBox allowFormsCheck;
    @FXML private Button protectButton;

    // Remove protection section
    @FXML private PasswordField currentPasswordField;
    @FXML private Label verifyStatusLabel;
    @FXML private Button verifyButton;
    @FXML private Button removeProtectionButton;

    // PDF info section
    @FXML private VBox pdfInfoSection;
    @FXML private Label encryptionStatusLabel;
    @FXML private Label permissionsInfoLabel;

    // Output options
    @FXML private RadioButton saveAsNewRadio;
    @FXML private RadioButton overwriteOriginalRadio;
    @FXML private ToggleGroup outputToggleGroup;

    // Result section
    @FXML private HBox resultSection;
    @FXML private Label resultLabel;

    private File selectedFile;
    private boolean isEncrypted = false;
    private boolean passwordVerified = false;
    private final PdfSecurityService securityService = new PdfSecurityService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @FXML
    public void initialize() {
        logger.info("Initializing PDF Security");
        setupWindowCloseHandler();
        setupListeners();
        updateUI();
    }

    private void setupWindowCloseHandler() {
        Platform.runLater(() -> {
            if (selectFileButton.getScene() != null && selectFileButton.getScene().getWindow() != null) {
                selectFileButton.getScene().getWindow().setOnCloseRequest(event -> cleanup());
            }
        });
    }

    private void setupListeners() {
        // Owner password is required for protection
        ownerPasswordField.textProperty().addListener((obs, oldVal, newVal) -> updateUI());

        // Current password changes reset verification
        currentPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordVerified = false;
            verifyStatusLabel.setText("");
            updateUI();
        });
    }

    @FXML
    private void onSelectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File file = fileChooser.showOpenDialog(selectFileButton.getScene().getWindow());
        if (file != null) {
            selectFile(file);
        }
    }

    private void selectFile(File file) {
        selectedFile = file;
        fileNameLabel.setText(file.getName());
        passwordVerified = false;
        verifyStatusLabel.setText("");
        currentPasswordField.clear();
        resultSection.setVisible(false);
        resultSection.setManaged(false);

        // Check if file is encrypted
        executor.submit(() -> {
            try {
                isEncrypted = securityService.isProtected(file);

                // Try to get security info without password
                SecurityInfo info = null;
                try {
                    info = securityService.getSecurityInfo(file, null);
                } catch (Exception e) {
                    // File requires password
                }

                final SecurityInfo finalInfo = info;
                Platform.runLater(() -> {
                    if (isEncrypted) {
                        statusLabel.setText("Protected");
                        statusLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #ff5722;");
                        encryptionStatusLabel.setText("Status: Password protected");
                    } else {
                        statusLabel.setText("Unprotected");
                        statusLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #4caf50;");
                        encryptionStatusLabel.setText("Status: No password protection");
                    }

                    if (finalInfo != null && finalInfo.getPermissions() != null) {
                        Permissions perms = finalInfo.getPermissions();
                        permissionsInfoLabel.setText(String.format(
                                "Print: %s | Copy: %s | Edit: %s | Forms: %s",
                                perms.isCanPrint() ? "Yes" : "No",
                                perms.isCanCopy() ? "Yes" : "No",
                                perms.isCanModify() ? "Yes" : "No",
                                perms.isCanFillForms() ? "Yes" : "No"
                        ));
                    } else {
                        permissionsInfoLabel.setText("");
                    }

                    pdfInfoSection.setVisible(true);
                    pdfInfoSection.setManaged(true);
                    updateUI();
                });
            } catch (Exception e) {
                logger.error("Error checking file security: {}", e.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText("Error");
                    statusLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #cc0000;");
                });
            }
        });
    }

    @FXML
    private void onVerifyPassword() {
        if (selectedFile == null) return;

        String password = currentPasswordField.getText();
        if (password.isEmpty()) {
            verifyStatusLabel.setText("Please enter a password");
            verifyStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #cc0000;");
            return;
        }

        verifyButton.setDisable(true);
        verifyStatusLabel.setText("Verifying...");
        verifyStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666666;");

        executor.submit(() -> {
            boolean valid = securityService.verifyPassword(selectedFile, password);

            Platform.runLater(() -> {
                verifyButton.setDisable(false);
                passwordVerified = valid;

                if (valid) {
                    verifyStatusLabel.setText("Password correct!");
                    verifyStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #4caf50; -fx-font-weight: bold;");
                } else {
                    verifyStatusLabel.setText("Incorrect password");
                    verifyStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #cc0000;");
                }
                updateUI();
            });
        });
    }

    @FXML
    private void onProtect() {
        if (selectedFile == null) return;

        String ownerPassword = ownerPasswordField.getText();
        if (ownerPassword.isEmpty()) {
            showResult("Owner password is required", false);
            return;
        }

        String userPassword = userPasswordField.getText();

        // Build permissions
        Permissions permissions = new Permissions();
        permissions.setCanPrint(allowPrintingCheck.isSelected());
        permissions.setCanCopy(allowCopyingCheck.isSelected());
        permissions.setCanModify(allowEditingCheck.isSelected());
        permissions.setCanFillForms(allowFormsCheck.isSelected());

        // Determine output file
        File outputFile = getOutputFile(selectedFile, "_protected");
        if (outputFile == null) return;

        protectButton.setDisable(true);
        showResult("Protecting PDF...", true);

        executor.submit(() -> {
            try {
                securityService.protectPdf(selectedFile, outputFile, userPassword, ownerPassword, permissions);

                // If overwrite mode, replace original
                if (overwriteOriginalRadio.isSelected()) {
                    if (selectedFile.delete() && outputFile.renameTo(selectedFile)) {
                        Platform.runLater(() -> showResult("PDF protected successfully (overwritten)", true));
                    } else {
                        Platform.runLater(() -> showResult("Protected PDF saved as: " + outputFile.getName(), true));
                    }
                } else {
                    Platform.runLater(() -> showResult("Protected PDF saved as: " + outputFile.getName(), true));
                }
            } catch (Exception e) {
                logger.error("Failed to protect PDF: {}", e.getMessage(), e);
                Platform.runLater(() -> showResult("Failed: " + e.getMessage(), false));
            } finally {
                Platform.runLater(() -> {
                    protectButton.setDisable(false);
                    // Refresh file info
                    if (selectedFile.exists()) {
                        selectFile(selectedFile);
                    }
                });
            }
        });
    }

    @FXML
    private void onRemoveProtection() {
        if (selectedFile == null || !passwordVerified) return;

        String password = currentPasswordField.getText();

        // Determine output file
        File outputFile = getOutputFile(selectedFile, "_unprotected");
        if (outputFile == null) return;

        removeProtectionButton.setDisable(true);
        showResult("Removing protection...", true);

        executor.submit(() -> {
            try {
                securityService.removeProtection(selectedFile, outputFile, password);

                // If overwrite mode, replace original
                if (overwriteOriginalRadio.isSelected()) {
                    if (selectedFile.delete() && outputFile.renameTo(selectedFile)) {
                        Platform.runLater(() -> showResult("Protection removed successfully (overwritten)", true));
                    } else {
                        Platform.runLater(() -> showResult("Unprotected PDF saved as: " + outputFile.getName(), true));
                    }
                } else {
                    Platform.runLater(() -> showResult("Unprotected PDF saved as: " + outputFile.getName(), true));
                }
            } catch (Exception e) {
                logger.error("Failed to remove protection: {}", e.getMessage(), e);
                Platform.runLater(() -> showResult("Failed: " + e.getMessage(), false));
            } finally {
                Platform.runLater(() -> {
                    removeProtectionButton.setDisable(false);
                    passwordVerified = false;
                    currentPasswordField.clear();
                    verifyStatusLabel.setText("");
                    // Refresh file info
                    if (selectedFile.exists()) {
                        selectFile(selectedFile);
                    }
                });
            }
        });
    }

    private File getOutputFile(File inputFile, String suffix) {
        if (overwriteOriginalRadio.isSelected()) {
            // Create temp file that will replace original
            return new File(inputFile.getParent(),
                    inputFile.getName().replace(".pdf", "_temp.pdf"));
        } else {
            // Save as new file with suffix
            return new File(inputFile.getParent(),
                    inputFile.getName().replace(".pdf", suffix + ".pdf"));
        }
    }

    private void showResult(String message, boolean success) {
        resultSection.setVisible(true);
        resultSection.setManaged(true);
        resultLabel.setText(message);
        if (success) {
            resultLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #4caf50;");
        } else {
            resultLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #cc0000;");
        }
    }

    private void updateUI() {
        boolean hasFile = selectedFile != null;

        // Protect button: enabled if file selected and owner password provided
        protectButton.setDisable(!hasFile || ownerPasswordField.getText().isEmpty());

        // Verify button: enabled if file is encrypted and password entered
        verifyButton.setDisable(!hasFile || !isEncrypted || currentPasswordField.getText().isEmpty());

        // Remove protection button: enabled if password verified
        removeProtectionButton.setDisable(!hasFile || !isEncrypted || !passwordVerified);
    }

    public void cleanup() {
        logger.info("Cleaning up PDF Security");
        executor.shutdownNow();
    }
}
