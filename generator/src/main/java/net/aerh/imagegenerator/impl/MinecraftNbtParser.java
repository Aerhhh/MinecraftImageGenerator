package net.aerh.imagegenerator.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.exception.NbtParseException;
import net.aerh.imagegenerator.impl.nbt.ComponentsNbtFormatHandler;
import net.aerh.imagegenerator.impl.nbt.NbtFormatHandler;
import net.aerh.imagegenerator.impl.nbt.NbtFormatMetadata;
import net.aerh.imagegenerator.impl.nbt.PostFlatteningNbtFormatHandler;
import net.aerh.imagegenerator.impl.nbt.PreFlatteningNbtFormatHandler;
import net.aerh.imagegenerator.impl.nbt.SnbtParser;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.parser.text.PlaceholderReverseMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Entry point for parsing Minecraft item NBT (JSON or SNBT) into renderable generators.
 * <p>
 * Accepts raw NBT input, auto-detects the format version via the registered {@link NbtFormatHandler}
 * chain, and produces a {@link ParsedNbt} containing the item and tooltip generators needed
 * to render the item image.
 */
@Slf4j
public class MinecraftNbtParser {

    private static final NbtFormatHandler DEFAULT_FORMAT_HANDLER = new DefaultNbtFormatHandler();

    private static final List<NbtFormatHandler> FORMAT_HANDLERS = List.of(
        new ComponentsNbtFormatHandler(),       // 1.20.5+ (components key)
        new PostFlatteningNbtFormatHandler(),   // 1.13-1.20.4 (tag + JSON text component strings)
        new PreFlatteningNbtFormatHandler()     // pre-1.13 (tag + plain § strings, broadest fallback)
    );

