package net.aerh.jigsaw.api.nbt;

import net.aerh.jigsaw.exception.ParseException;

/**
 * Parses a raw NBT string into a {@link ParsedItem}.
 */
public interface NbtParser {

    /**
     * Parses the given NBT input string and returns the structured item data.
     *
     * @param input The raw NBT string (SNBT, component format, etc.).
     * @return The parsed item.
     * @throws ParseException if the input cannot be parsed into a valid item.
     */
    ParsedItem parse(String input) throws ParseException;
}
