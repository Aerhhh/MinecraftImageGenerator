package net.aerh.jigsaw.api.text;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * All 16 Minecraft chat colors with their format codes and exact RGB values.
 *
 * <p>Each constant maps to the single-character code used after the {@code §} or {@code &}
 * marker in Minecraft formatted strings. The associated {@link java.awt.Color} matches the
 * exact values used by Minecraft's default renderer.
 *
 * @see ChatFormatting
 * @see FormattingParser
 */
public enum ChatColor {

    /**
     * Black ({@code §0}), RGB {@code (0, 0, 0)}.
     */
    BLACK('0', new Color(0, 0, 0)),

    /** Dark blue ({@code §1}), RGB {@code (0, 0, 170)}. */
    DARK_BLUE('1', new Color(0, 0, 170)),

    /** Dark green ({@code §2}), RGB {@code (0, 170, 0)}. */
    DARK_GREEN('2', new Color(0, 170, 0)),

    /** Dark aqua / cyan ({@code §3}), RGB {@code (0, 170, 170)}. */
    DARK_AQUA('3', new Color(0, 170, 170)),

    /** Dark red ({@code §4}), RGB {@code (170, 0, 0)}. */
    DARK_RED('4', new Color(170, 0, 0)),

    /** Dark purple ({@code §5}), RGB {@code (170, 0, 170)}. */
    DARK_PURPLE('5', new Color(170, 0, 170)),

    /** Gold / orange ({@code §6}), RGB {@code (255, 170, 0)}. */
    GOLD('6', new Color(255, 170, 0)),

    /** Gray ({@code §7}), RGB {@code (170, 170, 170)}. */
    GRAY('7', new Color(170, 170, 170)),

    /** Dark gray ({@code §8}), RGB {@code (85, 85, 85)}. */
    DARK_GRAY('8', new Color(85, 85, 85)),

    /** Blue ({@code §9}), RGB {@code (85, 85, 255)}. */
    BLUE('9', new Color(85, 85, 255)),

    /** Bright green ({@code §a}), RGB {@code (85, 255, 85)}. */
    GREEN('a', new Color(85, 255, 85)),

    /** Aqua / cyan ({@code §b}), RGB {@code (85, 255, 255)}. */
    AQUA('b', new Color(85, 255, 255)),

    /** Red ({@code §c}), RGB {@code (255, 85, 85)}. */
    RED('c', new Color(255, 85, 85)),

    /** Light purple / pink ({@code §d}), RGB {@code (255, 85, 255)}. */
    LIGHT_PURPLE('d', new Color(255, 85, 255)),

    /** Yellow ({@code §e}), RGB {@code (255, 255, 85)}. */
    YELLOW('e', new Color(255, 255, 85)),

    /** White ({@code §f}), RGB {@code (255, 255, 255)}. */
    WHITE('f', new Color(255, 255, 255));

    private static final Map<Character, ChatColor> BY_CODE = new HashMap<>();
    private static final Map<String, ChatColor> BY_NAME = new HashMap<>();

    static {
        for (ChatColor color : values()) {
            BY_CODE.put(color.code, color);
            BY_NAME.put(color.name().toLowerCase(), color);
        }
    }

    private final char code;
    private final Color color;

    ChatColor(char code, Color color) {
        this.code = code;
        this.color = color;
    }

    /**
     * Returns the single-character format code for this color (e.g. {@code 'a'} for {@link #GREEN}).
     *
     * @return the format code character
     */
    public char code() {
        return code;
    }

    /**
     * Returns the AWT {@link Color} corresponding to this chat color.
     *
     * @return the exact RGB color used by Minecraft's renderer
     */
    public Color color() {
        return color;
    }

    /**
     * Returns the {@code ChatColor} for the given format code (e.g. {@code 'a'} for GREEN),
     * or {@code null} if no color has that code.
     */
    public static ChatColor byCode(char code) {
        return BY_CODE.get(code);
    }

    /**
     * Returns the {@code ChatColor} for the given name (case-insensitive, e.g. {@code "green"}),
     * or {@code null} if no color has that name.
     */
    public static ChatColor byName(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.get(name.toLowerCase());
    }
}
