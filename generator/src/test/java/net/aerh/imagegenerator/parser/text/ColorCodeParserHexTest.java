package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code %%#RRGGBB%%} placeholder mirrors the {@code %%RED%%} convention and resolves
 * to the in-band {@code &#RRGGBB} hex color code.
 */
class ColorCodeParserHexTest {

    private final ColorCodeParser parser = new ColorCodeParser();

    @Test
    void convertsHexPlaceholderToAmpersandCode() {
        assertEquals("&#ff00aaHello", parser.parse("%%#ff00aa%%Hello"));
    }

    @Test
    void hexPlaceholderIsCaseInsensitive() {
        assertEquals("&#FF00aaHello", parser.parse("%%#FF00aa%%Hello"));
    }

    @Test
    void namedPlaceholdersStillWork() {
        assertEquals("&cHello", parser.parse("%%RED%%Hello"));
    }

    @Test
    void invalidHexPlaceholdersAreLeftUntouched() {
        assertEquals("%%#ff00a%%Hello", parser.parse("%%#ff00a%%Hello"), "five digits");
        assertEquals("%%#zzzzzz%%Hello", parser.parse("%%#zzzzzz%%Hello"), "non-hex digits");
        assertEquals("%%#ff00aa0%%Hello", parser.parse("%%#ff00aa0%%Hello"), "seven digits");
    }

    @Test
    void hexPlaceholderResolvesThroughTheFullParseChain() {
        assertEquals("&#ff00aaHello", TextWrapper.parseLine("%%#ff00aa%%Hello"));
    }
}
