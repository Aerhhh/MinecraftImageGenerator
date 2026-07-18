package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aerh.imagegenerator.text.MinecraftFont;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import net.aerh.imagegenerator.text.segment.LineSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link NbtTextComponentUtil#toLineSegment}, the tree-walking component parser that
 * retains per-segment font id and drop-shadow state that {@link NbtTextComponentUtil#toFormattedString}
 * cannot carry through its ampersand flattening.
 */
class NbtTextComponentLineSegmentTest {

    private static JsonObject component(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static List<ColorSegment> segments(String json) {
        return NbtTextComponentUtil.toLineSegment(component(json)).getSegments();
    }

    @Test
    void retainsPerSegmentPackFontIdThroughTheTree() {
        String json = "{\"text\":\"\",\"font\":\"minecraft:language/custom\",\"extra\":["
            + "{\"text\":\"Cascade\",\"color\":\"#55FFFF\"},"
            + "{\"text\":\"\\uDB40\\uDC13\",\"font\":\"minecraft:space\"}"
            + "]}";
        List<ColorSegment> segments = segments(json);

        assertEquals(2, segments.size());
        // The label run inherits the line's language font id; the kern run keeps its own space font.
        assertEquals("minecraft:language/custom", segments.get(0).getPackFontId());
        assertEquals("Cascade", segments.get(0).getText());
        assertEquals("minecraft:space", segments.get(1).getPackFontId());
    }

    @Test
    void builtInFontMapsToEnumAndClearsInheritedPackFont() {
        String json = "{\"text\":\"\",\"font\":\"minecraft:space\",\"extra\":["
            + "{\"text\":\"x\",\"font\":\"minecraft:default\"}"
            + "]}";
        List<ColorSegment> segments = segments(json);

        assertEquals(1, segments.size());
        assertNull(segments.get(0).getPackFontId(), "a built-in font clears an inherited pack font id");
        assertEquals(MinecraftFont.DEFAULT, segments.get(0).getFont());
    }

    @Test
    void shadowColorAlphaZeroDisablesShadowForThatRunOnly() {
        String json = "{\"text\":\"label\",\"extra\":["
            + "{\"text\":\"icon\",\"shadow_color\":16777215}"
            + "]}";
        List<ColorSegment> segments = segments(json);

        assertEquals(2, segments.size());
        assertTrue(segments.get(0).isShadowEnabled(), "the plain run keeps its default shadow");
        assertFalse(segments.get(1).isShadowEnabled(), "the alpha-0 shadow_color run draws no shadow");
    }

    @Test
    void nonZeroShadowColorAlphaKeepsShadow() {
        // 0xFF000000 = opaque black shadow: still drawn.
        assertTrue(segments("{\"text\":\"x\",\"shadow_color\":-16777216}").get(0).isShadowEnabled());
    }

    @Test
    void childrenInheritColorFontAndShadowFromParentNotSiblings() {
        String json = "{\"text\":\"\",\"color\":\"#FF00AA\",\"font\":\"minecraft:space\",\"shadow_color\":16777215,\"extra\":["
            + "{\"text\":\"A\"},"
            + "{\"text\":\"B\",\"color\":\"gold\"},"
            + "{\"text\":\"C\"}"
            + "]}";
        List<ColorSegment> segments = segments(json);

        assertEquals(3, segments.size());
        assertEquals(ChatColor.of(0xFF00AA), segments.get(0).getColor().orElseThrow());
        // Sibling B's gold color must not leak into C, which inherits the parent's hex color.
        assertEquals(ChatColor.of(0xFF00AA), segments.get(2).getColor().orElseThrow());
        for (ColorSegment segment : segments) {
            assertEquals("minecraft:space", segment.getPackFontId(), "font inherited from parent");
            assertFalse(segment.isShadowEnabled(), "shadow state inherited from parent");
        }
    }

    @Test
    void emptyTextNodesContributeNoSegmentButStillPassStyleDown() {
        String json = "{\"text\":\"\",\"extra\":["
            + "{\"text\":\"\",\"font\":\"minecraft:space\",\"extra\":[{\"text\":\"only\"}]}"
            + "]}";
        List<ColorSegment> segments = segments(json);

        assertEquals(1, segments.size(), "the two empty-text wrappers emit nothing");
        assertEquals("only", segments.get(0).getText());
        assertEquals("minecraft:space", segments.get(0).getPackFontId());
    }

    @Test
    void formattingFlagsInheritAndOverride() {
        String json = "{\"text\":\"\",\"bold\":true,\"italic\":false,\"extra\":["
            + "{\"text\":\"A\"},"
            + "{\"text\":\"B\",\"bold\":false,\"underlined\":true}"
            + "]}";
        List<ColorSegment> segments = segments(json);

        assertTrue(segments.get(0).isBold(), "A inherits bold");
        assertFalse(segments.get(0).isUnderlined());
        assertFalse(segments.get(1).isBold(), "B overrides bold off");
        assertTrue(segments.get(1).isUnderlined(), "B sets underline");
    }

    @Test
    void primitiveExtraStringsInheritTheResolvedStyle() {
        String json = "{\"text\":\"\",\"font\":\"minecraft:space\",\"color\":\"red\",\"extra\":[\"plain\"]}";
        List<ColorSegment> segments = segments(json);

        assertEquals(1, segments.size());
        assertEquals("plain", segments.get(0).getText());
        assertEquals("minecraft:space", segments.get(0).getPackFontId());
    }

    @Test
    void shadowColorArrayFormAlphaZeroDisablesShadow() {
        assertTrue(NbtTextComponentUtil.shadowColorDraws(
            JsonParser.parseString("[1.0,1.0,1.0,1.0]")), "alpha 1.0 draws");
        assertFalse(NbtTextComponentUtil.shadowColorDraws(
            JsonParser.parseString("[0.0,1.0,1.0,1.0]")), "alpha 0.0 does not draw");
    }

    @Test
    void malformedShadowColorDefaultsToDrawingShadow() {
        assertTrue(NbtTextComponentUtil.shadowColorDraws(JsonParser.parseString("\"nonsense\"")));
        assertTrue(NbtTextComponentUtil.shadowColorDraws(JsonParser.parseString("[1,2]")));
    }

    @Test
    void toJsonRoundTripsDisabledShadowAndFontButNotDefaults() {
        LineSegment line = NbtTextComponentUtil.toLineSegment(component(
            "{\"text\":\"\",\"extra\":["
                + "{\"text\":\"x\",\"font\":\"minecraft:space\",\"shadow_color\":16777215},"
                + "{\"text\":\"y\"}"
                + "]}"));
        JsonObject shadowless = line.getSegments().get(0).toJson();
        JsonObject plain = line.getSegments().get(1).toJson();

        assertEquals(16777215, shadowless.get("shadow_color").getAsInt());
        assertEquals("minecraft:space", shadowless.get("font").getAsString());
        assertFalse(plain.has("shadow_color"), "the default-shadow run emits no shadow_color");
    }
}
