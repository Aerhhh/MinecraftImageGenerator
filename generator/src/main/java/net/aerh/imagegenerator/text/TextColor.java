package net.aerh.imagegenerator.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * A renderable text color: either one of the sixteen named {@link ChatFormat} colors or an
 * arbitrary {@link RgbColor} hex color (vanilla 1.16+).
 */
public interface TextColor {

    @NotNull Color getColor();

    @NotNull Color getBackgroundColor();

    @NotNull String getLegacyCode();

    @NotNull String toLegacyString();

    @NotNull String toJsonString();

    /**
     * Resolves a JSON text component color value: a named color (e.g. {@code "gold"}) maps to
     * its {@link ChatFormat}, a hex value (e.g. {@code "#ff00aa"}) to an {@link RgbColor}.
     *
     * @param value the JSON color string
     *
     * @return the resolved color, or null when the value is neither a named color nor valid hex
     */
    static @Nullable TextColor fromJsonString(@NotNull String value) {
        if (value.startsWith("#")) {
            return RgbColor.tryParse(value);
        }

        ChatFormat format = ChatFormat.of(value);
        return format != null && format.isColor() ? format : null;
    }
}
