package com.datmt.pdftools;

/**
 * Launcher class for the PDF Tools application.
 * This class is needed for creating fat JARs with JavaFX applications.
 * When the main class extends Application, the JVM checks for JavaFX runtime
 * before the main method runs. This launcher bypasses that check.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApplication.main(args);
    }
}
