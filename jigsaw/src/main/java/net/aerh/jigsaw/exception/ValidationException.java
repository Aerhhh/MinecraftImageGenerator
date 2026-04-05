package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when the public API is called with invalid arguments or in an invalid state.
 * <p>
 * This is a programming error - the calling code has violated a documented contract.
 */
public class ValidationException extends JigsawException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Map<String, Object> context) {
        super(message, context);
    }

    public ValidationException(String message, Map<String, Object> context, Throwable cause) {
        super(message, context, cause);
    }
}
