package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.aerh.imagegenerator.text.ChatFormat;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Shared utilities for working with Minecraft JSON text components and NBT boolean values.
 * <p>
 * These methods are used across NBT format handlers, the tooltip generator, and text segment parsing.
 * Centralizing them here avoids duplication and ensures consistent behavior when adding new format support.
 */
public final class NbtTextComponentUtil {

    private NbtTextComponentUtil() {
    }

    /**
     * Parses a boolean value from a {@link JsonElement} that may be a native boolean, a number,
     * or a string representation ({@code "true"}, {@code "1b"}, etc.).
     * <p>
     * Returns {@code null} if the element is null, not a primitive, or not a recognized boolean representation.
     * This three-state return (true/false/null) allows callers to distinguish between
     * "explicitly false" and "not present".
     *
     * @param element the JSON element to parse
     *
     * @return true, false, or null if the value cannot be interpreted as a boolean
     */
    public static @Nullable Boolean parseBoolean(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }

        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }

        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsNumber().intValue() != 0;
        }

        if (element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString().trim().toLowerCase(Locale.ROOT);
            if (raw.equals("true") || raw.equals("1") || raw.equals("1b")) {
                return true;
            }

            if (raw.equals("false") || raw.equals("0") || raw.equals("0b")) {
                return false;
            }
        }

        return null;
    }

    /**
     * Convenience overload that returns a primitive boolean, defaulting to {@code false}
     * when the element is null or unrecognized.
     *
     * @param element the JSON element to parse
     *
     * @return true if the element represents a truthy value, false otherwise
     */
    public static boolean parseBooleanStrict(JsonElement element) {
        Boolean result = parseBoolean(element);
        return result != null && result;
    }

    /**
     * Determines whether a string is a JSON text component (e.g., {@code {"text":"Hello","color":"gold"}})
     * rather than a plain section symbol coded string (e.g., {@code §6Hello}).
     * <p>
     * The check requires the string to start with {@code {} or {@code [} and successfully parse as valid JSON.
     * This avoids false positives for strings like {@code "§7{Ability}"} which start with {@code {} but are not JSON.
     *
     * @param value the string to check
     *
     * @return true if the string is a valid JSON text component
     */
    public static boolean isJsonTextComponent(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        String trimmed = value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false;
        }

        try {
            JsonParser.parseString(trimmed);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * Recursively extracts visible text from a JSON text component object, stripping all formatting.
     * Concatenates the {@code text} fields from the root and all {@code extra} entries.
     * <p>
     * Used for line length measurement where formatting codes should not count toward the width.
     *
     * @param textComponent the JSON text component object
     *
     * @return the concatenated visible text
     */
    public static String extractVisibleText(JsonObject textComponent) {
        StringBuilder result = new StringBuilder();

        if (textComponent.has("text")) {
            String text = textComponent.get("text").getAsString();
            if (!text.isEmpty()) {
                result.append(text);
            }
        }

        if (textComponent.has("extra")) {
            JsonArray extraArray = textComponent.getAsJsonArray("extra");
            for (JsonElement extraElement : extraArray) {
                if (extraElement.isJsonObject()) {
                    result.append(extractVisibleText(extraElement.getAsJsonObject()));
                } else if (extraElement.isJsonPrimitive()) {
                    result.append(extraElement.getAsString());
                }
            }
        }

        return result.toString();
    }

    /**
     * Converts a JSON text component object into an ampersand-formatted string
     * (e.g., {@code &6&lCool Sword}).
     * <p>
     * Handles the {@code color}, {@code text}, {@code extra} array, and formatting flags
     * ({@code bold}, {@code italic}, {@code underlined}, {@code strikethrough}, {@code obfuscated}).
     *
     * @param textComponent the JSON text component object
     *
     * @return the ampersand-formatted string
     */
    public static String toFormattedString(JsonObject textComponent) {
        StringBuilder result = new StringBuilder();
        String lastColorCode = null;

        if (textComponent.has("color")) {
            String colorName = textComponent.get("color").getAsString();
            ChatFormat colorFormat = ChatFormat.of(colorName);
            lastColorCode = appendColorIfNeeded(result, colorFormat, lastColorCode);
        }

        appendFormattingCodes(result, textComponent);

        if (textComponent.has("text")) {
            String text = textComponent.get("text").getAsString();
            if (!text.isEmpty()) {
                result.append(text);
            }
        }

        if (textComponent.has("extra")) {
            JsonArray extraArray = textComponent.getAsJsonArray("extra");
            for (JsonElement extraElement : extraArray) {
                if (!extraElement.isJsonObject()) {
                    continue;
                }

                JsonObject extraComponent = extraElement.getAsJsonObject();

                if (extraComponent.has("color")) {
                    String colorName = extraComponent.get("color").getAsString();
                    ChatFormat colorFormat = ChatFormat.of(colorName);
                    lastColorCode = appendColorIfNeeded(result, colorFormat, lastColorCode);
                }

                appendFormattingCodes(result, extraComponent);

                if (extraComponent.has("text")) {
                    result.append(extraComponent.get("text").getAsString());
                }
            }
        }

        return result.toString();
    }

    /**
     * Parses a legacy text value that may be either a plain section symbol coded string
     * (pre-1.13) or a JSON text component string (1.13-1.20.4).
     * <p>
     * If the value is a JSON text component, it is parsed and converted to an ampersand-formatted string.
     * Otherwise, section symbols are replaced with ampersands.
     *
     * @param rawValue the raw text value from NBT
     *
     * @return the ampersand-formatted string
     */
    public static String parseTextValue(String rawValue) {
        if (isJsonTextComponent(rawValue)) {
            JsonElement parsed = JsonParser.parseString(rawValue);
            if (parsed.isJsonObject()) {
                return toFormattedString(parsed.getAsJsonObject());
            }
        }

        return rawValue.replace(ChatFormat.SECTION_SYMBOL, ChatFormat.AMPERSAND_SYMBOL);
    }

    private static void appendFormattingCodes(StringBuilder result, JsonObject component) {
        appendFormattingCode(result, component, "bold", ChatFormat.BOLD);
        appendFormattingCode(result, component, "italic", ChatFormat.ITALIC);
        appendFormattingCode(result, component, "underlined", ChatFormat.UNDERLINE);
        appendFormattingCode(result, component, "strikethrough", ChatFormat.STRIKETHROUGH);
        appendFormattingCode(result, component, "obfuscated", ChatFormat.OBFUSCATED);
    }

    private static void appendFormattingCode(StringBuilder result, JsonObject component, String key, ChatFormat format) {
        if (component.has(key) && parseBooleanStrict(component.get(key))) {
            result.append("&").append(format.getCode());
        }
    }

    private static String appendColorIfNeeded(StringBuilder builder, ChatFormat colorFormat, String lastColorCode) {
        if (colorFormat == null || !colorFormat.isColor()) {
            return lastColorCode;
        }

        String code = "&" + colorFormat.getCode();
        if (code.equalsIgnoreCase(lastColorCode)) {
            return lastColorCode;
        }

        builder.append(code);
        return code;
    }
}