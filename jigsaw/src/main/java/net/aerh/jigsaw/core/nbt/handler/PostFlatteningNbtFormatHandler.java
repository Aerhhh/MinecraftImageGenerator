package net.aerh.jigsaw.core.nbt.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.core.nbt.NbtTextComponentUtil;
import net.aerh.jigsaw.core.nbt.SnbtParser;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.spi.NbtFormatHandler;
import net.aerh.jigsaw.spi.NbtFormatHandlerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the 1.13-1.20.4 "post-flattening" NBT format.
 * <p>
 * Detects the presence of a {@code "tag"} key with a display sub-object whose
 * {@code Name} field contains a JSON text component string.
 * <p>
 * Enchantment keys: {@code "Enchantments"} and {@code "StoredEnchantments"}.
 */
public final class PostFlatteningNbtFormatHandler implements NbtFormatHandler {

    /**
     * Returns the unique identifier for this handler.
     *
     * @return {@code "jigsaw:post-flattening"}
     */
    @Override
    public String id() {
        return "jigsaw:post-flattening";
    }

    /**
     * Returns the priority of this handler. Lower values are evaluated first.
     *
     * @return {@code 200}
     */
    @Override
    public int priority() {
        return 200;
    }

    /**
     * Returns {@code true} if the input contains a {@code "tag"} key.
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
        return input.contains("\"tag\"") || input.contains("tag:");
    }

    /**
     * Parses the 1.13-1.20.4 post-flattening format and returns the extracted item data.
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
        Optional<String> base64Texture = extractTexture(tag);
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
            throw new ParseException("Failed to parse post-flattening NBT: " + e.getMessage(),
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
        return new JsonObject();
    }

    private boolean extractEnchanted(JsonObject tag) {
        if (tag.has("Enchantments")) {
            JsonElement e = tag.get("Enchantments");
            return e.isJsonArray() && e.getAsJsonArray().size() > 0;
        }
        if (tag.has("StoredEnchantments")) {
            JsonElement e = tag.get("StoredEnchantments");
            return e.isJsonArray() && e.getAsJsonArray().size() > 0;
        }
        return false;
    }

    private Optional<String> extractTexture(JsonObject tag) {
        // SkullOwner.Properties.textures[0].Value
        if (!tag.has("SkullOwner")) {
            return Optional.empty();
        }
        JsonElement skullOwner = tag.get("SkullOwner");
        if (!skullOwner.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject skullObj = skullOwner.getAsJsonObject();
        if (!skullObj.has("Properties")) {
            return Optional.empty();
        }
        JsonElement props = skullObj.get("Properties");
        if (!props.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject propsObj = props.getAsJsonObject();
        if (!propsObj.has("textures")) {
            return Optional.empty();
        }
        JsonElement textures = propsObj.get("textures");
        if (!textures.isJsonArray() || textures.getAsJsonArray().isEmpty()) {
            return Optional.empty();
        }
        JsonElement first = textures.getAsJsonArray().get(0);
        if (!first.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject firstObj = first.getAsJsonObject();
        if (firstObj.has("Value")) {
            return Optional.of(firstObj.get("Value").getAsString());
        }
        return Optional.empty();
    }

    private List<String> extractLore(JsonObject tag) {
        if (!tag.has("display")) {
            return List.of();
        }
        JsonElement display = tag.get("display");
        if (!display.isJsonObject()) {
            return List.of();
        }
        JsonObject displayObj = display.getAsJsonObject();
        if (!displayObj.has("Lore")) {
            return List.of();
        }
        JsonElement loreElement = displayObj.get("Lore");
        if (!loreElement.isJsonArray()) {
            return List.of();
        }

        List<String> lore = new ArrayList<>();
        for (JsonElement line : loreElement.getAsJsonArray()) {
            String raw = line.getAsString();
            JsonElement parsed = NbtTextComponentUtil.tryParseJson(raw);
            String formatted = parsed != null
                    ? NbtTextComponentUtil.toFormattedString(parsed)
                    : raw;
            lore.add(formatted);
        }
        return lore;
    }

    private Optional<String> extractDisplayName(JsonObject tag) {
        if (!tag.has("display")) {
            return Optional.empty();
        }
        JsonElement display = tag.get("display");
        if (!display.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject displayObj = display.getAsJsonObject();
        if (!displayObj.has("Name")) {
            return Optional.empty();
        }

        String raw = displayObj.get("Name").getAsString();
        JsonElement parsed = NbtTextComponentUtil.tryParseJson(raw);
        String formatted = parsed != null
                ? NbtTextComponentUtil.toFormattedString(parsed)
                : raw;
        return formatted.isBlank() ? Optional.empty() : Optional.of(formatted);
    }

    private Optional<Integer> extractDyeColor(JsonObject tag) {
        if (!tag.has("display")) {
            return Optional.empty();
        }
        JsonElement display = tag.get("display");
        if (!display.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject displayObj = display.getAsJsonObject();
        if (displayObj.has("color")) {
            return Optional.of(displayObj.get("color").getAsInt());
        }
        return Optional.empty();
    }
}
