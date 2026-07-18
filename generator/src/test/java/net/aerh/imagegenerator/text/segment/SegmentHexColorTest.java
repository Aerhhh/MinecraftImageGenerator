package net.aerh.imagegenerator.text.segment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.MinecraftFont;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Hex color support in the segment model: legacy {@code &#RRGGBB} parsing, malformed
 * sequences staying literal, vanilla formatting-reset semantics, JSON emission, and the
 * {@code TextSegment.fromJson} path that previously crashed on vanilla 1.16+ hex colors.
 */
class SegmentHexColorTest {

    private static ColorSegment onlySegment(LineSegment line) {
        List<ColorSegment> segments = withText(line);
        assertEquals(1, segments.size(), "expected a single non-empty segment, got: " + segments);
        return segments.getFirst();
    }

    private static List<ColorSegment> withText(LineSegment line) {
        return line.getSegments().stream().filter(segment -> !segment.getText().isEmpty()).toList();
    }

    @Test
    void parsesAmpersandHexIntoRgbColorSegment() {
        ColorSegment segment = onlySegment(ColorSegment.fromLegacy("&#ff00aaHello"));
        assertEquals("Hello", segment.getText());
        assertEquals(ChatColor.of(0xFF00AA), segment.getColor().orElseThrow());
    }

    @Test
    void parsesSectionSymbolHexCaseInsensitively() {
        ColorSegment segment = onlySegment(ColorSegment.fromLegacy("§#FF00AAHello"));
        assertEquals("Hello", segment.getText());
        assertEquals(ChatColor.of(0xFF00AA), segment.getColor().orElseThrow());
    }

    @Test
    void malformedHexStaysLiteralText() {
        ColorSegment segment = onlySegment(ColorSegment.fromLegacy("&#ff00aHello"));
        assertEquals("&#ff00aHello", segment.getText(), "five hex digits must not be consumed as a color");
    }

    @Test
    void truncatedHexAtEndOfStringStaysLiteral() {
        ColorSegment segment = onlySegment(ColorSegment.fromLegacy("text &#ff00a"));
        assertEquals("text &#ff00a", segment.getText());
    }

    @Test
    void hexColorResetsActiveFormattingLikeAnyColorCode() {
        ColorSegment segment = onlySegment(ColorSegment.fromLegacy("&l&#ff00aatext"));
        assertEquals("text", segment.getText());
        assertFalse(segment.isBold(), "a color change resets formatting in vanilla");
        assertEquals(ChatColor.of(0xFF00AA), segment.getColor().orElseThrow());
    }

    @Test
    void hexColorPreservesActiveFont() {
        ColorSegment segment = onlySegment(ColorSegment.fromLegacy("&g&#ff00aarunes"));
        assertEquals(MinecraftFont.GALACTIC, segment.getFont());
        assertEquals(ChatColor.of(0xFF00AA), segment.getColor().orElseThrow());
    }

    @Test
    void hexColorSplitsSegmentsMidLine() {
        List<ColorSegment> segments = withText(ColorSegment.fromLegacy("&cred &#00ff00lime"));
        assertEquals(2, segments.size());
        assertEquals(ChatColor.Legacy.RED, segments.get(0).getColor().orElseThrow());
        assertEquals(ChatColor.of(0x00FF00), segments.get(1).getColor().orElseThrow());
        assertEquals("lime", segments.get(1).getText());
    }

    @Test
    void toJsonEmitsHexColorString() {
        ColorSegment segment = onlySegment(ColorSegment.fromLegacy("&#ff00aaHello"));
        JsonObject json = segment.toJson();
        assertEquals("#ff00aa", json.get("color").getAsString());
    }

    @Test
    void fromJsonParsesVanillaHexColor() {
        JsonObject json = JsonParser.parseString("{\"text\":\"Hi\",\"color\":\"#ff00aa\"}").getAsJsonObject();
        TextSegment segment = assertDoesNotThrow(() -> TextSegment.fromJson(json),
            "vanilla 1.16+ hex colors must not crash fromJson");
        assertNotNull(segment);
        assertEquals(ChatColor.of(0xFF00AA), segment.getColor().orElseThrow());
    }

    @Test
    void fromJsonStillParsesNamedColors() {
        JsonObject json = JsonParser.parseString("{\"text\":\"Hi\",\"color\":\"gold\"}").getAsJsonObject();
        TextSegment segment = TextSegment.fromJson(json);
        assertNotNull(segment);
        assertEquals(ChatColor.Legacy.GOLD, segment.getColor().orElseThrow());
    }

    @Test
    void fromJsonIgnoresUnknownColorsInsteadOfCrashing() {
        JsonObject json = JsonParser.parseString("{\"text\":\"Hi\",\"color\":\"chartreuse\"}").getAsJsonObject();
        TextSegment segment = assertDoesNotThrow(() -> TextSegment.fromJson(json));
        assertNotNull(segment);
        assertEquals(ChatColor.Legacy.GRAY, segment.getColor().orElseThrow(), "unknown colors fall back to the default");
    }

    @Test
    void jsonRoundTripPreservesHexColor() {
        JsonObject json = JsonParser.parseString("{\"text\":\"Hi\",\"color\":\"#ff00aa\"}").getAsJsonObject();
        TextSegment segment = TextSegment.fromJson(json);
        assertNotNull(segment);
        JsonObject reEmitted = segment.toJson();
        assertEquals("#ff00aa", reEmitted.get("color").getAsString());
        assertInstanceOf(ChatColor.Custom.class, segment.getColor().orElseThrow());
    }
}
