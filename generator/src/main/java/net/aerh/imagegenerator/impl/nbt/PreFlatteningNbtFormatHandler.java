package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Handles NBT from Minecraft versions prior to 1.13 ("The Flattening").
 * <p>
 * In these versions, {@code tag.display.Name} and {@code tag.display.Lore} entries are plain
 * section symbol coded strings (e.g., {@code "§6§lCool Sword"}), and enchantments use the
 * {@code ench} key with numeric enchantment IDs. The {@code ench} key was renamed to
 * {@code Enchantments} with namespaced string IDs in snapshot 18w21a (1.13).
 * <p>
 * This handler also serves as the broadest fallback for any {@code tag}-based NBT that does
 * not match the stricter {@link PostFlatteningNbtFormatHandler} detection. This includes
 * edge-case 1.13 items that have plain-string Lore but no custom Name (Lore did not become
 * JSON until 1.14 / snapshot 18w43a).
 *
 * @see <a href="https://minecraft.wiki/w/Java_Edition_Flattening">The Flattening (1.13)</a>
 * @see <a href="https://minecraft.wiki/w/Item_format/Before_1.20.5">Item Format before 1.20.5</a>
 */
public class PreFlatteningNbtFormatHandler extends AbstractTagNbtFormatHandler {

    @Override
    public boolean supports(JsonObject nbt) {
        if (nbt == null || !nbt.has("tag")) {
            return false;
        }

        JsonObject tag = nbt.getAsJsonObject("tag");
        if (tag == null) {
            return false;
        }

        if (tag.has("ench")) {
            return true;
        }

        // If display.Name exists, accept only if it's NOT a JSON text component
        if (tag.has("display")) {
            JsonObject display = tag.getAsJsonObject("display");
            if (display != null && display.has("Name")) {
                String name = display.get("Name").getAsString();
                return !NbtTextComponentUtil.isJsonTextComponent(name);
            }
        }

        return true;
    }

    @Override
    protected String[] getEnchantmentKeys() {
        return new String[]{"ench", "StoredEnchantments"};
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
            maxLength = Math.max(maxLength, line.length());
        }

        return maxLength == 0 ? null : maxLength;
    }
}