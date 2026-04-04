package net.aerh.imagegenerator.data;

import net.aerh.imagegenerator.text.ChatFormat;

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
}
