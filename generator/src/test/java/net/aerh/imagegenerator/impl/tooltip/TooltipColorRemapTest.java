package net.aerh.imagegenerator.impl.tooltip;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.text.TextColorRemap;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the shader-equivalent text color remap: it fires at the single text
 * draw choke point (text, shadow, strikethrough, underline), leaves unmapped colors alone,
 * enters the render cache key, and never leaks into slash commands.
 */
class TooltipColorRemapTest {

    // Hypixel pack values: dark_red 0xAA0000 -> 0xD13228 (derived shadow 0x340C0A).
    private static final TextColorRemap HYPIXEL_DARK_RED =
        TextColorRemap.builder().remap(0xAA0000, 0xD13228).build();

    private static MinecraftTooltipGenerator.Builder borderlessLore(String lore) {
        return new MinecraftTooltipGenerator.Builder()
            .withItemLore(lore)
            .withRenderBorder(false)
            .withAlpha(0)
            .hasFirstLinePadding(false);
    }

    private static boolean containsColor(BufferedImage image, int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == argb) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void remapsTextAndDerivedShadow() {
        BufferedImage image = borderlessLore("&4remapped")
            .withTextColorRemap(HYPIXEL_DARK_RED)
            .build().generate().getImage();
        assertTrue(containsColor(image, 0xFFD13228), "remapped foreground present");
        assertTrue(containsColor(image, 0xFF340C0A), "derived shadow present");
        assertFalse(containsColor(image, 0xFFAA0000), "original dark_red absent");
        assertFalse(containsColor(image, 0xFF2A0000), "original shadow absent");
    }

    @Test
    void leavesUnmappedColorsUntouched() {
        BufferedImage image = borderlessLore("&4red &agreen")
            .withTextColorRemap(HYPIXEL_DARK_RED)
            .build().generate().getImage();
        assertTrue(containsColor(image, 0xFF55FF55), "green stays vanilla");
        assertTrue(containsColor(image, 0xFFD13228), "dark_red remapped");
    }

    @Test
    void remapsStrikethroughAndUnderlineLines() {
        BufferedImage image = borderlessLore("&4&m----&r &4&n____")
            .withTextColorRemap(HYPIXEL_DARK_RED)
            .build().generate().getImage();
        assertTrue(containsColor(image, 0xFFD13228), "line effects draw in the remapped color");
        assertFalse(containsColor(image, 0xFFAA0000));
    }

    @Test
    void noRemapRendersIdenticallyToBaseline() {
        BufferedImage without = borderlessLore("&4baseline").build().generate().getImage();
        BufferedImage withEmptyRemap = borderlessLore("&4baseline")
            .withTextColorRemap(TextColorRemap.builder().build())
            .build().generate().getImage();
        assertEquals(without.getWidth(), withEmptyRemap.getWidth());
        for (int y = 0; y < without.getHeight(); y++) {
            for (int x = 0; x < without.getWidth(); x++) {
                assertEquals(without.getRGB(x, y), withEmptyRemap.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void remapEntersTheCacheKey() {
        MinecraftTooltipGenerator plain = borderlessLore("&4cache").build();
        MinecraftTooltipGenerator remapped = borderlessLore("&4cache")
            .withTextColorRemap(HYPIXEL_DARK_RED).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(plain), GeneratorCacheKey.fromGenerator(remapped),
            "a remap must split the render cache key or recolored tooltips collide");
    }

    @Test
    void remapDoesNotLeakIntoSlashCommands() {
        String command = borderlessLore("&4lore")
            .withTextColorRemap(HYPIXEL_DARK_RED)
            .buildSlashCommand();
        assertFalse(command.contains("remap"), command);
        assertFalse(command.contains("D13228"), command);
    }
}
