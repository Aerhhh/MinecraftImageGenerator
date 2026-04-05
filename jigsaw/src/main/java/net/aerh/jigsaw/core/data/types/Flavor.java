package net.aerh.jigsaw.core.data.types;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a flavor line type (e.g. "requires", "view_recipes") that can appear on item tooltips.
 */
public record Flavor(
        String icon,
        String name,
        String stat,
        String display,
        String color,
        @Nullable String subColor,
        String parseType
) {

    public Flavor {
        Objects.requireNonNull(icon, "icon must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(stat, "stat must not be null");
        Objects.requireNonNull(display, "display must not be null");
        Objects.requireNonNull(color, "color must not be null");
        Objects.requireNonNull(parseType, "parseType must not be null");
    }
}
