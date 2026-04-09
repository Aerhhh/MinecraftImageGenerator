package net.aerh.jigsaw.core.generator.body;

import java.util.List;

/**
 * Maps armor slots to the body parts they cover, with UV origins on the armor texture
 * and cuboid inflation values.
 *
 * <p>Minecraft uses two armor texture layers (both 64x32):
 * <ul>
 *   <li>{@code layer_1} - helmet, chestplate (body + arms), boots</li>
 *   <li>{@code layer_2} - leggings (legs + waist)</li>
 * </ul>
 *
 * <p>The UV layout on armor textures follows the same cross pattern as skin textures:
 * the head section is at (0,0), body at (16,16), right arm at (40,16), right leg at (0,16).
 * Left-side pieces mirror the right side.
 */
public final class ArmorLayer {

    /** Inflation added to each half-extent for outer armor (helmet, chestplate, boots). */
    public static final double OUTER_INFLATION = 0.25;

    /** Inflation added to each half-extent for inner armor (leggings). */
    public static final double INNER_INFLATION = 0.125;

    /** Small inward shift for arm armor to close the shoulder seam with the body armor. */
    private static final double ARM_INWARD_NUDGE = 0.0625;

    private ArmorLayer() {}

    /**
     * Returns the armor cuboid mappings for the given slot and skin model.
     *
     * <p>Each mapping describes a body part covered by the armor, the UV origin on the
     * armor texture, the cuboid pixel dimensions, and whether the texture should be
     * mirrored for left-side pieces.
     *
     * @param slot  the armor slot
     * @param model the skin model (affects arm width for chestplate)
     * @return list of armor cuboid mappings
     */
    public static List<ArmorMapping> mappingsFor(ArmorSlot slot, SkinModel model) {
        return switch (slot) {
            case HELMET -> List.of(
                    new ArmorMapping(BodyPart.HEAD, 0, 0, 8, 8, 8, OUTER_INFLATION, false, 0)
            );
            case CHESTPLATE -> List.of(
                    // Armor arm textures are always 4px wide regardless of skin model.
                    // Arms are nudged slightly inward to close the shoulder seam with the body.
                    new ArmorMapping(BodyPart.BODY, 16, 16, 8, 12, 4, OUTER_INFLATION, false, 0),
                    new ArmorMapping(BodyPart.RIGHT_ARM, 40, 16, 4, 12, 4, OUTER_INFLATION, false, ARM_INWARD_NUDGE),
                    new ArmorMapping(BodyPart.LEFT_ARM, 40, 16, 4, 12, 4, OUTER_INFLATION, true, -ARM_INWARD_NUDGE)
            );
            case LEGGINGS -> List.of(
                    new ArmorMapping(BodyPart.RIGHT_LEG, 0, 16, 4, 12, 4, INNER_INFLATION, false, 0),
                    new ArmorMapping(BodyPart.LEFT_LEG, 0, 16, 4, 12, 4, INNER_INFLATION, true, 0)
            );
            case BOOTS -> List.of(
                    new ArmorMapping(BodyPart.RIGHT_LEG, 0, 16, 4, 12, 4, OUTER_INFLATION, false, 0),
                    new ArmorMapping(BodyPart.LEFT_LEG, 0, 16, 4, 12, 4, OUTER_INFLATION, true, 0)
            );
        };
    }

    /**
     * Describes how an armor piece maps to a single body part cuboid.
     *
     * @param bodyPart   the body part this armor covers
     * @param uvX        UV origin x on the armor texture
     * @param uvY        UV origin y on the armor texture
     * @param pixelWidth pixel width of the cuboid on the armor texture
     * @param pixelHeight pixel height of the cuboid
     * @param pixelDepth pixel depth of the cuboid
     * @param inflation  amount to inflate each half-extent (in renderer units)
     * @param mirrored   whether to mirror the texture horizontally (for left-side pieces)
     * @param offsetX    additional X offset to nudge the cuboid (used to close shoulder seams)
     */
    public record ArmorMapping(
            BodyPart bodyPart,
            int uvX, int uvY,
            int pixelWidth, int pixelHeight, int pixelDepth,
            double inflation,
            boolean mirrored,
            double offsetX
    ) {}
}
