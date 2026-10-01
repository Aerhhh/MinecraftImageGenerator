package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.exception.GeneratorValidationException;
import org.jetbrains.annotations.Nullable;

/** Required-option checks for the request records, so a missing value fails inside the generator exception family. */
public final class Required {

    private Required() {
    }

    /**
     * @param optionName the command option name, for example {@code item_name}
     *
     * @throws GeneratorValidationException if the value is null
     */
    public static <T> T check(@Nullable T value, String optionName) {
        if (value == null) {
            throw new GeneratorValidationException(optionName + " is required");
        }
        return value;
    }
}
