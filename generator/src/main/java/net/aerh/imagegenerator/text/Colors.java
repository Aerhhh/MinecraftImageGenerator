package net.aerh.imagegenerator.text;

import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MIG-local helpers for the in-band {@code &#RRGGBB} hex color grammar and channel interpolation,
 * bridging MIG's legacy text pipeline to the library's {@link ChatColor} model.
 * <p>
 * The library's {@link ChatColor#fromJsonString(String)} only parses a standalone {@code #RRGGBB}
 * string; MIG additionally needs to recognize a hex code embedded mid-string (after a {@code &}/{@code §}
 * symbol) and to interpolate between colors for gradients. Those two capabilities lived on the
 * retired {@code RgbColor} record and are preserved here.
 */
public final class Colors {

    /** Length of an in-band hex color code without its {@code &}/{@code §} symbol: {@code #RRGGBB}. */
    public static final int HEX_CODE_LENGTH = 7;

    private Colors() {
    }

    /**
     * Parses a strict {@code #RRGGBB} string (case-insensitive, exactly six hex digits) into a
     * custom {@link ChatColor}.
     *
     * @param value the string to parse
     *
     * @return the parsed color, or null if the string is not a valid hex color
     */
    public static @Nullable ChatColor tryParseHex(@NotNull String value) {
        return value.length() == HEX_CODE_LENGTH ? tryParseHexAt(value, 0) : null;
    }

    /**
     * Parses a {@code #RRGGBB} sequence starting at {@code start} within a larger string, ignoring
     * any content after the six hex digits. Used by the legacy text pipeline to recognize in-band
     * {@code &#RRGGBB} color codes.
     *
     * @param text  the text containing the potential hex color code
     * @param start the index of the expected {@code #} character
     *
     * @return the parsed color, or null if no valid hex color starts at {@code start}
     */
    public static @Nullable ChatColor tryParseHexAt(@NotNull CharSequence text, int start) {
        if (start < 0 || start + HEX_CODE_LENGTH > text.length() || text.charAt(start) != '#') {
            return null;
        }

        int rgb = 0;
        for (int i = start + 1; i < start + HEX_CODE_LENGTH; i++) {
            int digit = Character.digit(text.charAt(i), 16);
            if (digit == -1) {
                return null;
            }
            rgb = (rgb << 4) | digit;
        }

        return ChatColor.of(rgb);
    }

    /**
     * Linearly interpolates each RGB channel between two 24-bit colors with half-up rounding,
     * matching the RGBirdflop reference generator.
     *
     * @param fromRgb the 24-bit color at {@code t = 0}
     * @param toRgb   the 24-bit color at {@code t = 1}
     * @param t       the interpolation position, expected within {@code [0, 1]}
     *
     * @return the interpolated 24-bit color
     */
    public static int lerp(int fromRgb, int toRgb, double t) {
        int red = lerpChannel(fromRgb >> 16 & 0xFF, toRgb >> 16 & 0xFF, t);
        int green = lerpChannel(fromRgb >> 8 & 0xFF, toRgb >> 8 & 0xFF, t);
        int blue = lerpChannel(fromRgb & 0xFF, toRgb & 0xFF, t);
        return red << 16 | green << 8 | blue;
    }

    private static int lerpChannel(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }
}
