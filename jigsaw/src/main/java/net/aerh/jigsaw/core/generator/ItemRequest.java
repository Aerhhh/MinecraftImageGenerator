package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.RenderRequest;
import net.aerh.jigsaw.api.text.ChatColor;

import java.util.Objects;
import java.util.Optional;

/**
 * Input request for the {@link ItemGenerator}.
 *
 * @param itemId            the Minecraft item ID (e.g. {@code "diamond_sword"})
 * @param enchanted         whether to apply the enchantment glint effect
 * @param hovered           whether to apply the hover highlight effect
 * @param scale             integer scale multiplier applied to the item texture; must be in {@code [1, 64]}
 * @param durabilityPercent optional durability fraction in {@code [0.0, 1.0]}
 * @param dyeColor          optional packed RGB dye color for leather armor
 */
public record ItemRequest(
        String itemId,
        boolean enchanted,
        boolean hovered,
        int scale,
        Optional<Double> durabilityPercent,
        Optional<Integer> dyeColor
) implements RenderRequest {

    public ItemRequest {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(durabilityPercent, "durabilityPercent must not be null");
        Objects.requireNonNull(dyeColor, "dyeColor must not be null");
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be >= 1, got: " + scale);
        }
        if (scale > 64) {
            throw new IllegalArgumentException("scale must be <= 64, got: " + scale);
        }
    }

    /**
     * Returns a builder for constructing an {@link ItemRequest}.
     * Call {@link Builder#itemId(String)} to set the required item ID before building.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ItemRequest}.
     */
    public static final class Builder {

        private String itemId;
        private boolean enchanted = false;
        private boolean hovered = false;
        private int scale = 1;
        private Optional<Double> durabilityPercent = Optional.empty();
        private Optional<Integer> dyeColor = Optional.empty();

        private Builder() {
        }

        /**
         * Sets the Minecraft item ID (e.g. {@code "diamond_sword"}).
         *
         * @param val the item ID; must not be {@code null}
         *
         * @return this builder for chaining
         */
        public Builder itemId(String val) {
            this.itemId = Objects.requireNonNull(val, "itemId must not be null");
            return this;
        }

        /**
         * Sets whether to apply the enchantment glint effect.
         *
         * @param val {@code true} to apply the glint
         *
         * @return this builder for chaining
         */
        public Builder enchanted(boolean val) {
            this.enchanted = val;
            return this;
        }

        /**
         * Sets whether to apply the hover highlight effect.
         *
         * @param val {@code true} to apply the hover highlight
         * @return this builder for chaining
         */
        public Builder hovered(boolean val) {
            this.hovered = val;
            return this;
        }

        /**
         * Sets the integer scale multiplier applied to the item texture.
         * Values are clamped to {@code [1, 64]}.
         *
         * @param val the scale factor; must be in {@code [1, 64]}
         * @return this builder for chaining
         */
        public Builder scale(int val) {
            this.scale = Math.max(1, Math.min(64, val));
            return this;
        }

        /**
         * Sets the optional durability fraction ({@code [0.0, 1.0]}).
         *
         * @param val the durability fraction
         * @return this builder for chaining
         */
        public Builder durabilityPercent(double val) {
            this.durabilityPercent = Optional.of(val);
            return this;
        }

        /**
         * Sets the optional packed RGB dye color for leather armor.
         *
         * @param val the packed RGB color
         * @return this builder for chaining
         */
        public Builder dyeColor(int val) {
            this.dyeColor = Optional.of(val);
            return this;
        }

        /**
         * Sets the dye color from a named Minecraft color or a hex string.
         *
         * <p>If {@code nameOrHex} starts with {@code #}, it is parsed as a 6-digit hex RGB value
         * (e.g. {@code "#FF0000"} for red). Otherwise it is looked up case-insensitively against
         * the {@link ChatColor} enum names (e.g. {@code "red"}, {@code "dark_blue"}).
         *
         * @param nameOrHex the color name or hex string; must not be {@code null}
         * @return this builder for chaining
         * @throws IllegalArgumentException if the name is not recognized or the hex string is malformed
         */
        public Builder color(String nameOrHex) {
            this.dyeColor = Optional.of(resolveColor(
                    Objects.requireNonNull(nameOrHex, "nameOrHex must not be null")));
            return this;
        }

        /**
         * Builds the {@link ItemRequest}.
         *
         * @return a new request
         * @throws NullPointerException if {@code itemId} has not been set
         */
        public ItemRequest build() {
            Objects.requireNonNull(itemId, "itemId must not be null");
            return new ItemRequest(itemId, enchanted, hovered, scale, durabilityPercent, dyeColor);
        }

        /**
         * Resolves a color name or hex string to a packed RGB integer.
         *
         * @param nameOrHex the color name or hex string
         * @return the packed RGB integer (no alpha component)
         * @throws IllegalArgumentException if the value cannot be resolved
         */
        private static int resolveColor(String nameOrHex) {
            if (nameOrHex.startsWith("#")) {
                try {
                    return Integer.parseInt(nameOrHex.substring(1), 16);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid hex color: " + nameOrHex, e);
                }
            }
            ChatColor color = ChatColor.byName(nameOrHex);
            if (color != null) {
                return color.color().getRGB() & 0xFFFFFF;
            }
            throw new IllegalArgumentException("Unknown color: " + nameOrHex);
        }
    }
}
