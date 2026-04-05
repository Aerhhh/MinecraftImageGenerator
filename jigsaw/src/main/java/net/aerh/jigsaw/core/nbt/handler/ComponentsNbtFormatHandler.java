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
 * Handles the 1.20.5+ "components" NBT format.
 * <p>
 * Detects the presence of a {@code "components"} key in the root compound and extracts:
 * <ul>
 *   <li>{@code minecraft:enchantments} - enchantment presence for glint</li>
 *   <li>{@code minecraft:enchantment_glint_override} - explicit glint override</li>
 *   <li>{@code minecraft:profile} - skull texture (Base64)</li>
 *   <li>{@code minecraft:lore} - item lore lines</li>
 *   <li>{@code minecraft:custom_name} - display name</li>
 *   <li>{@code minecraft:dyed_color} - leather armor dye color</li>
 * </ul>
 */
public final class ComponentsNbtFormatHandler implements NbtFormatHandler {

    @Override
    public String id() {
        return "jigsaw:components";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean canHandle(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return input.contains("\"components\"") || input.contains("components:");
    }

    @Override
    public ParsedItem parse(String input, NbtFormatHandlerContext context) throws ParseException {
        JsonObject root = parseRoot(input);

        String itemId = extractItemId(root);
        JsonObject components = extractComponents(root);

        boolean enchanted = extractEnchanted(components);
        Optional<String> base64Texture = extractTexture(components);
        List<String> lore = extractLore(components);
        Optional<String> displayName = extractDisplayName(components);
        Optional<Integer> dyeColor = extractDyeColor(components);

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
            throw new ParseException("Failed to parse components NBT: " + e.getMessage(),
                    Map.of("input", input), e);
        }
    }

    private String extractItemId(JsonObject root) {
        if (root.has("id")) {
            return root.get("id").getAsString();
        }
        return "minecraft:air";
    }

    private JsonObject extractComponents(JsonObject root) {
        if (root.has("components") && root.get("components").isJsonObject()) {
            return root.getAsJsonObject("components");
        }
        return new JsonObject();
    }

    private boolean extractEnchanted(JsonObject components) {
        if (components.has("minecraft:enchantment_glint_override")) {
            return components.get("minecraft:enchantment_glint_override").getAsBoolean();
        }
        if (components.has("minecraft:enchantments")) {
            JsonElement enchantments = components.get("minecraft:enchantments");
            if (enchantments.isJsonObject()) {
                JsonObject obj = enchantments.getAsJsonObject();
                // Has "levels" sub-object with enchantment entries, or direct entries
                if (obj.has("levels")) {
                    return obj.getAsJsonObject("levels").size() > 0;
                }
                return obj.size() > 0;
            }
        }
        return false;
    }

    private Optional<String> extractTexture(JsonObject components) {
        if (!components.has("minecraft:profile")) {
            return Optional.empty();
        }
        JsonElement profile = components.get("minecraft:profile");
        if (!profile.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject profileObj = profile.getAsJsonObject();
        if (profileObj.has("properties") && profileObj.get("properties").isJsonArray()) {
            JsonArray properties = profileObj.getAsJsonArray("properties");
            for (JsonElement prop : properties) {
                if (!prop.isJsonObject()) {
                    continue;
                }
                JsonObject propObj = prop.getAsJsonObject();
                if (propObj.has("name") && "textures".equals(propObj.get("name").getAsString())
                        && propObj.has("value")) {
                    return Optional.of(propObj.get("value").getAsString());
                }
            }
        }
        return Optional.empty();
    }

    private List<String> extractLore(JsonObject components) {
        if (!components.has("minecraft:lore")) {
            return List.of();
        }
        JsonElement loreElement = components.get("minecraft:lore");
        if (!loreElement.isJsonArray()) {
            return List.of();
        }
        List<String> lore = new ArrayList<>();
        for (JsonElement line : loreElement.getAsJsonArray()) {
            String formatted;
            if (line.isJsonPrimitive()) {
                String raw = line.getAsString();
                JsonElement parsed = NbtTextComponentUtil.tryParseJson(raw);
                formatted = parsed != null
                        ? NbtTextComponentUtil.toFormattedString(parsed)
                        : raw;
            } else {
                formatted = NbtTextComponentUtil.toFormattedString(line);
            }
            lore.add(formatted);
        }
        return lore;
    }

    private Optional<String> extractDisplayName(JsonObject components) {
        if (!components.has("minecraft:custom_name")) {
            return Optional.empty();
        }
        JsonElement nameElement = components.get("minecraft:custom_name");
        String raw = nameElement.getAsString();
        JsonElement parsed = NbtTextComponentUtil.tryParseJson(raw);
        String formatted = parsed != null
                ? NbtTextComponentUtil.toFormattedString(parsed)
                : raw;
        return formatted.isBlank() ? Optional.empty() : Optional.of(formatted);
    }

    private Optional<Integer> extractDyeColor(JsonObject components) {
        if (!components.has("minecraft:dyed_color")) {
            return Optional.empty();
        }
        JsonElement dyeElement = components.get("minecraft:dyed_color");
        if (dyeElement.isJsonObject()) {
            JsonObject dyeObj = dyeElement.getAsJsonObject();
            if (dyeObj.has("rgb")) {
                return Optional.of(dyeObj.get("rgb").getAsInt());
            }
        } else if (dyeElement.isJsonPrimitive()) {
            return Optional.of(dyeElement.getAsInt());
        }
        return Optional.empty();
    }
}
