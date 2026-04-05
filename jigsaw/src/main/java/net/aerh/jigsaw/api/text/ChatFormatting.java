package net.aerh.jigsaw.api.text;

import java.util.HashMap;
import java.util.Map;

/**
 * Text formatting modifiers (bold, italic, etc.) and the reset code used in Minecraft formatted
 * strings.
 *
 * <p>Each constant maps to a single character appended after the {@code §} or {@code &} marker.
 * Formatting codes are cumulative within a style run; applying a {@link ChatColor} resets all
 * active modifiers, and {@link #RESET} resets both color and all modifiers.
 *
 * @see ChatColor
 * @see FormattingParser
 */
public enum ChatFormatting {

    /** Switches to the Standard Galactic Alphabet font ({@code §g}). */
    FONT_GALACTIC('g'),

    /** Switches to the Illageralt font ({@code §h}). */
    FONT_ILLAGERALT('h'),

    /**
     * Randomises the displayed characters on each render tick ({@code §k}).
     */
    OBFUSCATED('k'),

    /** Renders text in bold ({@code §l}). */
    BOLD('l'),

    /** Renders text with a strikethrough line ({@code §m}). */
    STRIKETHROUGH('m'),

    /** Renders text with an underline ({@code §n}). */
    UNDERLINE('n'),

    /** Renders text in italic ({@code §o}). */
    ITALIC('o'),

    /** Resets all active color and formatting back to the default style ({@code §r}). */
    RESET('r');

    private static final Map<Character, ChatFormatting> BY_CODE = new HashMap<>();

    static {
        for (ChatFormatting fmt : values()) {
            BY_CODE.put(fmt.code, fmt);
        }
    }

    private final char code;

    ChatFormatting(char code) {
        this.code = code;
    }

    /**
     * Returns the single-character format code for this modifier (e.g. {@code 'l'} for {@link #BOLD}).
     *
     * @return the format code character
     */
    public char code() {
        return code;
    }

    /**
     * Returns the {@code ChatFormatting} for the given code character (e.g. {@code 'l'} for BOLD),
     * or {@code null} if no formatting matches.
     */
    public static ChatFormatting byCode(char code) {
        return BY_CODE.get(code);
    }
}
