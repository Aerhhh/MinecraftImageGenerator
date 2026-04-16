package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when a registry is in a misconfigured or inconsistent state.
 *
 * <p>This is a programming error - registries should be properly populated before use.
 * The most common subclass is {@link UnknownItemException}, thrown when an item ID is not
 * present in the registry.
 *
 * @see UnknownItemException
 */
public class RegistryException extends JigsawException {

    /**
     * Constructs a {@code RegistryException} with the given message and no diagnostic context.
     *
     * @param message a human-readable description of the registry problem
     */
    public RegistryException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code RegistryException} with the given message and diagnostic context entries.
     *
     * @param message the human-readable description of the registry problem
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public RegistryException(String message, Map<String, Object> context) {
        super(message, context);
    }

    /**
     * Constructs a {@code RegistryException} with the given message, diagnostic context, and cause.
     *
     * @param message the human-readable description of the registry problem
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause   the underlying exception that triggered this failure
     */
    public RegistryException(String message, Map<String, Object> context, Throwable cause) {
        super(message, context, cause);
    }
}
