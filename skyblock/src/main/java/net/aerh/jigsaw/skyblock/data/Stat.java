package net.aerh.jigsaw.skyblock.data;

import net.aerh.jigsaw.api.text.ChatColor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * A SkyBlock item stat entry (e.g. Strength, Health, Defense).
 *
 * <p>Instances are loaded from {@code data/stats.json} via {@link SkyBlockRegistries}.
 * Use {@link #byName(String)} for case-insensitive name lookup.
 *
 * @param icon                   the unicode icon character for the stat (e.g. {@code "❁"})
 * @param name                   the internal identifier (e.g. {@code "strength"})
 * @param stat                   the display name of the stat (e.g. {@code "Strength"})
 * @param display                the full display string including icon (e.g. {@code "❁ Strength"})
 * @param color                  the primary Minecraft color name (e.g. {@code "RED"})
 * @param subColor               the secondary color name, or {@code null} if none
 * @param parseType              the parse type key used for formatting (e.g. {@code "NORMAL"})
 * @param powerScalingMultiplier the power scaling multiplier for this stat, or {@code null}
 */
public record Stat(
    String icon,
    String name,
    String stat,
    String display,
    String color,
    @Nullable String subColor,
    String parseType,
    @Nullable Double powerScalingMultiplier
) {

    /**
     * Returns the primary {@link ChatColor} for this stat, or {@code null} if unresolvable.
     *
     * @return the resolved primary {@link ChatColor}, or {@code null}
     */
    @Nullable
    public ChatColor chatColor() {
        return ChatColor.byName(color);
    }

    /**
     * Returns the secondary {@link ChatColor} for this stat.
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
     * Looks up a {@link Stat} by its internal name, ignoring case.
     *
     * @param name the name to look up (e.g. {@code "strength"})
     * @return an {@link Optional} containing the stat, or empty if not found
     */
    public static Optional<Stat> byName(String name) {
        return SkyBlockRegistries.stats().get(name);
    }

    /**
     * Returns all registered stats.
     *
     * @return an unmodifiable collection of all stats
     */
    public static Collection<Stat> getStats() {
        return SkyBlockRegistries.stats().values();
    }
}
