package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Required;
import net.aerh.imagegenerator.tools.support.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Options of the item tool: a tooltip with an optional item or head and an optional recipe grid.
 * Defaults are those of the Discord {@code /gen item} command.
 */
public record ItemRequest(String itemName, String itemLore, String type, String rarity,
                          @Nullable String itemId, @Nullable String itemModel, @Nullable String color, @Nullable String skinValue,
                          @Nullable String recipe, int alpha, int padding, boolean enchanted, boolean centered, boolean firstLinePadding,
                          int maxLineLength, MinecraftTooltipGenerator.TooltipSide tooltipSide, boolean renderBorder, int durability,
                          @Nullable PackId packId, @Nullable String tooltipStyle) {

    public static final String DEFAULT_TYPE = "";
    public static final String DEFAULT_RARITY = "none";
    public static final int DEFAULT_ALPHA = MinecraftTooltip.DEFAULT_ALPHA;
    public static final int DEFAULT_PADDING = MinecraftTooltip.DEFAULT_PADDING;
    public static final boolean DEFAULT_ENCHANTED = false;
    public static final boolean DEFAULT_CENTERED = false;
    public static final boolean DEFAULT_FIRST_LINE_PADDING = true;
    public static final int DEFAULT_MAX_LINE_LENGTH = MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
    public static final MinecraftTooltipGenerator.TooltipSide DEFAULT_TOOLTIP_SIDE = MinecraftTooltipGenerator.TooltipSide.RIGHT;
    public static final boolean DEFAULT_RENDER_BORDER = true;
    public static final int DEFAULT_DURABILITY = 100;

    public ItemRequest {
        Required.check(itemName, "item_name");
        Required.check(itemLore, "item_lore");
        Required.check(type, "type");
        Required.check(rarity, "rarity");
        Required.check(tooltipSide, "tooltip_side");
        itemId = Strings.blankToNull(itemId);
        itemModel = Strings.blankToNull(itemModel);
        color = Strings.blankToNull(color);
        skinValue = Strings.blankToNull(skinValue);
        recipe = Strings.blankToNull(recipe);
        tooltipStyle = Strings.blankToNull(tooltipStyle);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Parses a side name case-insensitively; anything unrecognised is the default side, as in the bot. */
    public static MinecraftTooltipGenerator.TooltipSide parseTooltipSide(@Nullable String side) {
        if (side == null) {
            return DEFAULT_TOOLTIP_SIDE;
        }

        try {
            return MinecraftTooltipGenerator.TooltipSide.valueOf(side.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_TOOLTIP_SIDE;
        }
    }

    /** Passing null to a defaulted option (the boxed number and boolean setters, and type, rarity and tooltipSide) leaves its value unchanged; passing null to an optional text or pack setter clears it. */
    public static final class Builder {
        private String itemName;
        private String itemLore;
        private String type = DEFAULT_TYPE;
        private String rarity = DEFAULT_RARITY;
        private String itemId;
        private String itemModel;
        private String color;
        private String skinValue;
        private String recipe;
        private int alpha = DEFAULT_ALPHA;
        private int padding = DEFAULT_PADDING;
        private boolean enchanted = DEFAULT_ENCHANTED;
        private boolean centered = DEFAULT_CENTERED;
        private boolean firstLinePadding = DEFAULT_FIRST_LINE_PADDING;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private MinecraftTooltipGenerator.TooltipSide tooltipSide = DEFAULT_TOOLTIP_SIDE;
        private boolean renderBorder = DEFAULT_RENDER_BORDER;
        private int durability = DEFAULT_DURABILITY;
        private PackId packId;
        private String tooltipStyle;

        public Builder itemName(String itemName) {
            this.itemName = itemName;
            return this;
        }

        public Builder itemLore(String itemLore) {
            this.itemLore = itemLore;
            return this;
        }

        public Builder type(@Nullable String type) {
            this.type = Objects.requireNonNullElse(type, this.type);
            return this;
        }

        public Builder rarity(@Nullable String rarity) {
            this.rarity = Objects.requireNonNullElse(rarity, this.rarity);
            return this;
        }

        public Builder itemId(@Nullable String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder itemModel(@Nullable String itemModel) {
            this.itemModel = itemModel;
            return this;
        }

        public Builder color(@Nullable String color) {
            this.color = color;
            return this;
        }

        public Builder skinValue(@Nullable String skinValue) {
            this.skinValue = skinValue;
            return this;
        }

        public Builder recipe(@Nullable String recipe) {
            this.recipe = recipe;
            return this;
        }

        public Builder alpha(@Nullable Integer alpha) {
            this.alpha = Objects.requireNonNullElse(alpha, this.alpha);
            return this;
        }

        public Builder padding(@Nullable Integer padding) {
            this.padding = Objects.requireNonNullElse(padding, this.padding);
            return this;
        }

        public Builder enchanted(@Nullable Boolean enchanted) {
            this.enchanted = Objects.requireNonNullElse(enchanted, this.enchanted);
            return this;
        }

        public Builder centered(@Nullable Boolean centered) {
            this.centered = Objects.requireNonNullElse(centered, this.centered);
            return this;
        }

        public Builder firstLinePadding(@Nullable Boolean firstLinePadding) {
            this.firstLinePadding = Objects.requireNonNullElse(firstLinePadding, this.firstLinePadding);
            return this;
        }

        public Builder maxLineLength(@Nullable Integer maxLineLength) {
            this.maxLineLength = Objects.requireNonNullElse(maxLineLength, this.maxLineLength);
            return this;
        }

        /** Accepts the raw option text; see {@link ItemRequest#parseTooltipSide(String)}. Null leaves the side unchanged. */
        public Builder tooltipSide(@Nullable String tooltipSide) {
            if (tooltipSide != null) {
                this.tooltipSide = parseTooltipSide(tooltipSide);
            }
            return this;
        }

        public Builder tooltipSide(@Nullable MinecraftTooltipGenerator.TooltipSide tooltipSide) {
            this.tooltipSide = Objects.requireNonNullElse(tooltipSide, this.tooltipSide);
            return this;
        }

        public Builder renderBorder(@Nullable Boolean renderBorder) {
            this.renderBorder = Objects.requireNonNullElse(renderBorder, this.renderBorder);
            return this;
        }

        public Builder durability(@Nullable Integer durability) {
            this.durability = Objects.requireNonNullElse(durability, this.durability);
            return this;
        }

        public Builder packId(@Nullable PackId packId) {
            this.packId = packId;
            return this;
        }

        public Builder tooltipStyle(@Nullable String tooltipStyle) {
            this.tooltipStyle = tooltipStyle;
            return this;
        }

        public ItemRequest build() {
            return new ItemRequest(itemName, itemLore, type, rarity, itemId, itemModel, color, skinValue, recipe,
                alpha, padding, enchanted, centered, firstLinePadding, maxLineLength, tooltipSide, renderBorder,
                durability, packId, tooltipStyle);
        }
    }
}
