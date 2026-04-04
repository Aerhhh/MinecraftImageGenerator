package net.aerh.imagegenerator.exception;

import net.hypixel.nerdbot.marmalade.exception.FormattedException;

import java.util.Objects;

public class GeneratorException extends FormattedException {

    public GeneratorException(String message) {
        super(message);
    }

    public GeneratorException(String message, String... formatArgs) {
        super(formatMessage(message, formatArgs));
    }

    public GeneratorException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Formats a message template using %s placeholders, sanitising each argument
     * by stripping backticks and replacing blank values with a single space.
     *
     * @param message    the format string using %s placeholders
     * @param formatArgs the arguments to substitute
     * @return the formatted message
     */
    public static String formatMessage(String message, String... formatArgs) {
        for (int i = 0; i < formatArgs.length; i++) {
            String safeArg = Objects.requireNonNullElse(formatArgs[i], "").replaceAll("`", "");
            formatArgs[i] = !safeArg.isEmpty() ? safeArg : " ";
        }
        return String.format(message, (Object[]) formatArgs);
    }
}
