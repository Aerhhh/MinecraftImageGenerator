package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.pack.font.PackFont;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedPackFontTest {

    @TempDir
    Path packDir;

    private LoadedPack pack;

    @BeforeEach
    void loadFontFixturePack() {
        FixturePacks.writeFontPack(packDir);
        pack = new LoadedPack(PackId.parse("test:fontpack"),
            PackSource.directory(packDir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
    }

    @Test
    void fontOnlyPackRegisters() {
        // The fixture has no items and no tooltip sprites; fonts alone make a pack viable.
        assertTrue(pack.assetNamespaces().contains(FixturePacks.NAMESPACE));
    }

    @Test
    void resolvesBitmapFontWithVanillaMetrics() {
        PackFont font = pack.resolveFont("testpack:pixel").orElseThrow();
        assertEquals("testpack:pixel", font.id());
        assertEquals(7.0f, font.advanceOf('A', false), "ink 6 at scale 1");
        assertEquals(1.0f, font.advanceOf('B', false), "empty cell");
    }

    @Test
    void resolveFontCachesTheResolvedInstance() {
        PackFont first = pack.resolveFont("testpack:pixel").orElseThrow();
        PackFont second = pack.resolveFont("testpack:pixel").orElseThrow();
        assertSame(first, second);
    }

    @Test
    void unknownFontIdResolvesEmpty() {
        assertEquals(Optional.empty(), pack.resolveFont("testpack:nope"));
    }

    @Test
    void bareFontIdDefaultsToMinecraftNamespace() {
        PackFont font = pack.resolveFont("barefont").orElseThrow();
        assertEquals("minecraft:barefont", font.id());
        assertEquals(OptionalDouble.of(6.0), font.spaceAdvance(' '));
    }

    @Test
    void malformedFontIdThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> pack.resolveFont("a:b:c"));
    }

    @Test
    void brokenFontJsonFailsLoudly() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveFont("testpack:broken"));
        assertTrue(exception.getMessage().contains("testpack:broken"));
    }

    @Test
    void referenceExpandsAcrossFontFiles() {
        PackFont font = pack.resolveFont("testpack:alias").orElseThrow();
        assertEquals(7.0f, font.advanceOf('A', false), "glyph served by the referenced font");
        assertEquals(4.5f, font.advanceOf(' ', false), "own space provider after the reference");
    }

    @Test
    void missingReferenceTargetFailsLoudly() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveFont("testpack:dangling"));
        assertTrue(exception.getMessage().contains("testpack:nope"));
    }

    @Test
    void referenceCycleFailsLoudly() {
        assertThrows(PackResolveException.class, () -> pack.resolveFont("testpack:cycle_a"));
    }

    @Test
    void fontSheetAboveItemCapButBelowFontCapDecodes() {
        // 2048 px sheet: above the default item texture cap (1024), below the font cap (8192).
        PackFont font = pack.resolveFont("testpack:bigsheet").orElseThrow();
        assertEquals(2.0f, font.advanceOf('A', false), "ink 1 at scale 1");
    }

    @Test
    void fontSheetAboveFontCapFailsLoudly() {
        LoadedPack cappedPack = new LoadedPack(PackId.parse("test:capped"),
            PackSource.directory(packDir, PackLimits.fromSystemProperties()),
            new PackLimits(20_000, 8L * 1024 * 1024, 1_024, 64L * 1024 * 1024, 1_024));
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> cappedPack.resolveFont("testpack:bigsheet"));
        assertTrue(exception.getMessage().contains("big.png"));
    }

    @Test
    void allAbsentSheetsStillResolveAsAnEmptyFont() {
        // Deviation from vanilla, documented on resolveFont: absent sheets skip their provider
        // rather than failing the font; a font whose providers ALL skip resolves empty.
        PackFont font = pack.resolveFont("testpack:notexture").orElseThrow();
        assertEquals(Optional.empty(), font.glyph('A'));
        assertEquals(List.of(), font.mappedCodePoints());
    }

    @Test
    void absentSheetProviderSkipsWhilePresentProvidersServe() {
        // mixed_sheets lists the absent-sheet provider FIRST with chars "AC": after the skip,
        // 'A' falls through to the present pixel.png provider, 'C' (absent-only) is unmapped,
        // and the trailing space provider survives untouched.
        PackFont font = pack.resolveFont("testpack:mixed_sheets").orElseThrow();
        assertEquals(7.0f, font.advanceOf('A', false), "'A' is served by the present sheet after the skip");
        assertEquals(1.0f, font.advanceOf('B', false), "empty cell of the present sheet");
        assertEquals(Optional.empty(), font.glyph('C'), "codepoints only the absent provider mapped stay unmapped");
        assertEquals(OptionalDouble.of(2.0), font.spaceAdvance(' '));
    }

    @Test
    void vanillaSheetFallbackResolvesRealGlyphs() {
        // vanilla_ascii references minecraft:font/ascii.png, which the pack does not ship; the
        // bundled vanilla sheet supplies real bitmap glyphs (not the OTF fallback).
        PackFont font = pack.resolveFont("minecraft:vanilla_ascii").orElseThrow();
        assertEquals(6.0f, font.advanceOf('A', false), "vanilla ascii 'A' ink 5 -> advance 6");
        assertEquals(2.0f, font.advanceOf('i', false), "vanilla ascii 'i' ink 1 -> advance 2");
        assertFalse(font.glyph('A').orElseThrow().isEmpty(), "a real inked bitmap glyph");
        assertEquals(7, font.glyph('A').orElseThrow().ascent(), "the pack's declared vanilla ascent");
    }

    @Test
    void defaultOffsetReDeclaredAscentShiftsRenderingDown() {
        // vanilla_offset is the same bundled sheet with ascent -3 (vanilla ascii ascent is 7); the
        // 10 GUI px shift comes entirely from the re-declared ascent, the advance is unchanged.
        PackFont normal = pack.resolveFont("minecraft:vanilla_ascii").orElseThrow();
        PackFont offset = pack.resolveFont("minecraft:vanilla_offset").orElseThrow();
        assertEquals(7, normal.glyph('A').orElseThrow().ascent());
        assertEquals(-3, offset.glyph('A').orElseThrow().ascent(),
            "ascent 7 -> -3 places the cell top 10 GUI px lower");
        assertEquals(normal.advanceOf('A', false), offset.advanceOf('A', false),
            "only vertical placement shifts; the horizontal advance is unchanged");
    }

    @Test
    void minecraftSheetOutsideBundledSetStillSkips() {
        // minecraft:font/unifont.png is a minecraft-namespace sheet NOT in the bundled fallback
        // set, so the provider skips exactly like any non-vanilla absent sheet.
        PackFont font = pack.resolveFont("minecraft:vanilla_unmapped").orElseThrow();
        assertEquals(Optional.empty(), font.glyph('A'), "no bundled fallback for this sheet");
        assertEquals(List.of(), font.mappedCodePoints());
    }

    @Test
    void packShippedVanillaSheetWinsOverBundle(@TempDir Path shippedDir) {
        // The pack ships its OWN minecraft:font/ascii.png (a 16x16 sheet, 1x1 cells): 'A' resolves
        // from the shipped ink-1 cell (advance 2), never the bundled vanilla sheet (advance 6).
        FixturePacks.writeVanillaShippedFontPack(shippedDir);
        LoadedPack shippedPack = new LoadedPack(PackId.parse("test:shipped"),
            PackSource.directory(shippedDir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
        PackFont font = shippedPack.resolveFont("minecraft:vanilla_ascii").orElseThrow();
        assertEquals(2.0f, font.advanceOf('A', false),
            "the shipped 1x1 cell (ink 1) wins over the bundled vanilla sheet (ink 5)");
    }

    @Test
    void ttfOnlyFontLoadsButClaimsNothing() {
        PackFont font = pack.resolveFont("testpack:ttf_only").orElseThrow();
        assertEquals(Optional.empty(), font.glyph('a'));
        assertTrue(font.hasUnsupportedProviderFor('a'));
        assertFalse(font.hasUnsupportedProviderFor('x'), "'x' is in the TTF skip set");
    }

    /** 'A' box advance 20, 'B' box advance 12, at ppem 32 (size 8, oversample 4). */
    private static byte[] narrowTtf() {
        java.util.LinkedHashMap<Integer, net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.Glyph> glyphs =
            new java.util.LinkedHashMap<>();
        glyphs.put((int) 'A', net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.Glyph.box(20, 0, 0, 16, 24));
        glyphs.put((int) 'B', net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.Glyph.box(12, 0, 0, 8, 16));
        return net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.build(32, glyphs);
    }

    /** The same internal font name but a wider 'A' box (advance 28), for the no-collision test. */
    private static byte[] wideTtf() {
        java.util.LinkedHashMap<Integer, net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.Glyph> glyphs =
            new java.util.LinkedHashMap<>();
        glyphs.put((int) 'A', net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.Glyph.box(28, 0, 0, 28, 24));
        return net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.build(32, glyphs);
    }

    private static LoadedPack loadOtfPack(Path dir, byte[] ttf) {
        FixturePacks.writeOtfFontPack(dir, ttf);
        return new LoadedPack(PackId.parse("test:otf"),
            PackSource.directory(dir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
    }

    @Test
    void shippedTtfRendersRealVectorGlyphs(@TempDir Path otfDir) {
        LoadedPack otfPack = loadOtfPack(otfDir, narrowTtf());
        PackFont font = otfPack.resolveFont("testpack:otf").orElseThrow();
        assertEquals(5.0f, font.advanceOf('A', false), "ttf 'A' device advance 20 / oversample 4");
        assertFalse(font.glyph('A').orElseThrow().isEmpty(), "a real rasterized glyph, not the OTF fallthrough");
        assertEquals(6, font.glyph('A').orElseThrow().ascent(), "24 device px above baseline / oversample 4");
        assertEquals(Optional.empty(), font.glyph('Z'), "codepoints the font cannot display stay unclaimed");
    }

    @Test
    void minecraftDefaultReferencingTtfRendersTheTtfGlyph(@TempDir Path otfDir) {
        // The fixture's minecraft:default references testpack:otf, so default-font text renders the
        // ttf glyph rather than the bundled OTF.
        LoadedPack otfPack = loadOtfPack(otfDir, narrowTtf());
        PackFont font = otfPack.resolveFont("minecraft:default").orElseThrow();
        assertEquals(5.0f, font.advanceOf('A', false), "the referenced ttf serves 'A'");
    }

    @Test
    void shippedButBrokenTtfDegradesToUnsupported(@TempDir Path otfDir) {
        LoadedPack otfPack = loadOtfPack(otfDir, narrowTtf());
        PackFont font = otfPack.resolveFont("testpack:otf_broken").orElseThrow();
        assertEquals(Optional.empty(), font.glyph('A'), "an unloadable shipped ttf claims nothing");
        assertTrue(font.hasUnsupportedProviderFor('A'), "and still reports the fidelity gap");
    }

    @Test
    void sameNamedTtfInTwoPacksDoesNotCollide(@TempDir Path narrowDir, @TempDir Path wideDir) {
        // Both fonts share the internal name "TestPixel"; per-pack createFont (never a global
        // registerFont) means each pack renders its OWN 'A' geometry.
        LoadedPack narrow = loadOtfPack(narrowDir, narrowTtf());
        LoadedPack wide = loadOtfPack(wideDir, wideTtf());
        assertEquals(5.0f, narrow.resolveFont("testpack:otf").orElseThrow().advanceOf('A', false));
        assertEquals(7.0f, wide.resolveFont("testpack:otf").orElseThrow().advanceOf('A', false),
            "the second pack's wider 'A' (device advance 28 / 4) is unaffected by the first");
    }

    @Test
    void fontIdsAreSortedAndComplete() {
        List<String> ids = pack.fontIds();
        assertTrue(ids.contains("testpack:pixel"));
        assertTrue(ids.contains("minecraft:barefont"));
        assertEquals(ids.stream().sorted().toList(), ids);
    }

    @Test
    void nestedFontIdResolves() {
        // Real packs nest font paths (e.g. minecraft:tooltip/emblem/frame); the index and the
        // resolve key derivation must both handle multi-segment paths.
        PackFont font = pack.resolveFont("testpack:sub/deco").orElseThrow();
        assertEquals("testpack:sub/deco", font.id());
        assertEquals(OptionalDouble.of(3.0), font.spaceAdvance(' '));
    }

    @Test
    void invalidResourceLocationFontsAreSkippedAtIndexTime() {
        // assets/testpack/font/Fancy.json is not a valid resource location: advertising it would
        // break the fontIds -> resolveFont round-trip (resolveFont would throw IAE for an id the
        // library itself handed out).
        assertFalse(pack.fontIds().stream().anyMatch(id -> id.contains("Fancy")),
            "invalid resource locations are skipped at index time, mirroring vanilla");
    }

    @Test
    void everyAdvertisedFontIdIsParseableByResolveFont() {
        // IllegalArgumentException means malformed CALLER input; ids listed by fontIds() come
        // from the library, so none of them may trigger it. PackResolveException is still legal
        // (the fixture deliberately ships broken fonts).
        for (String id : pack.fontIds()) {
            try {
                pack.resolveFont(id);
            } catch (PackResolveException expectedForBrokenFixtureFonts) {
                // present-but-broken is the documented loud contract
            }
        }
    }

    @Test
    void fontsReferencingTheSameSheetShareOneBuiltProvider() {
        // alias references pixel: without sharing, each resolved font would re-decode the sheet
        // and duplicate every glyph cell (the N-fonts-one-base blowup real packs exhibit).
        PackFont pixel = pack.resolveFont("testpack:pixel").orElseThrow();
        PackFont alias = pack.resolveFont("testpack:alias").orElseThrow();
        assertSame(pixel.glyph('A').orElseThrow(), alias.glyph('A').orElseThrow(),
            "both fonts serve the same shared glyph instance");
    }

    @Test
    void repositoryExposesFontResolution() {
        PackRepository repository = new PackRepository();
        PackId id = repository.register("repo:fontpack",
            PackSource.directory(packDir, PackLimits.fromSystemProperties()));
        PackFont font = repository.resolveFont(id, "testpack:pixel").orElseThrow();
        assertEquals(7.0f, font.advanceOf('A', false));
        assertTrue(repository.fontIds(id).contains("testpack:pixel"));
        assertThrows(PackResolveException.class,
            () -> repository.resolveFont(PackId.parse("repo:unregistered"), "testpack:pixel"));
    }
}
