package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when an item ID is not present in the registry.
 *
 * <p>The unrecognised item ID is stored in the diagnostic context under the key {@code "itemId"}.
 */
public class UnknownItemException extends RegistryException {

    /**
     * Constructs an {@code UnknownItemException} for the given item ID.
     *
     * @param itemId the Minecraft item ID that was not found in the registry
     */
    public UnknownItemException(String itemId) {
        super("Unknown item ID: " + itemId, Map.of("itemId", itemId));
    }
}
