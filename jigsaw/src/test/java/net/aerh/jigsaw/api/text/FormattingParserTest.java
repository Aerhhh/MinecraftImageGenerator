package net.aerh.jigsaw.api.text;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormattingParserTest {

    // Test 1: Parse section symbol color
    @Test
    void parse_sectionSymbol_color() {
        List<TextSegment> segments = FormattingParser.parse("§aHello");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hello");
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(85, 255, 85)); // GREEN
    }

    // Test 2: Parse ampersand color
    @Test
    void parse_ampersand_color() {
        List<TextSegment> segments = FormattingParser.parse("&cRed text");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Red text");
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(255, 85, 85)); // RED
    }

    // Test 3: Parse bold formatting
    @Test
    void parse_bold_formatting() {
        List<TextSegment> segments = FormattingParser.parse("§lBold text");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Bold text");
        assertThat(segments.get(0).style().bold()).isTrue();
    }

    // Test 4: Parse multiple segments
    @Test
    void parse_multipleSegments() {
        List<TextSegment> segments = FormattingParser.parse("§aGreen§cRed");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).isEqualTo("Green");
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(85, 255, 85)); // GREEN
        assertThat(segments.get(1).text()).isEqualTo("Red");
        assertThat(segments.get(1).style().color()).isEqualTo(new Color(255, 85, 85)); // RED
    }

    // Test 5: Parse reset clears formatting
    @Test
    void parse_reset_clearsFormatting() {
        List<TextSegment> segments = FormattingParser.parse("§lBold§rNormal");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).style().bold()).isTrue();
        assertThat(segments.get(1).style().bold()).isFalse();
        // After reset, color should be back to default (white)
        assertThat(segments.get(1).style().color()).isEqualTo(new Color(255, 255, 255));
    }

    // Test 6: stripColors removes all codes
    @Test
    void stripColors_removesAllFormattingCodes() {
        String stripped = FormattingParser.stripColors("§aGreen §lBold §cRed");
        assertThat(stripped).isEqualTo("Green Bold Red");
    }

    @Test
    void stripColors_removesAmpersandCodes() {
        String stripped = FormattingParser.stripColors("&aGreen &lBold");
        assertThat(stripped).isEqualTo("Green Bold");
    }

    // Test 7: Parse plain text returns default style
    @Test
    void parse_plainText_returnsDefaultStyle() {
        List<TextSegment> segments = FormattingParser.parse("Plain text");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Plain text");
        assertThat(segments.get(0).style()).isEqualTo(TextStyle.DEFAULT);
    }

    @Test
    void parse_emptyString_returnsEmptyList() {
        List<TextSegment> segments = FormattingParser.parse("");
        assertThat(segments).isEmpty();
    }

    @Test
    void parse_colorAndBoldCombined() {
        List<TextSegment> segments = FormattingParser.parse("§a§lGreenBold");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("GreenBold");
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(85, 255, 85)); // GREEN
        assertThat(segments.get(0).style().bold()).isTrue();
    }
}
