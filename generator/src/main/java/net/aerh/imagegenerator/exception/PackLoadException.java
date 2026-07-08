package net.aerh.imagegenerator.exception;

/**
 * Thrown when a resource pack cannot be opened, indexed, or read (I/O failures, defensive-cap
 * violations, malformed archive entries).
 */
public class PackLoadException extends GeneratorException {

    public PackLoadException(String message) {
        super(message);
    }

    public PackLoadException(String message, String... formatArgs) {
        super(message, formatArgs);
    }

    public PackLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
