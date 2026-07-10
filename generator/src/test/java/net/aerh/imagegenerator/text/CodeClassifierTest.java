package net.aerh.imagegenerator.text;

import net.aerh.imagegenerator.text.CodeClassifier.CodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shared in-band code grammar that GradientParser and TextWrapper both lex through.
 * These classifications must not drift: skip lengths and recognized codes feed both gradient
 * expansion and wrapped-line width counting.
 */
class CodeClassifierTest {

    @Test
    void classifiesNamedColors() {
        assertEquals(CodeType.NAMED_COLOR, CodeClassifier.classify("&c", 0));
        assertEquals(CodeType.NAMED_COLOR, CodeClassifier.classify("&0", 0));
        assertEquals(CodeType.NAMED_COLOR, CodeClassifier.classify("&f", 0));
    }

    @Test
    void classifiesStyles() {
        assertEquals(CodeType.STYLE, CodeClassifier.classify("&k", 0));
        assertEquals(CodeType.STYLE, CodeClassifier.classify("&l", 0));
        assertEquals(CodeType.STYLE, CodeClassifier.classify("&m", 0));
        assertEquals(CodeType.STYLE, CodeClassifier.classify("&n", 0));
        assertEquals(CodeType.STYLE, CodeClassifier.classify("&o", 0));
    }

    @Test
    void classifiesFontsSeparatelyFromStyles() {
        assertEquals(CodeType.FONT, CodeClassifier.classify("&g", 0));
        assertEquals(CodeType.FONT, CodeClassifier.classify("&h", 0));
    }

    @Test
    void classifiesReset() {
        assertEquals(CodeType.RESET, CodeClassifier.classify("&r", 0));
    }

    @Test
    void classifiesHexColors() {
        assertEquals(CodeType.HEX_COLOR, CodeClassifier.classify("&#ff00aa", 0));
        assertEquals(CodeType.HEX_COLOR, CodeClassifier.classify("&#FF00AA", 0));
    }

    @Test
    void malformedHexIsNotACode() {
        assertEquals(CodeType.NONE, CodeClassifier.classify("&#ff00a", 0), "too short");
        assertEquals(CodeType.NONE, CodeClassifier.classify("&#zzzzzz", 0), "non-hex digits");
        assertEquals(CodeType.NONE, CodeClassifier.classify("&#", 0), "no digits");
    }

    @Test
    void classificationIsCaseInsensitive() {
        assertEquals(CodeType.NAMED_COLOR, CodeClassifier.classify("&C", 0));
        assertEquals(CodeType.STYLE, CodeClassifier.classify("&L", 0));
        assertEquals(CodeType.FONT, CodeClassifier.classify("&G", 0));
        assertEquals(CodeType.RESET, CodeClassifier.classify("&R", 0));
    }

    @Test
    void bothCodeSymbolsAreRecognized() {
        assertEquals(CodeType.NAMED_COLOR, CodeClassifier.classify("§c", 0));
        assertEquals(CodeType.HEX_COLOR, CodeClassifier.classify("§#ff00aa", 0));
        assertTrue(CodeClassifier.isCodeSymbol('&'));
        assertTrue(CodeClassifier.isCodeSymbol('§'));
        assertFalse(CodeClassifier.isCodeSymbol('%'));
    }

    @Test
    void nonCodesClassifyAsNone() {
        assertEquals(CodeType.NONE, CodeClassifier.classify("abc", 0), "not a code symbol");
        assertEquals(CodeType.NONE, CodeClassifier.classify("&z", 0), "unrecognized code char");
        assertEquals(CodeType.NONE, CodeClassifier.classify("&&c", 0), "symbol followed by symbol");
        assertEquals(CodeType.NONE, CodeClassifier.classify("&", 0), "trailing symbol");
    }

    @Test
    void classifiesAtGivenIndexWithinLargerText() {
        assertEquals(CodeType.NAMED_COLOR, CodeClassifier.classify("ab&cde", 2));
        assertEquals(CodeType.NONE, CodeClassifier.classify("ab&cde", 3));
    }

    @Test
    void skipLengthsMatchCodeWidths() {
        assertEquals(RgbColor.HEX_CODE_LENGTH, CodeClassifier.skipLength(CodeType.HEX_COLOR));
        assertEquals(1, CodeClassifier.skipLength(CodeType.NAMED_COLOR));
        assertEquals(1, CodeClassifier.skipLength(CodeType.STYLE));
        assertEquals(1, CodeClassifier.skipLength(CodeType.FONT));
        assertEquals(1, CodeClassifier.skipLength(CodeType.RESET));
        assertEquals(0, CodeClassifier.skipLength(CodeType.NONE));
    }

    @Test
    void recognizesNewlineMarker() {
        assertTrue(CodeClassifier.isNewlineMarker("a\\nb", 1));
        assertFalse(CodeClassifier.isNewlineMarker("a\\nb", 0), "not at the backslash - 1");
        assertFalse(CodeClassifier.isNewlineMarker("a\\", 1), "trailing backslash");
        assertFalse(CodeClassifier.isNewlineMarker("a\nb", 1), "real newline is not the marker");
    }

    @Test
    void newlineRegexMatchesRealNewlineAndMarker() {
        assertEquals("a b", String.join(" ", "a\nb".split(CodeClassifier.NEWLINE_REGEX)));
        assertEquals("a b", String.join(" ", "a\\nb".split(CodeClassifier.NEWLINE_REGEX)));
    }

    @Test
    void alphabetCoversEveryChatFormatConstant() {
        // Every ChatFormat code must classify as something other than NONE so that a new
        // enum constant cannot silently fall outside the shared grammar.
        for (ChatFormat format : ChatFormat.VALUES) {
            assertTrue(CodeClassifier.classify("&" + format.getCode(), 0) != CodeType.NONE,
                "ChatFormat." + format.name() + " must be part of the code grammar");
        }
    }
}
