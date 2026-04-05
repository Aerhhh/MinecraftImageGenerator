package net.aerh.jigsaw.skyblock.data;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * A SkyBlock gemstone type (e.g. Ruby, Sapphire, Jade).
 *
 * <p>Instances are loaded from {@code data/gemstones.json} via {@link SkyBlockRegistries}.
 * Use {@link #byName(String)} for case-insensitive name lookup.
 *
 * <p>The {@link #color()} field is a hex color string (e.g. {@code "#FF5555"}) and may be
 * {@code null} for generic/universal gemstone slots that have no specific color.
 *
 * @param name           the internal identifier (e.g. {@code "gem_ruby"})
 * @param color          the hex color string, or {@code null} if not applicable
 * @param icon           the unicode icon character for the gemstone
 * @param formattedTiers a map of tier name to formatted display template string
 *                       (e.g. {@code "flawless"} to {@code "&5[%s&5]&r"})
 */
public record Gemstone(
    String name,
    @Nullable String color,
    String icon,
    Map<String, String> formattedTiers
) {

    /**
     * Looks up a {@link Gemstone} by its internal name, ignoring case.
     *
     * @param name the name to look up (e.g. {@code "gem_ruby"})
     * @return an {@link Optional} containing the gemstone, or empty if not found
     */
    public static Optional<Gemstone> byName(String name) {
        return SkyBlockRegistries.gemstones().get(name);
    }

    /**
     * Returns all registered gemstones.
     *
     * @return an unmodifiable collection of all gemstones
     */
    public static Collection<Gemstone> getGemstones() {
        return SkyBlockRegistries.gemstones().values();
    }
}
