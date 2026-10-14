package net.aerh.imagegenerator.tooling;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads {@code --flag=value} and bare {@code --flag} arguments the way every tooling entry point expects them.
 * Later occurrences of a flag override earlier ones, and a bad value names the flag in the error.
 */
public final class CliArgs {

    private CliArgs() {
    }

    /**
     * Returns the text after {@code =} for the last {@code --flag=value} argument, or empty when the flag is absent.
     */
    public static Optional<String> value(String[] args, String flag) {
        String prefix = flag + "=";
        String found = null;

        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                found = arg.substring(prefix.length());
            }
        }

        return Optional.ofNullable(found);
    }

    /**
     * Parses an integer flag, falling back to {@code defaultValue} when the flag is absent.
     *
     * @throws IllegalArgumentException when the value is present but is not an integer
     */
    public static int intValue(String[] args, String flag, int defaultValue) {
        Optional<String> raw = value(args, flag);
        if (raw.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(raw.get().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " expects an integer, got '" + raw.get() + "'");
        }
    }

    /**
     * Parses a path flag.
     *
     * @throws IllegalArgumentException when the flag is present with a blank value
     */
    public static Optional<Path> pathValue(String[] args, String flag) {
        Optional<String> raw = value(args, flag);
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        if (raw.get().isBlank()) {
            throw new IllegalArgumentException(flag + " must not be empty");
        }

        return Optional.of(Path.of(raw.get().trim()));
    }

    /**
     * True when {@code flag} appears as a whole argument, so {@code --clean} does not match {@code --cleanup}.
     */
    public static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }

        return false;
    }
}
