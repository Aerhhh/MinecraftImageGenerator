package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.aerh.imagegenerator.text.ChatFormat;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

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
     * Tracks the resolved formatting state of a text component for correct
     * ampersand-code emission. In Minecraft's text component model, each
     * element in an {@code extra} array inherits formatting from its
     * <em>parent</em>, not from preceding siblings. This record lets us
     * detect when formatting must be reset between siblings.
     */
    private record FormattingState(String colorCode, boolean bold, boolean italic,
                                   boolean underlined, boolean strikethrough, boolean obfuscated) {

        static final FormattingState EMPTY = new FormattingState(null, false, false, false, false, false);

        /**
         * Returns {@code true} when transitioning from {@code this} state to
         * {@code target} requires clearing at least one active formatting flag
         * (which in Minecraft's system means re-emitting a color code or
         * {@code &r}).
         */
        boolean needsFormattingReset(FormattingState target) {
            return (bold && !target.bold)
                || (italic && !target.italic)
                || (underlined && !target.underlined)
                || (strikethrough && !target.strikethrough)
                || (obfuscated && !target.obfuscated);
        }
    }

    /**
     * Converts a JSON text component object into an ampersand-formatted string
     * (e.g., {@code &6&lCool Sword}).
     * <p>
     * Correctly models Minecraft's text component inheritance: each element in
     * an {@code extra} array inherits formatting from its parent, not from
     * preceding siblings. Formatting codes that need to be <em>removed</em>
     * between siblings are handled by re-emitting a color code (which resets
     * all formatting in Minecraft) or {@code &r} when no color is present.
     * <p>
     * Handles nested {@code extra} arrays and primitive string entries.
     *
     * @param textComponent the JSON text component object
     *
     * @return the ampersand-formatted string
     */
    public static String toFormattedString(JsonObject textComponent) {
        StringBuilder result = new StringBuilder();
        FormattingState[] activeState = {FormattingState.EMPTY};
        appendComponent(result, textComponent, FormattingState.EMPTY, activeState);
        return result.toString();
    }

    /**
     * Recursively appends a text component and its {@code extra} children,
     * emitting formatting transitions as needed.
     *
     * @param result      the output buffer
     * @param component   the current text component
     * @param inherited   formatting inherited from the parent component
     * @param activeState single-element array holding the last-emitted state
     *                    (mutable across the entire tree traversal)
     */
    private static void appendComponent(StringBuilder result, JsonObject component,
                                        FormattingState inherited, FormattingState[] activeState) {
        FormattingState resolved = resolveFormatting(component, inherited);

        emitFormattingTransition(result, activeState[0], resolved);
        activeState[0] = resolved;

        if (component.has("text")) {
            String text = component.get("text").getAsString();
            if (!text.isEmpty()) {
                result.append(text);
            }
        }

        if (component.has("extra")) {
            JsonArray extraArray = component.getAsJsonArray("extra");
            for (JsonElement extraElement : extraArray) {
                if (extraElement.isJsonObject()) {
                    appendComponent(result, extraElement.getAsJsonObject(), resolved, activeState);
                } else if (extraElement.isJsonPrimitive()) {
                    result.append(extraElement.getAsString());
                }
            }
        }
    }

    /**
     * Determines the resolved formatting for a component by combining its own
     * properties with those inherited from its parent.
     */
    private static FormattingState resolveFormatting(JsonObject component, FormattingState inherited) {
        String color = inherited.colorCode();
        if (component.has("color")) {
            ChatFormat colorFormat = ChatFormat.of(component.get("color").getAsString());
            if (colorFormat != null && colorFormat.isColor()) {
                color = "&" + colorFormat.getCode();
            }
        }

        return new FormattingState(
            color,
            resolveFormattingFlag(component, "bold", inherited.bold()),
            resolveFormattingFlag(component, "italic", inherited.italic()),
            resolveFormattingFlag(component, "underlined", inherited.underlined()),
            resolveFormattingFlag(component, "strikethrough", inherited.strikethrough()),
            resolveFormattingFlag(component, "obfuscated", inherited.obfuscated())
        );
    }

    /**
     * Resolves a single formatting flag: uses the component's own value if
     * present, otherwise falls back to the inherited value.
     */
    private static boolean resolveFormattingFlag(JsonObject component, String key, boolean inherited) {
        if (component.has(key)) {
            return parseBooleanStrict(component.get(key));
        }
        return inherited;
    }

    /**
     * Emits the full formatting state whenever anything has changed from
     * {@code from} to {@code to}. Each component's output is self-describing
     * (color + all flags), so downstream processing (text wrapping, reverse
     * mapping, rarity extraction) cannot lose formatting context.
     */
    private static void emitFormattingTransition(StringBuilder result,
                                                 FormattingState from, FormattingState to) {
        if (from.equals(to)) {
            return;
        }

        if (to.colorCode() != null) {
            result.append(to.colorCode());
        } else if (from.needsFormattingReset(to)) {
            result.append("&").append(ChatFormat.RESET.getCode());
        }

        if (to.bold()) result.append("&").append(ChatFormat.BOLD.getCode());
        if (to.italic()) result.append("&").append(ChatFormat.ITALIC.getCode());
        if (to.underlined()) result.append("&").append(ChatFormat.UNDERLINE.getCode());
        if (to.strikethrough()) result.append("&").append(ChatFormat.STRIKETHROUGH.getCode());
        if (to.obfuscated()) result.append("&").append(ChatFormat.OBFUSCATED.getCode());
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

}