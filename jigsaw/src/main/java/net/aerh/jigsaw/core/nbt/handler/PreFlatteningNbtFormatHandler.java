package net.aerh.jigsaw.core.nbt.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.core.nbt.SnbtParser;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.spi.NbtFormatHandler;
import net.aerh.jigsaw.spi.NbtFormatHandlerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the pre-1.13 "pre-flattening" NBT format.
 * <p>
 * Detects the presence of an {@code "ench"} key or a plain-string {@code Name} in the
 * display sub-object (as opposed to the JSON text component format used post-flattening).
 */
public final class PreFlatteningNbtFormatHandler implements NbtFormatHandler {

    /**
     * Returns the unique identifier for this handler.
     *
     * @return {@code "jigsaw:pre-flattening"}
     */
    @Override
    public String id() {
        return "jigsaw:pre-flattening";
    }

    /**
     * Returns the priority of this handler. Lower values are evaluated first.
     *
     * @return {@code 300}
     */
    @Override
    public int priority() {
        return 300;
    }

    /**
     * Returns {@code true} if the input contains an {@code "ench"} key indicating pre-flattening format.
     *
     * @param input the normalized NBT string to inspect
     *
     * @return whether this handler can process the input
     */
    @Override
    public boolean canHandle(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return input.contains("\"ench\"") || input.contains("ench:") || input.contains("ench:[");
    }

    /**
     * Parses the pre-1.13 pre-flattening format and returns the extracted item data.
     *
     * @param input   the normalized NBT JSON string
     * @param context the handler context providing optional registry access
     * @return the parsed item
     * @throws ParseException if the input cannot be parsed
     */
    @Override
    public ParsedItem parse(String input, NbtFormatHandlerContext context) throws ParseException {
        JsonObject root = parseRoot(input);
        String itemId = extractItemId(root);
        JsonObject tag = extractTag(root);

        boolean enchanted = extractEnchanted(tag);
        Optional<String> base64Texture = Optional.empty();
        List<String> lore = extractLore(tag);
        Optional<String> displayName = extractDisplayName(tag);
        Optional<Integer> dyeColor = extractDyeColor(tag);

        return new ParsedItem(itemId, enchanted, base64Texture, lore, displayName, dyeColor);
    }

    private JsonObject parseRoot(String input) throws ParseException {
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
            if (!element.isJsonObject()) {
                throw new ParseException("Expected JSON object at root", Map.of("input", input));
            }
            return element.getAsJsonObject();
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse pre-flattening NBT: " + e.getMessage(),
                    Map.of("input", input), e);
        }
    }

    private String extractItemId(JsonObject root) {
        if (root.has("id")) {
            String id = root.get("id").getAsString();
            return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        }
        return "air";
    }

    private JsonObject extractTag(JsonObject root) {
        if (root.has("tag") && root.get("tag").isJsonObject()) {
            return root.getAsJsonObject("tag");
        }
        return root;
    }

    private boolean extractEnchanted(JsonObject tag) {
        // Pre-flattening uses "ench" key
        if (tag.has("ench")) {
            JsonElement ench = tag.get("ench");
            return ench.isJsonArray() && ench.getAsJsonArray().size() > 0;
        }
        return false;
    }

    private List<String> extractLore(JsonObject tag) {
        JsonObject display = getDisplay(tag);
        if (display == null || !display.has("Lore")) {
            return List.of();
        }
        JsonElement loreElement = display.get("Lore");
        if (!loreElement.isJsonArray()) {
            return List.of();
        }
        List<String> lore = new ArrayList<>();
        for (JsonElement line : loreElement.getAsJsonArray()) {
            lore.add(line.getAsString());
        }
        return lore;
    }

    private Optional<String> extractDisplayName(JsonObject tag) {
        JsonObject display = getDisplay(tag);
        if (display == null || !display.has("Name")) {
            return Optional.empty();
        }
        String name = display.get("Name").getAsString();
        return name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    private Optional<Integer> extractDyeColor(JsonObject tag) {
        JsonObject display = getDisplay(tag);
        if (display != null && display.has("color")) {
            return Optional.of(display.get("color").getAsInt());
        }
        return Optional.empty();
    }

    private JsonObject getDisplay(JsonObject tag) {
        if (tag.has("display") && tag.get("display").isJsonObject()) {
            return tag.getAsJsonObject("display");
        }
        return null;
    }
}
