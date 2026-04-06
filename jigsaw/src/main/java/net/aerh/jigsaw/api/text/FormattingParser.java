package net.aerh.jigsaw.api.text;

import net.aerh.jigsaw.core.font.MinecraftFontId;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Minecraft-formatted text strings (using {@code §} or {@code &} as format markers)
 * into a list of {@link TextSegment}s, each carrying its own {@link TextStyle}.
 *
 * @see TextSegment
 * @see TextStyle
 * @see ChatColor
 * @see ChatFormatting
 */
public final class FormattingParser {

    private static final Pattern NAMED_FORMAT_PATTERN = Pattern.compile("%%([^%]+)%%");

    private FormattingParser() {}

    /**
     * Parses a formatted string and returns an ordered list of {@link TextSegment}s.
     * <p>
     * Both {@code §} (section sign, U+00A7) and {@code &} (ampersand) are accepted as format markers.
     * Color codes reset all formatting modifiers to their defaults. {@code §r} / {@code &r} resets
     * both color and all modifiers back to {@link TextStyle#DEFAULT}.
     * <p>
     * Segments with no text content are omitted from the result.
     *
     * @param text The raw formatted string.
     * @return An unmodifiable list of segments in encounter order.
     */
    public static List<TextSegment> parse(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        text = resolveNamedFormats(text);

        List<TextSegment> segments = new ArrayList<>();
        TextStyle currentStyle = TextStyle.DEFAULT;
        StringBuilder currentText = new StringBuilder();

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if ((c == '\u00a7' || c == '&') && i + 1 < text.length()) {
                char code = text.charAt(i + 1);

                // Try &#RRGGBB hex color (8 chars total: & # R R G G B B)
                if (code == '#' && i + 7 < text.length()) {
                    Color hexColor = tryParseHex(text, i + 2, 6);
                    if (hexColor != null) {
                        if (!currentText.isEmpty()) {
                            segments.add(new TextSegment(currentText.toString(), currentStyle));
                            currentText.setLength(0);
                        }
                        currentStyle = TextStyle.DEFAULT.withColor(hexColor);
                        i += 8;
                        continue;
                    }
                }

                // Try §x§R§R§G§G§B§B Spigot hex color (14 chars total)
                if ((code == 'x' || code == 'X') && i + 13 < text.length()) {
                    Color hexColor = tryParseSpigotHex(text, i + 2);
                    if (hexColor != null) {
                        if (!currentText.isEmpty()) {
                            segments.add(new TextSegment(currentText.toString(), currentStyle));
                            currentText.setLength(0);
                        }
                        currentStyle = TextStyle.DEFAULT.withColor(hexColor);
                        i += 14;
                        continue;
                    }
                }

                // Flush current buffer before applying the new style
                if (!currentText.isEmpty()) {
                    segments.add(new TextSegment(currentText.toString(), currentStyle));
                    currentText.setLength(0);
                }

                ChatColor color = ChatColor.byCode(code);
                if (color != null) {
                    // Color code: apply color, reset all formatting modifiers
                    currentStyle = TextStyle.DEFAULT.withColor(color.color());
                    i += 2;
                    continue;
                }

                ChatFormatting formatting = ChatFormatting.byCode(code);
                if (formatting != null) {
                    if (formatting == ChatFormatting.RESET) {
                        currentStyle = TextStyle.DEFAULT;
                    } else {
                        currentStyle = applyFormatting(currentStyle, formatting);
                    }
                    i += 2;
                    continue;
                }

                // Unrecognized code - treat the marker and code as literal text
                currentText.append(c);
                i++;
            } else {
                currentText.append(c);
                i++;
            }
        }

        if (!currentText.isEmpty()) {
            segments.add(new TextSegment(currentText.toString(), currentStyle));
        }

