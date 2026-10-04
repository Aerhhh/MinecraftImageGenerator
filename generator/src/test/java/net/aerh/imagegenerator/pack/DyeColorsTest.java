package net.aerh.imagegenerator.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DyeColorsTest {

    @Test
    void parsesHexInEitherCase() {
        assertEquals(OptionalInt.of(0x8932B8), DyeColors.parse("#8932B8"));
        assertEquals(OptionalInt.of(0x8932B8), DyeColors.parse("#8932b8"));
    }

    @Test
    void parsesTheHexExtremes() {
        assertEquals(OptionalInt.of(0x000000), DyeColors.parse("#000000"), "black is a real dye color, not absent");
        assertEquals(OptionalInt.of(0xFFFFFF), DyeColors.parse("#FFFFFF"));
    }

    @Test
    void parsesEveryVanillaDyeName() {
        Map<String, Integer> expected = Map.ofEntries(
            Map.entry("white", 0xF9FFFE),
            Map.entry("orange", 0xF9801D),
            Map.entry("magenta", 0xC74EBD),
            Map.entry("light_blue", 0x3AB3DA),
            Map.entry("yellow", 0xFED83D),
            Map.entry("lime", 0x80C71F),
            Map.entry("pink", 0xF38BAA),
            Map.entry("gray", 0x474F52),
            Map.entry("light_gray", 0x9D9D97),
            Map.entry("cyan", 0x169C9C),
            Map.entry("purple", 0x8932B8),
            Map.entry("blue", 0x3C44AA),
            Map.entry("brown", 0x835432),
            Map.entry("green", 0x5E7C16),
            Map.entry("red", 0xB02E26),
            Map.entry("black", 0x1D1D21));
        expected.forEach((name, rgb) -> assertEquals(OptionalInt.of(rgb), DyeColors.parse(name), name));
    }

    @Test
    void namesMatchTheLeatherArmorOverlayColors() throws IOException {
        // The overlay resources carry the same vanilla dye table for leather armor; a pack item
        // dyed "red" must match a vanilla leather item dyed "red", so the two must never drift.
        JsonObject options = leatherArmorOverlayOptions();
        assertEquals(16, options.size(), "the overlay table lists the 16 vanilla dyes");
        for (Map.Entry<String, JsonElement> option : options.entrySet()) {
            JsonArray colors = option.getValue().getAsJsonArray();
            assertEquals(OptionalInt.of(colors.get(0).getAsInt() & 0xFFFFFF), DyeColors.parse(option.getKey()),
                option.getKey());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RED", "Red", " red ", "Light Blue", "light-blue", "LIGHT_BLUE", "light gray"})
    void namesIgnoreCaseWhitespaceAndSeparators(String value) {
        assertTrue(DyeColors.parse(value).isPresent(), value);
    }

    @Test
    void spacedNamesResolveToTheUnderscoredDye() {
        assertEquals(DyeColors.parse("light_blue"), DyeColors.parse("Light Blue"));
        assertEquals(DyeColors.parse("light_gray"), DyeColors.parse("light-gray"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "#12345", "#1234567", "123456", "8932B8", "#GGGGGG", "0xFF0000",
        "#FF0000FF", "crimson", "speed", "light__blue", "#", "##FF0000"})
    void rejectsAnythingElse(String value) {
        assertEquals(OptionalInt.empty(), DyeColors.parse(value), String.valueOf(value));
    }

    private static JsonObject leatherArmorOverlayOptions() throws IOException {
        try (InputStream stream = DyeColorsTest.class.getResourceAsStream("/minecraft/assets/json/overlay_colors.json")) {
            assertNotNull(stream, "overlay_colors.json is on the classpath");
            JsonArray tables = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement table : tables) {
                JsonObject object = table.getAsJsonObject();
                if (object.get("name").getAsString().equals("leather_armor")) {
                    return object.getAsJsonObject("options");
                }
            }
            throw new AssertionError("no leather_armor table in overlay_colors.json");
        }
    }
}
