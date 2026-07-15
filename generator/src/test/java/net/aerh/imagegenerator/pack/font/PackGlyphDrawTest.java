package net.aerh.imagegenerator.pack.font;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackGlyphDrawTest {

    private static final int RED = 0xFFFF0000;
    private static final int WHITE = 0xFFFFFFFF;

    /**
     * 4x2 sheet, chars ["AB"]: 2x2 cells, height 2, ascent 2, scale 1. Cell 'A' has an opaque
     * red pixel at cell (0,0) and an opaque white pixel at cell (1,1); cell 'B' is empty.
     */
    private static PackGlyph glyph() {
        BufferedImage sheet = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(0, 0, RED);
        sheet.setRGB(1, 1, WHITE);
        PackFont font = PackFont.create("test:draw",
            List.of(new FontProviderDefinition.Bitmap("test:font/draw.png", 2, 2, List.of("AB"), FontFilter.none())),
            ref -> sheet);
        return font.glyph('A').orElseThrow();
    }

    private static BufferedImage canvas() {
        return new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    }

    private static void draw(BufferedImage target, PackGlyph glyph, double xGuiPx, double lineTopGuiPx,
                             int pixelSize, Color tint, boolean italic) {
        Graphics2D graphics = target.createGraphics();
        try {
            glyph.draw(graphics, xGuiPx, lineTopGuiPx, pixelSize, tint, italic);
        } finally {
            graphics.dispose();
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test
    void drawsFullCellAtPixelSizeTwoWithAscentPlacement() {
        BufferedImage target = canvas();
        // Top edge = lineTop + 7 - ascent = 0 + 7 - 2 = 5 GUI px -> canvas y 10; x 1 GUI -> 2.
        draw(target, glyph(), 1.0, 0.0, 2, Color.WHITE, false);
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                assertEquals(RED, target.getRGB(2 + dx, 10 + dy), "red cell pixel (0,0) at (2,10)..(3,11)");
                assertEquals(WHITE, target.getRGB(4 + dx, 12 + dy), "white cell pixel (1,1) at (4,12)..(5,13)");
            }
        }
        assertEquals(0, target.getRGB(4, 10), "transparent cell pixel (1,0) stays clear");
        assertEquals(0, target.getRGB(2, 12), "transparent cell pixel (0,1) stays clear");
        assertEquals(0, target.getRGB(2, 9), "nothing above the cell top edge");
        assertEquals(0, target.getRGB(1, 10), "nothing left of the cell");
    }

    @Test
    void ascentPlacementFollowsLineTop() {
        BufferedImage target = canvas();
        // Top edge = 3 + 7 - 2 = 8 GUI px -> canvas y 16 at pixelSize 2.
        draw(target, glyph(), 0.0, 3.0, 2, Color.WHITE, false);
        assertEquals(RED, target.getRGB(0, 16));
        assertEquals(0, target.getRGB(0, 15), "row above the computed top edge stays clear");
    }

    @Test
    void negativeAscentPlacesCellBelowBaseline() {
        BufferedImage sheet = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(0, 0, RED);
        PackFont font = PackFont.create("test:neg",
            List.of(new FontProviderDefinition.Bitmap("test:font/neg.png", 2, -1, List.of("A"), FontFilter.none())),
            ref -> sheet);
        BufferedImage target = canvas();
        // Top edge = 0 + 7 - (-1) = 8 GUI px -> canvas y 16.
        draw(target, font.glyph('A').orElseThrow(), 0.0, 0.0, 2, Color.WHITE, false);
        assertEquals(RED, target.getRGB(0, 16));
        assertEquals(0, target.getRGB(0, 15));
    }

    @Test
    void tintMultipliesAllChannels() {
        BufferedImage target = canvas();
        draw(target, glyph(), 1.0, 0.0, 2, new Color(128, 64, 32), false);
        assertEquals(0xFF800000, target.getRGB(2, 10), "red pixel: only the red channel survives, scaled");
        assertEquals(0xFF804020, target.getRGB(4, 12), "white pixel becomes the tint color exactly");
    }

    @Test
    void italicShearsPerGuiRow() {
        BufferedImage target = canvas();
        draw(target, glyph(), 1.0, 0.0, 2, Color.WHITE, true);
        // Cell row 0 sits 5 GUI px below line top: shear = 1 - 0.25*5 = -0.25 GUI px,
        // destX = round((1 - 0.25) * 2) = 2 (unchanged after rounding).
        assertEquals(RED, target.getRGB(2, 10));
        assertEquals(RED, target.getRGB(3, 11));
        // Cell row 1 sits 6 GUI px below line top: shear = -0.5 GUI px, destX = round(0.5*2) = 1;
        // the white pixel (cell x 1) lands at canvas x 3..4 instead of the upright 4..5.
        assertEquals(WHITE, target.getRGB(3, 12));
        assertEquals(WHITE, target.getRGB(4, 13));
        assertEquals(0, target.getRGB(5, 12), "the upright position is vacated by the shear");
    }

    @Test
    void emptyCellGlyphDrawsNothing() {
        // Cell 'B' of the fixture sheet is fully transparent: it retains no raster at all, and
        // drawing it must be a clean no-op (regression for the null-cell fast path).
        BufferedImage sheet = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(0, 0, RED);
        PackFont font = PackFont.create("test:empty",
            List.of(new FontProviderDefinition.Bitmap("test:font/e.png", 2, 2, List.of("AB"), FontFilter.none())),
            ref -> sheet);
        PackGlyph empty = font.glyph('B').orElseThrow();
        BufferedImage target = canvas();
        draw(target, empty, 1.0, 0.0, 2, Color.WHITE, false);
        draw(target, empty, 1.0, 0.0, 2, Color.WHITE, true);
        assertArrayEquals(new int[32 * 32], pixels(target));
        assertEquals(1.0f, empty.advance(false), "empty cells still advance 1");
    }

    @Test
    void spaceGlyphDrawsNothing() {
        PackFont font = PackFont.create("test:space",
            List.of(new FontProviderDefinition.Space(Map.of((int) ' ', 4.0f), FontFilter.none())), ref -> {
                throw new AssertionError("no textures");
            });
        BufferedImage target = canvas();
        draw(target, font.glyph(' ').orElseThrow(), 1.0, 0.0, 2, Color.WHITE, false);
        assertArrayEquals(new int[32 * 32], pixels(target));
    }

    @Test
    void zeroHeightGlyphDrawsNothingButAdvancesOne() {
        BufferedImage sheet = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(0, 0, RED);
        PackFont font = PackFont.create("test:flat",
            List.of(new FontProviderDefinition.Bitmap("test:font/flat.png", 0, 0, List.of("A"), FontFilter.none())),
            ref -> sheet);
        PackGlyph flat = font.glyph('A').orElseThrow();
        BufferedImage target = canvas();
        draw(target, flat, 0.0, 0.0, 2, Color.WHITE, false);
        assertArrayEquals(new int[32 * 32], pixels(target));
        assertEquals(1.0f, flat.advance(false), "scale 0: ink * 0 rounds to 0, plus the unscaled +1");
    }

    @Test
    void nonPositivePixelSizeIsRejected() {
        PackGlyph bitmapGlyph = glyph();
        BufferedImage target = canvas();
        Graphics2D graphics = target.createGraphics();
        try {
            assertThrows(IllegalArgumentException.class,
                () -> bitmapGlyph.draw(graphics, 0.0, 0.0, 0, Color.WHITE, false));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void repeatedDrawsArePixelIdentical() {
        PackGlyph shared = glyph();
        BufferedImage first = canvas();
        BufferedImage second = canvas();
        Color tint = new Color(200, 150, 100);
        draw(first, shared, 1.5, 0.0, 2, tint, true);
        draw(second, shared, 1.5, 0.0, 2, tint, true);
        assertArrayEquals(pixels(first), pixels(second));
    }

    @Test
    void scaledCellCacheServesMultiplePixelSizesIndependently() {
        PackGlyph shared = glyph();
        BufferedImage small = canvas();
        BufferedImage large = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        draw(small, shared, 0.0, 0.0, 2, Color.WHITE, false);
        Graphics2D graphics = large.createGraphics();
        try {
            shared.draw(graphics, 0.0, 0.0, 4, Color.WHITE, false);
        } finally {
            graphics.dispose();
        }
        assertEquals(RED, small.getRGB(0, 10), "pixelSize 2: top edge at y 10");
        assertEquals(RED, large.getRGB(0, 20), "pixelSize 4: top edge at y 20");
        assertEquals(RED, large.getRGB(3, 23), "pixelSize 4: red covers a 4x4 block");
    }
}
