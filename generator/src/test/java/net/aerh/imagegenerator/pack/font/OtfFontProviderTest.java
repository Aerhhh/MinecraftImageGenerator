package net.aerh.imagegenerator.pack.font;

import net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont;
import net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.Glyph;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rasterization coverage for the Tier-1 TTF path ({@link OtfFontProvider} + {@link OtfGlyph})
 * against a synthetic pixel font built at test runtime ({@link MinimalTrueTypeFont}).
 *
 * <p>Fixture geometry (unitsPerEm 32, so at ppem 32 one font unit is one device pixel): 'A' is a
 * filled box design bounds {@code [0,0,16,24]} advancing 20, 'B' a box {@code [0,0,8,16]}
 * advancing 12, space a blank cell advancing 16. Rendered at {@code size 8, oversample 4}
 * ({@code ppem = 32}): 'A' scales to a 4x6 GUI-px cell sitting on the baseline (ascent 6, advance
 * {@code 20 / 4 = 5}).
 */
class OtfFontProviderTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static final byte[] FIXTURE = fixture();

    private static byte[] fixture() {
        LinkedHashMap<Integer, Glyph> glyphs = new LinkedHashMap<>();
        glyphs.put((int) 'A', Glyph.box(20, 0, 0, 16, 24));
        glyphs.put((int) 'B', Glyph.box(12, 0, 0, 8, 16));
        glyphs.put((int) ' ', Glyph.blank(16));
        return MinimalTrueTypeFont.build(32, glyphs);
    }

    private static PackFont.TextureLoader ttfLoader(byte[] bytes) {
        return new PackFont.TextureLoader() {
            @Override
            public BufferedImage load(String textureFileRef) {
                throw new AssertionError("TTF providers load no sheet textures");
            }

            @Override
            public Optional<byte[]> ttfFontData(String fontFileRef) {
                return Optional.ofNullable(bytes);
            }
        };
    }

    private static FontProviderDefinition.Ttf ttf(float size, float oversample, float shiftX, float shiftY,
                                                  Set<Integer> skip) {
        return new FontProviderDefinition.Ttf("test:font/pixel.ttf", size, oversample, shiftX, shiftY,
            skip, FontFilter.none());
    }

    private static OtfFontProvider provider(float size, float oversample, float shiftX, float shiftY,
                                            Set<Integer> skip) {
        return OtfFontProvider.tryCreate(ttf(size, oversample, shiftX, shiftY, skip), ttfLoader(FIXTURE), "test:otf")
            .orElseThrow();
    }

    private static OtfFontProvider provider() {
        return provider(8.0f, 4.0f, 0.0f, 0.0f, Set.of());
    }

    private static BufferedImage draw(PackGlyph glyph, boolean italic) {
        BufferedImage canvas = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            glyph.draw(graphics, 0.0, 0.0, 1, Color.WHITE, italic);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static void assertSolidBox(BufferedImage image, int x0, int x1, int y0, int y1) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                boolean inside = x >= x0 && x <= x1 && y >= y0 && y <= y1;
                assertEquals(inside ? WHITE : 0, image.getRGB(x, y),
                    "pixel (" + x + "," + y + ") " + (inside ? "inside" : "outside") + " the box");
            }
        }
    }

    @Test
    void ppemIsSizeTimesOversampleRounded() {
        assertEquals(28, provider(7.0f, 4.0f, 0, 0, Set.of()).ppem(), "MCC hud.ttf: 7 * 4 = 28");
        assertEquals(32, provider().ppem(), "8 * 4 = 32");
        assertEquals(11, provider(2.75f, 4.0f, 0, 0, Set.of()).ppem(), "round(2.75 * 4) = 11");
    }

    @Test
    void advanceIsDeviceAdvanceDividedByOversample() {
        OtfGlyph a = provider().glyph('A');
        assertEquals(5.0f, a.advance(false), "device advance 20 at ppem / oversample 4");
        assertEquals(6.0f, a.advance(true), "bold adds exactly +1 GUI px");
        assertEquals(3.0f, provider().glyph('B').advance(false), "device advance 12 / 4");
    }

    @Test
    void coverageFollowsCanDisplayAndSkip() {
        OtfFontProvider provider = provider(8.0f, 4.0f, 0, 0, Set.of((int) 'B'));
        assertInstanceOf(OtfGlyph.class, provider.glyph('A'), "'A' is displayable and not skipped");
        assertNull(provider.glyph('B'), "'B' is in the skip set");
        assertNull(provider.glyph('Z'), "'Z' is not in the font");
    }

    @Test
    void inkedGlyphSitsOnTheBaselineWithVanillaAscentAndHeight() {
        OtfGlyph a = provider().glyph('A');
        assertFalse(a.isEmpty());
        assertEquals(6, a.ascent(), "24 device px above the baseline / oversample 4");
        assertEquals(6, a.height(), "24 device px tall / oversample 4");
        // Cell top at lineTop + 7 - ascent = 1 GUI px; a 4x6 box down to the baseline at 7.
        assertSolidBox(draw(a, false), 0, 3, 1, 6);
    }

    @Test
    void blankGlyphDrawsNothingButStillAdvances() {
        OtfGlyph space = provider().glyph(' ');
        assertTrue(space.isEmpty(), "the blank space outline rasterizes to no ink");
        assertEquals(4.0f, space.advance(false), "device advance 16 / 4");
        assertSolidBox(draw(space, false), 1, 0, 1, 0); // empty box: nothing drawn
    }

    @Test
    void rasterizationIsAntialiasingOff() {
        // Every pixel is fully opaque or fully transparent - no partial-coverage fringe.
        BufferedImage image = draw(provider().glyph('A'), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                assertTrue(argb == 0 || argb == WHITE,
                    "pixel (" + x + "," + y + ") is binary, got " + Integer.toHexString(argb));
            }
        }
    }

    @Test
    void shiftMovesPlacementNotAdvance() {
        OtfGlyph plain = provider().glyph('A');
        OtfGlyph shifted = provider(8.0f, 4.0f, 2.0f, 1.0f, Set.of()).glyph('A');
        assertEquals(plain.advance(false), shifted.advance(false), "shift never changes the advance");
        // +x right, +y down: the box moves 2 GUI px right and 1 GUI px down from (0..3, 1..6).
        assertSolidBox(draw(plain, false), 0, 3, 1, 6);
        assertSolidBox(draw(shifted, false), 2, 5, 2, 7);
    }

    @Test
    void inkBoxCoversShiftAndCellWidthPastTheAdvance() {
        OtfGlyph a = provider().glyph('A');
        // 'A' cell is 4 GUI px wide at the pen origin (ink 16 device / oversample 4), advancing 5.
        assertEquals(0.0, a.inkLeftGuiPx(), "no side bearing, no shift: ink starts at the origin");
        assertEquals(4.0, a.inkRightGuiPx(), "cell width 4 GUI px, inside the advance-5 box");
        // shiftX moves the whole cell right, so its ink now overhangs the advance-5 right edge -
        // the case the advance-only measurement clipped.
        OtfGlyph shifted = provider(8.0f, 4.0f, 2.0f, 0.0f, Set.of()).glyph('A');
        assertEquals(2.0, shifted.inkLeftGuiPx(), "shiftX moves the ink left edge to 2");
        assertEquals(6.0, shifted.inkRightGuiPx(), "ink right edge 6 overhangs the advance 5");
    }

    @Test
    void inklessGlyphReportsADegenerateInkBox() {
        OtfGlyph space = provider().glyph(' ');
        assertEquals(0.0, space.inkLeftGuiPx(), "a blank glyph draws no ink");
        assertEquals(0.0, space.inkRightGuiPx(), "left == right: extent folding skips it");
    }

    @Test
    void maxRetainedCellBytesReservesTheBoundedCapBeforeAnyRaster() {
        OtfFontProvider provider = provider(); // ppem 32
        assertEquals(0L, provider.retainedCellBytes(), "nothing rasterized yet, so live bytes are zero");
        // The font cache weighs a font once at insertion; a lazily-filling TTF font must reserve
        // its cap (512 cells * an em-box-sized device raster) or it evades the budget entirely.
        assertEquals(512L * 4 * 32 * 32, provider.maxRetainedCellBytes(), "cap * 4 * ppem * ppem");
    }

    @Test
    void packFontWeighsTtfProviderAtItsReservationNotZeroLiveBytes() {
        PackFont font = PackFont.create("test:otf",
            java.util.List.of(ttf(8.0f, 4.0f, 0, 0, Set.of())), ttfLoader(FIXTURE));
        // Before any glyph lookup the OTF provider has rasterized nothing, yet the font weighs its
        // bounded reservation so the font-cache byte budget applies to TTF fonts too.
        assertEquals(512L * 4 * 32 * 32, font.retainedCellBytes(),
            "ttf provider reserves its cap up front, not zero live bytes");
    }

    @Test
    void rasterizationIsDeterministicWithinRun() {
        BufferedImage first = draw(provider().glyph('A'), false);
        BufferedImage second = draw(provider().glyph('A'), false);
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                assertEquals(first.getRGB(x, y), second.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void boldAndItalicMatchAnEquivalentBitmapGlyphThroughTheSharedGlyphCell() {
        // A BitmapGlyph whose cell is the same 4x6 white box: both delegate to GlyphCell, so their
        // italic shear and bold advance must be pixel- and value-identical.
        BufferedImage sheet = new BufferedImage(4, 6, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 4; x++) {
                sheet.setRGB(x, y, WHITE);
            }
        }
        PackFont bitmapFont = PackFont.create("test:bmp",
            java.util.List.of(new FontProviderDefinition.Bitmap("test:font/box.png", 6, 6,
                java.util.List.of("A"), FontFilter.none())),
            ref -> sheet);
        PackGlyph bitmap = bitmapFont.glyph('A').orElseThrow();
        OtfGlyph otf = provider().glyph('A');

        assertEquals(bitmap.advance(false), otf.advance(false), "same box, same unbold advance");
        assertEquals(bitmap.advance(true) - bitmap.advance(false), otf.advance(true) - otf.advance(false),
            "bold adds +1 for both");

        BufferedImage bitmapItalic = draw(bitmap, true);
        BufferedImage otfItalic = draw(otf, true);
        for (int y = 0; y < bitmapItalic.getHeight(); y++) {
            for (int x = 0; x < bitmapItalic.getWidth(); x++) {
                assertEquals(bitmapItalic.getRGB(x, y), otfItalic.getRGB(x, y),
                    "italic shear parity at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void glyphCacheIsBoundedAndReRastersIdentically() {
        // The provider caches at most MAX_CACHED_GLYPHS rasters; requesting the same glyph twice
        // returns an equal render whether cached or freshly re-rasterized.
        OtfFontProvider provider = provider();
        BufferedImage before = draw(provider.glyph('A'), false);
        BufferedImage after = draw(provider.glyph('A'), false);
        for (int y = 0; y < before.getHeight(); y++) {
            for (int x = 0; x < before.getWidth(); x++) {
                assertEquals(before.getRGB(x, y), after.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
        assertTrue(provider.retainedCellBytes() > 0, "a rendered glyph counts toward font memory");
    }

    @Test
    void missingFontFileDoesNotBuildAProvider() {
        assertEquals(Optional.empty(),
            OtfFontProvider.tryCreate(ttf(8.0f, 4.0f, 0, 0, Set.of()), ttfLoader(null), "test:otf"),
            "an absent font file yields no provider so the caller degrades to unsupported");
    }

    @Test
    void unparseableFontBytesDoNotBuildAProvider() {
        assertEquals(Optional.empty(),
            OtfFontProvider.tryCreate(ttf(8.0f, 4.0f, 0, 0, Set.of()),
                ttfLoader("not a font".getBytes()), "test:otf"),
            "broken TTF bytes degrade to no provider without throwing");
    }

    @Test
    void differentFontBytesProduceDifferentGlyphsNoGlobalCollision() {
        // Two providers built from DIFFERENT fonts that share the same internal name must not
        // collide - proof the fonts are per-provider instances, never globally registered.
        LinkedHashMap<Integer, Glyph> wide = new LinkedHashMap<>();
        wide.put((int) 'A', Glyph.box(28, 0, 0, 28, 24));
        OtfFontProvider narrowFont = provider();
        OtfFontProvider wideFont = OtfFontProvider.tryCreate(ttf(8.0f, 4.0f, 0, 0, Set.of()),
            ttfLoader(MinimalTrueTypeFont.build(32, wide)), "test:otf2").orElseThrow();
        assertNotEquals(narrowFont.glyph('A').advance(false), wideFont.glyph('A').advance(false),
            "each provider rasterizes its own font, so the same codepoint differs");
    }
}
