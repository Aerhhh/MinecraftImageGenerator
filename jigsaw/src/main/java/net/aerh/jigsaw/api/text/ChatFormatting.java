package net.aerh.jigsaw.api.text;

import java.util.HashMap;
import java.util.Map;

/**
 * Text formatting modifiers (bold, italic, etc.) and the reset code.
 */
public enum ChatFormatting {

    OBFUSCATED('k'),
    BOLD('l'),
    STRIKETHROUGH('m'),
    UNDERLINE('n'),
    ITALIC('o'),
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
