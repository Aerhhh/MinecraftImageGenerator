package net.aerh.imagegenerator.pack.font;

import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Pins the bounded per-glyph scaled raster cache: at most two pixel sizes are retained, least
 * recently used evicted first, and an evicted size re-renders identically from the owned cell.
 */
class BitmapGlyphScaledCacheTest {

    private static final int RED = 0xFFFF0000;

    /**
     * 4x2 sheet, chars ["AB"]: 2x2 cells, height 2, ascent 2. Cell 'A' has one red pixel at
     * cell (0, 0); cell 'B' is fully transparent.
     */
    private static PackFont font() {
        BufferedImage sheet = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(0, 0, RED);
        return PackFont.create("test:cache",
            List.of(new FontProviderDefinition.Bitmap("test:font/cache.png", 2, 2, List.of("AB"), FontFilter.none())),
            ref -> sheet);
    }

    private static BitmapGlyph glyph(char character) {
        return assertInstanceOf(BitmapGlyph.class, font().glyph(character).orElseThrow());
    }

    private static BufferedImage draw(BitmapGlyph glyph, int pixelSize) {
        BufferedImage canvas = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            glyph.draw(graphics, 0.0, 0.0, pixelSize, Color.WHITE, false);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    @Test
    void cacheRetainsAtMostTwoPixelSizesEvictingLeastRecentlyUsed() {
        BitmapGlyph glyph = glyph('A');
        draw(glyph, 1);
        draw(glyph, 2);
        assertEquals(Set.of(1, 2), glyph.cachedScaledPixelSizes());

        draw(glyph, 3);
        assertEquals(Set.of(2, 3), glyph.cachedScaledPixelSizes(), "size 1 is the LRU entry and evicts");

        draw(glyph, 2);
        draw(glyph, 4);
        assertEquals(Set.of(2, 4), glyph.cachedScaledPixelSizes(),
            "re-drawing at 2 refreshed it, so 3 became the LRU entry");
    }

    @Test
    void evictedPixelSizeReRendersIdentically() {
        BitmapGlyph glyph = glyph('A');
        BufferedImage before = draw(glyph, 2);
        draw(glyph, 3);
        draw(glyph, 4);
        assertEquals(Set.of(3, 4), glyph.cachedScaledPixelSizes(), "size 2 was evicted");
        ImageAssertions.assertPixelsEqual(before, draw(glyph, 2),
            "an evicted size rescales from the owned cell with identical pixels");
    }

    @Test
    void emptyCellsCacheNothing() {
        BitmapGlyph glyph = glyph('B');
        draw(glyph, 1);
        draw(glyph, 2);
        draw(glyph, 3);
        assertEquals(Set.of(), glyph.cachedScaledPixelSizes(),
            "a fully transparent cell draws nothing and retains no rasters");
    }
}
