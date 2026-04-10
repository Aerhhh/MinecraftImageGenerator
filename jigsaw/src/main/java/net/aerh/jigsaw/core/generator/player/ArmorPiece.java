package net.aerh.jigsaw.core.generator.player;

import java.util.List;

/**
 * The four Minecraft armor equipment slots.
 *
 * <p>Each piece knows which body parts it covers, which texture layer to use,
 * and how much to inflate the cuboid relative to the body.
 *
 * <p>Minecraft uses two armor texture layers (both 64x32):
 * <ul>
 *   <li>Layer 1: helmet, chestplate (body + arms), boots</li>
 *   <li>Layer 2: leggings</li>
 * </ul>
 *
 * <p>Inflation values match Minecraft's {@code HumanoidModel}:
 * <ul>
 *   <li>Outer model (layer 1): 1.0 pixel per side = 0.25 renderer units</li>
 *   <li>Inner model (layer 2): 0.5 pixel per side = 0.125 renderer units</li>
 * </ul>
 */
public enum ArmorPiece {

    /** Covers the head, uses layer 1 texture. */
    HELMET(1, List.of(BodyPart.HEAD), PlayerModelSettings.OUTER_ARMOR_INFLATION),

    /** Covers the body and both arms, uses layer 1 texture. */
    CHESTPLATE(1, List.of(BodyPart.BODY, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM),
            PlayerModelSettings.OUTER_ARMOR_INFLATION),

    /** Covers both legs, uses layer 2 texture with inner (smaller) inflation. */
    LEGGINGS(2, List.of(BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG),
            PlayerModelSettings.INNER_ARMOR_INFLATION),

    /** Covers both legs (lower portion), uses layer 1 texture. */
    BOOTS(1, List.of(BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG),
            PlayerModelSettings.OUTER_ARMOR_INFLATION);

    private final int textureLayer;
    private final List<BodyPart> coveredParts;
    private final double inflation;

    ArmorPiece(int textureLayer, List<BodyPart> coveredParts, double inflation) {
        this.textureLayer = textureLayer;
        this.coveredParts = coveredParts;
        this.inflation = inflation;
    }

    /** Returns the armor texture layer number (1 or 2). */
    public int textureLayer() { return textureLayer; }

    /** Returns the body parts this armor piece covers. */
    public List<BodyPart> coveredParts() { return coveredParts; }

    /** Returns the cuboid inflation in renderer units. */
    public double inflation() { return inflation; }

    /**
     * Returns whether the armor texture should be mirrored for the given body part.
     * Left-side pieces (left arm, left leg) mirror the right-side armor texture.
     */
    public boolean isMirrored(BodyPart part) {
        return part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
    }
}
