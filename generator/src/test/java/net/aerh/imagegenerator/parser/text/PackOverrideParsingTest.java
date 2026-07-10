package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.pack.PackId;
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
        assertNull(ParseContext.of(PackId.VANILLA).packId());
        assertEquals("\u23E3", new IconParser().parse("%%zone%%", ParseContext.of(PackId.VANILLA)));
    }

    @Test
    void textWrapperThreadsTheContextThroughParsing() {
        String packed = String.join("\n", TextWrapper.wrapString("%%strength%%", 36, HYPIXEL));
        assertTrue(packed.contains("\uE00D Strength"), packed);

        String base = String.join("\n", TextWrapper.wrapString("%%strength%%", 36));
        assertTrue(base.contains("\u2741 Strength"), base);
    }
}
