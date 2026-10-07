package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLineage;
import net.aerh.imagegenerator.parser.ParseContext;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pack-conditional placeholder substitution through the parser pipeline.
 */
class PackOverrideParsingTest {

    private static final ParseContext HYPIXEL = ParseContext.of(PackId.parse("hypixel:skyblock"));
    private static final ParseContext ALPHA_VARIANT = ParseContext.of(
        new PackLineage(PackId.parse("hypixel:alpha"), PackId.parse("hypixel:skyblock")));
    private static final ParseContext ALPHA_STANDALONE = ParseContext.of(PackId.parse("hypixel:alpha"));

    @Test
    void iconParserUsesOverrideForActivePack() {
        IconParser parser = new IconParser();

        assertEquals("\u23E3", parser.parse("%%zone%%"));
        assertEquals("\u23E3", parser.parse("%%zone%%", ParseContext.empty()));
        assertEquals("\uE067", parser.parse("%%zone%%", HYPIXEL));
    }

    @Test
    void iconParserRepeatsTheOverrideCharacter() {
        assertEquals("\uE067\uE067\uE067", new IconParser().parse("%%zone:3%%", HYPIXEL));
    }

    @Test
    void newPuaEntriesResolveUnderAnyContext() {
        IconParser parser = new IconParser();

        assertEquals("\uE084", parser.parse("%%mob_undead%%"));
        assertEquals("\uE084", parser.parse("%%mob_undead%%", HYPIXEL));
        assertEquals("\uE01F", parser.parse("%%rift_hearts%%", HYPIXEL));
        assertEquals("\u12DE", parser.parse("%%hypixel_staff%%"));
    }

    @Test
    void unknownPlaceholdersPassThroughUnchanged() {
        assertEquals("%%mining_fortne%%", new IconParser().parse("%%mining_fortne%%", HYPIXEL));
    }

    @Test
    void statParserUsesOverrideIconAndDerivedDisplay() {
        StatParser parser = new StatParser();

        String base = parser.parse("%%strength%%");
        assertTrue(base.contains("\u2741 Strength"), "base render must keep the classic icon: " + base);
        assertFalse(base.contains("\uE00D"));

        String packed = parser.parse("%%strength%%", HYPIXEL);
        assertTrue(packed.contains("\uE00D Strength"), "pack render must use the override icon: " + packed);
        assertFalse(packed.contains("\u2741"));
    }

    @Test
    void gemstoneParserUsesOverrideForActivePack() {
        GemstoneParser parser = new GemstoneParser();

        assertEquals("&8[\u2764]&r", parser.parse("%%gem_ruby%%"));
        assertEquals("&8[\uE010]&r", parser.parse("%%gem_ruby%%", HYPIXEL));
        // "unlocked" carries its own color code, so the bare override character is used
        assertEquals("&8[&7\uE010&8]&r", parser.parse("%%gem_ruby:unlocked%%", HYPIXEL));
        // other tiers use the formatted icon; the override keeps the hand-tuned color code
        assertEquals("&9[&c\uE010&9]&r", parser.parse("%%gem_ruby:fine%%", HYPIXEL));
        assertEquals("&9[&c\u2764&9]&r", parser.parse("%%gem_ruby:fine%%"));
    }

    @Test
    void flavorParserUsesOverrideIcon() {
        FlavorParser parser = new FlavorParser();

        assertTrue(parser.parse("%%undead%%").contains("\u0F15 Undead"));
        assertTrue(parser.parse("%%undead%%", HYPIXEL).contains("\uE084 Undead"));
        assertTrue(parser.parse("%%undead%%", ParseContext.empty()).contains("\u0F15 Undead"));
    }

    @Test
    void flavorParserSwapsEmbeddedIconCharacters() {
        String packed = new FlavorParser().parse("%%undead_item%%", HYPIXEL);

        assertTrue(packed.contains("This armor piece is undead \uE084!"), packed);
        assertFalse(packed.contains("\u0F15"), packed);
    }

    @Test
    void vanillaPackNormalizesToNoOverrides() {
        assertNull(ParseContext.of(PackId.VANILLA).pack());
        assertEquals("\u23E3", new IconParser().parse("%%zone%%", ParseContext.of(PackId.VANILLA)));
    }

    @Test
    void vanillaOrMissingLineageNormalizesToNoOverrides() {
        assertNull(ParseContext.of(PackLineage.of(PackId.VANILLA)).pack());
        assertNull(ParseContext.of((PackLineage) null).pack());
        assertNull(ParseContext.of((PackId) null).pack());
    }

    @Test
    void packIdContextHasNoVariant() {
        assertEquals(PackLineage.of(PackId.parse("hypixel:skyblock")), HYPIXEL.pack());
    }

    @Test
    void variantContextUsesTheBasePacksOverrides() {
        assertEquals("\uE067", new IconParser().parse("%%zone%%", ALPHA_VARIANT));
        assertEquals("\u23E3", new IconParser().parse("%%zone%%", ALPHA_STANDALONE));

        String strength = new StatParser().parse("%%strength%%", ALPHA_VARIANT);
        assertTrue(strength.contains("\uE00D Strength"), strength);

        assertEquals("&9[&c\uE010&9]&r", new GemstoneParser().parse("%%gem_ruby:fine%%", ALPHA_VARIANT));
        String flavor = new FlavorParser().parse("%%undead_item%%", ALPHA_VARIANT);
        assertTrue(flavor.contains("This armor piece is undead \uE084!"), flavor);
    }

    @Test
    void variantContextRendersTheReportedLoreLikeTheBasePack() {
        // The lore from the bug report: the zone icon before "Mining Zones" fell back to the
        // vanilla glyph on the alpha pack.
        String lore = "While in %%zone%% &6Mining Zones&7, detects anomalies";
        IconParser parser = new IconParser();
        assertEquals(parser.parse(lore, HYPIXEL), parser.parse(lore, ALPHA_VARIANT));
        assertTrue(parser.parse(lore, ALPHA_VARIANT).contains("\uE067"));
    }

    @Test
    void textWrapperThreadsTheContextThroughParsing() {
        String packed = String.join("\n", TextWrapper.wrapString("%%strength%%", 36, HYPIXEL));
        assertTrue(packed.contains("\uE00D Strength"), packed);

        String base = String.join("\n", TextWrapper.wrapString("%%strength%%", 36));
        assertTrue(base.contains("\u2741 Strength"), base);
    }
}
