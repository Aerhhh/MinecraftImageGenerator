package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aerh.imagegenerator.exception.TooManyTexturesException;

/**
 * Base class for NBT format handlers that operate on the {@code tag} compound
 * used in Minecraft versions prior to 1.20.5 (when it was replaced by the
 * {@code components} system in snapshot 24w09a).
 * <p>
 * Shared logic for player-head texture resolution ({@code tag.SkullOwner}) and
 * enchantment detection is defined here. Subclasses provide version-specific
 * enchantment keys via {@link #getEnchantmentKeys()}, implement
 * {@link #supports(JsonObject)} for version-specific detection, and
 * {@link #resolveMaxLineLength(JsonObject)} for version-specific lore measurement.
 *
 * @see <a href="https://minecraft.wiki/w/Item_format/Before_1.20.5">Item Format before 1.20.5</a>
 * @see <a href="https://minecraft.wiki/w/Item_format/Player_Heads">Player Head Format</a>
 */
abstract class AbstractTagNbtFormatHandler implements NbtFormatHandler {

    @Override
    public NbtFormatMetadata extractMetadata(JsonObject nbt) {
        JsonObject tag = nbt.getAsJsonObject("tag");
        if (tag == null) {
            return NbtFormatMetadata.EMPTY;
        }

        String skinValue = resolveSkinValue(tag);
        Integer maxLineLength = resolveMaxLineLength(tag);
        boolean enchanted = detectEnchanted(tag);

        return NbtFormatMetadata.builder()
            .withValue(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE, skinValue)
            .withValue(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, maxLineLength)
            .withValue(NbtFormatMetadata.KEY_ENCHANTED, enchanted ? Boolean.TRUE : null)
            .build();
    }

    /**
     * Resolves the max lore line length from the {@code tag} object.
     * Differs between pre-flattening (plain strings) and post-flattening (JSON text component strings).
     */
    protected abstract Integer resolveMaxLineLength(JsonObject tag);

    /**
     * Returns the enchantment key names relevant to this version's NBT format.
     * For example, pre-1.13 uses {@code ench} while 1.13+ uses {@code Enchantments}.
     */
    protected abstract String[] getEnchantmentKeys();

    /**
     * Resolves the player-head skin texture from the legacy {@code SkullOwner} path.
     * Identical across both pre- and post-flattening eras.
     */
    protected String resolveSkinValue(JsonObject tag) {
        if (!tag.has("SkullOwner")) {
            return null;
        }

        JsonObject skullOwner = tag.getAsJsonObject("SkullOwner");
        if (skullOwner == null || !skullOwner.has("Properties")) {
            return null;
        }

        JsonObject properties = skullOwner.getAsJsonObject("Properties");
        if (properties == null || !properties.has("textures")) {
            return null;
        }

        JsonArray textures = properties.getAsJsonArray("textures");
        if (textures == null || textures.isEmpty()) {
            return null;
        }

        if (textures.size() > 1) {
            throw new TooManyTexturesException();
        }

        JsonObject texture = textures.get(0).getAsJsonObject();
        if (texture.has("Value")) {
            return texture.get("Value").getAsString();
        }

        if (texture.has("value")) {
            return texture.get("value").getAsString();
        }

        return null;
    }

    /**
     * Detects whether the item has enchantments by checking the keys returned by
     * {@link #getEnchantmentKeys()}.
     */
    protected boolean detectEnchanted(JsonObject tag) {
        if (tag == null) {
            return false;
        }

        for (String key : getEnchantmentKeys()) {
            JsonElement element = tag.get(key);
            if (element == null) {
                continue;
            }

            if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
                return true;
            }

            if (element.isJsonObject() && !element.getAsJsonObject().entrySet().isEmpty()) {
                return true;
            }
        }

        return false;
    }
}