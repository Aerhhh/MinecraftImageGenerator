package net.aerh.jigsaw.core.resource;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
 * <p>Starting from an item ID, the resolver:
 * <ol>
 *   <li>Checks for a 1.21.4+ item definition at {@code assets/minecraft/items/<id>.json}.</li>
 *   <li>If found, extracts the model reference (handling {@code minecraft:model},
 *       {@code condition}, {@code select}, {@code range_dispatch}, and {@code composite} types).</li>
 *   <li>Falls back to the pre-1.21.4 path {@code assets/minecraft/models/item/<id>.json}.</li>
 *   <li>Follows the {@code parent} inheritance chain, merging textures at each level.</li>
 *   <li>Detects and rejects circular references.</li>
 * </ol>
 */
public class ModelResolver {

    private static final Gson GSON = new Gson();

    private static final Set<String> TERMINAL_PARENTS = Set.of(
            "builtin/generated",
            "minecraft:builtin/generated",
            "item/generated",
            "minecraft:item/generated",
            "builtin/entity",
            "minecraft:builtin/entity"
    );

    /**
     * Resolves the {@link ItemModelData} for the given item ID by following the parent chain.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Check for a 1.21.4+ item definition at {@code assets/minecraft/items/<id>.json}</li>
     *   <li>If found, extract the model reference and resolve that model</li>
     *   <li>Otherwise, fall back to {@code assets/minecraft/models/item/<id>.json}</li>
     * </ol>
     *
     * @param pack   the resource pack to load models from
     * @param itemId the item ID (e.g. "diamond_sword"), without namespace or path prefix
     * @return the resolved model data, or empty if the model is missing or a circular reference is detected
     */
    public Optional<ItemModelData> resolve(ResourcePack pack, String itemId) {
        // Try 1.21.4+ item definition first
        Optional<String> modelRef = resolveItemDefinition(pack, itemId);
        if (modelRef.isPresent()) {
            String modelPath = toModelPath(modelRef.get());
            return resolveChain(pack, modelPath, new HashSet<>());
        }

        // Fall back to pre-1.21.4 models/item path
        String startPath = "assets/minecraft/models/item/" + itemId + ".json";
        return resolveChain(pack, startPath, new HashSet<>());
    }

    // -------------------------------------------------------------------------
    // 1.21.4+ item definition resolution
    // -------------------------------------------------------------------------

    /**
     * Attempts to resolve a model reference from a 1.21.4+ item definition file.
     *
     * <p>Item definitions live at {@code assets/minecraft/items/<id>.json} and contain
     * a {@code model} field with a type-dispatched structure. This method extracts the
     * model path from simple types and uses fallback/default models for complex types
     * that require runtime context.
     *
     * @param pack   the resource pack
     * @param itemId the item ID
     * @return the model reference (e.g. "minecraft:item/diamond_sword"), or empty if no definition exists
     */
    private Optional<String> resolveItemDefinition(ResourcePack pack, String itemId) {
        String defPath = "assets/minecraft/items/" + itemId + ".json";
        Optional<InputStream> streamOpt = pack.getResource(defPath);
        if (streamOpt.isEmpty()) {
            return Optional.empty();
        }

        try (InputStream stream = streamOpt.get();
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject definition = GSON.fromJson(reader, JsonObject.class);
            if (!definition.has("model")) {
                return Optional.empty();
            }
            return extractModelRef(definition.getAsJsonObject("model"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Extracts a model path from a type-dispatched item model entry.
     *
     * <p>Supported types:
     * <ul>
     *   <li>{@code minecraft:model} - returns the {@code model} field directly</li>
     *   <li>{@code minecraft:composite} - returns the first model in the {@code models} list</li>
     *   <li>{@code minecraft:condition} - returns the {@code on_false} model (default/idle state)</li>
     *   <li>{@code minecraft:select} - returns the {@code fallback} model if present, else first case</li>
     *   <li>{@code minecraft:range_dispatch} - returns the {@code fallback} model if present, else first entry</li>
     *   <li>{@code minecraft:special} - returns the {@code base} model reference</li>
     *   <li>{@code minecraft:empty} - returns empty</li>
     * </ul>
     */
    private Optional<String> extractModelRef(JsonObject modelEntry) {
        if (modelEntry == null || !modelEntry.has("type")) {
            return Optional.empty();
        }

        String type = stripNamespace(modelEntry.get("type").getAsString());

        return switch (type) {
            case "model" -> extractFromDirectModel(modelEntry);
            case "composite" -> extractFromComposite(modelEntry);
            case "condition" -> extractFromCondition(modelEntry);
            case "select" -> extractFromSelect(modelEntry);
            case "range_dispatch" -> extractFromRangeDispatch(modelEntry);
            case "special" -> extractFromSpecial(modelEntry);
            default -> Optional.empty();
        };
    }

    private Optional<String> extractFromDirectModel(JsonObject entry) {
        if (entry.has("model")) {
            return Optional.of(entry.get("model").getAsString());
        }
        return Optional.empty();
    }

    private Optional<String> extractFromComposite(JsonObject entry) {
        if (entry.has("models")) {
            JsonArray models = entry.getAsJsonArray("models");
            if (!models.isEmpty()) {
                return extractModelRef(models.get(0).getAsJsonObject());
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractFromCondition(JsonObject entry) {
        // Prefer on_false (the default/idle state, e.g. bow not pulling)
        if (entry.has("on_false")) {
            Optional<String> result = extractModelRef(entry.getAsJsonObject("on_false"));
            if (result.isPresent()) {
                return result;
            }
        }
        if (entry.has("on_true")) {
            return extractModelRef(entry.getAsJsonObject("on_true"));
        }
        return Optional.empty();
    }

    private Optional<String> extractFromSelect(JsonObject entry) {
        if (entry.has("fallback")) {
            Optional<String> result = extractModelRef(entry.getAsJsonObject("fallback"));
            if (result.isPresent()) {
                return result;
            }
        }
        if (entry.has("cases")) {
            JsonArray cases = entry.getAsJsonArray("cases");
            if (!cases.isEmpty()) {
                JsonObject firstCase = cases.get(0).getAsJsonObject();
                if (firstCase.has("model")) {
                    return extractModelRef(firstCase.getAsJsonObject("model"));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractFromRangeDispatch(JsonObject entry) {
        if (entry.has("fallback")) {
            Optional<String> result = extractModelRef(entry.getAsJsonObject("fallback"));
            if (result.isPresent()) {
                return result;
            }
        }
        if (entry.has("entries")) {
            JsonArray entries = entry.getAsJsonArray("entries");
            if (!entries.isEmpty()) {
                JsonObject firstEntry = entries.get(0).getAsJsonObject();
                if (firstEntry.has("model")) {
                    return extractModelRef(firstEntry.getAsJsonObject("model"));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractFromSpecial(JsonObject entry) {
        // Special models use a base model for the 2D representation
        if (entry.has("base")) {
            return Optional.of(entry.get("base").getAsString());
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Model chain resolution
    // -------------------------------------------------------------------------

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
     */
    private static String toModelPath(String parent) {
        String stripped = stripNamespace(parent);
        return "assets/minecraft/models/" + stripped + ".json";
    }

    /**
     * Strips the {@code minecraft:} namespace prefix from a reference if present.
     */
    private static String stripNamespace(String reference) {
        if (reference.startsWith("minecraft:")) {
            return reference.substring("minecraft:".length());
        }
        return reference;
    }
}
