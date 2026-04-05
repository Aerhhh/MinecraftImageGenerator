package net.aerh.jigsaw.core.generator;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes a single item placed in an inventory slot.
 *
 * @param slot               zero-based slot index
 * @param itemId             Minecraft item ID (e.g. {@code "diamond_sword"})
 * @param enchanted          whether the enchantment glint should be applied
 * @param stackCount         number of items in the stack (1-64); only drawn when &gt; 1
 * @param durabilityPercent  optional durability fraction in {@code [0.0, 1.0]}
 * @param playerHeadTexture  optional Base64-encoded player skin texture for skull items
 */
public record InventoryItem(
        int slot,
        String itemId,
        boolean enchanted,
        int stackCount,
        Optional<Double> durabilityPercent,
        Optional<String> playerHeadTexture
) {

    public InventoryItem {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(durabilityPercent, "durabilityPercent must not be null");
        Objects.requireNonNull(playerHeadTexture, "playerHeadTexture must not be null");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be >= 0, got: " + slot);
        }
        if (stackCount < 1 || stackCount > 64) {
            throw new IllegalArgumentException("stackCount must be in [1, 64], got: " + stackCount);
        }
    }

    /**
     * Returns a builder for the given item ID in the given slot.
     */
    public static Builder builder(int slot, String itemId) {
        return new Builder(slot, itemId);
    }

    public static final class Builder {

        private final int slot;
        private final String itemId;
        private boolean enchanted = false;
        private int stackCount = 1;
        private Optional<Double> durabilityPercent = Optional.empty();
        private Optional<String> playerHeadTexture = Optional.empty();

        private Builder(int slot, String itemId) {
            this.slot = slot;
            this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        }

        public Builder enchanted(boolean val) {
            this.enchanted = val;
            return this;
        }

        public Builder stackCount(int val) {
            this.stackCount = val;
            return this;
        }

        public Builder durabilityPercent(double val) {
            this.durabilityPercent = Optional.of(val);
            return this;
        }

        public Builder playerHeadTexture(String val) {
            this.playerHeadTexture = Optional.of(val);
            return this;
        }

        public InventoryItem build() {
            return new InventoryItem(slot, itemId, enchanted, stackCount, durabilityPercent, playerHeadTexture);
        }
    }
}
