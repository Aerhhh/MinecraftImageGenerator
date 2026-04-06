package net.aerh.jigsaw.core.resource;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves an item model's texture data by following Minecraft's parent inheritance chain.
 *
 * <p>Starting from {@code assets/minecraft/models/item/<itemId>.json}, this resolver:
 * <ol>
 *   <li>Parses the JSON model file.</li>
 *   <li>Extracts the {@code parent} and {@code textures} fields.</li>
 *   <li>Recursively resolves parent models, stopping at terminal parents.</li>
 *   <li>Merges textures with child values overriding parent values.</li>
 *   <li>Detects and rejects circular references.</li>
 * </ol>
 */
public class ModelResolver {

    private static final Gson GSON = new Gson();

    private static final Set<String> TERMINAL_PARENTS = Set.of(
            "builtin/generated",
            "minecraft:builtin/generated",
            "builtin/entity",
            "minecraft:builtin/entity"
    );

    /**
     * Resolves the {@link ItemModelData} for the given item ID by following the parent chain.
     *
     * @param pack   the resource pack to load models from
     * @param itemId the item ID (e.g. "diamond_sword"), without namespace or path prefix
     * @return the resolved model data, or empty if the model is missing or a circular reference is detected
     */
    public Optional<ItemModelData> resolve(ResourcePack pack, String itemId) {
        String startPath = "assets/minecraft/models/item/" + itemId + ".json";
        return resolveChain(pack, startPath, new HashSet<>());
    }

    private Optional<ItemModelData> resolveChain(ResourcePack pack, String modelPath, Set<String> visited) {
        if (!visited.add(modelPath)) {
            // Circular reference detected
            return Optional.empty();
        }

        Optional<InputStream> streamOpt = pack.getResource(modelPath);
        if (streamOpt.isEmpty()) {
            return Optional.empty();
        }

        JsonObject model;
        try (InputStream stream = streamOpt.get();
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            model = GSON.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            return Optional.empty();
        }

        Map<String, String> ownTextures = parseTextures(model);

        if (!model.has("parent")) {
            return Optional.of(new ItemModelData(ownTextures, "unknown"));
        }

        String rawParent = model.get("parent").getAsString();

        if (TERMINAL_PARENTS.contains(rawParent)) {
            return Optional.of(new ItemModelData(ownTextures, stripNamespace(rawParent)));
        }

        String parentPath = toModelPath(rawParent);
        Optional<ItemModelData> parentResult = resolveChain(pack, parentPath, visited);
        if (parentResult.isEmpty()) {
            return Optional.empty();
        }

        ItemModelData parentData = parentResult.get();
        Map<String, String> merged = new HashMap<>(parentData.textures());
        merged.putAll(ownTextures);

        return Optional.of(new ItemModelData(merged, parentData.parentType()));
    }

    /**
     * Parses the {@code textures} object from a model JSON, returning an empty map if absent.
     */
    private static Map<String, String> parseTextures(JsonObject model) {
        if (!model.has("textures")) {
            return Map.of();
        }
        JsonObject texturesObj = model.getAsJsonObject("textures");
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : texturesObj.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    /**
     * Converts a model parent reference to a filesystem path within the pack.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "item/generated"} -> {@code "assets/minecraft/models/item/generated.json"}</li>
     *   <li>{@code "minecraft:item/generated"} -> {@code "assets/minecraft/models/item/generated.json"}</li>
     *   <li>{@code "block/stone"} -> {@code "assets/minecraft/models/block/stone.json"}</li>
     *   <li>{@code "minecraft:block/stone"} -> {@code "assets/minecraft/models/block/stone.json"}</li>
     * </ul>
     */
    private static String toModelPath(String parent) {
        String stripped = stripNamespace(parent);
        return "assets/minecraft/models/" + stripped + ".json";
    }

    /**
     * Strips the {@code minecraft:} namespace prefix from a model reference if present.
     */
    private static String stripNamespace(String reference) {
        if (reference.startsWith("minecraft:")) {
            return reference.substring("minecraft:".length());
        }
        return reference;
    }
}
