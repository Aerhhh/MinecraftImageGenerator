package net.aerh.imagegenerator.tools.support;

import org.jetbrains.annotations.Nullable;

/** String normalisation shared by the request records. */
public final class Strings {

    private Strings() {
    }

    /**
     * Treats a blank optional option the same as an omitted one.
     *
     * @return the value unchanged when it has content, otherwise null
     */
    @Nullable
    public static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
