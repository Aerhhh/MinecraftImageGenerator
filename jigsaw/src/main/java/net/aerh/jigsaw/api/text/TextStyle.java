package net.aerh.jigsaw.api.text;

import java.awt.Color;

/**
 * Immutable text rendering style applied to a {@link TextSegment}.
 *
 * <p>Use the {@code with*} copy methods to derive a modified style without altering the original.
 * The shared {@link #DEFAULT} constant represents the baseline gray, non-formatted style used
 * before any formatting codes are encountered.
 *
 * @param color         the text color
 * @param fontId        the font identifier (e.g. {@code "minecraft:default"})
 * @param bold          whether text is rendered bold
 * @param italic        whether text is rendered italic
 * @param underlined    whether text is rendered with an underline
 * @param strikethrough whether text is rendered with a strikethrough
 * @param obfuscated    whether text is rendered as random (obfuscated) characters
 * @see TextSegment
 * @see FormattingParser
 */
public record TextStyle(
        Color color,
        String fontId,
        boolean bold,
        boolean italic,
        boolean underlined,
        boolean strikethrough,
        boolean obfuscated
) {

    /**
     * The default text style: gray, {@code minecraft:default} font, no formatting.
     */
    public static final TextStyle DEFAULT = new TextStyle(
            ChatColor.GRAY.color(),
            "minecraft:default",
            false,
            false,
            false,
            false,
            false
    );

    /**
     * Returns a copy of this style with the color replaced.
     *
     * @param newColor the new text color; must not be {@code null}
     *
     * @return a new {@code TextStyle} with the updated color
     */
    public TextStyle withColor(Color newColor) {
        return new TextStyle(newColor, fontId, bold, italic, underlined, strikethrough, obfuscated);
    }

    /**
     * Returns a copy of this style with the font ID replaced.
     *
     * @param newFontId the new font identifier (e.g. {@code "minecraft:default"})
     * @return a new {@code TextStyle} with the updated font ID
     */
    public TextStyle withFont(String newFontId) {
        return new TextStyle(color, newFontId, bold, italic, underlined, strikethrough, obfuscated);
    }

    /**
     * Returns a copy of this style with the bold flag set to the given value.
     *
     * @param newBold {@code true} to enable bold rendering
     * @return a new {@code TextStyle} with the updated bold flag
     */
    public TextStyle withBold(boolean newBold) {
        return new TextStyle(color, fontId, newBold, italic, underlined, strikethrough, obfuscated);
    }

    /**
     * Returns a copy of this style with the italic flag set to the given value.
     *
     * @param newItalic {@code true} to enable italic rendering
     * @return a new {@code TextStyle} with the updated italic flag
     */
    public TextStyle withItalic(boolean newItalic) {
        return new TextStyle(color, fontId, bold, newItalic, underlined, strikethrough, obfuscated);
    }

    /**
     * Returns a copy of this style with the obfuscated flag set to the given value.
     *
     * @param newObfuscated {@code true} to enable obfuscated (randomised) rendering
     * @return a new {@code TextStyle} with the updated obfuscated flag
     */
    public TextStyle withObfuscated(boolean newObfuscated) {
        return new TextStyle(color, fontId, bold, italic, underlined, strikethrough, newObfuscated);
    }
}
