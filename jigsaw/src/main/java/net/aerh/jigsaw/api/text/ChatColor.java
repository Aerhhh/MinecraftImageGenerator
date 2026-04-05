package net.aerh.jigsaw.api.text;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * All 16 Minecraft chat colors with their format codes and exact RGB values.
 */
public enum ChatColor {

    BLACK('0', new Color(0, 0, 0)),
    DARK_BLUE('1', new Color(0, 0, 170)),
    DARK_GREEN('2', new Color(0, 170, 0)),
    DARK_AQUA('3', new Color(0, 170, 170)),
    DARK_RED('4', new Color(170, 0, 0)),
    DARK_PURPLE('5', new Color(170, 0, 170)),
    GOLD('6', new Color(255, 170, 0)),
    GRAY('7', new Color(170, 170, 170)),
    DARK_GRAY('8', new Color(85, 85, 85)),
    BLUE('9', new Color(85, 85, 255)),
    GREEN('a', new Color(85, 255, 85)),
    AQUA('b', new Color(85, 255, 255)),
    RED('c', new Color(255, 85, 85)),
    LIGHT_PURPLE('d', new Color(255, 85, 255)),
    YELLOW('e', new Color(255, 255, 85)),
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

    public char code() {
        return code;
    }

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
