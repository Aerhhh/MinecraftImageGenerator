package net.aerh.jigsaw.core.generator.body;

/**
 * The four Minecraft armor equipment slots.
 *
 * <p>Each slot determines which body parts are covered and which armor texture layer
 * ({@code layer_1} or {@code layer_2}) provides the UV data.
 */
public enum ArmorSlot {

    /** Covers the head. Uses armor {@code layer_1} texture. */
    HELMET,

    /** Covers the torso and both arms. Uses armor {@code layer_1} texture. */
    CHESTPLATE,

    /** Covers both legs and the waist region. Uses armor {@code layer_2} texture. */
    LEGGINGS,

    /** Covers the lower portion of both legs. Uses armor {@code layer_1} texture. */
    BOOTS
}
