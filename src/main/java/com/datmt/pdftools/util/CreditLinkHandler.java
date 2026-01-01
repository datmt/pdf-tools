package com.datmt.pdftools.util;

import javafx.application.HostServices;
import javafx.scene.control.Hyperlink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to handle the credit link in all tool windows.
 */
public class CreditLinkHandler {
    private static final Logger logger = LoggerFactory.getLogger(CreditLinkHandler.class);
    private static final String BASE_URL = "https://tools.datmt.com";

    /**
     * Setup the credit link to open the website when clicked.
     */
    public static void setup(Hyperlink creditLink) {
        if (creditLink == null) {
            logger.info("Credit link is null, skipping setup");
            return;
        }

        creditLink.setOnAction(event -> openUrl(buildUrl()));
    }

    /**
     * Build the URL with source and OS parameters.
     */
    private static String buildUrl() {
        String os = getOsName();
        return BASE_URL + "?src=pdf-tools&os=" + os;
    }

    /**
     * Get a clean OS name for the URL parameter.
     */
    private static String getOsName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) {
            return "linux";
        } else if (os.contains("mac")) {
            return "macos";
        } else if (os.contains("win")) {
            return "windows";
        }
        return "unknown";
    }

    /**
     * Open URL using platform-specific command.
     */
    private static void openUrl(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("linux")) {
                pb = new ProcessBuilder("xdg-open", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else {
                logger.warn("Unsupported OS for opening URL: {}", os);
                return;
            }

            pb.inheritIO();
            pb.start();
            logger.info("Opened credit link: {}", url);
        } catch (Exception e) {
            logger.error("Failed to open credit link: {}", e.getMessage());
        }
    }
}
