package net.aerh.imagegenerator.text;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextColorRemapTest {

    // Hypixel pack values: dark_red 0xAA0000 -> 0xD13228, whose derived shadow is 0x340C0A.
    private static final TextColorRemap DARK_RED_REMAP =
        TextColorRemap.builder().remap(0xAA0000, 0xD13228).build();

    @Test
    void remapsMatchingForeground() {
        assertEquals(new Color(0xD13228), DARK_RED_REMAP.foreground(new Color(0xAA0000)));
    }

    @Test
    void passesThroughUnmappedForeground() {
        assertEquals(new Color(0x55FF55), DARK_RED_REMAP.foreground(new Color(0x55FF55)));
    }

    @Test
    void derivesShadowByQuarteringTheReplacement() {
        // (0xD13228 >> 2) & 0x3F3F3F = 0x340C0A, the exact vanilla shadow derivation.
        assertEquals(new Color(0x340C0A),
            DARK_RED_REMAP.shadow(new Color(0xAA0000), new Color(0x2A0000)));
    }

    @Test
    void explicitShadowOverrideWins() {
        TextColorRemap remap = TextColorRemap.builder().remap(0xAA0000, 0xD13228, 0x123456).build();
        assertEquals(new Color(0x123456), remap.shadow(new Color(0xAA0000), new Color(0x2A0000)));
    }

    @Test
    void unmappedForegroundKeepsOriginalShadow() {
        assertEquals(new Color(0x153F15),
            DARK_RED_REMAP.shadow(new Color(0x55FF55), new Color(0x153F15)));
    }

    @Test
    void preservesAlphaOnRemappedColors() {
        Color translucent = new Color(0xAA, 0x00, 0x00, 128);
        Color remapped = DARK_RED_REMAP.foreground(translucent);
        assertEquals(0xD13228, remapped.getRGB() & 0xFFFFFF);
        assertEquals(128, remapped.getAlpha());
    }

    @Test
    void matchingIsByExactRgb() {
        assertEquals(new Color(0xAA0001), DARK_RED_REMAP.foreground(new Color(0xAA0001)),
            "one-off colors must not match (shader uses exact byte equality)");
    }

    @Test
    void rejectsOutOfRangeRgbValues() {
        assertThrows(IllegalArgumentException.class,
            () -> TextColorRemap.builder().remap(0x1000000, 0xD13228));
        assertThrows(IllegalArgumentException.class,
            () -> TextColorRemap.builder().remap(0xAA0000, -1));
        assertThrows(IllegalArgumentException.class,
            () -> TextColorRemap.builder().remap(0xAA0000, 0xD13228, 0x1000000));
    }

    @Test
    void rejectsDuplicateForegroundEntries() {
        TextColorRemap.Builder builder = TextColorRemap.builder().remap(0xAA0000, 0xD13228);
        assertThrows(IllegalArgumentException.class, () -> builder.remap(0xAA0000, 0xFF9000));
    }

    @Test
    void valueSemanticsForCacheKeys() {
        TextColorRemap first = TextColorRemap.builder().remap(0xAA0000, 0xD13228).remap(0xFFAA00, 0xFF9000).build();
        TextColorRemap second = TextColorRemap.builder().remap(0xFFAA00, 0xFF9000).remap(0xAA0000, 0xD13228).build();
        assertEquals(first, second, "entry order must not matter");
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString(), "toString feeds the reflective cache key and must be stable");
        assertNotEquals(first, TextColorRemap.builder().remap(0xAA0000, 0xD13228).build());
    }
}
