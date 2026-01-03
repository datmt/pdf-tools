package __PACKAGE_NAME__.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the main window.
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private Label welcomeLabel;

    @FXML
    public void initialize() {
        logger.debug("MainController initialized");
        welcomeLabel.setText("Welcome to __PROJECT_NAME__!");
    }

    @FXML
    private void onHelloButtonClick() {
        logger.info("Hello button clicked");
        welcomeLabel.setText("Hello from __PROJECT_NAME__!");
    }
}
