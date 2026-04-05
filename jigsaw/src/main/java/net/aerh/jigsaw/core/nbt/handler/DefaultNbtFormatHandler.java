package net.aerh.jigsaw.core.nbt.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.core.nbt.SnbtParser;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.spi.NbtFormatHandler;
import net.aerh.jigsaw.spi.NbtFormatHandlerContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fallback {@link NbtFormatHandler} that always accepts any input and extracts minimal information.
 * <p>
 * This handler is intended as a last resort when no more specific handler can process the input.
 * It attempts to extract the item ID and nothing else.
 */
public final class DefaultNbtFormatHandler implements NbtFormatHandler {

    /**
     * Returns the unique identifier for this handler.
     *
     * @return {@code "jigsaw:default"}
     */
    @Override
    public String id() {
        return "jigsaw:default";
    }

    /**
     * Returns the lowest possible priority so this handler is always evaluated last.
     *
     * @return {@link Integer#MAX_VALUE}
     */
    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }

    /**
     * Returns {@code true} for any non-null, non-blank input.
     *
     * @param input the normalized NBT string
     *
     * @return {@code true} unless the input is null or blank
     */
    @Override
    public boolean canHandle(String input) {
        return input != null && !input.isBlank();
    }

    /**
     * Extracts the item ID from the input as a best-effort fallback, returning defaults for all other fields.
     *
     * @param input   the normalized NBT JSON string
     * @param context the handler context (unused by this handler)
     * @return a {@link ParsedItem} with only the item ID populated
     * @throws ParseException never thrown by this handler
     */
    @Override
    public ParsedItem parse(String input, NbtFormatHandlerContext context) throws ParseException {
        String itemId = tryExtractItemId(input);
        return new ParsedItem(itemId, false, Optional.empty(), List.of(), Optional.empty(), Optional.empty());
    }

    private String tryExtractItemId(String input) {
        try {
            JsonElement element;
            if (input.strip().startsWith("{")) {
                try {
                    element = JsonParser.parseString(input);
                } catch (Exception e) {
                    element = SnbtParser.parse(input);
                }
            } else {
                element = SnbtParser.parse(input);
            }
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("id")) {
                    return obj.get("id").getAsString();
                }
            }
        } catch (Exception ignored) {
            // Best-effort; fall through to default
        }
        return "minecraft:air";
    }
}
