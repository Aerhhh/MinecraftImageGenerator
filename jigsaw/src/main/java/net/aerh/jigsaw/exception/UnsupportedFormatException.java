package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when no handler is registered for the given input format.
 * <p>
 * This is a parse-time failure: the input was syntactically valid enough to
 * identify its format, but that format has no registered handler.
 */
public class UnsupportedFormatException extends ParseException {

    public UnsupportedFormatException(String message) {
        super(message);
    }

    public UnsupportedFormatException(String message, Map<String, Object> context) {
        super(message, context);
    }

    public UnsupportedFormatException(String message, Map<String, Object> context, Throwable cause) {
        super(message, context, cause);
    }
}
