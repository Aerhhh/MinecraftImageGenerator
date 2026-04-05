package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.RenderRequest;

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
) implements RenderRequest {

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

    /**
     * Builder for {@link InventoryRequest}.
     */
    public static final class Builder {

        private int rows = 6;
        private int slotsPerRow = 9;
        private String title = "Chest";
        private boolean drawTitle = true;
        private boolean drawBorder = true;
        private boolean drawBackground = true;
        private final List<InventoryItem> items = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the number of item rows.
         *
         * @param val the row count; must be {@code >= 1}
         *
         * @return this builder for chaining
         */
        public Builder rows(int val) {
            this.rows = val;
            return this;
        }

        /**
         * Sets the number of slots per row.
         *
         * @param val the slot count per row; must be {@code >= 1}
         * @return this builder for chaining
         */
        public Builder slotsPerRow(int val) {
            this.slotsPerRow = val;
            return this;
        }

        /**
         * Sets the inventory title text.
         *
         * @param val the title; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder title(String val) {
            this.title = Objects.requireNonNull(val, "title must not be null");
            return this;
        }

        /**
         * Sets whether to draw the title bar above the inventory slots.
         *
         * @param val {@code true} to draw the title
         * @return this builder for chaining
         */
        public Builder drawTitle(boolean val) {
            this.drawTitle = val;
            return this;
        }

        /**
         * Sets whether to draw the Minecraft-style outer/inner border.
         *
         * @param val {@code true} to draw the border
         * @return this builder for chaining
         */
        public Builder drawBorder(boolean val) {
            this.drawBorder = val;
            return this;
        }

        /**
         * Sets whether to fill the inventory background with gray.
         *
         * @param val {@code true} to draw the background
         * @return this builder for chaining
         */
        public Builder drawBackground(boolean val) {
            this.drawBackground = val;
            return this;
        }

        /**
         * Adds a single item to the inventory.
         *
         * @param val the item to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder item(InventoryItem val) {
            this.items.add(Objects.requireNonNull(val, "item must not be null"));
            return this;
        }

        /**
         * Adds all items from the given list.
         *
         * @param val the items to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder items(List<InventoryItem> val) {
            this.items.addAll(Objects.requireNonNull(val, "items must not be null"));
            return this;
        }

        /**
         * Parses a {@code %%}-delimited inventory string and adds the resulting items to the
         * item list.
         *
         * <p>The format is described in detail in {@link InventoryStringParser}. Each token in the
         * string is {@code material[,modifier...]:slotSpec}. Multi-slot tokens are expanded into
         * one {@link InventoryItem} per slot.
         *
         * @param inventoryString the inventory string to parse; must not be {@code null} or blank
         * @return this builder for chaining
         * @throws IllegalArgumentException if the string contains malformed tokens
         */
        public Builder withInventoryString(String inventoryString) {
            Objects.requireNonNull(inventoryString, "inventoryString must not be null");
            if (inventoryString.isBlank()) {
                return this;
            }
            int totalSlots = rows * slotsPerRow;
            InventoryStringParser parser = new InventoryStringParser(totalSlots);
            this.items.addAll(parser.parse(inventoryString));
            return this;
        }

        /**
         * Builds the {@link InventoryRequest}.
         *
         * @return a new request
         */
        public InventoryRequest build() {
            return new InventoryRequest(rows, slotsPerRow, title, drawTitle, drawBorder, drawBackground, items);
        }
    }
}
