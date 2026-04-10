package net.aerh.jigsaw.core.generator.player;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes a full set of armor to render on a player model.
 *
 * <p>Each slot holds an optional material name (e.g. "iron", "diamond", "leather").
 * Empty slots have no armor rendered.
 *
 * <p>For leather armor, an optional dye color can be specified. The dye color is
 * applied to the leather base texture via per-pixel RGB multiplication, and the
 * non-dyeable overlay (buckles, trim) is composited on top untinted.
 *
 * @param helmet          material name for the helmet slot
 * @param chestplate      material name for the chestplate slot
 * @param leggings        material name for the leggings slot
 * @param boots           material name for the boots slot
 * @param leatherDyeColor optional RGB dye color for leather armor pieces
 */
public record ArmorSet(
        Optional<String> helmet,
        Optional<String> chestplate,
        Optional<String> leggings,
        Optional<String> boots,
        Optional<Integer> leatherDyeColor
) {

    public ArmorSet {
        Objects.requireNonNull(helmet, "helmet must not be null");
        Objects.requireNonNull(chestplate, "chestplate must not be null");
        Objects.requireNonNull(leggings, "leggings must not be null");
        Objects.requireNonNull(boots, "boots must not be null");
        Objects.requireNonNull(leatherDyeColor, "leatherDyeColor must not be null");
    }

    /**
     * Returns the material name for the given armor piece, or empty if not equipped.
     */
    public Optional<String> materialFor(ArmorPiece piece) {
        return switch (piece) {
            case HELMET -> helmet;
            case CHESTPLATE -> chestplate;
            case LEGGINGS -> leggings;
            case BOOTS -> boots;
        };
    }

    /** Returns a builder for constructing an {@link ArmorSet}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ArmorSet}.
     */
    public static final class Builder {
        private Optional<String> helmet = Optional.empty();
        private Optional<String> chestplate = Optional.empty();
        private Optional<String> leggings = Optional.empty();
        private Optional<String> boots = Optional.empty();
        private Optional<Integer> leatherDyeColor = Optional.empty();

        private Builder() {}

        public Builder helmet(String material) {
            this.helmet = Optional.of(Objects.requireNonNull(material));
            return this;
        }

        public Builder chestplate(String material) {
            this.chestplate = Optional.of(Objects.requireNonNull(material));
            return this;
        }

        public Builder leggings(String material) {
            this.leggings = Optional.of(Objects.requireNonNull(material));
            return this;
        }

        public Builder boots(String material) {
            this.boots = Optional.of(Objects.requireNonNull(material));
            return this;
        }

        public Builder leatherDyeColor(int color) {
            this.leatherDyeColor = Optional.of(color);
            return this;
        }

        public ArmorSet build() {
            return new ArmorSet(helmet, chestplate, leggings, boots, leatherDyeColor);
        }
    }
}
