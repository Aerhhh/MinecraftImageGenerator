package net.aerh.jigsaw.core.data.types;

import java.util.Objects;

/**
 * Represents a stat parse/display format type.
 *
 * <p>The {@code formatWithDetails} string is used when extra runtime details are available
 * (e.g. ability names, item stats). The {@code formatWithoutDetails} string is used as the
 * fallback when those details are absent.
 */
public record ParseType(String name, String formatWithDetails, String formatWithoutDetails) {

    public ParseType {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(formatWithDetails, "formatWithDetails must not be null");
        Objects.requireNonNull(formatWithoutDetails, "formatWithoutDetails must not be null");
    }
}
