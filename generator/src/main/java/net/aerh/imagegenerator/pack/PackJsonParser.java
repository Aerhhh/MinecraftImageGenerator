package net.aerh.imagegenerator.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import lombok.experimental.UtilityClass;
import net.aerh.imagegenerator.exception.PackLoadException;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gson-based parsers for the pack JSON formats used at register time: item model definitions,
 * item models, and texture animation mcmeta.
 */
@UtilityClass
public class PackJsonParser {

    private static final String MINECRAFT_PREFIX = "minecraft:";

    public static ItemModelNode parseItemDefinition(byte[] json) {
        JsonObject root = parseObject(json);
        if (!root.has("model") || !root.get("model").isJsonObject()) {
            throw new PackLoadException("Item definition is missing required 'model' object");
        }
        return parseNode(root.getAsJsonObject("model"));
    }

    private static ItemModelNode parseNode(JsonObject node) {
        String type = normalize(requireString(node, "type"));
        return switch (type) {
            case "model" -> new ItemModelNode.ModelLeaf(requireString(node, "model"));
            case "condition" -> new ItemModelNode.ConditionNode(
                normalize(requireString(node, "property")),
                parseNode(requireObject(node, "on_true")),
                parseNode(requireObject(node, "on_false")));
            case "select" -> parseSelect(node);
            case "range_dispatch" -> parseRangeDispatch(node);
            case "composite" -> parseComposite(node);
            default -> new ItemModelNode.UnsupportedNode(type);
        };
    }

    private static ItemModelNode parseSelect(JsonObject node) {
        String property = normalize(requireString(node, "property"));
        List<ItemModelNode.SelectNode.Case> cases = new ArrayList<>();
        for (JsonElement caseElement : requireArray(node, "cases")) {
            JsonObject caseObject = asObject(caseElement, "select case");
            Set<String> when = new LinkedHashSet<>();
            JsonElement whenElement = caseObject.get("when");
            if (whenElement == null || whenElement.isJsonNull()) {
                throw new PackLoadException("Select case is missing 'when'");
            }
            if (whenElement.isJsonArray()) {
                whenElement.getAsJsonArray().forEach(value -> when.add(asString(value, "select case 'when' value")));
            } else {
                when.add(asString(whenElement, "select case 'when' value"));
            }
            cases.add(new ItemModelNode.SelectNode.Case(Set.copyOf(when), parseNode(requireObject(caseObject, "model"))));
        }
        return new ItemModelNode.SelectNode(property, List.copyOf(cases), parseFallback(node));
    }

    private static ItemModelNode parseRangeDispatch(JsonObject node) {
        String property = normalize(requireString(node, "property"));
        double scale = node.has("scale") ? requireNumber(node, "scale") : 1.0;
        List<ItemModelNode.RangeDispatchNode.Entry> entries = new ArrayList<>();
        if (node.has("entries")) {
            for (JsonElement entryElement : requireArray(node, "entries")) {
                JsonObject entryObject = asObject(entryElement, "range_dispatch entry");
                entries.add(new ItemModelNode.RangeDispatchNode.Entry(
                    requireNumber(entryObject, "threshold"),
                    parseNode(requireObject(entryObject, "model"))));
            }
        }
        return new ItemModelNode.RangeDispatchNode(property, scale, List.copyOf(entries), parseFallback(node));
    }

    private static ItemModelNode parseComposite(JsonObject node) {
        List<ItemModelNode> models = new ArrayList<>();
        for (JsonElement modelElement : requireArray(node, "models")) {
            models.add(parseNode(asObject(modelElement, "composite model")));
        }
        return new ItemModelNode.CompositeNode(List.copyOf(models));
    }

    static JsonObject parseObject(byte[] json) {
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(json), StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(jsonReader);
            if (!element.isJsonObject()) {
                throw new PackLoadException("Expected a JSON object at pack file root");
            }
            // STRICT mode makes peek() throw on any trailing non-whitespace content - no explicit
            // END_DOCUMENT comparison needed. The peek() call itself is load-bearing.
            jsonReader.peek();
            return element.getAsJsonObject();
        } catch (JsonSyntaxException | java.io.IOException | IllegalStateException e) {
            throw new PackLoadException("Malformed pack JSON", e);
        }
    }

    public static ModelInfo parseModel(byte[] json) {
        JsonObject root = parseObject(json);
        String parent = optionalString(root, "parent");
        String layer0 = null;
        if (root.has("textures") && root.get("textures").isJsonObject()) {
            layer0 = optionalString(root.getAsJsonObject("textures"), "layer0");
        }
        return new ModelInfo(parent, layer0);
    }

    public static AnimationMeta parseAnimationMeta(byte[] json) {
        JsonObject root = parseObject(json);
        if (!root.has("animation") || !root.get("animation").isJsonObject()) {
            throw new PackLoadException("Texture mcmeta has no 'animation' section");
        }
        JsonObject animation = root.getAsJsonObject("animation");
        int firstFrameIndex = 0;
        if (animation.has("frames") && animation.get("frames").isJsonArray()) {
            JsonArray frames = animation.getAsJsonArray("frames");
            if (!frames.isEmpty()) {
                firstFrameIndex = firstFrameIndex(frames.get(0));
            }
        }
        Integer width = optionalInt(animation, "width");
        Integer height = optionalInt(animation, "height");
        return new AnimationMeta(firstFrameIndex, width, height);
    }

    private static int firstFrameIndex(JsonElement first) {
        if (first.isJsonObject()) {
            JsonElement index = first.getAsJsonObject().get("index");
            if (index == null || !index.isJsonPrimitive() || !index.getAsJsonPrimitive().isNumber()) {
                throw new PackLoadException("Animation frame entry is missing numeric 'index'");
            }
            return index.getAsInt();
        }
        if (!first.isJsonPrimitive() || !first.getAsJsonPrimitive().isNumber()) {
            throw new PackLoadException("Animation frame entry must be a number or an object with 'index'");
        }
        return first.getAsInt();
    }

    private static ItemModelNode parseFallback(JsonObject node) {
        return node.has("fallback") ? parseNode(requireObject(node, "fallback")) : null;
    }

    private static String normalize(String type) {
        return type.startsWith(MINECRAFT_PREFIX) ? type.substring(MINECRAFT_PREFIX.length()) : type;
    }

    private static String requireString(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new PackLoadException("Item definition node is missing string member '%s'", member);
        }
        return element.getAsString();
    }

    private static double requireNumber(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new PackLoadException("Item definition node is missing numeric member '%s'", member);
        }
        return element.getAsDouble();
    }

    private static String optionalString(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new PackLoadException("Expected string for member '%s'", member);
        }
        return element.getAsString();
    }

    private static Integer optionalInt(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new PackLoadException("Expected number for member '%s'", member);
        }
        return element.getAsInt();
    }

    private static String asString(JsonElement element, String description) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new PackLoadException("Expected string for %s", description);
        }
        return element.getAsString();
    }

    private static JsonObject requireObject(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonObject()) {
            throw new PackLoadException("Item definition node is missing object member '%s'", member);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonArray()) {
            throw new PackLoadException("Item definition node is missing array member '%s'", member);
        }
        return element.getAsJsonArray();
    }

    private static JsonObject asObject(JsonElement element, String description) {
        if (!element.isJsonObject()) {
            throw new PackLoadException("Expected JSON object for %s", description);
        }
        return element.getAsJsonObject();
    }
}
