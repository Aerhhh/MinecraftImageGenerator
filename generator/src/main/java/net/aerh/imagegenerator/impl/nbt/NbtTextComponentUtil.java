package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.LegacyCode;
import net.aerh.imagegenerator.text.MinecraftFont;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import net.aerh.imagegenerator.text.segment.LineSegment;
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
            // Named colors and vanilla 1.16+ hex colors; unrecognized values inherit the parent's color.
            ChatColor textColor = ChatColor.fromJsonString(component.get("color").getAsString());
            if (textColor != null) {
                color = "&" + legacyCodeOf(textColor);
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
            result.append("&").append(LegacyCode.RESET.getCode());
        }

        if (to.bold()) result.append("&").append(LegacyCode.BOLD.getCode());
        if (to.italic()) result.append("&").append(LegacyCode.ITALIC.getCode());
        if (to.underlined()) result.append("&").append(LegacyCode.UNDERLINE.getCode());
        if (to.strikethrough()) result.append("&").append(LegacyCode.STRIKETHROUGH.getCode());
        if (to.obfuscated()) result.append("&").append(LegacyCode.OBFUSCATED.getCode());
    }

    /**
     * Walks a JSON text component tree into a {@link LineSegment} of {@link ColorSegment}s that
     * retains, per drawn run, its resolved color, font, formatting flags AND drop-shadow state.
     * <p>
     * Unlike {@link #toFormattedString(JsonObject)} - which flattens the tree to an ampersand
     * string and therefore cannot carry a {@code font} id or a {@code shadow_color} - this walk
     * preserves both. Each segment inherits color, font, the five formatting flags and the
     * shadow state from its parent (Minecraft's inheritance model: children inherit from their
     * parent, not from preceding siblings), overriding only the properties it declares. A
     * built-in {@code font} maps to its {@link MinecraftFont}; any other id is kept as a raw
     * pack font id. A {@code shadow_color} whose alpha byte is zero disables the run's shadow.
     * <p>
     * Runs are emitted in draw order: the component's own {@code text} first, then each
     * {@code extra} child recursively. Empty-text nodes contribute no segment but still pass
     * their resolved style down to their children. A component without a resolvable style still
     * yields its literal text under the inherited style, so nothing is dropped.
     *
     * @param component the JSON text component object
     *
     * @return the parsed line, one {@link ColorSegment} per non-empty drawn run
     */
    public static LineSegment toLineSegment(JsonObject component) {
        LineSegment.Builder builder = LineSegment.builder();
        appendSegments(builder, component, ComponentStyle.ROOT);
        return builder.build();
    }

    /**
     * Recursively appends a component's own text run and its {@code extra} children to
     * {@code builder}, threading resolved style down the tree.
     */
    private static void appendSegments(LineSegment.Builder builder, JsonObject component, ComponentStyle inherited) {
        ComponentStyle resolved = inherited.resolve(component);

        if (component.has("text")) {
            String text = component.get("text").getAsString();
            if (!text.isEmpty()) {
                builder.withSegments(resolved.toSegment(text));
            }
        }

        if (component.has("extra")) {
            for (JsonElement extraElement : component.getAsJsonArray("extra")) {
                if (extraElement.isJsonObject()) {
                    appendSegments(builder, extraElement.getAsJsonObject(), resolved);
                } else if (extraElement.isJsonPrimitive()) {
                    String text = extraElement.getAsString();
                    if (!text.isEmpty()) {
                        builder.withSegments(resolved.toSegment(text));
                    }
                }
            }
        }
    }

    /**
     * The resolved style inherited down a component tree by {@link #toLineSegment}: a color (null
     * until one is declared, matching the segment default), a font (a built-in {@link MinecraftFont}
     * or a raw pack font id), the five formatting flags and whether the run draws its drop shadow.
     */
    private record ComponentStyle(@Nullable ChatColor color, MinecraftFont font, @Nullable String packFontId,
                                  boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                                  boolean obfuscated, boolean shadowEnabled) {

        static final ComponentStyle ROOT =
            new ComponentStyle(null, MinecraftFont.DEFAULT, null, false, false, false, false, false, true);

        ComponentStyle resolve(JsonObject component) {
            ChatColor resolvedColor = color;
            if (component.has("color")) {
                ChatColor declared = ChatColor.fromJsonString(component.get("color").getAsString());
                if (declared != null) {
                    resolvedColor = declared;
                }
            }

            MinecraftFont resolvedFont = font;
            String resolvedPackFontId = packFontId;
            if (component.has("font")) {
                String fontId = component.get("font").getAsString();
                MinecraftFont builtIn = MinecraftFont.fromResourceLocationOrNull(fontId);
                if (builtIn != null) {
                    resolvedFont = builtIn;
                    resolvedPackFontId = null;
                } else {
                    resolvedPackFontId = fontId;
                }
            }

            boolean resolvedShadow = shadowEnabled;
            if (component.has("shadow_color")) {
                resolvedShadow = shadowColorDraws(component.get("shadow_color"));
            }

            return new ComponentStyle(
                resolvedColor,
                resolvedFont,
                resolvedPackFontId,
                resolveFlag(component, "bold", bold),
                resolveFlag(component, "italic", italic),
                resolveFlag(component, "underlined", underlined),
                resolveFlag(component, "strikethrough", strikethrough),
                resolveFlag(component, "obfuscated", obfuscated),
                resolvedShadow
            );
        }

        private static boolean resolveFlag(JsonObject component, String key, boolean inherited) {
            return component.has(key) ? parseBooleanStrict(component.get(key)) : inherited;
        }

        ColorSegment toSegment(String text) {
            ColorSegment.Builder builder = ColorSegment.builder()
                .withText(text)
                .withFont(font)
                .withPackFontId(packFontId)
                .isBold(bold)
                .isItalic(italic)
                .isUnderlined(underlined)
                .isStrikethrough(strikethrough)
                .isObfuscated(obfuscated)
                .withShadowEnabled(shadowEnabled);
            if (color != null) {
                builder.withColor(color);
            }
            return builder.build();
        }
    }

    /**
     * Whether a {@code shadow_color} component value draws a visible drop shadow, i.e. its alpha
     * component is non-zero. Accepts the packed-ARGB integer form (e.g. {@code 16777215} =
     * {@code 0x00FFFFFF} = alpha 0 = no shadow) and the {@code [a, r, g, b]} float-array form
     * (each in {@code 0..1}); an unrecognized shape defaults to drawing the shadow (the vanilla
     * default), so a malformed value never silently suppresses a shadow.
     *
     * @param element the {@code shadow_color} JSON element
     *
     * @return true when the shadow should be drawn
     */
    public static boolean shadowColorDraws(JsonElement element) {
        if (element == null) {
            return true;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                long argb = primitive.getAsLong();
                return ((argb >>> 24) & 0xFF) != 0;
            }
            return true;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() == 4 && array.get(0).isJsonPrimitive() && array.get(0).getAsJsonPrimitive().isNumber()) {
                return array.get(0).getAsDouble() != 0.0;
            }
        }
        return true;
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

        return rawValue.replace(LegacyCode.SECTION_SYMBOL, LegacyCode.AMPERSAND_SYMBOL);
    }

    /**
     * The in-band legacy code for a resolved color: the single-character code for a named
     * {@link ChatColor.Legacy} color, or the lowercase {@code #rrggbb} hex literal for a custom color.
     */
    private static String legacyCodeOf(ChatColor color) {
        return color.code()
            .map(String::valueOf)
            .orElseGet(() -> String.format("#%06x", color.rgb() & 0xFFFFFF));
    }

}