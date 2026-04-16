package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextLayoutEngineTest {

    private static TextSegment seg(String text) {
        return new TextSegment(text, TextStyle.DEFAULT);
    }

    // --- empty input ---

    @Test
    void layout_emptyList_returnsEmptyLayout() {
        TextLayout layout = TextLayoutEngine.layout(List.of(), TextLayoutOptions.defaults());

        assertThat(layout.isEmpty()).isTrue();
        assertThat(layout.lines()).isEmpty();
        assertThat(layout.width()).isZero();
        assertThat(layout.height()).isZero();
    }

    @Test
    void layout_nullInput_returnsEmptyLayout() {
        TextLayout layout = TextLayoutEngine.layout(null, TextLayoutOptions.defaults());

        assertThat(layout.isEmpty()).isTrue();
    }

    // --- single segment, fits in one line ---

    @Test
    void layout_shortSegment_fitsOnOneLine() {
        TextLayout layout = TextLayoutEngine.layout(List.of(seg("Hello")), TextLayoutOptions.defaults());

        assertThat(layout.lines()).hasSize(1);
        assertThat(layout.height()).isEqualTo(1);
        assertThat(layout.width()).isGreaterThan(0);
    }

    @Test
    void layout_singleLine_widthEqualsTextLength() {
        TextLayout layout = TextLayoutEngine.layout(List.of(seg("Hello")), TextLayoutOptions.defaults());

        assertThat(layout.lines().get(0).width()).isEqualTo(5);
    }

    // --- wrapping ---

    @Test
    void layout_longText_wrapsToMultipleLines() {
        // maxWidth = 10; input is 30 chars
        TextLayoutOptions opts = new TextLayoutOptions(10, false, 1);
        TextLayout layout = TextLayoutEngine.layout(
                List.of(seg("Hello World This Is Long")), opts);

        assertThat(layout.lines().size()).isGreaterThan(1);
    }

    @Test
    void layout_widthNeverExceedsMaxWidth() {
        TextLayoutOptions opts = new TextLayoutOptions(10, false, 1);
        TextLayout layout = TextLayoutEngine.layout(
                List.of(seg("Hello World This Is Long")), opts);

        for (TextLine line : layout.lines()) {
            assertThat(line.width()).isLessThanOrEqualTo(10);
        }
    }

    // --- newline handling ---

    @Test
    void layout_newlineCharacter_createsNewLine() {
        TextLayout layout = TextLayoutEngine.layout(
                List.of(seg("Line1\nLine2")), TextLayoutOptions.defaults());

        assertThat(layout.lines()).hasSize(2);
        assertThat(layout.lines().get(0).segments().get(0).text()).isEqualTo("Line1");
        assertThat(layout.lines().get(1).segments().get(0).text()).isEqualTo("Line2");
    }

    @Test
    void layout_multipleNewlines_createsMultipleLines() {
        TextLayout layout = TextLayoutEngine.layout(
                List.of(seg("A\nB\nC")), TextLayoutOptions.defaults());

        assertThat(layout.lines()).hasSize(3);
    }

    // --- dimensions ---

    @Test
    void layout_nonEmpty_hasPositiveDimensions() {
        TextLayout layout = TextLayoutEngine.layout(
                List.of(seg("Hello")), TextLayoutOptions.defaults());

        assertThat(layout.width()).isGreaterThan(0);
        assertThat(layout.height()).isGreaterThan(0);
    }

    @Test
    void layout_height_equalsNumberOfLines() {
        TextLayout layout = TextLayoutEngine.layout(
                List.of(seg("A\nB\nC")), TextLayoutOptions.defaults());

        assertThat(layout.height()).isEqualTo(layout.lines().size());
    }

    // --- multiple segments ---

    @Test
    void layout_multipleSegments_combinedOnOneLine() {
        TextLayout layout = TextLayoutEngine.layout(
                List.of(seg("Hello "), seg("World")), new TextLayoutOptions(20, false, 1));

        assertThat(layout.lines()).hasSize(1);
    }

    // --- options validation ---

    @Test
    void textLayoutOptions_zeroMaxWidth_throws() {
        assertThatThrownBy(() -> new TextLayoutOptions(0, false, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textLayoutOptions_negativeScaleFactor_throws() {
        assertThatThrownBy(() -> new TextLayoutOptions(80, false, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- TextLine immutability ---

    @Test
    void textLine_segmentsList_isImmutable() {
        TextLine line = new TextLine(List.of(seg("Hi")), 2);

        assertThatThrownBy(() -> line.segments().add(seg("More")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- TextLayout immutability ---

    @Test
    void textLayout_linesList_isImmutable() {
        TextLayout layout = new TextLayout(List.of(new TextLine(List.of(seg("Hi")), 2)), 2, 1);

        assertThatThrownBy(() -> layout.lines().add(new TextLine(List.of(), 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
