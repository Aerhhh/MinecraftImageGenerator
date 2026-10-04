package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Parses user-supplied {@code minecraft:dyed_color} values: a {@code #RRGGBB} hex color or the
 * name of one of the 16 vanilla dyes. A name maps to that dye's vanilla color, which is also
 * what dyeing an undyed item with that single dye produces in game.
 */
@UtilityClass
public class DyeColors {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    /** Vanilla {@code DyeColor} texture colors, keyed by dye name. */
    private static final Map<String, Integer> NAMED = Map.ofEntries(
        Map.entry("white", 0xF9FFFE),
        Map.entry("orange", 0xF9801D),
        Map.entry("magenta", 0xC74EBD),
        Map.entry("light_blue", 0x3AB3DA),
        Map.entry("yellow", 0xFED83D),
        Map.entry("lime", 0x80C71F),
        Map.entry("pink", 0xF38BAA),
        Map.entry("gray", 0x474F52),
        Map.entry("light_gray", 0x9D9D97),
        Map.entry("cyan", 0x169C9C),
        Map.entry("purple", 0x8932B8),
        Map.entry("blue", 0x3C44AA),
        Map.entry("brown", 0x835432),
        Map.entry("green", 0x5E7C16),
        Map.entry("red", 0xB02E26),
        Map.entry("black", 0x1D1D21)
    );

    /**
     * The packed {@code 0xRRGGBB} color of {@code value}: {@code #RRGGBB} (either case), or a
     * dye name matched ignoring case, surrounding whitespace, and spaces or hyphens in place
     * of underscores ({@code "Light Blue"} is {@code light_blue}).
     *
     * @return empty when {@code value} is null, blank, or neither form
     */
    public static OptionalInt parse(@Nullable String value) {
        if (value == null) {
            return OptionalInt.empty();
        }
        String trimmed = value.strip();
        if (HEX.matcher(trimmed).matches()) {
            return OptionalInt.of(Integer.parseInt(trimmed.substring(1), 16));
        }
        String name = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        Integer named = NAMED.get(name);
        return named == null ? OptionalInt.empty() : OptionalInt.of(named);
    }
}
