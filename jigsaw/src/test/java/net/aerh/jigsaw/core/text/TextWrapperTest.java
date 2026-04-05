package net.aerh.jigsaw.core.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextWrapperTest {

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
}
