package net.aerh.jigsaw.skyblock.data;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A SkyBlock accessory power strength tier (e.g. Starter, Intermediate, Master).
 *
 * <p>Instances are loaded from {@code data/power_strengths.json} via {@link SkyBlockRegistries}.
 * Use {@link #byName(String)} for case-insensitive lookup by display name.
 *
 * @param name    the internal identifier (e.g. {@code "starter"})
 * @param display the human-readable tier label (e.g. {@code "Starter"})
 * @param stone   whether this tier requires a power stone (higher tiers do)
 */
public record PowerStrength(String name, String display, boolean stone) {

    /**
     * Returns a formatted display string combining the tier label and optional "Stone" suffix,
     * followed by " Power" (e.g. {@code "Marvelous Stone Power"} or {@code "Starter Power"}).
     *
     * @return the formatted display string
     */
    public String formattedDisplay() {
        return display + (stone ? " Stone" : "") + " Power";
    }

    /**
     * Looks up a {@link PowerStrength} by its display name, ignoring case.
     *
     * @param displayName the display name to look up (e.g. {@code "Starter"})
     * @return an {@link Optional} containing the power strength, or empty if not found
     */
    public static Optional<PowerStrength> byName(String displayName) {
        return SkyBlockRegistries.powerStrengths().values().stream()
            .filter(ps -> ps.display().equalsIgnoreCase(displayName))
            .findFirst();
    }

    /**
     * Returns all registered power strength internal names (e.g. {@code ["starter", "intermediate", ...]}).
     *
     * @return an unmodifiable list of power strength name strings
     */
    public static List<String> getPowerStrengthNames() {
        return SkyBlockRegistries.powerStrengths().values().stream()
            .map(PowerStrength::name)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all registered power strength tiers.
     *
     * @return an unmodifiable collection of all power strength tiers
     */
    public static Collection<PowerStrength> getAllPowerStrengths() {
        return SkyBlockRegistries.powerStrengths().values();
    }
}