    /**
     * Parses a raw NBT string (JSON or SNBT) into generators for rendering.
     *
     * @param nbt the raw NBT input
     *
     * @return the parsed result containing generators, item id, texture, and enchantment state
     *
     * @throws NbtParseException if the input cannot be parsed or is missing a required {@code id} field
     */
    public static ParsedNbt parse(String nbt) {
        JsonObject jsonObject = parseToJsonObject(nbt);
        ArrayList<ClassBuilder<? extends Generator>> generators = new ArrayList<>();

        if (!jsonObject.has("id")) {
            throw new NbtParseException("NBT data is missing required 'id' field");
        }

        if (jsonObject.get("id").getAsString().contains("skull")) {
            String value = jsonObject.get("id").getAsString();
            value = value.replace("minecraft:", "")
                .replace("skull", "player_head");
            jsonObject.addProperty("id", value);
            log.debug("Normalized skull item id to '{}'", value);
        }

        // Handle player head for both legacy and component formats
        String parsedItemId = jsonObject.get("id").getAsString();
        boolean isPlayerHead = isPlayerHeadId(parsedItemId);
        NbtFormatHandler formatHandler = resolveFormatHandler(jsonObject);
        log.debug("Parsing item '{}' with NBT format handler '{}'", parsedItemId, handlerName(formatHandler));
        NbtFormatMetadata formatMetadata = formatHandler.extractMetadata(jsonObject);
        boolean hasTextureMetadata = formatMetadata.containsKey(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE);
        Integer metadataMaxLineLength = formatMetadata.get(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, Integer.class);
        Boolean metadataEnchanted = formatMetadata.get(NbtFormatMetadata.KEY_ENCHANTED, Boolean.class);
        boolean enchanted = metadataEnchanted != null && metadataEnchanted;

        log.debug(
            "Extracted metadata via '{}': texturePresent={}, maxLineLength={}",
            handlerName(formatHandler),
            hasTextureMetadata,
            metadataMaxLineLength
        );

        String itemModel = formatMetadata.get(NbtFormatMetadata.KEY_ITEM_MODEL, String.class);
        if (itemModel != null) {
            log.debug("Item '{}' is addressed by item model '{}'", parsedItemId, itemModel);
        }

        String base64Texture = null;
        if (isPlayerHead) {
            base64Texture = formatMetadata.get(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE, String.class);
            if (base64Texture != null) {
                base64Texture = resolveTextureHash(base64Texture);
                log.debug("Resolved player head texture via '{}': '{}'", handlerName(formatHandler), base64Texture);
            } else {
                log.debug("Handler '{}' did not provide a player head texture; falling back to static item render", handlerName(formatHandler));
            }
        }

        int maxLineLength = resolveMaxLineLength(formatMetadata, formatHandler);
        log.debug("Using max line length {} for item '{}' (handler='{}')", maxLineLength, parsedItemId, handlerName(formatHandler));

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .parseNbtJson(jsonObject)
            .withRenderBorder(true)
            .hasFirstLinePadding(true)
            .withMaxLineLength(maxLineLength);

        String dyeColor = tooltipGenerator.getDyeColor(jsonObject);
        if (dyeColor == null) {
            log.trace("No dye color present for item '{}'", parsedItemId);
        }

        // An item model replaces the item's model outright (as it does in vanilla), so it wins
        // over the player head render even when a profile texture is present. The texture stays
        // on the result so callers that cannot resolve the model can still fall back to the head.
        if (rendersAsPlayerHead(isPlayerHead, base64Texture, itemModel)) {
            if (dyeColor != null) {
                log.trace("Ignoring dye color '{}' because '{}' renders as a player head", dyeColor, parsedItemId);
            }
            generators.add(new MinecraftPlayerHeadGenerator.Builder()
                .withSkin(base64Texture)
                .withScale(-2));
        } else {
            if (dyeColor != null) {
                log.debug("Detected dye color '{}' for item '{}'", dyeColor, parsedItemId);
            }
            generators.add(newItemGenerator(itemModel, parsedItemId, dyeColor, enchanted));
        }

        generators.add(tooltipGenerator);

        PlaceholderReverseMapper reverseMapper;
        try {
            reverseMapper = new PlaceholderReverseMapper();
        } catch (IllegalStateException e) {
            // Invalid glyph data (e.g. a bad external icons.json/stats.json override file) must
            // surface as a user-visible parse error, not an uncaught exception.
            throw new NbtParseException("Invalid glyph override data: " + e.getMessage());
        }
        String mappedLore = reverseMapper.mapPlaceholders(tooltipGenerator.getItemLore());
        String mappedName = reverseMapper.mapPlaceholders(tooltipGenerator.getItemName());

        tooltipGenerator
            .withItemLore(mappedLore)
            .withName(mappedName);

        return new ParsedNbt(generators, base64Texture, parsedItemId, itemModel, enchanted);
    }

    /**
     * Whether the item renders as a 3D player head: a {@code player_head} whose profile texture
     * resolved and which carries no {@code minecraft:item_model} component. An item model
     * replaces the model vanilla would pick for the item, head included.
     */
    private static boolean rendersAsPlayerHead(boolean isPlayerHead, String base64Texture, String itemModel) {
        return isPlayerHead && base64Texture != null && itemModel == null;
    }

    /**
     * The single place an item generator is built for a parsed item, so the item model, dye color
     * and enchant glint cannot be dropped by one construction path disagreeing with another.
     * A {@code minecraft:item_model} component addresses the item by its model definition;
     * without one the item id is used.
     */
    private static MinecraftItemGenerator.Builder newItemGenerator(String itemModel, String itemId, String dyeColor, boolean enchanted) {
        MinecraftItemGenerator.Builder builder = new MinecraftItemGenerator.Builder();

        if (itemModel != null) {
            builder.withItemModel(itemModel);
        } else {
            builder.withItem(itemId);
        }

        return builder.withData(dyeColor).isEnchanted(enchanted);
    }

