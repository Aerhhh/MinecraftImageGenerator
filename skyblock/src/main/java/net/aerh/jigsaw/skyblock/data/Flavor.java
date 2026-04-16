package net.aerh.jigsaw.skyblock.data;

import net.aerh.jigsaw.api.text.ChatColor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * A SkyBlock flavor/annotation entry (e.g. Soulbound, Requires, Ability).
 *
 * <p>Instances are loaded from {@code data/flavor.json} via {@link SkyBlockRegistries}.
 * Use {@link #byName(String)} for case-insensitive name lookup.
 *
 * @param icon      the unicode icon character for the entry (may be empty)
 * @param name      the internal identifier (e.g. {@code "soulbound"})
 * @param stat      the display name of the entry (e.g. {@code "Soulbound"})
 * @param display   the full display string including icon
 * @param color     the primary Minecraft color name
 * @param subColor  the secondary color name, or {@code null} if none
 * @param parseType the parse type key used for formatting (e.g. {@code "SOULBOUND"})
 */
public record Flavor(
    String icon,
    String name,
    String stat,
    String display,
    String color,
    @Nullable String subColor,
    String parseType
) {

    /**
     * Returns the primary {@link ChatColor} for this flavor entry, or {@code null} if unresolvable.
     *
     * @return the resolved primary {@link ChatColor}, or {@code null}
     */
    @Nullable
    public ChatColor chatColor() {
        return ChatColor.byName(color);
    }

    /**
     * Returns the secondary {@link ChatColor} for this flavor entry.
     * Falls back to the primary color when {@link #subColor()} is {@code null}.
     *
     * @return the secondary (or primary fallback) {@link ChatColor}, or {@code null}
     */
    @Nullable
    public ChatColor secondaryChatColor() {
        if (subColor != null) {
            return ChatColor.byName(subColor);
        }
        return chatColor();
    }

    /**
     * Looks up a {@link Flavor} entry by its internal name, ignoring case.
     *
     * @param name the name to look up (e.g. {@code "soulbound"})
     * @return an {@link Optional} containing the flavor entry, or empty if not found
     */
    public static Optional<Flavor> byName(String name) {
        return SkyBlockRegistries.flavors().get(name);
    }

    /**
     * Returns all registered flavor entries.
     *
     * @return an unmodifiable collection of all flavor entries
     */
    public static Collection<Flavor> getFlavors() {
        return SkyBlockRegistries.flavors().values();
    }
}
