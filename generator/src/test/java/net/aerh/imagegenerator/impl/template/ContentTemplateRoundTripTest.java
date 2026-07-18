package net.aerh.imagegenerator.impl.template;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The key invariant: a template authored from a capture, filled with that capture's own values,
 * reproduces the original capture EXACTLY. Private-use glyph runs, negative-space kern runs, nested
 * extra trees, per-segment fonts and {@code shadow_color} all stay byte-verbatim - only the marked
 * {@code {placeholder}} tokens and {@code template_repeat} rows change.
 *
 * <p>Glyph and kern runs are written with {@code \\uXXXX} escapes (private-use codepoints);
 * the Java compiler folds them to the raw characters before the JSON is parsed, so the capture and
 * the template carry byte-identical glyph runs.
 */
class ContentTemplateRoundTripTest {

    @Test
    void fillingWithTheCapturesOwnValuesReproducesTheCapture() {
        // The original capture: an item NBT tree with PUA glyph runs (U+E000-range icons and a
        // U+F800-range negative-space kern run), nested extra trees, per-segment fonts and a
        // shadow_color, alongside the plain text a template would parameterize.
        String capture = """
            {
              "components": {
                "minecraft:custom_name": {
                  "text": "",
                  "extra": [
                    {"text": "", "font": "myserver:icons", "color": "#55FFFF"},
                    {"text": "Aerh's Bank", "bold": true, "shadow_color": -16777216},
                    {"text": "", "font": "myserver:space"}
                  ]
                },
                "minecraft:lore": [
                  "",
                  {"text": "Balance: 1,024", "color": "gray",
                   "extra": [{"text": " coins", "italic": true}]},
                  " Rank: MVP"
                ]
              }
            }
            """;

        // The template: the same tree with the three variable spots marked, everything else - the
        // glyph runs, the kern run, fonts, shadow_color, nesting - copied verbatim.
        String templateJson = """
            {
              "schema_version": 1,
              "placeholders": {
                "owner": {},
                "balance": {},
                "rank": {}
              },
              "content": {
                "components": {
                  "minecraft:custom_name": {
                    "text": "",
                    "extra": [
                      {"text": "", "font": "myserver:icons", "color": "#55FFFF"},
                      {"text": "{owner}'s Bank", "bold": true, "shadow_color": -16777216},
                      {"text": "", "font": "myserver:space"}
                    ]
                  },
                  "minecraft:lore": [
                    "",
                    {"text": "Balance: {balance}", "color": "gray",
                     "extra": [{"text": " coins", "italic": true}]},
                    " Rank: {rank}"
                  ]
                }
              }
            }
            """;

        ContentTemplate template = ContentTemplate.parse(templateJson);
        JsonObject filled = template.fill(Map.of(
            "owner", "Aerh",
            "balance", "1,024",
            "rank", "MVP"));

        JsonObject expected = JsonParser.parseString(capture).getAsJsonObject();
        assertEquals(expected, filled, "filled template must deep-equal the original capture");
    }

    @Test
    void repeatSectionFilledWithCapturedRowsReproducesTheCapture() {
        // A capture whose lore is a fixed glyph header followed by two rows of the same shape.
        String capture = """
            {
              "components": {
                "minecraft:lore": [
                  {"text": " Contents", "font": "myserver:icons"},
                  {"text": "Diamond x64", "color": "aqua"},
                  {"text": "Emerald x12", "color": "green"}
                ]
              }
            }
            """;

        String templateJson = """
            {
              "schema_version": 1,
              "placeholders": {
                "item": {},
                "count": {},
                "color": {}
              },
              "content": {
                "components": {
                  "minecraft:lore": [
                    {"text": " Contents", "font": "myserver:icons"},
                    {"template_repeat": "entries", "text": "{item} x{count}", "color": "{color}"}
                  ]
                }
              }
            }
            """;

        ContentTemplate template = ContentTemplate.parse(templateJson);
        JsonObject filled = template.fill(Map.of(), Map.of("entries", List.of(
            Map.of("item", "Diamond", "count", "64", "color", "aqua"),
            Map.of("item", "Emerald", "count", "12", "color", "green"))));

        JsonObject expected = JsonParser.parseString(capture).getAsJsonObject();
        assertEquals(expected, filled, "expanded rows must deep-equal the captured rows in order");
    }
}
