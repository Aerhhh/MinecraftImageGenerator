package net.aerh.jigsaw.api.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Minecraft-formatted text strings (using {@code §} or {@code &} as format markers)
 * into a list of {@link TextSegment}s, each carrying its own {@link TextStyle}.
 */
public final class FormattingParser {

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

        List<TextSegment> segments = new ArrayList<>();
        TextStyle currentStyle = TextStyle.DEFAULT;
        StringBuilder currentText = new StringBuilder();

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if ((c == '\u00a7' || c == '&') && i + 1 < text.length()) {
                char code = text.charAt(i + 1);

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

        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '\u00a7' || c == '&') && i + 1 < text.length()) {
                // Skip both the marker and the code character
                i += 2;
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    private static TextStyle applyFormatting(TextStyle style, ChatFormatting formatting) {
        return switch (formatting) {
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
