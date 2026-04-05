package net.aerh.jigsaw.core.data.types;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a flavor line type (e.g. "requires", "view_recipes") that can appear on item tooltips.
 *
 * @param icon      the icon character shown before the flavor line
 * @param name      the identifier used as the registry lookup key
 * @param stat      the stat key this flavor is associated with
 * @param display   the human-readable display name
 * @param color     the primary color code for this flavor line
 * @param subColor  an optional secondary color code; may be {@code null} when not specified in the data
 * @param parseType the name of the {@link ParseType} used to format this flavor line
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
