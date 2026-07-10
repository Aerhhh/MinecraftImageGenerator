package net.aerh.imagegenerator.data;

import net.aerh.imagegenerator.text.ChatFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackGlyphIndexTest {

    private static Icon icon(String name, String character, Map<String, String> overrides) {
        return new Icon(name, character, overrides);
    }

    private static Stat stat(String name, Map<String, String> overrides) {
        return new Stat("\u2741", name, "Test", "\u2741 Test", ChatFormat.RED, null, "NORMAL", null, overrides);
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
        // Stat glyphs never join the icon-owned tier; they live in the separate stat tier that
        // regenerates through %%icon:<stat>%% instead of expanding into formatted stat text.
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(), List.of(stat("strengthlike", Map.of("hypixel:skyblock", "\uE100"))));

        assertTrue(index.getBareCharacterOwners().isEmpty());
    }

    @Test
    void statCharactersOwnBareCharactersWhenNoIconClaimsThem() {
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(), List.of(stat("healthlike", Map.of("hypixel:skyblock", ""))));

        assertEquals("healthlike", index.getStatBareCharacterOwners().get("❁").getName());
        assertEquals("healthlike", index.getStatBareCharacterOwners().get("").getName());
    }

    @Test
    void iconClaimedCharactersStayOutOfTheStatBareDomain() {
        // Icons regenerate a bare character faithfully via %%name%%, so they keep precedence over
        // the %%icon:<stat>%% form for contested characters (e.g. tracking vs mob_elusive).
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(icon("alpha", "", null)),
            List.of(stat("healthlike", Map.of("hypixel:skyblock", ""))));

        assertEquals("alpha", index.getBareCharacterOwners().get("").getName());
        assertFalse(index.getStatBareCharacterOwners().containsKey(""));
        assertEquals("healthlike", index.getStatBareCharacterOwners().get("❁").getName());
    }

    @Test
    void sharedStatCharactersResolveToTheFirstStatInFileOrder() {
        PackGlyphIndex index = PackGlyphIndex.build(
            List.of(), List.of(stat("first", null), stat("second", null)));

        assertEquals("first", index.getStatBareCharacterOwners().get("❁").getName());
    }

    @Test
    void iconEntryNamedIconFailsValidation() {
        // "icon" is the reserved %%icon:<name>%% keyword handled before any registry lookup
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(icon("icon", "", null)), List.of()));

        assertTrue(e.getMessage().contains("reserved"));
    }

    @Test
    void statEntryNamedIconFailsValidation() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(), List.of(stat("icon", null))));

        assertTrue(e.getMessage().contains("reserved"));
    }

    @Test
    void reservedRegistryEntryNamedIconFailsValidation() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PackGlyphIndex.build(
            List.of(), List.of(), Map.of("icon", "flavor.json")));

        assertTrue(e.getMessage().contains("reserved"));
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
            List.of(icon("pest", "\uE018", null)), List.of(), Map.of("pest", "flavor.json")));

        assertTrue(e.getMessage().contains("flavor.json"));
    }
}
