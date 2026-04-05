package net.aerh.jigsaw.core.generator;

import java.util.Objects;
import java.util.Optional;

/**
 * Input request for the {@link ItemGenerator}.
 *
 * @param itemId            the Minecraft item ID (e.g. {@code "diamond_sword"})
 * @param enchanted         whether to apply the enchantment glint effect
 * @param hovered           whether to apply the hover highlight effect
 * @param bigImage          whether to upscale the image (10x)
 * @param durabilityPercent optional durability fraction in {@code [0.0, 1.0]}
 * @param dyeColor          optional packed RGB dye color for leather armor
 */
public record ItemRequest(
        String itemId,
        boolean enchanted,
        boolean hovered,
        boolean bigImage,
        Optional<Double> durabilityPercent,
        Optional<Integer> dyeColor
) {

    public ItemRequest {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(durabilityPercent, "durabilityPercent must not be null");
        Objects.requireNonNull(dyeColor, "dyeColor must not be null");
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
        private boolean bigImage = false;
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
         * Sets whether to upscale the image by 10x.
         *
         * @param val {@code true} to upscale
         * @return this builder for chaining
         */
        public Builder bigImage(boolean val) {
            this.bigImage = val;
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
         * Builds the {@link ItemRequest}.
         *
         * @return a new request
         * @throws NullPointerException if {@code itemId} has not been set
         */
        public ItemRequest build() {
            Objects.requireNonNull(itemId, "itemId must not be null");
            return new ItemRequest(itemId, enchanted, hovered, bigImage, durabilityPercent, dyeColor);
        }
    }
}
