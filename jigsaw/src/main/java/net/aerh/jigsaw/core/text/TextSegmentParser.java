package net.aerh.jigsaw.core.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.aerh.jigsaw.api.text.ChatColor;
import net.aerh.jigsaw.api.text.FormattingParser;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses text into {@link TextSegment} lists from either legacy Minecraft formatting codes
 * or Minecraft JSON text component format.
 */
public final class TextSegmentParser {

    private TextSegmentParser() {}

    /**
     * Parses a legacy formatted string (using {@code &} or {@code §} codes) into segments.
     * Delegates to {@link FormattingParser#parse(String)}.
     *
     * @param text The raw legacy-formatted string.
     * @return An ordered list of {@link TextSegment}s.
     */
    public static List<TextSegment> parseLegacy(String text) {
        return FormattingParser.parse(text);
    }

    /**
     * Parses a Minecraft JSON text component string into segments.
     * <p>
     * Handles:
     * <ul>
     *   <li>JSON objects with {@code text}, {@code color}, {@code bold}, {@code italic},
     *       {@code underlined}, {@code strikethrough}, {@code obfuscated}, {@code font}, and {@code extra} fields.</li>
     *   <li>JSON string primitives (treated as plain text with default style).</li>
     *   <li>JSON arrays (each element is parsed independently).</li>
     * </ul>
     * Color names map to {@link ChatColor} values. Hex colors in {@code #RRGGBB} format are supported.
     * Formatting properties are inherited from parent to children unless overridden.
     *
     * @param json The JSON text component string.
     * @return An ordered list of {@link TextSegment}s, empty if input is null or blank.
     */
    public static List<TextSegment> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (Exception e) {
            return List.of();
        }

        List<TextSegment> segments = new ArrayList<>();
        parseElement(element, TextStyle.DEFAULT, segments);
        return List.copyOf(segments);
    }

    private static void parseElement(JsonElement element, TextStyle inherited, List<TextSegment> out) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                String text = primitive.getAsString();
                if (!text.isEmpty()) {
                    out.add(new TextSegment(text, inherited));
                }
            }
        } else if (element.isJsonObject()) {
            parseObject(element.getAsJsonObject(), inherited, out);
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                parseElement(child, inherited, out);
            }
        }
    }

    private static void parseObject(JsonObject obj, TextStyle inherited, List<TextSegment> out) {
        TextStyle style = resolveStyle(obj, inherited);

        // Emit this object's own text
        if (obj.has("text")) {
            String text = obj.get("text").getAsString();
            if (!text.isEmpty()) {
                out.add(new TextSegment(text, style));
            }
        }

        // Parse extra children with the resolved style as their inherited style
        if (obj.has("extra") && obj.get("extra").isJsonArray()) {
            for (JsonElement child : obj.getAsJsonArray("extra")) {
                parseElement(child, style, out);
            }
        }
    }

    private static TextStyle resolveStyle(JsonObject obj, TextStyle inherited) {
        TextStyle style = inherited;

        if (obj.has("color")) {
            String colorValue = obj.get("color").getAsString();
            Color resolved = resolveColor(colorValue);
            if (resolved != null) {
                style = style.withColor(resolved);
            }
        }

        if (obj.has("font")) {
            style = style.withFont(obj.get("font").getAsString());
        }

        if (obj.has("bold") && obj.get("bold").getAsBoolean()) {
            style = style.withBold(true);
        }

        if (obj.has("italic") && obj.get("italic").getAsBoolean()) {
            style = style.withItalic(true);
        }

        if (obj.has("obfuscated") && obj.get("obfuscated").getAsBoolean()) {
            style = style.withObfuscated(true);
        }

        if (obj.has("underlined") && obj.get("underlined").getAsBoolean()) {
            style = new TextStyle(
                    style.color(), style.fontId(), style.bold(), style.italic(),
                    true, style.strikethrough(), style.obfuscated()
            );
        }

        if (obj.has("strikethrough") && obj.get("strikethrough").getAsBoolean()) {
            style = new TextStyle(
                    style.color(), style.fontId(), style.bold(), style.italic(),
                    style.underlined(), true, style.obfuscated()
            );
        }

        return style;
    }

    /**
     * Resolves a color string to a {@link Color}. Supports named ChatColors and hex {@code #RRGGBB}.
     *
     * @param colorValue The color string from the JSON component.
     * @return The resolved {@link Color}, or {@code null} if unrecognized.
     */
    private static Color resolveColor(String colorValue) {
        if (colorValue == null) {
            return null;
        }
        if (colorValue.startsWith("#") && colorValue.length() == 7) {
            try {
                return Color.decode(colorValue);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        ChatColor chatColor = ChatColor.byName(colorValue);
        return chatColor != null ? chatColor.color() : null;
    }
}
