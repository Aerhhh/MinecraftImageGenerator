package net.aerh.jigsaw.core.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aerh.jigsaw.api.text.ChatColor;
import net.aerh.jigsaw.api.text.ChatFormatting;

/**
 * Utility methods for working with Minecraft JSON text components embedded in NBT data.
 */
public final class NbtTextComponentUtil {

    private NbtTextComponentUtil() {}

    /**
     * Returns {@code true} if the given string appears to be a JSON text component
     * (starts with {@code {} or {@code [}).
     *
     * @param text The string to test.
     * @return Whether the string is a JSON object or array.
     */
    public static boolean isJsonTextComponent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    /**
     * Recursively extracts the visible plain text from a JSON text component element.
     *
     * @param element The JSON text component.
     * @return The concatenated visible text, or an empty string if none is found.
     */
    public static String extractVisibleText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement child : element.getAsJsonArray()) {
                sb.append(extractVisibleText(child));
            }
            return sb.toString();
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            StringBuilder sb = new StringBuilder();

            if (obj.has("text")) {
                sb.append(obj.get("text").getAsString());
            }
            if (obj.has("extra")) {
                JsonElement extra = obj.get("extra");
                if (extra.isJsonArray()) {
                    for (JsonElement child : extra.getAsJsonArray()) {
                        sb.append(extractVisibleText(child));
                    }
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * Converts a JSON text component element to an ampersand-coded formatting string.
     * <p>
     * Applies color and formatting codes from the component's fields.
     *
     * @param element The JSON text component element.
     * @return An ampersand-coded string, or an empty string if the element is null/empty.
     */
    public static String toFormattedString(JsonElement element) {
        return toFormattedString(element, "");
    }

    private static String toFormattedString(JsonElement element, String inheritedPrefix) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return inheritedPrefix + element.getAsString();
        }
        if (element.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement child : element.getAsJsonArray()) {
                sb.append(toFormattedString(child, inheritedPrefix));
            }
            return sb.toString();
        }
        if (element.isJsonObject()) {
            return objectToFormattedString(element.getAsJsonObject(), inheritedPrefix);
        }
        return "";
    }

    private static String objectToFormattedString(JsonObject obj, String inheritedPrefix) {
        StringBuilder prefix = new StringBuilder();

        if (obj.has("color")) {
            String colorStr = obj.get("color").getAsString();
            char code = resolveColorCode(colorStr);
            if (code != 0) {
                prefix.append('&').append(code);
            }
        }

        if (isTruthy(obj, "bold")) {
            prefix.append('&').append(ChatFormatting.BOLD.code());
        }
        if (isTruthy(obj, "italic")) {
            prefix.append('&').append(ChatFormatting.ITALIC.code());
        }
        if (isTruthy(obj, "underlined")) {
            prefix.append('&').append(ChatFormatting.UNDERLINE.code());
        }
        if (isTruthy(obj, "strikethrough")) {
            prefix.append('&').append(ChatFormatting.STRIKETHROUGH.code());
        }
        if (isTruthy(obj, "obfuscated")) {
            prefix.append('&').append(ChatFormatting.OBFUSCATED.code());
        }

        // Build the full prefix for this component and its children
        String fullPrefix = prefix.isEmpty() ? inheritedPrefix : inheritedPrefix + prefix;

        StringBuilder sb = new StringBuilder();
        if (obj.has("text")) {
            String text = obj.get("text").getAsString();
            if (!text.isEmpty()) {
                sb.append(fullPrefix).append(text);
            }
        }

        if (obj.has("extra") && obj.get("extra").isJsonArray()) {
            for (JsonElement child : obj.getAsJsonArray("extra")) {
                sb.append(toFormattedString(child, fullPrefix));
            }
        }

        return sb.toString();
    }

    /**
     * Checks if a JSON field is truthy - handles both boolean true and numeric 1 (from SNBT byte values).
     */
    private static boolean isTruthy(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return false;
        }
        JsonElement element = obj.get(key);
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            }
            if (prim.isNumber()) {
                return prim.getAsInt() != 0;
            }
        }
        return false;
    }

    /**
     * Resolves a color name or hex string to a Minecraft color code character.
     *
     * @param colorStr The color string from a JSON text component.
     * @return The color code character, or {@code 0} if unrecognized.
     */
    private static char resolveColorCode(String colorStr) {
        if (colorStr == null) {
            return 0;
        }
        ChatColor chatColor = ChatColor.byName(colorStr);
        return chatColor != null ? chatColor.code() : 0;
    }

    /**
     * Attempts to parse a string as a JSON text component.
     * Returns {@code null} if the string is not valid JSON.
     *
     * @param text The string to parse.
     * @return The parsed {@link JsonElement} or {@code null}.
     */
    public static JsonElement tryParseJson(String text) {
        if (!isJsonTextComponent(text)) {
            return null;
        }
        try {
            return JsonParser.parseString(text);
        } catch (Exception e) {
            return null;
        }
    }
}
