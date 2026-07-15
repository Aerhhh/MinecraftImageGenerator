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
    void missingFontTextureFailsLoudly() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveFont("testpack:notexture"));
        assertTrue(exception.getMessage().contains("missing.png"));
    }

    @Test
    void ttfOnlyFontLoadsButClaimsNothing() {
        PackFont font = pack.resolveFont("testpack:ttf_only").orElseThrow();
        assertEquals(Optional.empty(), font.glyph('a'));
        assertTrue(font.hasUnsupportedProviderFor('a'));
        assertFalse(font.hasUnsupportedProviderFor('x'), "'x' is in the TTF skip set");
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
        // and duplicate every glyph cell (the Wynncraft N-fonts-one-base blowup).
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
