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
     */
    public static Builder builder(String itemId) {
        return new Builder(itemId);
    }

    public static final class Builder {

        private final String itemId;
        private boolean enchanted = false;
        private boolean hovered = false;
        private boolean bigImage = false;
        private Optional<Double> durabilityPercent = Optional.empty();
        private Optional<Integer> dyeColor = Optional.empty();

        private Builder(String itemId) {
            this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        }

        public Builder enchanted(boolean val) {
            this.enchanted = val;
            return this;
        }

        public Builder hovered(boolean val) {
            this.hovered = val;
            return this;
        }

        public Builder bigImage(boolean val) {
            this.bigImage = val;
            return this;
        }

        public Builder durabilityPercent(double val) {
            this.durabilityPercent = Optional.of(val);
            return this;
        }

        public Builder dyeColor(int val) {
            this.dyeColor = Optional.of(val);
            return this;
        }

        public ItemRequest build() {
            return new ItemRequest(itemId, enchanted, hovered, bigImage, durabilityPercent, dyeColor);
        }
    }
}
