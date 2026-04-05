package net.aerh.jigsaw.core.data.types;

import java.util.Objects;

/**
 * Represents an armor material type and its rendering capabilities.
 *
 * <p>The {@code materialName} matches the JSON field and is used as the lookup key in the registry
 * (e.g. {@code "leather"}, {@code "diamond"}).
 */
public record ArmorType(String materialName, boolean supportsCustomColoring) {

    public ArmorType {
        Objects.requireNonNull(materialName, "materialName must not be null");
    }
}
