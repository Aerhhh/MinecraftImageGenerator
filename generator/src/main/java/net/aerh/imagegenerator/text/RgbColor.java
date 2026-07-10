package net.aerh.imagegenerator.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * An arbitrary 24-bit text color (vanilla 1.16+ hex colors). The drop shadow is derived by
 * vanilla's quartering of each channel: {@code (rgb >> 2) & 0x3F3F3F}.
 * <p>
 * Value semantics (record equals/hashCode plus the canonical {@code #rrggbb} toString) are
 * load-bearing: the reflective render cache key stringifies segment colors, so equal colors
 * must produce equal keys.
 */
public record RgbColor(int rgb) implements TextColor {

    private static final int SHADOW_MASK = 0x3F3F3F;

    public RgbColor {
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException(
                String.format("rgb must be between 0x000000 and 0xFFFFFF, got 0x%X", rgb));
        }
    }

    /** Length of an in-band hex color code without its {@code &}/{@code §} symbol: {@code #RRGGBB}. */
    public static final int HEX_CODE_LENGTH = 7;

    /**
     * Parses a strict {@code #RRGGBB} string (case-insensitive, exactly six hex digits).
     *
     * @param value the string to parse
     *
     * @return the parsed color, or null if the string is not a valid hex color
     */
    public static @Nullable RgbColor tryParse(@NotNull String value) {
        return value.length() == HEX_CODE_LENGTH ? tryParseAt(value, 0) : null;
    }

    /**
     * Parses a {@code #RRGGBB} sequence starting at {@code start} within a larger string,
     * ignoring any content after the six hex digits. Used by the legacy text pipeline to
     * recognize in-band {@code &#RRGGBB} color codes.
     *
     * @param text  the text containing the potential hex color code
     * @param start the index of the expected {@code #} character
     *
     * @return the parsed color, or null if no valid hex color starts at {@code start}
     */
    public static @Nullable RgbColor tryParseAt(@NotNull CharSequence text, int start) {
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

        return new RgbColor(rgb);
    }

    @Override
    public @NotNull Color getColor() {
        return new Color(rgb);
    }

    @Override
    public @NotNull Color getBackgroundColor() {
        return new Color((rgb >> 2) & SHADOW_MASK);
    }

    @Override
    public @NotNull String getLegacyCode() {
        return this.toJsonString();
    }

    @Override
    public @NotNull String toLegacyString() {
        return ChatFormat.SECTION_SYMBOL + this.toJsonString();
    }

    @Override
    public @NotNull String toJsonString() {
        return String.format("#%06x", rgb);
    }

    @Override
    public @NotNull String toString() {
        return this.toJsonString();
    }
}
