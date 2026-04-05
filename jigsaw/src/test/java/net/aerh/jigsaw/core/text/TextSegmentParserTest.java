package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextSegmentParserTest {

    // --- parseLegacy ---

    @Test
    void parseLegacy_plainText_returnsDefaultStyle() {
        List<TextSegment> segments = TextSegmentParser.parseLegacy("Hello World");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hello World");
        assertThat(segments.get(0).style()).isEqualTo(TextStyle.DEFAULT);
    }

    @Test
    void parseLegacy_colorCode_returnsColoredSegment() {
        List<TextSegment> segments = TextSegmentParser.parseLegacy("&aGreen");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Green");
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(85, 255, 85));
    }

    @Test
    void parseLegacy_emptyString_returnsEmptyList() {
        assertThat(TextSegmentParser.parseLegacy("")).isEmpty();
    }

    @Test
    void parseLegacy_multipleSegments_delegatesToFormattingParser() {
        List<TextSegment> segments = TextSegmentParser.parseLegacy("§aGreen§cRed");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).isEqualTo("Green");
        assertThat(segments.get(1).text()).isEqualTo("Red");
    }

    // --- parseJson: primitives ---

    @Test
    void parseJson_stringPrimitive_returnsDefaultStyle() {
        List<TextSegment> segments = TextSegmentParser.parseJson("\"Hello\"");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hello");
        assertThat(segments.get(0).style()).isEqualTo(TextStyle.DEFAULT);
    }

    // --- parseJson: objects ---

    @Test
    void parseJson_textObject_returnsSegment() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Hello\"}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hello");
    }

    @Test
    void parseJson_textObject_withColorName_appliesColor() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Hi\",\"color\":\"red\"}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("Hi");
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(255, 85, 85));
    }

    @Test
    void parseJson_textObject_withHexColor_appliesColor() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Hi\",\"color\":\"#FF0000\"}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(0xFF, 0x00, 0x00));
    }

    @Test
    void parseJson_textObject_withBoldTrue_appliesBold() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Bold\",\"bold\":true}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).style().bold()).isTrue();
    }

    @Test
    void parseJson_textObject_withItalicTrue_appliesItalic() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Italic\",\"italic\":true}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).style().italic()).isTrue();
    }

    @Test
    void parseJson_textObject_withUnderlinedTrue_appliesUnderline() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Under\",\"underlined\":true}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).style().underlined()).isTrue();
    }

    @Test
    void parseJson_textObject_withStrikethroughTrue_appliesStrikethrough() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Strike\",\"strikethrough\":true}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).style().strikethrough()).isTrue();
    }

    @Test
    void parseJson_textObject_withObfuscatedTrue_appliesObfuscated() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"???\",\"obfuscated\":true}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).style().obfuscated()).isTrue();
    }

    @Test
    void parseJson_textObject_withFontField_appliesFont() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"Hi\",\"font\":\"minecraft:alt\"}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).style().fontId()).isEqualTo("minecraft:alt");
    }

    @Test
    void parseJson_textObject_emptyText_returnsNoSegments() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"\"}");

        assertThat(segments).isEmpty();
    }

    // --- parseJson: extra array / inheritance ---

    @Test
    void parseJson_extraArray_producesChildSegments() {
        String json = "{\"text\":\"Parent\",\"extra\":[{\"text\":\"Child\"}]}";
        List<TextSegment> segments = TextSegmentParser.parseJson(json);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).isEqualTo("Parent");
        assertThat(segments.get(1).text()).isEqualTo("Child");
    }

    @Test
    void parseJson_childInheritsParentColor() {
        String json = "{\"text\":\"Parent\",\"color\":\"green\",\"extra\":[{\"text\":\"Child\"}]}";
        List<TextSegment> segments = TextSegmentParser.parseJson(json);

        assertThat(segments).hasSize(2);
        // Child has no explicit color; it should inherit from parent
        assertThat(segments.get(1).style().color()).isEqualTo(new Color(85, 255, 85));
    }

    @Test
    void parseJson_childOverridesParentColor() {
        String json = "{\"text\":\"Parent\",\"color\":\"green\",\"extra\":[{\"text\":\"Child\",\"color\":\"red\"}]}";
        List<TextSegment> segments = TextSegmentParser.parseJson(json);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).style().color()).isEqualTo(new Color(85, 255, 85));
        assertThat(segments.get(1).style().color()).isEqualTo(new Color(255, 85, 85));
    }

    @Test
    void parseJson_childInheritsParentBold() {
        String json = "{\"text\":\"P\",\"bold\":true,\"extra\":[{\"text\":\"C\"}]}";
        List<TextSegment> segments = TextSegmentParser.parseJson(json);

        assertThat(segments.get(1).style().bold()).isTrue();
    }

    // --- parseJson: arrays ---

    @Test
    void parseJson_topLevelArray_parsesAllElements() {
        String json = "[{\"text\":\"A\"},{\"text\":\"B\"}]";
        List<TextSegment> segments = TextSegmentParser.parseJson(json);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).isEqualTo("A");
        assertThat(segments.get(1).text()).isEqualTo("B");
    }

    // --- parseJson: known color names ---

    @Test
    void parseJson_colorName_darkRed_appliesCorrectColor() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"T\",\"color\":\"dark_red\"}");

        assertThat(segments.get(0).style().color()).isEqualTo(new Color(170, 0, 0));
    }

    @Test
    void parseJson_colorName_gold_appliesCorrectColor() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"T\",\"color\":\"gold\"}");

        assertThat(segments.get(0).style().color()).isEqualTo(new Color(255, 170, 0));
    }

    @Test
    void parseJson_unknownColorName_usesDefault() {
        List<TextSegment> segments = TextSegmentParser.parseJson("{\"text\":\"T\",\"color\":\"not_a_color\"}");

        assertThat(segments.get(0).style().color()).isEqualTo(TextStyle.DEFAULT.color());
    }

    // --- edge cases ---

    @Test
    void parseJson_nullInput_returnsEmptyList() {
        assertThat(TextSegmentParser.parseJson(null)).isEmpty();
    }

    @Test
    void parseJson_emptyString_returnsEmptyList() {
        assertThat(TextSegmentParser.parseJson("")).isEmpty();
    }
}
