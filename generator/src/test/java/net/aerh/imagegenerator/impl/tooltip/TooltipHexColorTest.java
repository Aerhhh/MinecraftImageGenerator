package net.aerh.imagegenerator.impl.tooltip;

import net.aerh.imagegenerator.text.TextColorRemap;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for hex colored text: the in-band {@code &#RRGGBB} code and the
 * {@code %%#RRGGBB%%} placeholder both reach the renderer as the exact RGB with the vanilla
 * quartered drop shadow, and the shader-equivalent color remap applies to hex colors too.
 */
class TooltipHexColorTest {

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
    void inBandHexCodeRendersExactForegroundAndQuarteredShadow() {
        BufferedImage image = borderlessLore("&#ff00aahex text").build().generate().getImage();
        assertTrue(containsColor(image, 0xFFFF00AA), "hex foreground present");
        assertTrue(containsColor(image, 0xFF3F002A), "vanilla quartered shadow present");
    }

    @Test
    void hexPlaceholderRendersLikeTheInBandCode() {
        BufferedImage image = borderlessLore("%%#00ff00%%lime text").build().generate().getImage();
        assertTrue(containsColor(image, 0xFF00FF00), "placeholder foreground present");
        assertTrue(containsColor(image, 0xFF003F00), "placeholder shadow present");
    }

    @Test
    void textColorRemapAppliesToHexColors() {
        TextColorRemap remap = TextColorRemap.builder().remap(0xFF00AA, 0x123456).build();
        BufferedImage image = borderlessLore("&#ff00aaremapped")
            .withTextColorRemap(remap)
            .build().generate().getImage();
        assertTrue(containsColor(image, 0xFF123456), "remapped hex foreground present");
        assertFalse(containsColor(image, 0xFFFF00AA), "original hex color absent");
    }

    @Test
    void hexColorAppliesToStrikethroughAndUnderline() {
        BufferedImage image = borderlessLore("&#ff00aa&m----&r &#ff00aa&n____").build().generate().getImage();
        assertTrue(containsColor(image, 0xFFFF00AA), "line effects draw in the hex color");
    }

    @Test
    void hexColorSurvivesWrappingAcrossLines() {
        MinecraftTooltipGenerator generator = borderlessLore("&#ff00aaAAAA BBBB CCCC DDDD")
            .withMaxLineLength(6)
            .build();
        BufferedImage image = generator.generate().getImage();
        assertTrue(containsColor(image, 0xFFFF00AA), "hex color present on wrapped lines");
    }
}