    /**
     * Parses the input string as either JSON or SNBT, returning a {@link JsonObject}.
     * Tries JSON first; if that fails, falls back to SNBT parsing.
     *
     * @param input the raw NBT string (JSON or SNBT format)
     *
     * @return the parsed JSON object
     *
     * @throws NbtParseException if neither format can be parsed
     */
    private static JsonObject parseToJsonObject(String input) {
        if (input == null || input.isBlank()) {
            throw new NbtParseException("NBT input is null or blank");
        }

        String trimmed = input.trim();

        // Try JSON first
        try {
            JsonElement element = JsonParser.parseString(trimmed);
            if (element.isJsonObject()) {
                log.debug("Parsed input as JSON");
                return element.getAsJsonObject();
            }
        } catch (JsonSyntaxException e) {
            log.debug("Input is not valid JSON, attempting SNBT parse: {}", e.getMessage());
        }

        // Fall back to SNBT
        try {
            JsonObject snbtResult = SnbtParser.parse(trimmed);
            log.debug("Parsed input as SNBT");
            return snbtResult;
        } catch (NbtParseException e) {
            throw new NbtParseException("Input is neither valid JSON nor valid SNBT: " + e.getMessage());
        }
    }

    private static boolean isPlayerHeadId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        String normalizedId = itemId.toLowerCase();
        if (normalizedId.startsWith("minecraft:")) {
            normalizedId = normalizedId.substring("minecraft:".length());
        }

        return normalizedId.equals("player_head");
    }

    private static NbtFormatHandler resolveFormatHandler(JsonObject jsonObject) {
        for (NbtFormatHandler handler : FORMAT_HANDLERS) {
            if (handler.supports(jsonObject)) {
                return handler;
            }
        }

        log.info("No specific NBT handler matched payload, using default handler");
        return DEFAULT_FORMAT_HANDLER;
    }

    private static int resolveMaxLineLength(NbtFormatMetadata metadata, NbtFormatHandler handler) {
        Integer maxLineLength = metadata.get(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, Integer.class);
        if (maxLineLength == null) {
            log.debug("Handler '{}' did not specify max line length; defaulting to {}", handlerName(handler), MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH);
            return MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
        }

        return maxLineLength;
    }

    private static String resolveTextureHash(String texture) {
        // Try to decode as base64 skin data and extract the texture hash from the URL
        try {
            byte[] decoded = Base64.getDecoder().decode(texture);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root != null && root.has("textures")) {
                JsonObject textures = root.getAsJsonObject("textures");
                if (textures.has("SKIN")) {
                    String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
                    int lastSlash = url.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        return url.substring(lastSlash + 1);
                    }
                }
            }
        } catch (Exception ignored) {
            // Not valid base64 skin data, try other formats
        }

        // Try to extract hash from a full texture URL
        if (texture.contains("textures.minecraft.net/texture/")) {
            int lastSlash = texture.lastIndexOf('/');
            if (lastSlash >= 0) {
                return texture.substring(lastSlash + 1);
            }
        }

        // Already a hash or other format, return as-is
        return texture;
    }

    private static String handlerName(NbtFormatHandler handler) {
        if (handler == null) {
            return "null";
        }
        String simpleName = handler.getClass().getSimpleName();
        return simpleName.isBlank() ? handler.getClass().getName() : simpleName;
    }

    /** Result of parsing an NBT string, containing everything needed to render the item. */
    @Getter(AccessLevel.PUBLIC)
    @AllArgsConstructor
    public static class ParsedNbt {

        private ArrayList<ClassBuilder<? extends Generator>> generators;
        private String base64Texture;
        private String parsedItemId;
        /** The {@code minecraft:item_model} component value, or null when the item has none. */
        private String parsedItemModel;
        private boolean enchanted;
    }

    private static final class DefaultNbtFormatHandler implements NbtFormatHandler {

        @Override
        public boolean supports(JsonObject nbt) {
            return true;
        }
    }
}