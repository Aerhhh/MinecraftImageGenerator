package net.aerh.jigsaw.core.generator.body;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes an armor piece by material name, to be resolved at render time via an
 * {@link ArmorTextureProvider}.
 *
 * <p>This is the unresolved counterpart to {@link ArmorPiece}, which holds a pre-loaded texture.
 * Use this when you want the engine to look up the armor texture from its configured resource pack.
 *
 * @param slot     the armor slot
 * @param material the armor material name (e.g. "iron", "diamond", "leather")
 * @param dyeColor optional dye color for leather armor (ARGB)
 */
public record ArmorMaterialRequest(
        ArmorSlot slot,
        String material,
        Optional<Integer> dyeColor
) {

    public ArmorMaterialRequest {
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(material, "material must not be null");
        Objects.requireNonNull(dyeColor, "dyeColor must not be null");
    }

    /**
     * Creates an armor material request without a dye color.
     */
    public static ArmorMaterialRequest of(ArmorSlot slot, String material) {
        return new ArmorMaterialRequest(slot, material, Optional.empty());
    }

    /**
     * Creates an armor material request with a dye color (for leather armor).
     */
    public static ArmorMaterialRequest dyed(ArmorSlot slot, String material, int dyeColor) {
        return new ArmorMaterialRequest(slot, material, Optional.of(dyeColor));
    }
}
