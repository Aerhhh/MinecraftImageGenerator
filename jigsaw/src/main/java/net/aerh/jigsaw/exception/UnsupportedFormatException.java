package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when no {@link net.aerh.jigsaw.spi.NbtFormatHandler} is registered for the given input
 * format.
 *
 * <p>This is a parse-time failure: the input was recognisable enough to identify its format, but
 * no handler has been registered to process that format. Add the appropriate handler via
 * {@link net.aerh.jigsaw.api.EngineBuilder#nbtHandler(net.aerh.jigsaw.spi.NbtFormatHandler)} to
 * resolve this.
 *
 * @see net.aerh.jigsaw.spi.NbtFormatHandler
 */
public class UnsupportedFormatException extends ParseException {

    /**
     * Constructs an {@code UnsupportedFormatException} with the given message and no diagnostic
     * context.
     *
     * @param message a human-readable description of the unsupported format
     */
    public UnsupportedFormatException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code UnsupportedFormatException} with the given message and diagnostic context.
     *
     * @param message the human-readable description of the unsupported format
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public UnsupportedFormatException(String message, Map<String, Object> context) {
        super(message, context);
    }

    /**
     * Constructs an {@code UnsupportedFormatException} with the given message, diagnostic context,
     * and cause.
     *
     * @param message the human-readable description of the unsupported format
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause   the underlying exception that triggered this failure
     */
    public UnsupportedFormatException(String message, Map<String, Object> context, Throwable cause) {
        super(message, context, cause);
    }
}
