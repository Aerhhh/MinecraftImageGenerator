package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


/**
 * Handles NBT from Minecraft 1.20.5+ (snapshot 24w09a onwards), where the legacy {@code tag}
 * compound was replaced with structured data components.
 * <p>
 * Key differences from the {@code tag}-based format:
 * <ul>
 *   <li>Item data lives under a top-level {@code components} object</li>
 *   <li>Lore is stored in {@code minecraft:lore} as JSON text component objects (not strings)</li>
 *   <li>Custom name is stored in {@code minecraft:custom_name}</li>
 *   <li>Player head textures use {@code minecraft:profile} instead of {@code SkullOwner}</li>
 *   <li>Enchantments use {@code minecraft:enchantments} and {@code minecraft:stored_enchantments}</li>
 *   <li>Enchantment glint can be overridden via {@code minecraft:enchantment_glint_override}</li>
 * </ul>
 *
 * @see <a href="https://minecraft.wiki/w/Data_component_format">Data Component Format</a>
 * @see <a href="https://minecraft.wiki/w/Item_format/1.20.5">Item Format (1.20.5)</a>
 */
public class ComponentsNbtFormatHandler implements NbtFormatHandler {

    @Override
    public boolean supports(JsonObject nbt) {
        return nbt != null && nbt.has("components");
    }

    @Override
    public NbtFormatMetadata extractMetadata(JsonObject nbt) {
        JsonElement componentsElement = nbt.get("components");
        if (componentsElement == null || !componentsElement.isJsonObject()) {
            return NbtFormatMetadata.EMPTY;
        }

        JsonObject components = componentsElement.getAsJsonObject();
        String skinValue = resolveSkinValue(components);
        Integer maxLineLength = resolveMaxLineLength(components);
        boolean enchanted = detectEnchanted(components);

        NbtFormatMetadata.Builder builder = NbtFormatMetadata.builder()
            .withValue(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE, skinValue)
            .withValue(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, maxLineLength)
            .withValue(NbtFormatMetadata.KEY_ENCHANTED, enchanted ? Boolean.TRUE : null);

        extractDurability(components, builder);

        return builder.build();
    }

    private String resolveSkinValue(JsonObject components) {
        JsonElement profileElement = components.get("minecraft:profile");
        if (profileElement == null || !profileElement.isJsonObject()) {
            return null;
        }

        JsonObject profile = profileElement.getAsJsonObject();

        if (profile.has("properties")) {
            String texture = findTextureString(profile.get("properties"));
            if (texture != null && !texture.isBlank()) {
                return texture;
            }
        }

        if (profile.has("textures")) {
            return findTextureString(profile.get("textures"));
        }

        return null;
    }

    private Integer resolveMaxLineLength(JsonObject components) {
        if (!components.has("minecraft:lore")) {
            return null;
        }

        JsonArray loreArray = components.getAsJsonArray("minecraft:lore");
        int maxLength = 0;

        for (JsonElement loreElement : loreArray) {
            if (!loreElement.isJsonObject()) {
                continue;
            }

            JsonObject loreEntry = loreElement.getAsJsonObject();
            String parsedLine = NbtTextComponentUtil.extractVisibleText(loreEntry);
            maxLength = Math.max(maxLength, parsedLine.length());
        }

        return maxLength == 0 ? null : maxLength;
    }

    private void extractDurability(JsonObject components, NbtFormatMetadata.Builder builder) {
        if (components.has("minecraft:damage")) {
            JsonElement damage = components.get("minecraft:damage");
            if (damage.isJsonPrimitive()) {
                builder.withValue(NbtFormatMetadata.KEY_DAMAGE, damage.getAsInt());
            }
        }

        if (components.has("minecraft:max_damage")) {
            JsonElement maxDamage = components.get("minecraft:max_damage");
            if (maxDamage.isJsonPrimitive()) {
                builder.withValue(NbtFormatMetadata.KEY_MAX_DAMAGE, maxDamage.getAsInt());
            }
        }
    }

    private boolean detectEnchanted(JsonObject components) {
        Boolean override = NbtTextComponentUtil.parseBoolean(components.get("minecraft:enchantment_glint_override"));
        if (override != null) {
            return override;
        }

        if (hasComponentEnchantments(components.get("minecraft:enchantments"))) {
            return true;
        }

        return hasComponentEnchantments(components.get("minecraft:stored_enchantments"));
    }

    private boolean hasComponentEnchantments(JsonElement element) {
        if (element == null) {
            return false;
        }

        if (element.isJsonArray()) {
            return !element.getAsJsonArray().isEmpty();
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("levels")) {
                JsonElement levels = obj.get("levels");
                if (levels.isJsonObject() && !levels.getAsJsonObject().entrySet().isEmpty()) {
                    return true;
                }
                if (levels.isJsonArray() && !levels.getAsJsonArray().isEmpty()) {
                    return true;
                }
            }

            return obj.has("entries") && obj.get("entries").isJsonArray() && !obj.getAsJsonArray("entries").isEmpty();
        }

        return false;
    }

    /**
     * Recursively searches a JSON element tree for a texture string value.
     * Handles the variety of profile/texture structures Minecraft uses:
     * arrays of property objects, nested "textures" keys, and direct string values.
     * Looks for keys named "value", "Value", or "url" on objects, and recurses into
     * arrays and nested "textures" keys.
     */
    private String findTextureString(JsonElement element) {
        if (element == null) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }

        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String result = findTextureString(item);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
            return null;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Skip non-texture properties (e.g. {name: "other", value: "..."})
            if (obj.has("name") && !"textures".equalsIgnoreCase(obj.get("name").getAsString())) {
                return null;
            }

            // Check direct value keys
            for (String key : new String[]{"value", "Value", "url"}) {
                if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                    return obj.get(key).getAsString();
                }
            }

            // Recurse into nested "textures" or "properties"
            for (String key : new String[]{"textures", "properties"}) {
                if (obj.has(key)) {
                    String result = findTextureString(obj.get(key));
                    if (result != null && !result.isBlank()) {
                        return result;
                    }
                }
            }
        }

        return null;
    }

}
