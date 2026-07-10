package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.parser.ParseContext;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reserved {@code %%icon:<name>%%} placeholder: renders only the icon character of the named
 * stat, icon, or flavor entry without any display text, resolving pack overrides the same way the
 * full placeholder does.
 */
class IconReferenceParsingTest {

    private static final ParseContext HYPIXEL = ParseContext.of(PackId.parse("hypixel:skyblock"));

    private final IconParser parser = new IconParser();

    @Test
    void statIconRendersWithoutDisplayText() {
        assertEquals("❤", parser.parse("%%icon:health%%"));
        assertEquals("❁", parser.parse("%%icon:strength%%"));
    }

    @Test
    void statIconResolvesPackOverrides() {
        assertEquals("", parser.parse("%%icon:health%%", HYPIXEL));
    }

    @Test
    void statIconSupportsRepeatCounts() {
        assertEquals("❤❤❤", parser.parse("%%icon:health:3%%"));
        assertEquals("", parser.parse("%%icon:health:2%%", HYPIXEL));
    }

    @Test
    void iconRegistryEntriesResolveThroughTheSameSyntax() {
        assertEquals("⏣", parser.parse("%%icon:zone%%"));
        assertEquals("", parser.parse("%%icon:zone%%", HYPIXEL));
    }

    @Test
    void flavorEntriesResolveThroughTheSameSyntax() {
        assertEquals("❣", parser.parse("%%icon:requires%%"));
    }

    @Test
    void unknownTargetPassesThroughUnchanged() {
        assertEquals("%%icon:bogus%%", parser.parse("%%icon:bogus%%", HYPIXEL));
    }

    @Test
    void missingTargetPassesThroughUnchanged() {
        assertEquals("%%icon%%", parser.parse("%%icon%%"));
    }

    @Test
    void emptyIconTargetPassesThroughUnchanged() {
        // soulbound's flavor icon is the empty string; silently deleting the placeholder would
        // hide the mistake from the user
        assertEquals("%%icon:soulbound%%", parser.parse("%%icon:soulbound%%"));
    }

    @Test
    void nonPositiveRepeatCountPassesThroughUnchanged() {
        assertEquals("%%icon:health:0%%", parser.parse("%%icon:health:0%%"));
    }

    @Test
    void fullPipelineRendersBareIconWithoutStatText() {
        String base = String.join("\n", TextWrapper.wrapString("&c%%icon:health%%", 36));
        assertTrue(base.contains("❤"), base);
        assertFalse(base.contains("Health"), base);

        String packed = String.join("\n", TextWrapper.wrapString("&c%%icon:health%%", 36, HYPIXEL));
        assertTrue(packed.contains(""), packed);
        assertFalse(packed.contains("Health"), packed);
    }

    @Test
    void fullStatPlaceholderIsUnaffected() {
        String base = String.join("\n", TextWrapper.wrapString("%%health%%", 36));
        assertTrue(base.contains("❤ Health"), base);
    }
}
