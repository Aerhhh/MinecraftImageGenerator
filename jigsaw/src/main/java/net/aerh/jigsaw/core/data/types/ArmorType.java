package net.aerh.jigsaw.core.data.types;

import java.util.Objects;

/**
 * Represents an armor material type and its rendering capabilities.
 *
 * <p>The {@code materialName} matches the JSON field and is used as the lookup key in the registry
 * (e.g. {@code "leather"}, {@code "diamond"}).
 *
 * @param materialName         the armor material identifier used as the registry lookup key
 * @param supportsCustomColoring whether items of this material can be tinted with a custom dye color
 */
public record ArmorType(String materialName, boolean supportsCustomColoring) {

    public ArmorType {
        Objects.requireNonNull(materialName, "materialName must not be null");
    }
}
