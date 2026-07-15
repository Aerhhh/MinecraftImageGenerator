package net.aerh.imagegenerator.pack.font;

import net.aerh.imagegenerator.exception.PackResolveException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackFontTest {

    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private static BufferedImage sheet(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static FontProviderDefinition.Bitmap bitmap(String file, int height, int ascent, List<String> rows) {
        return new FontProviderDefinition.Bitmap(file, height, ascent, rows, FontFilter.none());
    }

    private static FontProviderDefinition.Space space(Map<Integer, Float> advances) {
        return new FontProviderDefinition.Space(advances, FontFilter.none());
    }

    private static PackFont.TextureLoader textures(Map<String, BufferedImage> sheets) {
        return ref -> {
            BufferedImage image = sheets.get(ref);
            if (image == null) {
                throw new PackResolveException("No such fixture sheet: %s", ref);
            }
            return image;
        };
    }

    /**
     * 16x16 sheet, 2x2 grid of 8x8 cells for chars ["AB","CD"]: 'A' inked to column 5 (ink 6),
     * 'B' fully transparent (ink 0), 'C' inked only at columns 3-5 (leading blanks, ink 6), 'D'
     * inked to the last column (ink 8).
     */
    private static BufferedImage metricsSheet() {
        BufferedImage image = sheet(16, 16);
        image.setRGB(5, 0, OPAQUE_WHITE);
        image.setRGB(3, 8, OPAQUE_WHITE);
        image.setRGB(4, 8, OPAQUE_WHITE);
        image.setRGB(5, 8, OPAQUE_WHITE);
        image.setRGB(15, 8, OPAQUE_WHITE);
        return image;
    }

    private static PackFont metricsFont(int height, int ascent) {
        return PackFont.create("test:metrics",
            List.of(bitmap("test:font/grid.png", height, ascent, List.of("AB", "CD"))),
            textures(Map.of("test:font/grid.png", metricsSheet())));
    }

    @Test
    void advanceAtScaleOne() {
        PackFont font = metricsFont(8, 7);
        assertEquals(7.0f, font.advanceOf('A', false), "ink 6: (int)(0.5 + 6*1) + 1");
        assertEquals(9.0f, font.advanceOf('D', false), "ink 8: full-width cell");
    }

    @Test
    void leadingBlankColumnsCountTowardInkWidth() {
        PackFont font = metricsFont(8, 7);
        assertEquals(font.advanceOf('A', false), font.advanceOf('C', false),
            "'C' has leading blank columns but the same rightmost inked column as 'A'");
    }

    @Test
    void emptyCellAdvanceIsOne() {
        PackFont font = metricsFont(8, 7);
        assertEquals(1.0f, font.advanceOf('B', false));
        assertTrue(font.glyph('B').orElseThrow().isEmpty());
    }

    @Test
    void boldAddsExactlyOneAtAnyScale() {
        assertEquals(8.0f, metricsFont(8, 7).advanceOf('A', true));
        assertEquals(5.0f, metricsFont(4, 3).advanceOf('A', true), "scale 0.5: unbold 4, bold 5");
    }

    @Test
    void advanceAtScaleHalf() {
        PackFont font = metricsFont(4, 3);
        assertEquals(4.0f, font.advanceOf('A', false), "ink 6 * 0.5 = 3.0 -> 3 + 1");
        assertEquals(5.0f, font.advanceOf('D', false), "ink 8 * 0.5 = 4.0 -> 4 + 1");
    }

    @Test
    void advanceAtScaleHalfRoundsHalfUpBeforeThePlusOne() {
        // Cells with ink 5 and ink 7 at scale 0.5: 2.5 rounds to 3, 3.5 rounds to 4.
        BufferedImage image = sheet(16, 8);
        image.setRGB(4, 0, OPAQUE_WHITE);
        image.setRGB(14, 0, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:rounding",
            List.of(bitmap("test:font/r.png", 4, 3, List.of("EF"))),
            textures(Map.of("test:font/r.png", image)));
        assertEquals(4.0f, font.advanceOf('E', false), "ink 5: (int)(0.5 + 2.5) + 1");
        assertEquals(5.0f, font.advanceOf('F', false), "ink 7: (int)(0.5 + 3.5) + 1");
    }

    @Test
    void advanceAtScaleTwo() {
        assertEquals(13.0f, metricsFont(16, 14).advanceOf('A', false), "ink 6 * 2 = 12 -> 12 + 1");
    }

    @Test
    void advanceAtFractionalScaleSevenTwelfths() {
        // 24x24 sheet, 12x12 cells, height 7: scale = 7/12.
        BufferedImage image = sheet(24, 24);
        image.setRGB(11, 0, OPAQUE_WHITE);
        image.setRGB(22, 0, OPAQUE_WHITE);
        image.setRGB(5, 12, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:fractional",
            List.of(bitmap("test:font/f.png", 7, 7, List.of("GH", "IJ"))),
            textures(Map.of("test:font/f.png", image)));
        assertEquals(8.0f, font.advanceOf('G', false), "ink 12 * 7/12 = 7.0 -> 7.5 truncates to 7, + 1");
        assertEquals(7.0f, font.advanceOf('H', false), "ink 11 * 7/12 = 6.42 -> 6 + 1");
        assertEquals(5.0f, font.advanceOf('I', false), "ink 6 * 7/12 = 3.5 -> 4 + 1");
    }

    @Test
    void nulGridEntriesAreSkippedEntirely() {
        BufferedImage image = sheet(24, 8);
        image.setRGB(0, 0, OPAQUE_WHITE);
        image.setRGB(9, 0, OPAQUE_WHITE);
        image.setRGB(18, 0, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:nul",
            List.of(bitmap("test:font/n.png", 8, 7, List.of("A\0B"))),
            textures(Map.of("test:font/n.png", image)));
        assertEquals(Optional.empty(), font.glyph(0), "U+0000 never maps a glyph");
        assertEquals(2.0f, font.advanceOf('A', false), "first cell, ink 1");
        assertEquals(4.0f, font.advanceOf('B', false), "THIRD cell (the U+0000 cell is consumed), ink 3");
    }

    @Test
    void duplicateCodepointInOneProviderLastOccurrenceWins() {
        BufferedImage image = sheet(16, 8);
        image.setRGB(1, 0, OPAQUE_WHITE);
        image.setRGB(11, 0, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:dupe",
            List.of(bitmap("test:font/d.png", 8, 7, List.of("AA"))),
            textures(Map.of("test:font/d.png", image)));
        assertEquals(5.0f, font.advanceOf('A', false), "second cell (ink 4) wins over first (ink 2)");
    }

    @Test
    void unmappedCodepointResolvesEmptyAndAdvancesZero() {
        PackFont font = metricsFont(8, 7);
        assertEquals(Optional.empty(), font.glyph('Z'));
        assertEquals(0.0f, font.advanceOf('Z', false));
    }

    @Test
    void sheetSmallerThanGridFailsLoudly() {
        assertThrows(PackResolveException.class, () -> PackFont.create("test:tiny",
            List.of(bitmap("test:font/t.png", 8, 7, List.of("AB"))),
            textures(Map.of("test:font/t.png", sheet(1, 1)))));
    }

    @Test
    void firstListedProviderWinsForSharedCodepoint() {
        BufferedImage narrow = sheet(8, 8);
        narrow.setRGB(1, 0, OPAQUE_WHITE);
        BufferedImage wide = sheet(8, 8);
        wide.setRGB(4, 0, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:priority", List.of(
                bitmap("test:font/narrow.png", 8, 7, List.of("A")),
                bitmap("test:font/wide.png", 8, 7, List.of("A"))),
            textures(Map.of("test:font/narrow.png", narrow, "test:font/wide.png", wide)));
        assertEquals(3.0f, font.advanceOf('A', false), "first provider (ink 2) wins over second (ink 5)");
    }

    @Test
    void firstListedProviderWinsAcrossReferenceBoundary() {
        BufferedImage narrow = sheet(8, 8);
        narrow.setRGB(1, 0, OPAQUE_WHITE);
        BufferedImage wide = sheet(16, 8);
        wide.setRGB(4, 0, OPAQUE_WHITE);
        wide.setRGB(14, 0, OPAQUE_WHITE);
        Map<String, List<FontProviderDefinition>> fonts = new HashMap<>();
        fonts.put("test:root", List.of(
            bitmap("test:font/narrow.png", 8, 7, List.of("A")),
            new FontProviderDefinition.Reference("test:other", FontFilter.none())));
        fonts.put("test:other", List.of(bitmap("test:font/wide.png", 8, 7, List.of("AB"))));
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:root",
            fontId -> Optional.ofNullable(fonts.get(fontId)));
        PackFont font = PackFont.create("test:root", resolved,
            textures(Map.of("test:font/narrow.png", narrow, "test:font/wide.png", wide)));
        assertEquals(3.0f, font.advanceOf('A', false), "root's own provider precedes the reference");
        assertEquals(8.0f, font.advanceOf('B', false), "codepoints only in the referenced font resolve");
    }

    @Test
    void referenceListedFirstWinsOverLaterOwnProvider() {
        BufferedImage narrow = sheet(8, 8);
        narrow.setRGB(1, 0, OPAQUE_WHITE);
        BufferedImage wide = sheet(8, 8);
        wide.setRGB(4, 0, OPAQUE_WHITE);
        Map<String, List<FontProviderDefinition>> fonts = new HashMap<>();
        fonts.put("test:root", List.of(
            new FontProviderDefinition.Reference("test:other", FontFilter.none()),
            bitmap("test:font/narrow.png", 8, 7, List.of("A"))));
        fonts.put("test:other", List.of(bitmap("test:font/wide.png", 8, 7, List.of("A"))));
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:root",
            fontId -> Optional.ofNullable(fonts.get(fontId)));
        PackFont font = PackFont.create("test:root", resolved,
            textures(Map.of("test:font/narrow.png", narrow, "test:font/wide.png", wide)));
        assertEquals(6.0f, font.advanceOf('A', false), "the reference expands at its (first) position");
    }

    @Test
    void spaceGlyphMetrics() {
        PackFont font = PackFont.create("test:space", List.of(space(Map.of(
            (int) ' ', 4.0f, (int) 'a', -3.5f, (int) 'b', 0.0f, (int) 'c', 2.25f))), ref -> {
            throw new AssertionError("space fonts load no textures");
        });
        PackGlyph spaceGlyph = font.glyph(' ').orElseThrow();
        assertEquals(4.0f, spaceGlyph.advance(false));
        assertEquals(5.0f, spaceGlyph.advance(true), "bold adds exactly +1");
        assertTrue(spaceGlyph.isEmpty());
        assertEquals(0, spaceGlyph.height());
        assertEquals(0, spaceGlyph.ascent());
        assertEquals(-3.5f, font.advanceOf('a', false), "negative advances are legal");
        assertEquals(-2.5f, font.advanceOf('a', true));
        assertEquals(1.0f, font.advanceOf('b', true), "zero advance, bold +1");
        assertEquals(2.25f, font.advanceOf('c', false), "fractional advances are legal");
    }

    @Test
    void spaceAdvanceReportsOnlySpaceWonCodepoints() {
        PackFont font = PackFont.create("test:mixed", List.of(
                space(Map.of((int) ' ', 4.0f)),
                bitmap("test:font/grid.png", 8, 7, List.of("AB", "CD"))),
            textures(Map.of("test:font/grid.png", metricsSheet())));
        assertEquals(OptionalDouble.of(4.0), font.spaceAdvance(' '));
        assertEquals(OptionalDouble.empty(), font.spaceAdvance('A'), "bitmap-won codepoint");
        assertEquals(OptionalDouble.empty(), font.spaceAdvance('Z'), "unmapped codepoint");
    }

    @Test
    void spaceProviderListedFirstBeatsBitmapForSharedCodepoint() {
        BufferedImage image = sheet(8, 8);
        image.setRGB(4, 0, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:clash", List.of(
                space(Map.of((int) 'A', 2.5f)),
                bitmap("test:font/a.png", 8, 7, List.of("A"))),
            textures(Map.of("test:font/a.png", image)));
        assertEquals(2.5f, font.advanceOf('A', false));
        assertEquals(OptionalDouble.of(2.5), font.spaceAdvance('A'));
    }

    @Test
    void ttfProviderNeverClaimsLettingLaterProvidersServe() {
        PackFont font = PackFont.create("test:ttf", List.of(
            new FontProviderDefinition.Ttf("test:font/x.ttf", 11.0f, 1.0f, 0.0f, 0.0f,
                Set.of(), FontFilter.none()),
            space(Map.of((int) 'a', 3.0f))), ref -> {
            throw new AssertionError("ttf providers load no textures in this library");
        });
        assertEquals(3.0f, font.advanceOf('a', false), "the later space provider serves the codepoint");
    }

    @Test
    void hasUnsupportedProviderForHonorsTtfSkip() {
        PackFont font = PackFont.create("test:ttfskip", List.of(
            new FontProviderDefinition.Ttf("test:font/x.ttf", 11.0f, 1.0f, 0.0f, 0.0f,
                Set.of((int) 'x', (int) 'y', (int) 'z'), FontFilter.none())), ref -> {
            throw new AssertionError("no textures");
        });
        assertTrue(font.hasUnsupportedProviderFor('a'), "the TTF might have served 'a'");
        assertFalse(font.hasUnsupportedProviderFor('x'), "'x' is in the TTF skip set");
        assertEquals(Optional.empty(), font.glyph('a'), "unsupported providers never claim");
    }

    @Test
    void unihexCountsAsUnsupportedForEveryCodepoint() {
        PackFont font = PackFont.create("test:unihex", List.of(
            new FontProviderDefinition.Unsupported("unihex", FontFilter.none())), ref -> {
            throw new AssertionError("no textures");
        });
        assertTrue(font.hasUnsupportedProviderFor('a'));
        assertTrue(font.hasUnsupportedProviderFor(0x10348));
        assertEquals(Optional.empty(), font.glyph('a'));
    }

    @Test
    void fontWithoutUnsupportedProvidersReportsFalse() {
        assertFalse(metricsFont(8, 7).hasUnsupportedProviderFor('A'));
    }

    @Test
    void earlierRenderableProviderSuppressesUnsupportedFidelityGap() {
        // [bitmap covering 'A', ttf]: vanilla's first-wins order serves the bitmap for 'A' and
        // never reaches the ttf, so no fidelity gap exists for 'A'; 'B' falls through to the ttf.
        BufferedImage image = sheet(8, 8);
        image.setRGB(1, 0, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:order", List.of(
                bitmap("test:font/a.png", 8, 7, List.of("A")),
                new FontProviderDefinition.Ttf("test:font/x.ttf", 11.0f, 1.0f, 0.0f, 0.0f,
                    Set.of(), FontFilter.none())),
            textures(Map.of("test:font/a.png", image)));
        assertFalse(font.hasUnsupportedProviderFor('A'), "the bitmap claims 'A' first, exactly as vanilla would");
        assertTrue(font.hasUnsupportedProviderFor('B'), "'B' is only covered by the ttf");
    }

    @Test
    void unsupportedProviderListedBeforeRenderableStillReportsGap() {
        // [ttf, bitmap covering 'A']: vanilla would have served the TTF glyph for 'A', so the
        // fidelity gap is real even though this library serves the later bitmap.
        BufferedImage image = sheet(8, 8);
        image.setRGB(1, 0, OPAQUE_WHITE);
        PackFont font = PackFont.create("test:gap", List.of(
                new FontProviderDefinition.Ttf("test:font/x.ttf", 11.0f, 1.0f, 0.0f, 0.0f,
                    Set.of(), FontFilter.none()),
                bitmap("test:font/a.png", 8, 7, List.of("A"))),
            textures(Map.of("test:font/a.png", image)));
        assertTrue(font.hasUnsupportedProviderFor('A'));
        assertEquals(3.0f, font.advanceOf('A', false), "the bitmap still serves the codepoint here");
    }

    @Test
    void retainedCellBytesCountsOnlyInkedCells() {
        // 'A', 'C' and 'D' have ink (8x8 ARGB cells); 'B' is fully transparent and retains no raster.
        assertEquals(3L * 8 * 8 * 4, metricsFont(8, 7).retainedCellBytes());
    }

    @Test
    void sharedProviderCacheLoadsEachSheetOnceAcrossFonts() {
        AtomicInteger loads = new AtomicInteger();
        PackFont.TextureLoader countingLoader = ref -> {
            loads.incrementAndGet();
            return metricsSheet();
        };
        BitmapProviderCache shared = new BitmapProviderCache();
        FontProviderDefinition.Bitmap definition = bitmap("test:font/grid.png", 8, 7, List.of("AB", "CD"));
        PackFont first = PackFont.create("test:one", List.of(definition), countingLoader, shared);
        PackFont second = PackFont.create("test:two", List.of(definition), countingLoader, shared);
        assertEquals(1, loads.get(), "the second font reuses the shared provider without re-decoding");
        assertSame(first.glyph('A').orElseThrow(), second.glyph('A').orElseThrow(),
            "shared providers mean shared glyph instances, not duplicated cell copies");
    }

    @Test
    void bitmapDefinitionValidatesCharRowsAtConstruction() {
        // Any construction path (not just PackFontParser) must fail with a diagnostic instead of
        // an ArrayIndexOutOfBoundsException while cutting the sheet.
        assertThrows(IllegalArgumentException.class,
            () -> new FontProviderDefinition.Bitmap("a:b.png", 8, 7, List.of("A", "BC"), FontFilter.none()),
            "unequal row codepoint counts");
        assertThrows(IllegalArgumentException.class,
            () -> new FontProviderDefinition.Bitmap("a:b.png", 8, 7, List.of(), FontFilter.none()),
            "empty charRows");
        assertThrows(IllegalArgumentException.class,
            () -> new FontProviderDefinition.Bitmap("a:b.png", 8, 7, List.of(""), FontFilter.none()),
            "empty row string");
    }

    @Test
    void unexpandedReferenceInCreateFailsLoudly() {
        assertThrows(PackResolveException.class, () -> PackFont.create("test:raw",
            List.of(new FontProviderDefinition.Reference("test:other", FontFilter.none())), ref -> {
                throw new AssertionError("no textures");
            }));
    }

    @Test
    void idIsExposed() {
        assertEquals("test:metrics", metricsFont(8, 7).id());
    }
}
