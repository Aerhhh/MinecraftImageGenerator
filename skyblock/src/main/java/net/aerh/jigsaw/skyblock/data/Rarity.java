package net.aerh.jigsaw.skyblock.data;

import net.aerh.jigsaw.api.text.ChatColor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A SkyBlock item rarity (e.g. COMMON, LEGENDARY).
 *
 * <p>Instances are loaded from {@code data/rarities.json} via {@link SkyBlockRegistries}.
 * Use {@link #byName(String)} for case-insensitive name lookup.
 *
 * @param name    the internal identifier (e.g. {@code "legendary"})
 * @param display the display label shown on tooltips (e.g. {@code "LEGENDARY"})
 * @param color   the Minecraft color name used for rarity formatting (e.g. {@code "GOLD"})
 */
public record Rarity(String name, String display, String color) {

    /**
     * Returns the {@link ChatColor} for this rarity's color string, or {@code null} if unknown.
     *
     * @return the resolved {@link ChatColor}, or {@code null}
     */
    public ChatColor chatColor() {
        return ChatColor.byName(color);
    }

    /**
     * Returns the ampersand color code prefix for this rarity (e.g. {@code "&6"} for GOLD).
     *
     * @return the ampersand color code string, or an empty string if the color is unresolvable
     */
    public String colorCode() {
        ChatColor c = chatColor();
        return c != null ? "&" + c.code() : "";
    }

    /**
     * Returns the bold, colored display string used in rarity footer lines
     * (e.g. {@code "&6&lLEGENDARY"}).
     *
     * @return the formatted display string
     */
    public String formattedDisplay() {
        return colorCode() + "&l" + display;
    }

    /**
     * Looks up a {@link Rarity} by its internal name, ignoring case.
     *
     * @param name the name to look up (e.g. {@code "legendary"})
     * @return an {@link Optional} containing the rarity, or empty if not found
     */
    public static Optional<Rarity> byName(String name) {
        return SkyBlockRegistries.rarities().get(name);
    }

    /**
     * Returns all registered rarity names (e.g. {@code ["common", "uncommon", ...]}).
     *
     * @return an unmodifiable list of rarity name strings
     */
    public static List<String> getRarityNames() {
        return SkyBlockRegistries.rarities().values().stream()
            .map(Rarity::name)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all registered rarities.
     *
     * @return an unmodifiable collection of all rarities
     */
    public static Collection<Rarity> getAllRarities() {
        return SkyBlockRegistries.rarities().values();
    }
}
