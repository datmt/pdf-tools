package com.datmt.pdftools.ui.extractor.components;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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

    /**
     * Create a page thumbnail panel.
     *
     * @param pageNumber Page number (1-based)
     * @param thumbnail  Image thumbnail
     * @param onSelected Callback when selection changes
     */
    public PageThumbnailPanel(int pageNumber, Image thumbnail, Consumer<Boolean> onSelected) {
        logger.trace("Creating thumbnail panel for page {}", pageNumber);
        
        this.pageNumber = pageNumber;
        
        setSpacing(5);
        setAlignment(Pos.TOP_CENTER);
        setStyle("-fx-border-color: #e0e0e0; -fx-border-radius: 3; -fx-padding: 5;");

        // Thumbnail image
        ImageView imageView = new ImageView(thumbnail);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(THUMBNAIL_SIZE);
        imageView.setFitHeight(THUMBNAIL_SIZE);

        // Checkbox
        selectCheckBox = new CheckBox();
        selectCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            logger.trace("Page {} thumbnail checkbox changed to: {}", pageNumber, newVal);
            onSelected.accept(newVal);
        });

        // Page label
        Label pageLabel = new Label("Page " + pageNumber);
        pageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");

        getChildren().addAll(imageView, selectCheckBox, pageLabel);
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
}
