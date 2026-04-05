package net.aerh.jigsaw.core.data.types;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a gemstone type (e.g. ruby, amethyst) with per-tier format strings.
 *
 * <p>The JSON field {@code formattedTiers} is a map from tier name (e.g. "flawless") to a
 * Minecraft-formatted string template used when rendering the gemstone slot.
 *
 * <p>{@code color} may be {@code null} for gemstones (e.g. gem_combat) that have no associated
 * color in the data file.
 *
 * @param name           the identifier used as the registry lookup key
 * @param color          the optional color code for this gemstone; may be {@code null}
 * @param icon           the icon character representing this gemstone
 * @param formattedTiers an immutable map from tier name to Minecraft-formatted display strings
 */
public record Gemstone(String name, @Nullable String color, String icon, Map<String, String> formattedTiers) {

    public Gemstone {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(icon, "icon must not be null");
        Objects.requireNonNull(formattedTiers, "formattedTiers must not be null");
        formattedTiers = Map.copyOf(formattedTiers);
    }
}
