package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Required;
import net.aerh.imagegenerator.tools.support.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Options of the inventory tool. Defaults are those of the Discord {@code /gen inventory} command;
 * {@code animateGlint} was a bot config value and is an explicit option here.
 */
public record InventoryRequest(int rows, int slotsPerRow, String inventoryString, @Nullable String hoveredItemString,
                               @Nullable String containerName, boolean drawBorder, int maxLineLength, boolean animateGlint,
                               @Nullable PackId packId) {

    public static final boolean DEFAULT_DRAW_BORDER = true;
    public static final int DEFAULT_MAX_LINE_LENGTH = MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
    public static final boolean DEFAULT_ANIMATE_GLINT = false;

    public InventoryRequest {
        Required.check(inventoryString, "inventory_string");
        if (rows <= 0) {
            throw new GeneratorValidationException("rows must be positive");
        }
        if (slotsPerRow <= 0) {
            throw new GeneratorValidationException("slotsPerRow must be positive");
        }
        hoveredItemString = Strings.blankToNull(hoveredItemString);
        containerName = Strings.blankToNull(containerName);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Passing null to a defaulted option (the boxed number and boolean setters) leaves its value unchanged; passing null to an optional text or pack setter clears it. */
    public static final class Builder {
        private int rows;
        private int slotsPerRow;
        private String inventoryString;
        private String hoveredItemString;
        private String containerName;
        private boolean drawBorder = DEFAULT_DRAW_BORDER;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private boolean animateGlint = DEFAULT_ANIMATE_GLINT;
        private PackId packId;

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder slotsPerRow(int slotsPerRow) {
            this.slotsPerRow = slotsPerRow;
            return this;
        }

        public Builder inventoryString(String inventoryString) {
            this.inventoryString = inventoryString;
            return this;
        }

        public Builder hoveredItemString(@Nullable String hoveredItemString) {
            this.hoveredItemString = hoveredItemString;
            return this;
        }

        public Builder containerName(@Nullable String containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder drawBorder(@Nullable Boolean drawBorder) {
            this.drawBorder = Objects.requireNonNullElse(drawBorder, this.drawBorder);
            return this;
        }

        public Builder maxLineLength(@Nullable Integer maxLineLength) {
            this.maxLineLength = Objects.requireNonNullElse(maxLineLength, this.maxLineLength);
            return this;
        }

        public Builder animateGlint(@Nullable Boolean animateGlint) {
            this.animateGlint = Objects.requireNonNullElse(animateGlint, this.animateGlint);
            return this;
        }

        public Builder packId(@Nullable PackId packId) {
            this.packId = packId;
            return this;
        }

        public InventoryRequest build() {
            return new InventoryRequest(rows, slotsPerRow, inventoryString, hoveredItemString, containerName,
                drawBorder, maxLineLength, animateGlint, packId);
        }
    }
}
