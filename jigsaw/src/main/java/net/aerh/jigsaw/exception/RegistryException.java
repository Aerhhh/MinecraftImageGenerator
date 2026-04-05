package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when a registry is in a misconfigured or inconsistent state.
 * <p>
 * This is a programming error - registries should be properly populated before use.
 */
public class RegistryException extends JigsawException {

    public RegistryException(String message) {
        super(message);
    }

    public RegistryException(String message, Map<String, Object> context) {
        super(message, context);
    }

    public RegistryException(String message, Map<String, Object> context, Throwable cause) {
        super(message, context, cause);
    }
}
