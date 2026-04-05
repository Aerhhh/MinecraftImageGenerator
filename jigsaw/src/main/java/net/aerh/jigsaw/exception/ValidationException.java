package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when the public API is called with invalid arguments or in an invalid state.
 *
 * <p>This is a programming error: the calling code has violated a documented contract. Fix the
 * call site rather than catching this exception in production code.
 *
 */
public class ValidationException extends JigsawException {

    /**
     * Constructs a {@code ValidationException} with the given message and no diagnostic context.
     *
     * @param message a human-readable description of the violated contract
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code ValidationException} with the given message and diagnostic context.
     *
     * @param message the human-readable description of the violated contract
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public ValidationException(String message, Map<String, Object> context) {
        super(message, context);
    }

    /**
     * Constructs a {@code ValidationException} with the given message, diagnostic context, and
     * cause.
     *
     * @param message the human-readable description of the violated contract
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause   the underlying exception that triggered this failure
     */
    public ValidationException(String message, Map<String, Object> context, Throwable cause) {
        super(message, context, cause);
    }
}
