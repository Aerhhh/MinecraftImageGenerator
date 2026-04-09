package net.aerh.jigsaw.core.generator.body;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes an armor piece to render on a player body.
 *
 * @param slot         the armor slot this piece occupies
 * @param armorTexture the armor texture image (Minecraft's 64x32 {@code layer_1} or {@code layer_2} format)
 * @param color        optional tint color for dyeable leather armor (ARGB)
 */
public record ArmorPiece(
        ArmorSlot slot,
        BufferedImage armorTexture,
        Optional<Integer> color
) {

    public ArmorPiece {
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(armorTexture, "armorTexture must not be null");
        Objects.requireNonNull(color, "color must not be null");
    }

    /**
     * Creates an armor piece without a dye color.
     *
     * @param slot         the armor slot
     * @param armorTexture the armor texture image
     * @return a new armor piece
     */
    public static ArmorPiece of(ArmorSlot slot, BufferedImage armorTexture) {
        return new ArmorPiece(slot, armorTexture, Optional.empty());
    }

    /**
     * Creates a dyed armor piece (leather armor).
     *
     * @param slot         the armor slot
     * @param armorTexture the armor texture image
     * @param dyeColor     the dye color (ARGB)
     * @return a new armor piece with the specified color
     */
    public static ArmorPiece dyed(ArmorSlot slot, BufferedImage armorTexture, int dyeColor) {
        return new ArmorPiece(slot, armorTexture, Optional.of(dyeColor));
    }
}
