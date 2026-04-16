package net.aerh.jigsaw.skyblock.data;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Minecraft armor material type with information about whether it supports custom coloring
 * (i.e. leather armor dye colors).
 *
 * <p>Instances are loaded from {@code data/armor_types.json} via {@link SkyBlockRegistries}.
 *
 * @param materialName          the lowercase material name (e.g. {@code "leather"}, {@code "diamond"})
 * @param supportsCustomColoring whether this material can have a custom dye color applied
 */
public record ArmorType(String materialName, boolean supportsCustomColoring) {

    /**
     * Returns {@code true} if the given overlay name contains a material that supports custom coloring.
     *
     * @param overlayName the overlay name to check; may be {@code null}
     * @return {@code true} if the overlay is for colorable armor, {@code false} otherwise
     */
    public static boolean isColorableArmor(String overlayName) {
        if (overlayName == null) {
            return false;
        }

        Set<String> colorableNames = SkyBlockRegistries.armorTypes().values().stream()
            .filter(ArmorType::supportsCustomColoring)
            .map(ArmorType::materialName)
            .collect(Collectors.toSet());

        String lower = overlayName.toLowerCase();
        return colorableNames.stream().anyMatch(lower::contains);
    }

    /**
     * Returns the {@link ArmorType} matching the given overlay name, or empty if none match.
     *
     * @param overlayName the overlay name to check; may be {@code null}
     * @return an {@link Optional} containing the matching armor type, or empty
     */
    public static Optional<ArmorType> fromOverlayName(String overlayName) {
        if (overlayName == null) {
            return Optional.empty();
        }

        String lower = overlayName.toLowerCase();
        return SkyBlockRegistries.armorTypes().values().stream()
            .filter(type -> lower.contains(type.materialName()))
            .findFirst();
    }

    /**
     * Returns all registered armor types.
     *
     * @return an unmodifiable collection of all armor types
     */
    public static Collection<ArmorType> getAll() {
        return SkyBlockRegistries.armorTypes().values();
    }
}
