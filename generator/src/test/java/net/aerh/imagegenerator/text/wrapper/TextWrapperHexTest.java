package net.aerh.imagegenerator.text.wrapper;

import net.aerh.imagegenerator.text.LegacyCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hex color codes ({@code &#RRGGBB} / {@code §#RRGGBB}) must behave exactly like named
 * color codes in the wrapping pipeline: zero visible width, carried across wrapped lines,
 * resetting active formatting, and stripped by both strip functions. Malformed sequences
 * are ordinary visible text.
 */
class TextWrapperHexTest {

    @Test
    void stripColorCodesRemovesHexCodes() {
        assertEquals("Hello World", TextWrapper.stripColorCodes("&#ff00aaHello §#FF00AAWorld"));
    }

    @Test
    void stripColorCodesLeavesMalformedHexAlone() {
        assertEquals("&#ff00a text", TextWrapper.stripColorCodes("&#ff00a text"),
            "five hex digits are not a color code");
    }

    @Test
    void stripColorStripsSectionSymbolHex() {
        assertEquals("Hellox", LegacyCode.stripColor("§#ff00aaHello§cx"));
    }

    @Test
    void hexCodesHaveZeroVisibleWidthWhenWrapping() {
        // The trailing space on the first line matches the wrapper's behavior for named colors.
        List<String> lines = TextWrapper.wrapString("&#ff00aaAAAA BBBB", 8);
        assertEquals(List.of("&#ff00aaAAAA ", "&#ff00aaBBBB"), lines,
            "the 8-char hex code must not count toward line length, and the color must carry over");
    }

    @Test
    void hexColorResetsCarriedFormatting() {
        List<String> lines = TextWrapper.wrapString("&l&#ff00aaAAAA BBBB", 8);
        assertEquals(List.of("&l&#ff00aaAAAA ", "&#ff00aaBBBB"), lines,
            "a color change resets formatting, so &l must not carry to the next line");
    }

    @Test
    void namedColorAfterHexReplacesCarriedColor() {
        List<String> lines = TextWrapper.wrapString("&#ff00aaAAAA &cBBBB CCCC", 9);
        assertEquals(List.of("&#ff00aaAAAA &cBBBB", "&cCCCC"), lines);
    }

    @Test
    void splitsLongWordsTreatingHexCodesAsZeroWidth() {
        List<String> lines = TextWrapper.wrapString("&#ff00aaABCDEFGHIJ", 5);
        assertEquals(List.of("&#ff00aaABCDE", "&#ff00aaFGHIJ"), lines);
    }

    @Test
    void malformedHexCountsAsVisibleText() {
        List<String> lines = TextWrapper.wrapString("&#zzzzzz", 8);
        assertEquals(List.of("&#zzzzzz"), lines, "an invalid hex sequence is ordinary text");
    }
}
