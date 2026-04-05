package net.aerh.jigsaw.spi;

import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.exception.ParseException;

/**
 * SPI contract for handling a specific NBT format variant (e.g. pre-flattening, components, SNBT).
 * <p>
 * Handlers are evaluated in descending {@link #priority()} order; the first handler for which
 * {@link #canHandle(String)} returns {@code true} is used.
 *
 * @see NbtFormatHandlerContext
 * @see net.aerh.jigsaw.api.nbt.ParsedItem
 */
public interface NbtFormatHandler {

    /**
     * Unique identifier for this handler (e.g. {@code "mig:components"}).
     */
    String id();

    /**
     * Priority relative to other handlers. Higher values are evaluated first.
     */
    int priority();

    /**
     * Returns {@code true} if this handler can parse the given input string.
     * This check must be fast; it should not perform a full parse.
     */
    boolean canHandle(String input);

    /**
     * Parses the given NBT input and returns the structured item data.
     *
     * @param input   The raw NBT string.
     * @param context Access to registered data registries.
     * @return The parsed item.
     * @throws ParseException if parsing fails.
     */
    ParsedItem parse(String input, NbtFormatHandlerContext context) throws ParseException;
}
