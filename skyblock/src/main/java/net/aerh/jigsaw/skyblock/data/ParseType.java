package net.aerh.jigsaw.skyblock.data;

import java.util.Optional;

/**
 * A parse type definition that controls how stat/flavor values are formatted in tooltip lines.
 *
 * <p>Format strings may contain template tokens such as {@code {color}}, {@code {stat}},
 * {@code {display}}, {@code {extraDetails}}, {@code {icon}}, {@code {subColor}}, and others.
 *
 * <p>Instances are loaded from {@code data/parse_types.json} via {@link SkyBlockRegistries}.
 * Use {@link #byName(String)} for case-insensitive name lookup.
 *
 * @param name                 the unique identifier (e.g. {@code "NORMAL"}, {@code "BOLD_ICON"})
 * @param formatWithDetails    the format string used when extra details (e.g. a numeric value) are present
 * @param formatWithoutDetails the format string used when no extra details are present
 */
public record ParseType(String name, String formatWithDetails, String formatWithoutDetails) {

    /**
     * Looks up a {@link ParseType} by its name, ignoring case.
     *
     * @param name the name to look up (e.g. {@code "NORMAL"})
     * @return an {@link Optional} containing the parse type, or empty if not found
     */
    public static Optional<ParseType> byName(String name) {
        return SkyBlockRegistries.parseTypes().get(name);
    }
}
