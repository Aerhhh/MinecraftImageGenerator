package net.aerh.imagegenerator.data;

import net.aerh.imagegenerator.text.ChatFormat;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackGlyphIndexTest {

    private static Icon icon(String name, String character, Map<String, String> overrides) {
        return new Icon(name, character, overrides);
    }

    private static Stat stat(String name, Map<String, String> overrides) {
        return new Stat("\u2741", name, "Test", "\u2741 Test", ChatFormat.RED, null, "NORMAL", null, overrides);
    }

    private static Flavor flavor(String name, Map<String, String> overrides) {
        return new Flavor("\u0f15", name, "Test", "\u0f15 Test", ChatFormat.DARK_GREEN, null, "NORMAL", overrides);
    }

    private static Gemstone gemstone(String name, Map<String, String> overrides) {
        return new Gemstone(name, "\u2764", "&c\u2764", Color.RED, Map.of("locked", "&8[%s]&r"), overrides);
    }

    @Test
    void singleOwnerResolves() {
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(icon("alpha", "\uE100", null)), List.of());

        assertEquals("alpha", index.getBareCharacterOwners().get("\uE100").getName());
    }

    @Test
    void sharedCharacterResolvesToTheFirstEntryInFileOrder() {
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(icon("alpha", "\uE100", null), icon("beta", "\uE100", null)), List.of());

        assertEquals("alpha", index.getBareCharacterOwners().get("\uE100").getName(),
            "shared characters resolve to the first claimant; the difference is only which"
                + " placeholder name /gen parse prints");
    }

    @Test
    void overrideCharactersJoinTheBareDomain() {
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(icon("alpha", "\u23E3", Map.of("hypixel:skyblock", "\uE067"))), List.of());

        assertEquals("alpha", index.getBareCharacterOwners().get("\u23E3").getName());
        assertEquals("alpha", index.getBareCharacterOwners().get("\uE067").getName());
    }

    @Test
    void namesWithDigitsFailValidation() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(icon("arrow_1", "\uE100", null)), List.of()));

        assertTrue(e.getMessage().contains("arrow_1"),
            "digits are not matched by the %%placeholder%% pattern, so the name must be rejected");
    }

    @Test
    void invalidPackKeyFailsValidation() {
        assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(icon("alpha", "\uE100", Map.of("NotAPack", "\uE101"))), List.of()));
    }

    @Test
    void multiCodePointOverrideValueFailsValidation() {
        assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(icon("alpha", "\uE100", Map.of("hypixel:skyblock", "ab"))), List.of()));
    }

    @Test
    void statOverridesAreValidatedToo() {
        assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(), List.of(stat("alphastat", Map.of("no_colon_here", "\uE100")))));
    }

    @Test
    void statOverrideCharactersDoNotOwnBareCharacters() {
        // Stat glyphs reverse map through stat format rules; a bare-char rule would expand back
        // into formatted stat text and break round-trip fidelity.
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(), List.of(stat("strengthlike", Map.of("hypixel:skyblock", "\uE100"))));

        assertTrue(index.getBareCharacterOwners().isEmpty());
    }

    @Test
    void iconNameCollidingWithAStatNameFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(icon("strengthlike", "\uE100", null)), List.of(stat("strengthlike", null))));

        assertTrue(e.getMessage().contains("IconParser runs first"));
    }

    @Test
    void iconNameCollidingWithAReservedRegistryNameFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(icon("pest", "\uE018", null)), List.of(), List.of(flavor("pest", null)), List.of()));

        assertTrue(e.getMessage().contains("flavor.json"));
    }

    @Test
    void flavorOverridesAreValidatedToo() {
        assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(), List.of(), List.of(flavor("alphaflavor", Map.of("no_colon_here", "\uE100"))), List.of()));
    }

    @Test
    void gemstoneOverridesAreValidatedToo() {
        assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(), List.of(), List.of(), List.of(gemstone("gem_alpha", Map.of("hypixel:skyblock", "ab")))));
    }

    @Test
    void flavorAndGemstoneOverrideCharactersDoNotOwnBareCharacters() {
        // Flavor and gemstone glyphs reverse map through their own format rules, exactly like
        // stat glyphs; only icons.json entries own bare characters.
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(), List.of(),
            List.of(flavor("alphaflavor", Map.of("hypixel:skyblock", "\uE100"))),
            List.of(gemstone("gem_alpha", Map.of("hypixel:skyblock", "\uE101"))));

        assertTrue(index.getBareCharacterOwners().isEmpty());
    }
}
