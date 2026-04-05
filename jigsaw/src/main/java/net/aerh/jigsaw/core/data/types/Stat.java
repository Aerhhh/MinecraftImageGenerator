package net.aerh.jigsaw.core.data.types;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a SkyBlock stat (e.g. Strength, Defense, Health).
 *
 * <p>{@code subColor} and {@code powerScalingMultiplier} are optional in the JSON source and may
 * be {@code null} / zero-valued respectively when not present.
 *
 * @param icon                  the icon character shown before the stat value
 * @param name                  the identifier used as the registry lookup key
 * @param stat                  the internal stat key string
 * @param display               the human-readable display name shown in tooltips
 * @param color                 the primary Minecraft color code for this stat
 * @param subColor              an optional secondary color code; may be {@code null}
 * @param parseType             the name of the {@link ParseType} used to format this stat's value
 * @param powerScalingMultiplier the multiplier applied when computing power-scaled stat bonuses; {@code 0} when absent
 */
public record Stat(
        String icon,
        String name,
        String stat,
        String display,
        String color,
        @Nullable String subColor,
        String parseType,
        double powerScalingMultiplier
) {

    public Stat {
        Objects.requireNonNull(icon, "icon must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(stat, "stat must not be null");
        Objects.requireNonNull(display, "display must not be null");
        Objects.requireNonNull(color, "color must not be null");
        Objects.requireNonNull(parseType, "parseType must not be null");
    }
}
