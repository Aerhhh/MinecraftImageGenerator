package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Required;
import net.aerh.imagegenerator.tools.support.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Options of the power stone tool. Defaults are those of the Discord {@code /gen powerstone}
 * command. Stat strings are {@code stat:value} pairs separated by commas.
 */
public record PowerStoneRequest(String powerName, String powerStrength, int magicalPower, @Nullable String scalingStats,
                                @Nullable String uniqueBonus, @Nullable String itemId, @Nullable String color, @Nullable String skinValue,
                                int alpha, int padding, boolean selected, boolean enchanted, @Nullable PackId packId) {

    public static final int DEFAULT_ALPHA = MinecraftTooltip.DEFAULT_ALPHA;
    public static final int DEFAULT_PADDING = MinecraftTooltip.DEFAULT_PADDING;
    public static final boolean DEFAULT_SELECTED = true;
    public static final boolean DEFAULT_ENCHANTED = false;

    public PowerStoneRequest {
        Required.check(powerName, "power_name");
        Required.check(powerStrength, "power_strength");
        scalingStats = Strings.blankToNull(scalingStats);
        uniqueBonus = Strings.blankToNull(uniqueBonus);
        itemId = Strings.blankToNull(itemId);
        color = Strings.blankToNull(color);
        skinValue = Strings.blankToNull(skinValue);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Passing null to a defaulted option (the boxed number and boolean setters) leaves its value unchanged; passing null to an optional text or pack setter clears it. */
    public static final class Builder {
        private String powerName;
        private String powerStrength;
        private int magicalPower;
        private String scalingStats;
        private String uniqueBonus;
        private String itemId;
        private String color;
        private String skinValue;
        private int alpha = DEFAULT_ALPHA;
        private int padding = DEFAULT_PADDING;
        private boolean selected = DEFAULT_SELECTED;
        private boolean enchanted = DEFAULT_ENCHANTED;
        private PackId packId;

        public Builder powerName(String powerName) {
            this.powerName = powerName;
            return this;
        }

        public Builder powerStrength(String powerStrength) {
            this.powerStrength = powerStrength;
            return this;
        }

        public Builder magicalPower(int magicalPower) {
            this.magicalPower = magicalPower;
            return this;
        }

        public Builder scalingStats(@Nullable String scalingStats) {
            this.scalingStats = scalingStats;
            return this;
        }

        public Builder uniqueBonus(@Nullable String uniqueBonus) {
            this.uniqueBonus = uniqueBonus;
            return this;
        }

        public Builder itemId(@Nullable String itemId) {
            this.itemId = itemId;
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

        public Builder alpha(@Nullable Integer alpha) {
            this.alpha = Objects.requireNonNullElse(alpha, this.alpha);
            return this;
        }

        public Builder padding(@Nullable Integer padding) {
            this.padding = Objects.requireNonNullElse(padding, this.padding);
            return this;
        }

        public Builder selected(@Nullable Boolean selected) {
            this.selected = Objects.requireNonNullElse(selected, this.selected);
            return this;
        }

        public Builder enchanted(@Nullable Boolean enchanted) {
            this.enchanted = Objects.requireNonNullElse(enchanted, this.enchanted);
            return this;
        }

        public Builder packId(@Nullable PackId packId) {
            this.packId = packId;
            return this;
        }

        public PowerStoneRequest build() {
            return new PowerStoneRequest(powerName, powerStrength, magicalPower, scalingStats, uniqueBonus, itemId, color,
                skinValue, alpha, padding, selected, enchanted, packId);
        }
    }
}
