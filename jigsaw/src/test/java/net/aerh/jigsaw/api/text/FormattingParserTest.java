package net.aerh.jigsaw.api.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormattingParserTest {

    // Test 1: Parse section symbol color
    @Test
    void parse_sectionSymbol_color() {
        List<TextSegment> segments = FormattingParser.parse("§aHello");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hello");
        assertThat(segments.get(0).style().color()).isEqualTo(ChatColor.GREEN.color()); // GREEN
    }

    // Test 2: Parse ampersand color
    @Test
    void parse_ampersand_color() {
        List<TextSegment> segments = FormattingParser.parse("&cRed text");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Red text");
        assertThat(segments.get(0).style().color()).isEqualTo(ChatColor.RED.color()); // RED
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
        assertThat(segments.get(0).style().color()).isEqualTo(ChatColor.GREEN.color()); // GREEN
        assertThat(segments.get(1).text()).isEqualTo("Red");
        assertThat(segments.get(1).style().color()).isEqualTo(ChatColor.RED.color()); // RED
    }

    // Test 5: Parse reset clears formatting
    @Test
    void parse_reset_clearsFormatting() {
        List<TextSegment> segments = FormattingParser.parse("§lBold§rNormal");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).style().bold()).isTrue();
        assertThat(segments.get(1).style().bold()).isFalse();
        // After reset, color should be back to default (gray)
        assertThat(segments.get(1).style().color()).isEqualTo(ChatColor.GRAY.color());
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
        assertThat(segments.get(0).style().color()).isEqualTo(ChatColor.GREEN.color()); // GREEN
        assertThat(segments.get(0).style().bold()).isTrue();
    }

    // --- resolveNamedFormats tests ---

    @Test
    void resolveNamedFormats_colorName_resolvesToSectionCode() {
        String result = FormattingParser.resolveNamedFormats("%%GREEN%%Hello");
        assertThat(result).isEqualTo("\u00a7aHello");
    }

    @Test
    void resolveNamedFormats_allColorNames_resolve() {
        assertThat(FormattingParser.resolveNamedFormats("%%BLACK%%")).isEqualTo("\u00a70");
        assertThat(FormattingParser.resolveNamedFormats("%%DARK_BLUE%%")).isEqualTo("\u00a71");
        assertThat(FormattingParser.resolveNamedFormats("%%DARK_GREEN%%")).isEqualTo("\u00a72");
        assertThat(FormattingParser.resolveNamedFormats("%%DARK_AQUA%%")).isEqualTo("\u00a73");
        assertThat(FormattingParser.resolveNamedFormats("%%DARK_RED%%")).isEqualTo("\u00a74");
        assertThat(FormattingParser.resolveNamedFormats("%%DARK_PURPLE%%")).isEqualTo("\u00a75");
        assertThat(FormattingParser.resolveNamedFormats("%%GOLD%%")).isEqualTo("\u00a76");
        assertThat(FormattingParser.resolveNamedFormats("%%GRAY%%")).isEqualTo("\u00a77");
        assertThat(FormattingParser.resolveNamedFormats("%%DARK_GRAY%%")).isEqualTo("\u00a78");
        assertThat(FormattingParser.resolveNamedFormats("%%BLUE%%")).isEqualTo("\u00a79");
        assertThat(FormattingParser.resolveNamedFormats("%%GREEN%%")).isEqualTo("\u00a7a");
        assertThat(FormattingParser.resolveNamedFormats("%%AQUA%%")).isEqualTo("\u00a7b");
        assertThat(FormattingParser.resolveNamedFormats("%%RED%%")).isEqualTo("\u00a7c");
        assertThat(FormattingParser.resolveNamedFormats("%%LIGHT_PURPLE%%")).isEqualTo("\u00a7d");
        assertThat(FormattingParser.resolveNamedFormats("%%YELLOW%%")).isEqualTo("\u00a7e");
        assertThat(FormattingParser.resolveNamedFormats("%%WHITE%%")).isEqualTo("\u00a7f");
    }

    @Test
    void resolveNamedFormats_formattingName_resolvesToSectionCode() {
        assertThat(FormattingParser.resolveNamedFormats("%%BOLD%%")).isEqualTo("\u00a7l");
        assertThat(FormattingParser.resolveNamedFormats("%%ITALIC%%")).isEqualTo("\u00a7o");
        assertThat(FormattingParser.resolveNamedFormats("%%UNDERLINE%%")).isEqualTo("\u00a7n");
        assertThat(FormattingParser.resolveNamedFormats("%%STRIKETHROUGH%%")).isEqualTo("\u00a7m");
        assertThat(FormattingParser.resolveNamedFormats("%%OBFUSCATED%%")).isEqualTo("\u00a7k");
        assertThat(FormattingParser.resolveNamedFormats("%%RESET%%")).isEqualTo("\u00a7r");
    }

    @Test
    void resolveNamedFormats_caseInsensitive() {
        assertThat(FormattingParser.resolveNamedFormats("%%green%%")).isEqualTo("\u00a7a");
        assertThat(FormattingParser.resolveNamedFormats("%%Green%%")).isEqualTo("\u00a7a");
        assertThat(FormattingParser.resolveNamedFormats("%%dark_gray%%")).isEqualTo("\u00a78");
        assertThat(FormattingParser.resolveNamedFormats("%%bold%%")).isEqualTo("\u00a7l");
    }

    @Test
    void resolveNamedFormats_unrecognizedName_leftUnchanged() {
        String input = "%%UNKNOWN%%Hello";
        assertThat(FormattingParser.resolveNamedFormats(input)).isEqualTo(input);
    }

    @Test
    void resolveNamedFormats_mixedWithExistingCodes() {
        String result = FormattingParser.resolveNamedFormats("&b%%GREEN%%Hello%%GRAY%%World");
        assertThat(result).isEqualTo("&b\u00a7aHello\u00a77World");
    }

    @Test
    void resolveNamedFormats_nullAndEmpty() {
        assertThat(FormattingParser.resolveNamedFormats(null)).isNull();
        assertThat(FormattingParser.resolveNamedFormats("")).isEmpty();
    }

    @Test
    void resolveNamedFormats_noPlaceholders_returnsUnchanged() {
        String input = "Just plain text with &a codes";
        assertThat(FormattingParser.resolveNamedFormats(input)).isEqualTo(input);
    }

    @Test
    void parse_namedColor_producesCorrectSegment() {
        List<TextSegment> segments = FormattingParser.parse("%%GREEN%%Hello");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hello");
        assertThat(segments.get(0).style().color()).isEqualTo(ChatColor.GREEN.color());
    }

    @Test
    void parse_namedColorAndFormatting_combined() {
        List<TextSegment> segments = FormattingParser.parse("%%GREEN%%%%BOLD%%Hello");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hello");
        assertThat(segments.get(0).style().color()).isEqualTo(ChatColor.GREEN.color());
        assertThat(segments.get(0).style().bold()).isTrue();
    }

    @Test
    void parse_multipleNamedSegments() {
        List<TextSegment> segments = FormattingParser.parse("%%GREEN%%3%%GRAY%%text");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).isEqualTo("3");
        assertThat(segments.get(0).style().color()).isEqualTo(ChatColor.GREEN.color());
        assertThat(segments.get(1).text()).isEqualTo("text");
        assertThat(segments.get(1).style().color()).isEqualTo(ChatColor.GRAY.color());
    }

    @Test
    void stripColors_namedFormats_areStripped() {
        String stripped = FormattingParser.stripColors("%%GREEN%%Hello %%BOLD%%World");
        assertThat(stripped).isEqualTo("Hello World");
    }
}
