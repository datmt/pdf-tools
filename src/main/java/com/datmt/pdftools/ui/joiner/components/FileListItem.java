package com.datmt.pdftools.ui.joiner.components;

import com.datmt.pdftools.model.JoinerFile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Component for displaying a file in the joiner file list.
 */
public class FileListItem extends HBox {
    private static final Logger logger = LoggerFactory.getLogger(FileListItem.class);
    private static final int THUMBNAIL_SIZE = 50;
    private static final String STYLE_NORMAL = "-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-radius: 3; -fx-cursor: hand;";
    private static final String STYLE_SELECTED = "-fx-background-color: #e3f2fd; -fx-border-color: #2196F3; -fx-border-radius: 3; -fx-border-width: 2; -fx-cursor: hand;";

    private final JoinerFile joinerFile;
    private final ImageView thumbnailView;
    private boolean isSelected;

    /**
     * Create a file list item.
     *
     * @param joinerFile The file to display
     * @param onSelect   Callback when item is clicked/selected
     * @param onRemove   Callback when remove button is clicked
     */
    public FileListItem(JoinerFile joinerFile, Consumer<FileListItem> onSelect, Consumer<FileListItem> onRemove) {
        this.joinerFile = joinerFile;
        this.isSelected = false;

        setSpacing(8);
        setPadding(new Insets(8));
        setAlignment(Pos.CENTER_LEFT);
        setStyle(STYLE_NORMAL);

        // Thumbnail
        thumbnailView = new ImageView();
        thumbnailView.setPreserveRatio(true);
        thumbnailView.setFitWidth(THUMBNAIL_SIZE);
        thumbnailView.setFitHeight(THUMBNAIL_SIZE);

        // File info
        VBox infoBox = new VBox(4);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Label nameLabel = new Label(joinerFile.getFileName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        nameLabel.setWrapText(true);

        String typeInfo = joinerFile.isPdf() ? "PDF" : "Image";
        String pageInfo = joinerFile.getPageCount() + " page" + (joinerFile.getPageCount() > 1 ? "s" : "");
        Label infoLabel = new Label(typeInfo + " - " + pageInfo);
        infoLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

        infoBox.getChildren().addAll(nameLabel, infoLabel);

        // Remove button
        Button removeBtn = new Button("\u2715");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 14; -fx-cursor: hand;");
        removeBtn.setOnMouseEntered(e -> removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #d32f2f; -fx-font-size: 14; -fx-cursor: hand;"));
        removeBtn.setOnMouseExited(e -> removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 14; -fx-cursor: hand;"));
        removeBtn.setOnAction(e -> {
            e.consume();
            if (onRemove != null) {
                onRemove.accept(this);
            }
        });

        getChildren().addAll(thumbnailView, infoBox, removeBtn);

        // Click handler for selection
        setOnMouseClicked(event -> {
            if (event.getTarget() != removeBtn) {
                if (onSelect != null) {
                    onSelect.accept(this);
                }
            }
        });

        logger.trace("Created FileListItem for: {}", joinerFile.getFileName());
    }

    public JoinerFile getJoinerFile() {
        return joinerFile;
    }

    public void setThumbnail(Image thumbnail) {
        thumbnailView.setImage(thumbnail);
    }

    public boolean isFileSelected() {
        return isSelected;
    }

    public void setFileSelected(boolean selected) {
        this.isSelected = selected;
        setStyle(selected ? STYLE_SELECTED : STYLE_NORMAL);
    }
}
