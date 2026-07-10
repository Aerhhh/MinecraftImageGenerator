package net.aerh.imagegenerator.data;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.text.ChatFormat;
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

    ChatFormat getColor();

    ChatFormat getSecondaryColor();

    String getParseType();

    /**
     * Resolves the icon character for the given pack. Entries without pack-conditional data
     * (e.g. {@link Flavor}) fall through to the base icon.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the icon character to render
     */
    default String getIcon(@Nullable PackId packId) {
        return getIcon();
    }

    /**
     * Resolves the display text for the given pack. Entries without pack-conditional data
     * fall through to the stored display.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the display text
     */
    default String getDisplay(@Nullable PackId packId) {
        return getDisplay();
    }
}
