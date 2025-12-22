package com.datmt.pdftools.ui.extractor.components;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Reusable component for displaying a page thumbnail with selection checkbox.
 */
public class PageThumbnailPanel extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(PageThumbnailPanel.class);
    private static final int THUMBNAIL_SIZE = 100;

    private CheckBox selectCheckBox;
    private int pageNumber;
    private Consumer<Void> onThumbnailClicked;
    private static final String BORDER_UNSELECTED = "-fx-border-color: #e0e0e0; -fx-border-radius: 3; -fx-padding: 5; -fx-cursor: hand;";
    private static final String BORDER_SELECTED = "-fx-border-color: #2196F3; -fx-border-radius: 3; -fx-padding: 5; -fx-border-width: 2; -fx-cursor: hand;";

    /**
     * Create a page thumbnail panel.
     *
     * @param pageNumber Page number (1-based)
     * @param thumbnail  Image thumbnail
     * @param onSelected Callback when selection changes
     */
    public PageThumbnailPanel(int pageNumber, Image thumbnail, Consumer<Boolean> onSelected) {
        this(pageNumber, thumbnail, onSelected, null);
    }

    /**
     * Create a page thumbnail panel with thumbnail click handler.
     *
     * @param pageNumber Page number (1-based)
     * @param thumbnail  Image thumbnail
     * @param onSelected Callback when selection changes
     * @param onThumbnailClicked Callback when thumbnail image is clicked
     */
    public PageThumbnailPanel(int pageNumber, Image thumbnail, Consumer<Boolean> onSelected, Consumer<Void> onThumbnailClicked) {
        logger.trace("Creating thumbnail panel for page {}", pageNumber);

        this.pageNumber = pageNumber;
        this.onThumbnailClicked = onThumbnailClicked;

        setSpacing(5);
        setAlignment(Pos.TOP_CENTER);
        setStyle(BORDER_UNSELECTED);

        // Thumbnail image
        ImageView imageView = new ImageView(thumbnail);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(THUMBNAIL_SIZE);
        imageView.setFitHeight(THUMBNAIL_SIZE);

        // Checkbox
        selectCheckBox = new CheckBox();
        selectCheckBox.setStyle("-fx-cursor: default;");
        selectCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            logger.trace("Page {} thumbnail checkbox changed to: {}", pageNumber, newVal);
            onSelected.accept(newVal);
        });

        // Page label
        Label pageLabel = new Label("Page " + pageNumber);
        pageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");

        getChildren().addAll(imageView, selectCheckBox, pageLabel);

        // Click handler on the container - ignore clicks on the checkbox
        setOnMouseClicked(event -> {
            if (event.getTarget() != selectCheckBox && !selectCheckBox.contains(event.getX(), event.getY())) {
                event.consume();
                if (onThumbnailClicked != null) {
                    logger.trace("Page {} thumbnail clicked", pageNumber);
                    onThumbnailClicked.accept(null);
                }
            }
        });
    }

    public boolean isSelected() {
        return selectCheckBox.isSelected();
    }

    public void setSelected(boolean selected) {
        selectCheckBox.setSelected(selected);
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPreviewSelected(boolean selected) {
        setStyle(selected ? BORDER_SELECTED : BORDER_UNSELECTED);
    }
}
