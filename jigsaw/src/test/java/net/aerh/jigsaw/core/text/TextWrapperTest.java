package net.aerh.jigsaw.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextWrapperTest {

    // -----------------------------------------------------------------------
    // stripActualNewlines
    // -----------------------------------------------------------------------

    @Test
    void stripActualNewlines_replacesNewlinesWithSpaces() {
        assertThat(TextWrapper.stripActualNewlines("hello\nworld")).isEqualTo("hello world");
    }

    @Test
    void stripActualNewlines_replacesCarriageReturnNewline() {
        assertThat(TextWrapper.stripActualNewlines("hello\r\nworld")).isEqualTo("hello world");
    }

    @Test
    void stripActualNewlines_replacesCarriageReturn() {
        assertThat(TextWrapper.stripActualNewlines("hello\rworld")).isEqualTo("hello world");
    }

    @Test
    void stripActualNewlines_preservesLiteralBackslashN() {
        assertThat(TextWrapper.stripActualNewlines("hello\\nworld")).isEqualTo("hello\\nworld");
    }

    @Test
    void stripActualNewlines_nullReturnsNull() {
        assertThat(TextWrapper.stripActualNewlines(null)).isNull();
    }

    @Test
    void stripActualNewlines_emptyReturnsEmpty() {
        assertThat(TextWrapper.stripActualNewlines("")).isEmpty();
    }

    @Test
    void stripActualNewlines_noNewlinesReturnsOriginal() {
        assertThat(TextWrapper.stripActualNewlines("no newlines here")).isEqualTo("no newlines here");
    }

    @Test
    void stripActualNewlines_multipleNewlines() {
        assertThat(TextWrapper.stripActualNewlines("a\nb\nc")).isEqualTo("a b c");
    }

    // -----------------------------------------------------------------------
    // wrapString
    // -----------------------------------------------------------------------

    @Test
    void wrapString_nullReturnsEmptyList() {
        assertThat(TextWrapper.wrapString(null, 10)).isEmpty();
    }

    @Test
    void wrapString_emptyPreservesEmptyLine() {
        assertThat(TextWrapper.wrapString("", 10)).containsExactly("");
    }

    @Test
    void wrapString_maxLengthZeroReturnsSingleElement() {
        assertThat(TextWrapper.wrapString("hello world", 0)).containsExactly("hello world");
    }

    @Test
    void wrapString_shortTextFitsOnOneLine() {
        assertThat(TextWrapper.wrapString("hello", 10)).containsExactly("hello");
    }

    @Test
    void wrapString_wrapsAtMaxLength() {
        // "hello world foo" with maxLineLength=10
        // "hello" (5) fits, then " " (1) is appended (total 6), then "world" (5) would make 11 > 10
        // so the line is flushed as "hello " (trailing space preserved from the buffer) and "world foo" continues
        List<String> result = TextWrapper.wrapString("hello world foo", 10);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("hello ");
        assertThat(result.get(1)).isEqualTo("world foo");
    }

    @Test
    void wrapString_wrapsAtExactBoundary() {
        // "abcde fghij" - each word is exactly 5 chars, maxLineLength=5
        List<String> result = TextWrapper.wrapString("abcde fghij", 5);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("abcde");
        assertThat(result.get(1)).isEqualTo("fghij");
    }

    @Test
    void wrapString_preservesFormattingAcrossWraps() {
        // "&cRed text that wraps" - the color code should be re-prepended on the second line
        List<String> result = TextWrapper.wrapString("&cRed text that wraps", 10);
        assertThat(result).hasSizeGreaterThan(1);
        // all lines after the first must start with the carried-over color code
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i)).startsWith("&c");
        }
    }

    @Test
    void wrapString_preservesFormattingWithBold() {
        // "&c&lBold colored text that is long enough to wrap onto another line"
        List<String> result = TextWrapper.wrapString("&c&lBold colored text that wraps here", 15);
        assertThat(result).hasSizeGreaterThan(1);
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i)).startsWith("&c&l");
        }
    }

    @Test
    void wrapString_handlesWordsLongerThanMaxLength() {
        // "abcdefghij" is 10 chars, maxLineLength=4 - must be split mid-word
        List<String> result = TextWrapper.wrapString("abcdefghij", 4);
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo("abcd");
        assertThat(result.get(1)).isEqualTo("efgh");
        assertThat(result.get(2)).isEqualTo("ij");
    }

    @Test
    void wrapString_preservesEmptyLines() {
        // Two paragraphs separated by an empty line
        List<String> result = TextWrapper.wrapString("hello\n\nworld", 20);
        assertThat(result).containsExactly("hello", "", "world");
    }

    @Test
    void wrapString_handlesNewlines() {
        // Literal \n marker should create a line break
        List<String> result = TextWrapper.wrapString("line one\\nline two", 20);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("line one");
        assertThat(result.get(1)).isEqualTo("line two");
    }

    @Test
    void wrapString_handlesActualNewlines() {
        List<String> result = TextWrapper.wrapString("line one\nline two", 20);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("line one");
        assertThat(result.get(1)).isEqualTo("line two");
    }

    @Test
    void wrapString_colorCodesDoNotCountTowardsLength() {
        // "&7hello" - only "hello" (5 chars) is visible; should fit on one line with maxLineLength=5
        List<String> result = TextWrapper.wrapString("&7hello", 5);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("&7hello");
    }

    // -----------------------------------------------------------------------
    // normalizeNewlines
    // -----------------------------------------------------------------------

    @Test
    void normalizeNewlines_nullReturnsNull() {
        assertThat(TextWrapper.normalizeNewlines(null)).isNull();
    }

    @Test
    void normalizeNewlines_emptyReturnsEmpty() {
        assertThat(TextWrapper.normalizeNewlines("")).isEmpty();
    }

    @Test
    void normalizeNewlines_handlesVariousFormats() {
        // \r\n -> \n
        assertThat(TextWrapper.normalizeNewlines("a\r\nb")).isEqualTo("a\nb");
        // \r alone -> \n
        assertThat(TextWrapper.normalizeNewlines("a\rb")).isEqualTo("a\nb");
        // literal \n -> actual newline
        assertThat(TextWrapper.normalizeNewlines("a\\nb")).isEqualTo("a\nb");
    }

    @Test
    void normalizeNewlines_dedupesAdjacentActualAndLiteralNewline() {
        // actual \n followed immediately by literal \n should be one line break
        assertThat(TextWrapper.normalizeNewlines("a\n\\nb")).isEqualTo("a\nb");
    }

    @Test
    void normalizeNewlines_preservesRegularText() {
        assertThat(TextWrapper.normalizeNewlines("no newlines here")).isEqualTo("no newlines here");
    }

    // -----------------------------------------------------------------------
    // stripColorCodes
    // -----------------------------------------------------------------------

    @Test
    void stripColorCodes_nullReturnsEmpty() {
        assertThat(TextWrapper.stripColorCodes(null)).isEmpty();
    }

    @Test
    void stripColorCodes_emptyReturnsEmpty() {
        assertThat(TextWrapper.stripColorCodes("")).isEmpty();
    }

    @Test
    void stripColorCodes_removesAllCodes() {
        assertThat(TextWrapper.stripColorCodes("&cRed &lBold &rReset")).isEqualTo("Red Bold Reset");
    }

    @Test
    void stripColorCodes_removesSectionSignCodes() {
        assertThat(TextWrapper.stripColorCodes("\u00a7cRed\u00a7r")).isEqualTo("Red");
    }

    @Test
    void stripColorCodes_removesHexStyleFormattingCodes() {
        // All formatting letters k-o and r in both cases
        assertThat(TextWrapper.stripColorCodes("&kObf&lBold&mStrike&nUnder&oItalic&rReset"))
                .isEqualTo("ObfBoldStrikeUnderItalicReset");
    }

    @Test
    void stripColorCodes_noCodesReturnsOriginal() {
        assertThat(TextWrapper.stripColorCodes("plain text")).isEqualTo("plain text");
    }
}
