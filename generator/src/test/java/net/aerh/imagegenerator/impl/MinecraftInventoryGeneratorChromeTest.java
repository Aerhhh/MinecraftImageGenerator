package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the inventory generator's chrome two ways: composition (the bordered render equals the
 * shared {@code drawVanillaChrome} + slot-texture composition it delegates to, shared with
 * {@link MinecraftContainerGenerator}) and hard-coded pixels (the palette and geometry of the
 * chrome itself, which the composition test alone cannot catch drifting because both sides
 * would route through the same mutated helper).
 */
class MinecraftInventoryGeneratorChromeTest {

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int DARK_SHADOW = 0xFF555555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    @Test
    void borderedEmptyInventoryIsExactlyChromePlusSlotTextures() {
        int scale = MinecraftInventoryGenerator.getScaleFactor();
        int slotSize = 18 * scale;
        int borderSize = 7 * scale;
        int rows = 1;
        int columns = 9;

        BufferedImage actual = new MinecraftInventoryGenerator.Builder()
            .withRows(rows).withSlotsPerRow(columns)
            .build().render(null).getImage();

        int width = columns * slotSize + borderSize * 2;
        int height = rows * slotSize + borderSize * 2;
        assertEquals(width, actual.getWidth());
        assertEquals(height, actual.getHeight());

        BufferedImage expected = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = expected.createGraphics();
        try {
            MinecraftInventoryGenerator.drawVanillaChrome(graphics, width, height, scale);
            BufferedImage slotTexture = MinecraftInventoryGenerator.loadSlotTexture(slotSize);
            for (int column = 0; column < columns; column++) {
                graphics.drawImage(slotTexture, borderSize + column * slotSize, borderSize, null);
            }
        } finally {
            graphics.dispose();
        }

        ImageAssertions.assertPixelsEqual(expected, actual, "inventory chrome");
    }

    /**
     * Hard-coded reference pixels for {@code drawVanillaChrome} at scale 2 on a 352x264 canvas:
     * the gray background (198,198,198), white highlight, dark shadow (85,85,85), black outline
     * with its rounded corners, and the transparent corner notches. Any palette swap or
     * transposed fill breaks these coordinates regardless of what the composition tests do.
     */
    @Test
    void vanillaChromePixelsMatchTheHardcodedReferencePalette() {
        int scale = 2;
        int width = 352;
        int height = 264;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            MinecraftInventoryGenerator.drawVanillaChrome(graphics, width, height, scale);
        } finally {
            graphics.dispose();
        }

        // Rounded corners: the outline does not reach the literal canvas corners.
        assertEquals(0, image.getRGB(0, 0), "top-left notch transparent");
        assertEquals(0, image.getRGB(width - 1, 0), "top-right notch transparent");
        assertEquals(0, image.getRGB(0, height - 1), "bottom-left notch transparent");
        assertEquals(0, image.getRGB(width - 1, height - 1), "bottom-right notch transparent");

        // Black outline: edges and inner corner squares.
        assertEquals(BLACK, image.getRGB(0, 2 * scale), "left outline");
        assertEquals(BLACK, image.getRGB(2 * scale, 0), "top outline");
        assertEquals(BLACK, image.getRGB(width - 1, 3 * scale), "right outline");
        assertEquals(BLACK, image.getRGB(3 * scale, height - 1), "bottom outline");
        assertEquals(BLACK, image.getRGB(scale, scale), "top-left corner square");
        assertEquals(BLACK, image.getRGB(width - 3 * scale, scale), "upper top-right corner square");
        assertEquals(BLACK, image.getRGB(width - 2 * scale, 2 * scale), "lower top-right corner square");
        assertEquals(BLACK, image.getRGB(width - 2 * scale, height - 2 * scale), "bottom-right corner square");
        assertEquals(BLACK, image.getRGB(scale, height - 3 * scale), "upper bottom-left corner square");
        assertEquals(BLACK, image.getRGB(2 * scale, height - 2 * scale), "lower bottom-left corner square");

        // White highlight: left column, top row and the square at (3s, 3s).
        assertEquals(WHITE, image.getRGB(scale, 3 * scale), "left highlight");
        assertEquals(WHITE, image.getRGB(4 * scale, scale), "top highlight");
        assertEquals(WHITE, image.getRGB(3 * scale, 3 * scale), "highlight square");

        // Dark shadow: right column, bottom row and the square at (w-4s, h-4s).
        assertEquals(DARK_SHADOW, image.getRGB(width - 3 * scale, 3 * scale), "right shadow");
        assertEquals(DARK_SHADOW, image.getRGB(3 * scale, height - 3 * scale), "bottom shadow");
        assertEquals(DARK_SHADOW, image.getRGB(width - 4 * scale, height - 4 * scale), "shadow square");

        // Gray background: interior plus the two anti-diagonal nubs.
        assertEquals(BACKGROUND, image.getRGB(width / 2, height / 2), "background center");
        assertEquals(BACKGROUND, image.getRGB(4 * scale, 4 * scale), "background near the highlight");
        assertEquals(BACKGROUND, image.getRGB(width - 3 * scale, 2 * scale), "top-right nub");
        assertEquals(BACKGROUND, image.getRGB(2 * scale, height - 3 * scale), "bottom-left nub");
    }
}
