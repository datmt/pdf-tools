package com.datmt.pdftools.ui.joiner.components;

import com.datmt.pdftools.model.JoinerSection;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Component for displaying a section in the joiner section list.
 * Shows source file, page range, and move/remove controls.
 */
public class SectionListItem extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(SectionListItem.class);
    private static final String STYLE_NORMAL = "-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-radius: 3; -fx-cursor: hand;";
    private static final String STYLE_SELECTED = "-fx-background-color: #e3f2fd; -fx-border-color: #2196F3; -fx-border-radius: 3; -fx-border-width: 2; -fx-cursor: hand;";

    private final JoinerSection section;
    private final Label indexLabel;
    private boolean isSelected;

    /**
     * Create a section list item.
     *
     * @param section  The section to display
     * @param index    The section index (1-based, for display)
     * @param onSelect Callback when item is clicked/selected
     * @param onMoveUp Callback when up button is clicked
     * @param onMoveDown Callback when down button is clicked
     * @param onRemove Callback when remove button is clicked
     */
    public SectionListItem(JoinerSection section, int index,
                           Consumer<SectionListItem> onSelect,
                           Consumer<SectionListItem> onMoveUp,
                           Consumer<SectionListItem> onMoveDown,
                           Consumer<SectionListItem> onRemove) {
        this.section = section;
        this.isSelected = false;

        setSpacing(4);
        setPadding(new Insets(8));
        setStyle(STYLE_NORMAL);

        // Header row with index and controls
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        indexLabel = new Label("Section " + index);
        indexLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        HBox.setHgrow(indexLabel, Priority.ALWAYS);

        // Control buttons
        Button upBtn = createIconButton("\u25B2", "Move up", onMoveUp);
        Button downBtn = createIconButton("\u25BC", "Move down", onMoveDown);
        Button removeBtn = createIconButton("\u2715", "Remove", onRemove);
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 12; -fx-cursor: hand; -fx-min-width: 24;");
        removeBtn.setOnMouseEntered(e -> removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #d32f2f; -fx-font-size: 12; -fx-cursor: hand; -fx-min-width: 24;"));
        removeBtn.setOnMouseExited(e -> removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 12; -fx-cursor: hand; -fx-min-width: 24;"));

        headerRow.getChildren().addAll(indexLabel, upBtn, downBtn, removeBtn);

        // File name
        Label fileLabel = new Label(section.getSourceFile().getFileName());
        fileLabel.setStyle("-fx-font-size: 11;");
        fileLabel.setWrapText(true);

        // Page range
        Label pagesLabel = new Label(section.getPageRangeString());
        pagesLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

        // Page count
        Label countLabel = new Label(section.getPageCount() + " page" + (section.getPageCount() > 1 ? "s" : ""));
        countLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");

        getChildren().addAll(headerRow, fileLabel, pagesLabel, countLabel);

        // Click handler for selection
        setOnMouseClicked(event -> {
            // Check if click was on a button
            if (!(event.getTarget() instanceof Button)) {
                if (onSelect != null) {
                    onSelect.accept(this);
                }
            }
        });

        logger.trace("Created SectionListItem for: {} ({})", section.getSourceFile().getFileName(), section.getPageRangeString());
    }

    private Button createIconButton(String icon, String tooltip, Consumer<SectionListItem> action) {
        Button btn = new Button(icon);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666; -fx-font-size: 10; -fx-cursor: hand; -fx-min-width: 24;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333; -fx-font-size: 10; -fx-cursor: hand; -fx-min-width: 24;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666; -fx-font-size: 10; -fx-cursor: hand; -fx-min-width: 24;"));
        btn.setOnAction(e -> {
            e.consume();
            if (action != null) {
                action.accept(this);
            }
        });
        return btn;
    }

    public JoinerSection getSection() {
        return section;
    }

    public void setIndex(int index) {
        indexLabel.setText("Section " + index);
    }

    public boolean isSectionSelected() {
        return isSelected;
    }

    public void setSectionSelected(boolean selected) {
        this.isSelected = selected;
        setStyle(selected ? STYLE_SELECTED : STYLE_NORMAL);
    }
}
