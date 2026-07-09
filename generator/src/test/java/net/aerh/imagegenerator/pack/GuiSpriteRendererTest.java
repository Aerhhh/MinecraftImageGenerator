package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pixel-exact coverage of the vanilla GUI sprite scaling algorithm: nearest center sampling
 * for stretch, top-left tiling with last-tile clipping, nine-slice corner crop under border
 * clamping, and stretch_inner behavior.
 */
class GuiSpriteRendererTest {

    private static final GuiScaling STRETCH = new GuiScaling.Stretch();

    /** Builds a texture whose pixel at (x, y) is the opaque color {@code 0xFF000000 | (y * width + x + 1)}. */
    private static BufferedImage sequentialTexture(int width, int height) {
        BufferedImage texture = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                texture.setRGB(x, y, value(y * width + x + 1));
            }
        }
        return texture;
    }

    private static int value(int index) {
        return 0xFF000000 | index;
    }

    @Test
    void stretchSamplesNearestWithCenterMapping() {
        BufferedImage result = GuiSpriteRenderer.render(sequentialTexture(2, 2), STRETCH, 4, 4);
        // Each source pixel becomes a 2x2 block.
        assertEquals(value(1), result.getRGB(0, 0));
        assertEquals(value(1), result.getRGB(1, 1));
        assertEquals(value(2), result.getRGB(2, 0));
        assertEquals(value(2), result.getRGB(3, 1));
        assertEquals(value(3), result.getRGB(0, 3));
        assertEquals(value(4), result.getRGB(3, 3));
    }

    @Test
    void stretchIsExactCopyAtSameSize() {
        BufferedImage texture = sequentialTexture(3, 2);
        BufferedImage result = GuiSpriteRenderer.render(texture, STRETCH, 3, 2);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                assertEquals(texture.getRGB(x, y), result.getRGB(x, y));
            }
        }
    }

    @Test
    void tileRepeatsFromTopLeftAndClipsLastTiles() {
        BufferedImage result = GuiSpriteRenderer.render(sequentialTexture(2, 2), new GuiScaling.Tile(2, 2), 5, 3);
        int[][] expected = {
            {1, 2, 1, 2, 1},
            {3, 4, 3, 4, 3},
            {1, 2, 1, 2, 1},
        };
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                assertEquals(value(expected[y][x]), result.getRGB(x, y), "at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void tileStretchesTextureToNominalTileSize() {
        // 2x1 texture on a 4x2 nominal tile: each tile is the texture stretched to 4x2.
        BufferedImage result = GuiSpriteRenderer.render(sequentialTexture(2, 1), new GuiScaling.Tile(4, 2), 8, 2);
        int[] expectedRow = {1, 1, 2, 2, 1, 1, 2, 2};
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(value(expectedRow[x]), result.getRGB(x, y), "at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void nineSliceIsExactCopyAtNominalSize() {
        BufferedImage texture = sequentialTexture(4, 4);
        GuiScaling nineSlice = new GuiScaling.NineSlice(4, 4, new GuiScaling.NineSlice.Border(1, 1, 1, 1), false);
        BufferedImage result = GuiSpriteRenderer.render(texture, nineSlice, 4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(texture.getRGB(x, y), result.getRGB(x, y), "at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void nineSliceKeepsCornersAndTilesEdgesAndCenter() {
        // 4x4 texture, border 1: inner source region is the middle 2x2.
        BufferedImage texture = sequentialTexture(4, 4);
        GuiScaling nineSlice = new GuiScaling.NineSlice(4, 4, new GuiScaling.NineSlice.Border(1, 1, 1, 1), false);
        BufferedImage result = GuiSpriteRenderer.render(texture, nineSlice, 8, 8);

        // Corners 1:1.
        assertEquals(value(1), result.getRGB(0, 0));
        assertEquals(value(4), result.getRGB(7, 0));
        assertEquals(value(13), result.getRGB(0, 7));
        assertEquals(value(16), result.getRGB(7, 7));

        // Top edge tiles the 2-wide source strip [2, 3].
        assertEquals(value(2), result.getRGB(1, 0));
        assertEquals(value(3), result.getRGB(2, 0));
        assertEquals(value(2), result.getRGB(3, 0));
        assertEquals(value(3), result.getRGB(6, 0));

        // Left edge tiles the 2-tall source strip [5, 9].
        assertEquals(value(5), result.getRGB(0, 1));
        assertEquals(value(9), result.getRGB(0, 2));
        assertEquals(value(5), result.getRGB(0, 3));

        // Right edge tiles [8, 12]; bottom edge tiles [14, 15].
        assertEquals(value(8), result.getRGB(7, 1));
        assertEquals(value(12), result.getRGB(7, 2));
        assertEquals(value(14), result.getRGB(1, 7));
        assertEquals(value(15), result.getRGB(2, 7));

        // Center tiles the 2x2 block [6, 7 / 10, 11].
        assertEquals(value(6), result.getRGB(1, 1));
        assertEquals(value(7), result.getRGB(2, 1));
        assertEquals(value(10), result.getRGB(1, 2));
        assertEquals(value(11), result.getRGB(2, 2));
        assertEquals(value(6), result.getRGB(3, 3));
    }

    @Test
    void nineSliceStretchInnerStretchesEdgesAndCenter() {
        BufferedImage texture = sequentialTexture(4, 4);
        GuiScaling nineSlice = new GuiScaling.NineSlice(4, 4, new GuiScaling.NineSlice.Border(1, 1, 1, 1), true);
        BufferedImage result = GuiSpriteRenderer.render(texture, nineSlice, 8, 8);

        // Corners still 1:1.
        assertEquals(value(1), result.getRGB(0, 0));
        assertEquals(value(16), result.getRGB(7, 7));

        // Top edge stretches [2, 3] across 6 px: 2,2,2,3,3,3.
        assertEquals(value(2), result.getRGB(1, 0));
        assertEquals(value(2), result.getRGB(3, 0));
        assertEquals(value(3), result.getRGB(4, 0));
        assertEquals(value(3), result.getRGB(6, 0));

        // Center stretches the 2x2 block into 3x3 quadrants.
        assertEquals(value(6), result.getRGB(1, 1));
        assertEquals(value(6), result.getRGB(3, 3));
        assertEquals(value(11), result.getRGB(4, 4));
        assertEquals(value(11), result.getRGB(6, 6));
    }

    @Test
    void nineSliceClampsBordersAndCropsCornersOnSmallTargets() {
        // 6x6 texture, border 2, target 3x3: borders clamp to 1, corners crop to their outer pixel.
        BufferedImage texture = sequentialTexture(6, 6);
        GuiScaling nineSlice = new GuiScaling.NineSlice(6, 6, new GuiScaling.NineSlice.Border(2, 2, 2, 2), false);
        BufferedImage result = GuiSpriteRenderer.render(texture, nineSlice, 3, 3);

        assertEquals(value(1), result.getRGB(0, 0));   // outer pixel of top-left corner
        assertEquals(value(6), result.getRGB(2, 0));   // outer pixel of top-right corner (5,0)
        assertEquals(value(31), result.getRGB(0, 2));  // (0,5)
        assertEquals(value(36), result.getRGB(2, 2));  // (5,5)
        assertEquals(value(3), result.getRGB(1, 0));   // top edge first strip pixel (2,0)
        assertEquals(value(13), result.getRGB(0, 1));  // left edge first strip pixel (0,2)
        assertEquals(value(18), result.getRGB(2, 1));  // right edge (5,2)
        assertEquals(value(33), result.getRGB(1, 2));  // bottom edge (2,5)
        assertEquals(value(15), result.getRGB(1, 1));  // center (2,2)
    }

    @Test
    void nineSliceWithZeroInnerRegionLeavesInteriorTransparent() {
        // Border 2 on a 4x4 texture: borders consume the whole texture, no inner source exists.
        BufferedImage texture = sequentialTexture(4, 4);
        GuiScaling nineSlice = new GuiScaling.NineSlice(4, 4, new GuiScaling.NineSlice.Border(2, 2, 2, 2), false);
        BufferedImage result = GuiSpriteRenderer.render(texture, nineSlice, 8, 8);

        assertEquals(value(1), result.getRGB(0, 0));
        assertEquals(value(6), result.getRGB(1, 1));   // (1,1) of top-left corner block
        assertEquals(value(11), result.getRGB(6, 6));  // bottom-right corner block starts at source (2,2)
        assertEquals(value(16), result.getRGB(7, 7));
        assertEquals(0, result.getRGB(3, 0));          // top edge region: nothing to tile
        assertEquals(0, result.getRGB(4, 4));          // center region: nothing to tile
    }

    @Test
    void nineSliceNormalizesTextureLargerThanNominalSize() {
        // 8x8 texture declared as a 4x4 nominal sprite: texture is first normalized down to 4x4.
        BufferedImage texture = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                // 2x2 blocks so nearest-normalization to 4x4 is unambiguous.
                texture.setRGB(x, y, value((y / 2) * 4 + (x / 2) + 1));
            }
        }
        GuiScaling nineSlice = new GuiScaling.NineSlice(4, 4, new GuiScaling.NineSlice.Border(1, 1, 1, 1), false);
        BufferedImage result = GuiSpriteRenderer.render(texture, nineSlice, 4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(value(y * 4 + x + 1), result.getRGB(x, y), "at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void rejectsNonPositiveTargetSizes() {
        BufferedImage texture = sequentialTexture(2, 2);
        assertThrows(IllegalArgumentException.class, () -> GuiSpriteRenderer.render(texture, STRETCH, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> GuiSpriteRenderer.render(texture, STRETCH, 4, -1));
    }

    @Test
    void rejectsTargetsBeyondHardCap() {
        BufferedImage texture = sequentialTexture(2, 2);
        assertThrows(IllegalArgumentException.class,
            () -> GuiSpriteRenderer.render(texture, STRETCH, GuiScaling.MAX_DIMENSION + 1, 4));
    }

    @Test
    void rejectsNullArguments() {
        BufferedImage texture = sequentialTexture(2, 2);
        assertThrows(IllegalArgumentException.class, () -> GuiSpriteRenderer.render(null, STRETCH, 4, 4));
        assertThrows(IllegalArgumentException.class, () -> GuiSpriteRenderer.render(texture, null, 4, 4));
    }

    @Test
    void rejectsExcessiveTileCounts() {
        BufferedImage texture = sequentialTexture(1, 1);
        assertThrows(IllegalArgumentException.class,
            () -> GuiSpriteRenderer.render(texture, new GuiScaling.Tile(1, 1), 300, 300));
    }
}
