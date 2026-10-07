package net.aerh.imagegenerator.data;

import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.pack.PackLineage;
import org.jetbrains.annotations.Nullable;

/**
 * Common shape shared by {@link Stat} and {@link Flavor} - the fields
 * needed to expand a {@link ParseType} format template.
 */
public interface FormattableEntry {

    String getIcon();

    String getName();

    String getStat();

    String getDisplay();

    ChatColor.Legacy getColor();

    ChatColor.Legacy getSecondaryColor();

    String getParseType();

    /**
     * Resolves the icon character for the given pack. Entries without pack-conditional data
     * (e.g. {@link Flavor}) fall through to the base icon.
     *
     * @param pack the active pack's lineage, or {@code null} for none
     *
     * @return the icon character to render
     */
    default String getIcon(@Nullable PackLineage pack) {
        return getIcon();
    }

    /**
     * Resolves the display text for the given pack. Entries without pack-conditional data
     * fall through to the stored display.
     *
     * @param pack the active pack's lineage, or {@code null} for none
     *
     * @return the display text
     */
    default String getDisplay(@Nullable PackLineage pack) {
        return getDisplay();
    }

    /**
     * Resolves the stat text for the given pack. Entries whose stat text embeds the icon
     * character (e.g. flavor lines like "This armor piece is undead X!") override this to swap
     * the embedded character; the default falls through to the stored stat text.
     *
     * @param pack the active pack's lineage, or {@code null} for none
     *
     * @return the stat text
     */
    default String getStat(@Nullable PackLineage pack) {
        return getStat();
    }
}
