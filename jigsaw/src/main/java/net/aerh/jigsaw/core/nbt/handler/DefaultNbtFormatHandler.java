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

    @Override
    public String id() {
        return "jigsaw:default";
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canHandle(String input) {
        return input != null && !input.isBlank();
    }

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
