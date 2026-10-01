package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Options of the display tool: one item or player head. Defaults are those of the Discord
 * {@code /gen display} command.
 */
public record DisplayRequest(@Nullable String itemId, @Nullable String itemModel, @Nullable String data, @Nullable String color,
                             boolean enchanted, boolean hoverEffect, @Nullable String skinValue, int durability, @Nullable PackId packId) {

    public static final boolean DEFAULT_ENCHANTED = false;
    public static final boolean DEFAULT_HOVER_EFFECT = false;
    public static final int DEFAULT_DURABILITY = 100;

    public DisplayRequest {
        itemId = Strings.blankToNull(itemId);
        itemModel = Strings.blankToNull(itemModel);
        data = Strings.blankToNull(data);
        color = Strings.blankToNull(color);
        skinValue = Strings.blankToNull(skinValue);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Passing null to a defaulted option (the boxed number and boolean setters) leaves its value unchanged; passing null to an optional text or pack setter clears it. */
    public static final class Builder {
        private String itemId;
        private String itemModel;
        private String data;
        private String color;
        private boolean enchanted = DEFAULT_ENCHANTED;
        private boolean hoverEffect = DEFAULT_HOVER_EFFECT;
        private String skinValue;
        private int durability = DEFAULT_DURABILITY;
        private PackId packId;

        public Builder itemId(@Nullable String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder itemModel(@Nullable String itemModel) {
            this.itemModel = itemModel;
            return this;
        }

        public Builder data(@Nullable String data) {
            this.data = data;
            return this;
        }

        public Builder color(@Nullable String color) {
            this.color = color;
            return this;
        }

        public Builder enchanted(@Nullable Boolean enchanted) {
            this.enchanted = Objects.requireNonNullElse(enchanted, this.enchanted);
            return this;
        }

        public Builder hoverEffect(@Nullable Boolean hoverEffect) {
            this.hoverEffect = Objects.requireNonNullElse(hoverEffect, this.hoverEffect);
            return this;
        }

        public Builder skinValue(@Nullable String skinValue) {
            this.skinValue = skinValue;
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

        public DisplayRequest build() {
            return new DisplayRequest(itemId, itemModel, data, color, enchanted, hoverEffect, skinValue, durability, packId);
        }
    }
}
