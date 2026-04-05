package net.aerh.jigsaw.core.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Input request for {@link InventoryGenerator}.
 *
 * @param rows            number of item rows to render (default 6)
 * @param slotsPerRow     number of slots per row (default 9)
 * @param title           title text displayed at the top of the inventory
 * @param drawTitle       whether to draw the title bar
 * @param drawBorder      whether to draw the Minecraft-style outer/inner border
 * @param drawBackground  whether to fill the background gray
 * @param items           list of items to place in the inventory
 */
public record InventoryRequest(
        int rows,
        int slotsPerRow,
        String title,
        boolean drawTitle,
        boolean drawBorder,
        boolean drawBackground,
        List<InventoryItem> items
) {

    public InventoryRequest {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(items, "items must not be null");
        if (rows < 1) {
            throw new IllegalArgumentException("rows must be >= 1, got: " + rows);
        }
        if (slotsPerRow < 1) {
            throw new IllegalArgumentException("slotsPerRow must be >= 1, got: " + slotsPerRow);
        }
        items = List.copyOf(items);
    }

    /** Returns a builder with default values applied. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int rows = 6;
        private int slotsPerRow = 9;
        private String title = "Chest";
        private boolean drawTitle = true;
        private boolean drawBorder = true;
        private boolean drawBackground = true;
        private final List<InventoryItem> items = new ArrayList<>();

        private Builder() {}

        public Builder rows(int val) {
            this.rows = val;
            return this;
        }

        public Builder slotsPerRow(int val) {
            this.slotsPerRow = val;
            return this;
        }

        public Builder title(String val) {
            this.title = Objects.requireNonNull(val, "title must not be null");
            return this;
        }

        public Builder drawTitle(boolean val) {
            this.drawTitle = val;
            return this;
        }

        public Builder drawBorder(boolean val) {
            this.drawBorder = val;
            return this;
        }

        public Builder drawBackground(boolean val) {
            this.drawBackground = val;
            return this;
        }

        public Builder item(InventoryItem val) {
            this.items.add(Objects.requireNonNull(val, "item must not be null"));
            return this;
        }

        public Builder items(List<InventoryItem> val) {
            this.items.addAll(Objects.requireNonNull(val, "items must not be null"));
            return this;
        }

        public InventoryRequest build() {
            return new InventoryRequest(rows, slotsPerRow, title, drawTitle, drawBorder, drawBackground, items);
        }
    }
}
