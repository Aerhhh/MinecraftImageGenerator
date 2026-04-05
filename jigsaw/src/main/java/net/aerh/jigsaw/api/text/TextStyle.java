package net.aerh.jigsaw.api.text;

import java.awt.Color;

/**
 * Immutable text rendering style applied to a {@link TextSegment}.
 *
 * @param color         The text color.
 * @param fontId        The font identifier (e.g. {@code "minecraft:default"}).
 * @param bold          Whether text is rendered bold.
 * @param italic        Whether text is rendered italic.
 * @param underlined    Whether text is rendered with an underline.
 * @param strikethrough Whether text is rendered with a strikethrough.
 * @param obfuscated    Whether text is rendered as random (obfuscated) characters.
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
     * The default text style: white, {@code minecraft:default} font, no formatting.
     */
    public static final TextStyle DEFAULT = new TextStyle(
            new Color(255, 255, 255),
            "minecraft:default",
            false,
            false,
            false,
            false,
            false
    );

    public TextStyle withColor(Color newColor) {
        return new TextStyle(newColor, fontId, bold, italic, underlined, strikethrough, obfuscated);
    }

    public TextStyle withFont(String newFontId) {
        return new TextStyle(color, newFontId, bold, italic, underlined, strikethrough, obfuscated);
    }

    public TextStyle withBold(boolean newBold) {
        return new TextStyle(color, fontId, newBold, italic, underlined, strikethrough, obfuscated);
    }

    public TextStyle withItalic(boolean newItalic) {
        return new TextStyle(color, fontId, bold, newItalic, underlined, strikethrough, obfuscated);
    }

    public TextStyle withObfuscated(boolean newObfuscated) {
        return new TextStyle(color, fontId, bold, italic, underlined, strikethrough, newObfuscated);
    }
}
