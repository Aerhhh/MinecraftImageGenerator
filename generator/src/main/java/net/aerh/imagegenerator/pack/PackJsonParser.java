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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gson-based parsers for the pack JSON formats used at register time: item model definitions,
 * item models, and texture animation mcmeta.
 */
@UtilityClass
class PackJsonParser {

    private static final String MINECRAFT_PREFIX = "minecraft:";

    public static ItemModelNode parseItemDefinition(byte[] json) {
        return parseItemDefinitionInfo(json).model();
    }

    public static ItemDefinition parseItemDefinitionInfo(byte[] json) {
        JsonObject root = parseObject(json);
        if (!root.has("model") || !root.get("model").isJsonObject()) {
            throw new PackLoadException("Item definition is missing required 'model' object");
        }
        return new ItemDefinition(parseNode(root.getAsJsonObject("model")),
            optionalBoolean(root, "oversized_in_gui"));
    }

    private static ItemModelNode parseNode(JsonObject node) {
        String type = normalize(requireString(node, "type"));
        return switch (type) {
            case "model" -> new ItemModelNode.ModelLeaf(requireString(node, "model"), parseTints(node));
            case "condition" -> new ItemModelNode.ConditionNode(
                normalize(requireString(node, "property")),
                optionalIndex(node),
                parseNode(requireObject(node, "on_true")),
                parseNode(requireObject(node, "on_false")));
            case "select" -> parseSelect(node);
            case "range_dispatch" -> parseRangeDispatch(node);
            case "composite" -> parseComposite(node);
            default -> new ItemModelNode.UnsupportedNode(type);
        };
    }

    /** The optional {@code index} field of custom_model_data dispatch nodes, default 0. */
    private static int optionalIndex(JsonObject node) {
        return node.has("index") ? requireIntegralInt(node, "index", "Item definition node") : 0;
    }

    private static List<ItemModelNode.TintSpec> parseTints(JsonObject node) {
        if (!node.has("tints")) {
            return List.of();
        }
        List<ItemModelNode.TintSpec> tints = new ArrayList<>();
        for (JsonElement tintElement : requireArray(node, "tints")) {
            JsonObject tint = asObject(tintElement, "tint source");
            String type = normalize(requireString(tint, "type", "Tint source"));
            tints.add(switch (type) {
                case "constant" -> new ItemModelNode.TintSpec.Constant(requireColor(tint, "value"));
                case "custom_model_data" -> new ItemModelNode.TintSpec.CustomModelDataTint(
                    optionalIndex(tint),
                    tint.has("default") ? requireColor(tint, "default") : GuiModelResolver.WHITE);
                // The dye default is REQUIRED per the vanilla format; requireColor fails loudly
                // when it is absent.
                case "dye" -> new ItemModelNode.TintSpec.Dye(requireColor(tint, "default"));
                default -> new ItemModelNode.TintSpec.Unsupported(type);
            });
        }
        return List.copyOf(tints);
    }

