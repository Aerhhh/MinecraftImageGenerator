package net.aerh.imagegenerator.text;

import lib.minecraft.text.ChatColor;
import lib.minecraft.text.ChatFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * MIG-local single source of truth for the legacy in-band code alphabet ({@code &}/{@code §}
 * codes): named colors, style formats, alternate-font selectors ({@code g}/{@code h}), and reset.
 * <p>
 * The upstream {@code lib.minecraft.text} library deliberately splits this alphabet across
 * {@link ChatColor.Legacy} (colors), {@link ChatFormat} (styles + reset), and its font enum, and it
 * has no notion of the {@code g}/{@code h} font codes at all. MIG's legacy parsing ({@code &g}/{@code &h}
 * font switching, {@code %%NAME%%} placeholder expansion, the shared {@code CodeClassifier} lexer)
 * needs one unified table, so this enum reassembles it and delegates the actual model back to the
 * library types. It carries no color values of its own - colors resolve through {@link #chatColor()}.
 */
public enum LegacyCode {

    BLACK('0', Type.COLOR),
    DARK_BLUE('1', Type.COLOR),
    DARK_GREEN('2', Type.COLOR),
    DARK_AQUA('3', Type.COLOR),
    DARK_RED('4', Type.COLOR),
    DARK_PURPLE('5', Type.COLOR),
    GOLD('6', Type.COLOR),
    GRAY('7', Type.COLOR),
    DARK_GRAY('8', Type.COLOR),
    BLUE('9', Type.COLOR),
    GREEN('a', Type.COLOR),
    AQUA('b', Type.COLOR),
    RED('c', Type.COLOR),
    LIGHT_PURPLE('d', Type.COLOR),
    YELLOW('e', Type.COLOR),
    WHITE('f', Type.COLOR),
    FONT_GALACTIC('g', Type.FONT),
    FONT_ILLAGERALT('h', Type.FONT),
    OBFUSCATED('k', Type.STYLE),
    BOLD('l', Type.STYLE),
    STRIKETHROUGH('m', Type.STYLE),
    UNDERLINE('n', Type.STYLE),
    ITALIC('o', Type.STYLE),
    RESET('r', Type.RESET);

    /** The classification of an in-band code. */
    public enum Type {
        COLOR, STYLE, FONT, RESET
    }

    public static final LegacyCode[] VALUES = values();
    public static final char SECTION_SYMBOL = ChatFormat.SECTION_SYMBOL;
    public static final char AMPERSAND_SYMBOL = '&';

    /**
     * Matches an in-band section-symbol code: a run of {@link #SECTION_SYMBOL} followed by either a
     * {@code #RRGGBB} hex color or a single legacy code character. Mirrors the grammar this enum
     * defines so {@link #stripColor(String)} cannot drift from it.
     */
    private static final Pattern SECTION_CODE_PATTERN =
        Pattern.compile(SECTION_SYMBOL + "+(#[0-9A-F]{6}|[0-9A-HK-OR])", Pattern.CASE_INSENSITIVE);

    private final char code;
    private final @NotNull Type type;

    LegacyCode(char code, @NotNull Type type) {
        this.code = code;
        this.type = type;
    }

    /**
     * The single-character code for this entry ({@code 0-9}, {@code a-h}, {@code k-o}, {@code r}).
     *
     * @return the code character
     */
    public char getCode() {
        return code;
    }

    public @NotNull Type getType() {
        return type;
    }

    public boolean isColor() {
        return type == Type.COLOR;
    }

    public boolean isFont() {
        return type == Type.FONT;
    }

    public boolean isStyle() {
        return type == Type.STYLE;
    }

    public boolean isReset() {
        return type == Type.RESET;
    }

    /**
     * The named color this code selects.
     *
     * @return the matching {@link ChatColor.Legacy}
     *
     * @throws IllegalStateException if this code is not a {@link Type#COLOR}
     */
    public @NotNull ChatColor chatColor() {
        if (type != Type.COLOR) {
            throw new IllegalStateException(name() + " is not a color code");
        }
        return java.util.Objects.requireNonNull(ChatColor.Legacy.of(name()), name());
    }

    /**
     * The style/reset format this code selects.
     *
     * @return the matching library {@link ChatFormat}
     *
     * @throws IllegalStateException if this code is neither a {@link Type#STYLE} nor {@link Type#RESET}
     */
    public @NotNull ChatFormat chatFormat() {
        if (type != Type.STYLE && type != Type.RESET) {
            throw new IllegalStateException(name() + " is not a style code");
        }
        return java.util.Objects.requireNonNull(ChatFormat.of(name()), name());
    }

    /**
     * The alternate font this code selects.
     *
     * @return the matching {@link MinecraftFont}
     *
     * @throws IllegalStateException if this code is not a {@link Type#FONT}
     */
    public @NotNull MinecraftFont font() {
        return switch (this) {
            case FONT_GALACTIC -> MinecraftFont.GALACTIC;
            case FONT_ILLAGERALT -> MinecraftFont.ILLAGERALT;
            default -> throw new IllegalStateException(name() + " is not a font code");
        };
    }

    /**
     * Looks up a code entry by its single-character code.
     *
     * @param code the code character
     *
     * @return the matching entry, or null if the character is not a legacy code
     */
    public static @Nullable LegacyCode of(char code) {
        for (LegacyCode value : VALUES) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    /**
     * Looks up a code entry by its enum name (case-insensitive), e.g. {@code "GOLD"}, {@code "BOLD"},
     * {@code "FONT_GALACTIC"}.
     *
     * @param name the entry name
     *
     * @return the matching entry, or null if no entry has that name
     */
    public static @Nullable LegacyCode of(@NotNull String name) {
        for (LegacyCode value : VALUES) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Whether {@code code} is any recognized legacy code character.
     *
     * @param code the code character
     *
     * @return true if the character introduces a valid code
     */
    public static boolean isValid(char code) {
        return of(code) != null;
    }

    /**
     * Strips all in-band section-symbol codes (hex colors, named colors, styles, fonts, reset) from
     * {@code value}. Only {@link #SECTION_SYMBOL} codes are removed, not {@link #AMPERSAND_SYMBOL}
     * substitutes, matching vanilla's rendered text.
     *
     * @param value the text to strip
     *
     * @return the text with section-symbol codes removed
     */
    public static @NotNull String stripColor(@NotNull String value) {
        return SECTION_CODE_PATTERN.matcher(value).replaceAll("");
    }
}
