package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Handles NBT from Minecraft versions 1.13 through 1.20.4 (post-Flattening, pre-Components).
 * <p>
 * In these versions, {@code tag.display.Name} is a JSON text component string
 * (e.g., {@code '{"text":"Cool Sword","color":"gold","bold":true}'}). The Name format changed
 * in snapshot 18w01a (1.13). {@code tag.display.Lore} entries became JSON text component strings
 * later, in snapshot 18w43a (1.14). Enchantments were renamed from {@code ench} to
 * {@code Enchantments} with namespaced string IDs in snapshot 18w21a (1.13).
 * <p>
 * Detection checks {@code display.Name} first (JSON since 1.13) and falls back to checking
 * {@code display.Lore} (JSON since 1.14). Items from 1.13-1.13.2 with only plain-string Lore
 * and no custom Name will fall through to {@link PreFlatteningNbtFormatHandler}, which correctly
 * measures plain strings by raw length.
 * <p>
 * This handler has stricter detection than {@link PreFlatteningNbtFormatHandler} and must be
 * registered before it in the handler list so that the more specific match wins.
 *
 * @see <a href="https://minecraft.wiki/w/Java_Edition_18w01a">18w01a - Name became JSON text component</a>
 * @see <a href="https://minecraft.wiki/w/Java_Edition_18w43a">18w43a - Lore became JSON text component</a>
 * @see <a href="https://minecraft.wiki/w/Item_format/Before_1.20.5">Item Format before 1.20.5</a>
 */
public class PostFlatteningNbtFormatHandler extends AbstractTagNbtFormatHandler {

    @Override
    public boolean supports(JsonObject nbt) {
        if (nbt == null || !nbt.has("tag")) {
            return false;
        }

        JsonObject tag = nbt.getAsJsonObject("tag");
        if (tag == null || !tag.has("display")) {
            return false;
        }

        JsonObject display = tag.getAsJsonObject("display");
        if (display == null) {
            return false;
        }

        // Check if Name is a JSON text component string
        if (display.has("Name")) {
            String name = display.get("Name").getAsString();
            if (NbtTextComponentUtil.isJsonTextComponent(name)) {
                return true;
            }
        }

        // Also check Lore entries - some items may not have a custom name but have JSON lore
        if (display.has("Lore")) {
            JsonArray loreArray = display.getAsJsonArray("Lore");
            if (loreArray != null && !loreArray.isEmpty()) {
                JsonElement first = loreArray.get(0);
                if (first.isJsonPrimitive()) {
                    return NbtTextComponentUtil.isJsonTextComponent(first.getAsString());
                }
            }
        }

        return false;
    }

    @Override
    protected String[] getEnchantmentKeys() {
        return new String[]{"Enchantments", "StoredEnchantments"};
    }

    @Override
    protected Integer resolveMaxLineLength(JsonObject tag) {
        if (!tag.has("display")) {
            return null;
        }

        JsonObject display = tag.getAsJsonObject("display");
        if (display == null || !display.has("Lore")) {
            return null;
        }

        JsonArray loreArray = display.getAsJsonArray("Lore");
        if (loreArray == null || loreArray.isEmpty()) {
            return null;
        }

        int maxLength = 0;
        for (JsonElement loreElement : loreArray) {
            if (!loreElement.isJsonPrimitive()) {
                continue;
            }

            String line = loreElement.getAsString();
            String visibleText = extractVisibleTextFromJsonString(line);
            maxLength = Math.max(maxLength, visibleText.length());
        }

        return maxLength == 0 ? null : maxLength;
    }

    private String extractVisibleTextFromJsonString(String jsonString) {
        try {
            JsonElement parsed = JsonParser.parseString(jsonString);
            if (parsed.isJsonObject()) {
                return NbtTextComponentUtil.extractVisibleText(parsed.getAsJsonObject());
            }
        } catch (JsonSyntaxException e) {
            // Not valid JSON, fall back to raw string length
        }

        return jsonString;
    }
}