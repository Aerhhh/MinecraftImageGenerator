package net.aerh.jigsaw.skyblock.data;

import java.util.Collection;
import java.util.Optional;

/**
 * A named icon entry used in SkyBlock tooltip rendering (e.g. stars, ticker symbols).
 *
 * <p>Instances are loaded from {@code data/icons.json} via {@link SkyBlockRegistries}.
 * Use {@link #byName(String)} for case-insensitive name lookup.
 *
 * @param name the internal identifier (e.g. {@code "star"})
 * @param icon the unicode icon character (e.g. {@code "✪"})
 */
public record Icon(String name, String icon) {

    /**
     * Looks up an {@link Icon} by its internal name, ignoring case.
     *
     * @param name the name to look up (e.g. {@code "star"})
     * @return an {@link Optional} containing the icon, or empty if not found
     */
    public static Optional<Icon> byName(String name) {
        return SkyBlockRegistries.icons().get(name);
    }

    /**
     * Returns all registered icons.
     *
     * @return an unmodifiable collection of all icons
     */
    public static Collection<Icon> getIcons() {
        return SkyBlockRegistries.icons().values();
    }
}
