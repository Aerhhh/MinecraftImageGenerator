package net.aerh.imagegenerator.impl.tooltip;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the {@code %%gradient%%} placeholder: interpolated per-character
 * colors reach the renderer exactly, with vanilla quartered drop shadows, through formatting
 * and line wrapping.
 */
class TooltipGradientTest {

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
    void gradientRendersInterpolatedPerCharacterColors() {
        // "AB CD" from #ff0000 to #0000ff: t = 0, 0.25, (space), 0.75, 1.
        BufferedImage image = borderlessLore("%%gradient:#ff0000:#0000ff%%AB CD%%/gradient%%")
            .build().generate().getImage();
        assertTrue(containsColor(image, 0xFFFF0000), "first stop present");
        assertTrue(containsColor(image, 0xFFBF0040), "t=0.25 interpolant present");
        assertTrue(containsColor(image, 0xFF4000BF), "t=0.75 interpolant present");
        assertTrue(containsColor(image, 0xFF0000FF), "last stop present");
    }

    @Test
    void gradientShadowsAreQuartered() {
        BufferedImage image = borderlessLore("%%gradient:#ff0000:#0000ff%%AB CD%%/gradient%%")
            .build().generate().getImage();
        assertTrue(containsColor(image, 0xFF3F0000), "quartered shadow of the first stop present");
    }

    @Test
    void boldGradientRenders() {
        BufferedImage image = borderlessLore("%%gradient:#ff0000:#0000ff%%&lAB%%/gradient%%")
            .build().generate().getImage();
        assertTrue(containsColor(image, 0xFFFF0000), "bold first stop present");
        assertTrue(containsColor(image, 0xFF0000FF), "bold last stop present");
    }

    @Test
    void gradientSurvivesWrappingAcrossLines() {
        MinecraftTooltipGenerator generator =
            borderlessLore("%%gradient:#ff0000:#0000ff%%AAAA BBBB%%/gradient%%")
                .withMaxLineLength(6)
                .build();
        BufferedImage image = generator.generate().getImage();
        assertTrue(containsColor(image, 0xFFFF0000), "first stop present on the first line");
        assertTrue(containsColor(image, 0xFF0000FF), "last stop present on the wrapped line");
    }
}
