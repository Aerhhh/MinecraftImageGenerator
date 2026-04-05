package net.aerh.jigsaw.core.data.types;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a SkyBlock stat (e.g. Strength, Defense, Health).
 *
 * <p>{@code subColor} and {@code powerScalingMultiplier} are optional in the JSON source and may
 * be {@code null} / zero-valued respectively when not present.
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