    /**
     * A tint color value: either a packed RGB integer or an {@code [r, g, b]} array of floats in
     * 0..1 (the two shapes the vanilla format accepts). Returns packed {@code 0xRRGGBB}.
     */
    private static int requireColor(JsonObject node, String member) {
        JsonElement element = node.get(member);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt() & 0xFFFFFF;
        }
        if (element != null && element.isJsonArray()) {
            JsonArray channels = element.getAsJsonArray();
            if (channels.size() != 3) {
                throw new PackLoadException("Tint color '%s' array must have exactly 3 channels", member);
            }
            int rgb = 0;
            for (int i = 0; i < 3; i++) {
                JsonElement channel = channels.get(i);
                if (!channel.isJsonPrimitive() || !channel.getAsJsonPrimitive().isNumber()) {
                    throw new PackLoadException("Tint color '%s' channels must be numbers", member);
                }
                float value = channel.getAsFloat();
                int scaled = Math.clamp(Math.round(value * 255.0f), 0, 255);
                rgb = (rgb << 8) | scaled;
            }
            return rgb;
        }
        throw new PackLoadException("Tint color '%s' must be an integer or an [r, g, b] float array", member);
    }

    private static ItemModelNode parseSelect(JsonObject node) {
        String property = normalize(requireString(node, "property"));
        int index = optionalIndex(node);
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
        return new ItemModelNode.SelectNode(property, index, List.copyOf(cases), parseFallback(node));
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
        // normalize defaults TRUE, the vanilla minecraft:damage default (damage fraction).
        return new ItemModelNode.RangeDispatchNode(property, optionalIndex(node), scale,
            optionalBoolean(node, "normalize", true), List.copyOf(entries), parseFallback(node));
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
        Map<String, String> textures = new LinkedHashMap<>();
        if (root.has("textures") && root.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("textures").entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || value.isJsonNull()) {
                    continue;
                }
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    // Only layer0 has always been validated (pre-elements behavior); other
                    // non-string entries are skipped so a model that previously loaded keeps
                    // loading - a face referencing the skipped key still fails loudly at
                    // resolve time with an undefined-texture-key error.
                    if ("layer0".equals(entry.getKey())) {
                        throw new PackLoadException("Expected string for member '%s'", entry.getKey());
                    }
                    continue;
                }
                textures.put(entry.getKey(), value.getAsString());
            }
        }
        List<ModelElement> elements = root.has("elements") ? parseElements(root) : null;
        return new ModelInfo(parent, textures.get("layer0"), Map.copyOf(textures), elements,
            parseDisplayGui(root), parseGuiLight(root));
    }

    private static List<ModelElement> parseElements(JsonObject root) {
        JsonElement elementsElement = root.get("elements");
        if (!elementsElement.isJsonArray()) {
            throw new PackLoadException("Model 'elements' must be an array");
        }
        List<ModelElement> elements = new ArrayList<>();
        for (JsonElement element : elementsElement.getAsJsonArray()) {
            elements.add(parseElement(asObject(element, "model element")));
        }
        return List.copyOf(elements);
    }

    private static ModelElement parseElement(JsonObject element) {
        float[] from = requireVector3(element, "from", "Model element");
        float[] to = requireVector3(element, "to", "Model element");
        for (int i = 0; i < 3; i++) {
            if (from[i] < -16 || from[i] > 32 || to[i] < -16 || to[i] > 32) {
                throw new PackLoadException("Model element coordinates must be within -16..32");
            }
        }
        ModelElement.Rotation rotation = null;
        if (element.has("rotation")) {
            JsonElement rotationElement = element.get("rotation");
            if (!rotationElement.isJsonObject()) {
                throw new PackLoadException("Model element 'rotation' must be an object");
            }
            rotation = parseElementRotation(rotationElement.getAsJsonObject());
        }
        boolean shade = optionalBoolean(element, "shade", true);
        Map<ModelElement.Direction, ModelElement.Face> faces = new EnumMap<>(ModelElement.Direction.class);
        if (element.has("faces")) {
            JsonElement facesElement = element.get("faces");
            if (!facesElement.isJsonObject()) {
                throw new PackLoadException("Model element 'faces' must be an object");
            }
            for (Map.Entry<String, JsonElement> faceEntry : facesElement.getAsJsonObject().entrySet()) {
                ModelElement.Direction direction;
                try {
                    direction = ModelElement.Direction.valueOf(faceEntry.getKey().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new PackLoadException("Unknown model element face '%s'", faceEntry.getKey());
                }
                faces.put(direction, parseFace(asObject(faceEntry.getValue(), "element face")));
            }
        }
        return new ModelElement(from[0], from[1], from[2], to[0], to[1], to[2],
            rotation, shade, Map.copyOf(faces));
    }

    /**
     * Parses an element rotation entry with vanilla validation: {@code angle} any finite angle
     * in degrees (modern vanilla lifted the legacy 22.5-degree-step whitelist in 1.21.6, the
     * client family whose packs this library targets), {@code axis} one of
     * {@code x}/{@code y}/{@code z} (lowercase, like vanilla's axis-by-name lookup),
     * {@code origin} a required 3-vector in model units and {@code rescale} an optional boolean
     * defaulting to false.
     */
    private static ModelElement.Rotation parseElementRotation(JsonObject rotation) {
        float angle = (float) requireNumber(rotation, "angle", "Element rotation");
        if (!Float.isFinite(angle)) {
            throw new PackLoadException(
                "Element rotation angle must be a finite number, got %s", String.valueOf(angle));
        }
        String axisName = requireString(rotation, "axis", "Element rotation");
        ModelElement.Axis axis = switch (axisName) {
            case "x" -> ModelElement.Axis.X;
            case "y" -> ModelElement.Axis.Y;
            case "z" -> ModelElement.Axis.Z;
            default -> throw new PackLoadException("Element rotation axis must be 'x', 'y' or 'z', got '%s'", axisName);
        };
        float[] origin = requireVector3(rotation, "origin", "Element rotation");
        return new ModelElement.Rotation(angle, axis, origin[0], origin[1], origin[2],
            optionalBoolean(rotation, "rescale"));
    }

    private static ModelElement.Face parseFace(JsonObject face) {
        ModelElement.FaceUv uv = null;
        if (face.has("uv")) {
            float[] values = requireVector(face, "uv", 4, "Element face");
            uv = new ModelElement.FaceUv(values[0], values[1], values[2], values[3]);
        }
        int rotation = 0;
        if (face.has("rotation")) {
            rotation = requireIntegralInt(face, "rotation", "Element face");
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                throw new PackLoadException("Element face rotation must be 0, 90, 180 or 270, got %s",
                    String.valueOf(rotation));
            }
        }
        int tintIndex = face.has("tintindex") ? requireIntegralInt(face, "tintindex", "Element face") : -1;
        return new ModelElement.Face(uv, requireString(face, "texture", "Element face"), rotation, tintIndex);
    }

    /**
     * The model's own {@code display.gui} entry, or null when the model declares none (the
     * entry then inherits from the parent chain as a whole, per vanilla).
     */
    private static GuiTransform parseDisplayGui(JsonObject root) {
        if (!root.has("display")) {
            return null;
        }
        JsonElement displayElement = root.get("display");
        if (!displayElement.isJsonObject()) {
            throw new PackLoadException("Model 'display' must be an object");
        }
        JsonObject display = displayElement.getAsJsonObject();
        if (!display.has("gui")) {
            return null;
        }
        JsonElement guiElement = display.get("gui");
        if (!guiElement.isJsonObject()) {
            throw new PackLoadException("Model 'display.gui' must be an object");
        }
        JsonObject gui = guiElement.getAsJsonObject();
        float[] rotation = optionalVector3(gui, "rotation", 0);
        float[] translation = optionalVector3(gui, "translation", 0);
        float[] scale = optionalVector3(gui, "scale", 1);
        return new GuiTransform(rotation[0], rotation[1], rotation[2],
            translation[0], translation[1], translation[2],
            scale[0], scale[1], scale[2]);
    }

    private static String parseGuiLight(JsonObject root) {
        String guiLight = optionalString(root, "gui_light");
        if (guiLight != null && !guiLight.equals("front") && !guiLight.equals("side")) {
            throw new PackLoadException("Model 'gui_light' must be 'front' or 'side', got '%s'", guiLight);
        }
        return guiLight;
    }

    private static float[] optionalVector3(JsonObject node, String member, float defaultValue) {
        if (!node.has(member)) {
            return new float[]{defaultValue, defaultValue, defaultValue};
        }
        return requireVector(node, member, 3, "Display transform");
    }

    private static float[] requireVector3(JsonObject node, String member, String owner) {
        return requireVector(node, member, 3, owner);
    }

    private static float[] requireVector(JsonObject node, String member, int length, String owner) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != length) {
            throw new PackLoadException("%s member '%s' must be an array of %s numbers",
                owner, member, String.valueOf(length));
        }
        float[] values = new float[length];
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < length; i++) {
            JsonElement value = array.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new PackLoadException("%s member '%s' must contain only numbers", owner, member);
            }
            values[i] = value.getAsFloat();
        }
        return values;
    }

    public static McMeta parseMcmeta(byte[] json) {
        JsonObject root = parseObject(json);
        AnimationMeta animation = root.has("animation") ? parseAnimationSection(root) : null;
        return new McMeta(animation, parseGuiSection(root));
    }

    public static AnimationMeta parseAnimationMeta(byte[] json) {
        return parseAnimationSection(parseObject(json));
    }

    private static AnimationMeta parseAnimationSection(JsonObject root) {
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

    private static GuiScaling parseGuiSection(JsonObject root) {
        if (!root.has("gui")) {
            return null;
        }
        JsonElement guiElement = root.get("gui");
        if (!guiElement.isJsonObject()) {
            throw new PackLoadException("Texture mcmeta 'gui' section must be an object");
        }
        JsonObject gui = guiElement.getAsJsonObject();
        if (!gui.has("scaling")) {
            return new GuiScaling.Stretch();
        }
        JsonElement scalingElement = gui.get("scaling");
        if (!scalingElement.isJsonObject()) {
            throw new PackLoadException("Texture mcmeta 'gui.scaling' must be an object");
        }
        JsonObject scaling = scalingElement.getAsJsonObject();
        String type = normalize(requireScalingString(scaling));
        try {
            return switch (type) {
                case "stretch" -> new GuiScaling.Stretch();
                case "tile" -> new GuiScaling.Tile(
                    requireScalingInt(scaling, "width"),
                    requireScalingInt(scaling, "height"));
                case "nine_slice" -> new GuiScaling.NineSlice(
                    requireScalingInt(scaling, "width"),
                    requireScalingInt(scaling, "height"),
                    parseNineSliceBorder(scaling),
                    optionalBoolean(scaling, "stretch_inner"));
                default -> throw new PackLoadException("Unsupported gui scaling type '%s'", type);
            };
        } catch (IllegalArgumentException e) {
            throw new PackLoadException("Invalid gui scaling values: " + e.getMessage(), e);
        }
    }

    private static GuiScaling.NineSlice.Border parseNineSliceBorder(JsonObject scaling) {
        JsonElement element = scaling.get("border");
        if (element == null || element.isJsonNull()) {
            throw new PackLoadException("nine_slice gui scaling is missing 'border'");
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            int uniform = integralInt(element, "border", "gui scaling");
            return new GuiScaling.NineSlice.Border(uniform, uniform, uniform, uniform);
        }
        if (element.isJsonObject()) {
            JsonObject border = element.getAsJsonObject();
            return new GuiScaling.NineSlice.Border(
                requireScalingInt(border, "left"),
                requireScalingInt(border, "top"),
                requireScalingInt(border, "right"),
                requireScalingInt(border, "bottom"));
        }
        throw new PackLoadException("nine_slice 'border' must be a number or an object with left/top/right/bottom");
    }

    private static String requireScalingString(JsonObject scaling) {
        JsonElement element = scaling.get("type");
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new PackLoadException("gui scaling is missing string member 'type'");
        }
        return element.getAsString();
    }

    private static int requireScalingInt(JsonObject node, String member) {
        return requireIntegralInt(node, member, "gui scaling");
    }

    /**
     * Requires a numeric member with an exactly integral value; {@code owner} names the JSON
     * shape (e.g. {@code "gui scaling"}, {@code "Font provider"}) for the diagnostic. Shared
     * across the pack parsers so numeric validation and messages stay consistent.
     */
    static int requireIntegralInt(JsonObject node, String member, String owner) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new PackLoadException("%s is missing numeric member '%s'", owner, member);
        }
        return integralInt(element, member, owner);
    }

    private static int integralInt(JsonElement element, String member, String owner) {
        double value = element.getAsDouble();
        int intValue = (int) value;
        if (value != intValue) {
            throw new PackLoadException("%s member '%s' must be an integer, got %s", owner, member, String.valueOf(value));
        }
        return intValue;
    }

    private static boolean optionalBoolean(JsonObject node, String member) {
        return optionalBoolean(node, member, false);
    }

    private static boolean optionalBoolean(JsonObject node, String member, boolean defaultValue) {
        JsonElement element = node.get(member);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new PackLoadException("Expected boolean for member '%s'", member);
        }
        return element.getAsBoolean();
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

    /**
     * Strips the optional {@code minecraft:} prefix from a type discriminator. Shared across the
     * pack parsers (fonts included) so prefix handling never drifts.
     */
    static String normalize(String type) {
        return type.startsWith(MINECRAFT_PREFIX) ? type.substring(MINECRAFT_PREFIX.length()) : type;
    }

    private static String requireString(JsonObject node, String member) {
        return requireString(node, member, "Item definition node");
    }

    /**
     * Requires a string member; {@code owner} names the JSON shape (e.g.
     * {@code "Item definition node"}, {@code "Font provider"}) for the diagnostic. Shared across
     * the pack parsers so string validation and messages stay consistent.
     */
    static String requireString(JsonObject node, String member, String owner) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new PackLoadException("%s is missing string member '%s'", owner, member);
        }
        return element.getAsString();
    }

    private static double requireNumber(JsonObject node, String member) {
        return requireNumber(node, member, "Item definition node");
    }

    private static double requireNumber(JsonObject node, String member, String owner) {
        JsonElement element = node.get(member);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new PackLoadException("%s is missing numeric member '%s'", owner, member);
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