        return List.copyOf(segments);
    }

    /**
     * Removes all formatting codes (both {@code §X} and {@code &X} forms) from the given string.
     *
     * @param text The formatted string.
     * @return The plain text with all formatting codes stripped.
     */
    public static String stripColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        text = resolveNamedFormats(text);

        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '\u00a7' || c == '&') && i + 1 < text.length()) {
                char code = text.charAt(i + 1);

                // Skip &#RRGGBB hex color (8 chars)
                if (code == '#' && i + 7 < text.length() && tryParseHex(text, i + 2, 6) != null) {
                    i += 8;
                    continue;
                }

                // Skip §x§R§R§G§G§B§B Spigot hex color (14 chars)
                if ((code == 'x' || code == 'X') && i + 13 < text.length() && tryParseSpigotHex(text, i + 2) != null) {
                    i += 14;
                    continue;
                }

                // Skip standard 2-char code
                i += 2;
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * Resolves named format placeholders (e.g. {@code %%GREEN%%}, {@code %%BOLD%%}) into their
     * equivalent {@code §code} sequences.
     *
     * <p>Both {@link ChatColor} names and {@link ChatFormatting} names are supported.
     * Lookup is case-insensitive. Unrecognized names are left unchanged.
     *
     * @param text the raw text potentially containing {@code %%NAME%%} placeholders
     * @return the text with recognized placeholders replaced by section-sign format codes
     */
    public static String resolveNamedFormats(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = NAMED_FORMAT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        matcher.reset();

        while (matcher.find()) {
            String name = matcher.group(1);

            ChatColor color = ChatColor.byName(name);
            if (color != null) {
                matcher.appendReplacement(result, "\u00a7" + color.code());
                continue;
            }

            ChatFormatting formatting = ChatFormatting.byName(name);
            if (formatting != null) {
                matcher.appendReplacement(result, "\u00a7" + formatting.code());
                continue;
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Attempts to parse {@code count} hex digits starting at {@code offset} in the given string.
     *
     * @return the parsed {@link Color}, or {@code null} if any character is not a valid hex digit
     */
    private static Color tryParseHex(String text, int offset, int count) {
        if (offset + count > text.length()) {
            return null;
        }
        for (int j = 0; j < count; j++) {
            if (Character.digit(text.charAt(offset + j), 16) == -1) {
                return null;
            }
        }
        int rgb = Integer.parseInt(text.substring(offset, offset + count), 16);
        return new Color(rgb);
    }

    /**
     * Attempts to parse the Spigot hex format: 6 pairs of {@code §R} (or {@code &R}) after
     * the initial {@code §x}. The 12 characters starting at {@code offset} must each be
     * a format marker followed by a hex digit.
     *
     * @return the parsed {@link Color}, or {@code null} if the format doesn't match
     */
    private static Color tryParseSpigotHex(String text, int offset) {
        if (offset + 12 > text.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int j = 0; j < 6; j++) {
            char marker = text.charAt(offset + j * 2);
            if (marker != '\u00a7' && marker != '&') {
                return null;
            }
            char digit = text.charAt(offset + j * 2 + 1);
            if (Character.digit(digit, 16) == -1) {
                return null;
            }
            hex.append(digit);
        }
        int rgb = Integer.parseInt(hex.toString(), 16);
        return new Color(rgb);
    }

    private static TextStyle applyFormatting(TextStyle style, ChatFormatting formatting) {
        return switch (formatting) {
            case FONT_GALACTIC -> style.withFont(MinecraftFontId.GALACTIC);
            case FONT_ILLAGERALT -> style.withFont(MinecraftFontId.ILLAGERALT);
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case OBFUSCATED -> style.withObfuscated(true);
            case UNDERLINE -> new TextStyle(
                    style.color(), style.fontId(), style.bold(), style.italic(),
                    true, style.strikethrough(), style.obfuscated()
            );
            case STRIKETHROUGH -> new TextStyle(
                    style.color(), style.fontId(), style.bold(), style.italic(),
                    style.underlined(), true, style.obfuscated()
            );
            case RESET -> TextStyle.DEFAULT;
        };
    }
}
