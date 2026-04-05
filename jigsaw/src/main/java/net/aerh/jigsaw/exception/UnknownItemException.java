package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when an item ID is not present in the registry.
 * <p>
 * The unrecognised item ID is stored in the context under the key {@code "itemId"}.
 */
public class UnknownItemException extends RegistryException {

    public UnknownItemException(String itemId) {
        super("Unknown item ID: " + itemId, Map.of("itemId", itemId));
    }
}
