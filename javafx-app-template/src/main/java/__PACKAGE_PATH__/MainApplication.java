package __PACKAGE_NAME__;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Main application entry point.
 */
public class MainApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApplication.class);

    public static void main(String[] args) {
        logger.trace("main() method called");
        launch();
    }

    @Override
    public void start(Stage stage) throws IOException {
        logger.info("=== Application Starting ===");
        logger.debug("JavaFX Version: {}", System.getProperty("javafx.version"));
        logger.debug("Java Version: {}", System.getProperty("java.version"));

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/__PACKAGE_PATH__/ui/main.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 800, 600);

            stage.setTitle("__PROJECT_NAME__");
            stage.setScene(scene);

            // Load application icon
            loadAppIcon(stage);

            logger.info("Main window initialized");
            stage.show();

            stage.setOnCloseRequest(event -> {
                logger.info("Application closing");
            });
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw e;
        }
    }

    private void loadAppIcon(Stage stage) {
        // Try to load multiple icon sizes for best quality on different displays
        String[] iconSizes = {"64", "32"};

        for (String size : iconSizes) {
            try (InputStream is = getClass().getResourceAsStream("/__PACKAGE_PATH__/icons/app-icon-" + size + ".png")) {
                if (is != null) {
                    stage.getIcons().add(new Image(is));
                    logger.debug("Loaded app icon: app-icon-{}.png", size);
                }
            } catch (Exception e) {
                logger.trace("Could not load icon size {}: {}", size, e.getMessage());
            }
        }

        // Fallback to single icon file
        if (stage.getIcons().isEmpty()) {
            try (InputStream is = getClass().getResourceAsStream("/__PACKAGE_PATH__/icons/app-icon.png")) {
                if (is != null) {
                    stage.getIcons().add(new Image(is));
                    logger.debug("Loaded app icon: app-icon.png");
                }
            } catch (Exception e) {
                logger.warn("Could not load application icon: {}", e.getMessage());
            }
        }
    }
}
