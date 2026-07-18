package net.aerh.imagegenerator.impl.scene;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.impl.MinecraftContainerGenerator;
import net.aerh.imagegenerator.impl.MinecraftContainerGenerator.TitleRun;
import net.aerh.imagegenerator.impl.StrictJson;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.AnchorPlacement;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.ArrangementContent;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.ArrangementKind;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.AtPlacement;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.Align;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.ContainerContent;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.Content;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.Edge;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.HudContent;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.ItemContent;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.Placement;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.Region;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.Scene;
import net.aerh.imagegenerator.impl.scene.JsonSceneGenerator.TooltipContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates the declarative scene document (schema v1) into the immutable
 * {@link Scene} model {@link JsonSceneGenerator} renders. Validation is exhaustive and loud:
 * duplicate keys, wrong types, invalid values of known keys, duplicate region names, unknown
 * anchor targets and anchor cycles are all hard rejections whose messages name the offender.
 * Unknown keys anywhere are the one tolerated deviation - they are logged and ignored, so a
 * document authored against a newer schema still renders on an older build.
 *
 * <p>The strict tokenizer and the field accessors come from the shared {@link StrictJson} stack,
 * the same one the menu recipe format uses, so duplicate keys and fractional integers fail the
 * same way in both formats.
 */
final class SceneParser {

    /** The scene schema version this build understands. Newer documents are rejected. */
    static final int SCHEMA_VERSION = 1;

    private static final String SCENE = "scene";
    private static final Set<String> ROOT_KEYS = Set.of("schema_version", "scale", "margin", "regions");
    private static final Set<String> TOP_BASE_KEYS = Set.of("name", "type", "z", "at", "anchor");
    private static final Set<String> NESTED_BASE_KEYS = Set.of("name", "type");
    private static final Set<String> ANCHOR_KEYS = Set.of("to", "edge", "align", "offset");
    private static final Set<String> RUN_KEYS = Set.of("text", "font", "color", "bold", "italic");
    private static final Set<String> TOOLTIP_KEYS = Set.of("nbt", "rarity", "lore", "max_line_length");
    private static final Set<String> ITEM_KEYS = Set.of("item", "model", "custom_model_data", "enchanted", "animated_textures");
    private static final Set<String> CONTAINER_KEYS = Set.of("recipe", "animated_textures");
    private static final Set<String> HUD_KEYS = Set.of("lines", "gui_width", "text_shadow");
    private static final Set<String> ARRANGEMENT_KEYS = Set.of("regions", "spacing", "alignment");
    private static final Set<String> GRID_KEYS = Set.of("regions", "spacing", "alignment", "columns", "cell");

    private SceneParser() {
    }

    /**
     * Parses one scene document.
     *
     * @param json the scene document text
     *
     * @return the validated scene model
     * @throws IllegalArgumentException on any schema violation, with a message naming the offender
     */
    static Scene parse(String json) {
        JsonObject root = StrictJson.parseObject(json, SCENE);
        StrictJson.warnUnknownKeys(root, ROOT_KEYS, SCENE);

        if (!root.has("schema_version")) {
            throw new IllegalArgumentException("Scene requires `schema_version`");
        }
        int version = StrictJson.requireInt(root, "schema_version");
        if (version < 1) {
            throw new IllegalArgumentException("Scene `schema_version` must be at least 1, got: " + version);
        }
        if (version > SCHEMA_VERSION) {
            throw new IllegalArgumentException("Scene `schema_version` " + version
                + " is newer than this build supports (max " + SCHEMA_VERSION + ")");
        }

        int scale = 1;
        if (root.has("scale")) {
            scale = StrictJson.requireInt(root, "scale");
            if (scale < 1) {
                throw new IllegalArgumentException("Scene `scale` must be at least 1, got: " + scale);
            }
        }

        int margin = 0;
        if (root.has("margin")) {
            margin = StrictJson.requireInt(root, "margin");
            if (margin < 0) {
                throw new IllegalArgumentException("Scene `margin` must not be negative, got: " + margin);
            }
        }

        if (!root.has("regions")) {
            throw new IllegalArgumentException("Scene requires `regions`");
        }
        JsonElement regionsElement = root.get("regions");
        if (!regionsElement.isJsonArray()) {
            throw new IllegalArgumentException("Scene `regions` must be a non-empty array");
        }
        JsonArray regionsArray = regionsElement.getAsJsonArray();
        if (regionsArray.isEmpty()) {
            throw new IllegalArgumentException("Scene `regions` must not be empty");
        }

        Set<String> allNames = new HashSet<>();
        List<Region> regions = new ArrayList<>(regionsArray.size());
        for (JsonElement element : regionsArray) {
            regions.add(parseRegion(element, false, allNames));
        }

        validateAnchors(regions);
        return new Scene(scale, margin, regions);
    }

    private static Region parseRegion(JsonElement element, boolean nested, Set<String> allNames) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Each region must be a JSON object, got: " + element);
        }
        JsonObject object = element.getAsJsonObject();
        String name = StrictJson.requireString(object, "name", "Region");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Region `name` must not be blank");
        }
        if (!allNames.add(name)) {
            throw new IllegalArgumentException("Duplicate region name `" + name + "`");
        }
        String type = StrictJson.requireString(object, "type", "Region `" + name + "`");

        int z = 0;
        Placement placement = null;
        if (nested) {
            if (object.has("z") || object.has("at") || object.has("anchor")) {
                throw new IllegalArgumentException(
                    "Nested region `" + name + "` must not declare `at`, `anchor`, or `z`");
            }
        } else {
            if (object.has("z")) {
                z = StrictJson.requireInt(object, "z");
            }
            placement = parsePlacement(object, name);
        }

        ParsedContent parsed = parseContent(type, object, name, allNames);

        Set<String> allowed = new HashSet<>(nested ? NESTED_BASE_KEYS : TOP_BASE_KEYS);
        allowed.addAll(parsed.contentKeys());
        StrictJson.warnUnknownKeys(object, allowed, "region `" + name + "`");

        return new Region(name, z, placement, parsed.content());
    }

    private static Placement parsePlacement(JsonObject object, String name) {
        boolean hasAt = object.has("at");
        boolean hasAnchor = object.has("anchor");
        if (hasAt && hasAnchor) {
            throw new IllegalArgumentException("Region `" + name + "` declares both `at` and `anchor`");
        }
        if (hasAt) {
            int[] xy = requireIntPair(object.get("at"), "Region `" + name + "` `at`");
            return new AtPlacement(xy[0], xy[1]);
        }
        if (hasAnchor) {
            return parseAnchor(object.get("anchor"), name);
        }
        return new AtPlacement(0, 0);
    }

    private static AnchorPlacement parseAnchor(JsonElement element, String name) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Region `" + name + "` `anchor` must be an object");
        }
        JsonObject anchor = element.getAsJsonObject();
        String context = "Region `" + name + "` anchor";
        String target = StrictJson.requireString(anchor, "to", context);
        Edge edge = parseEdge(StrictJson.requireString(anchor, "edge", context), name);
        Align align = anchor.has("align")
            ? parseAlign(StrictJson.requireString(anchor, "align", context), name, "align")
            : Align.START;
        int dx = 0;
        int dy = 0;
        if (anchor.has("offset")) {
            int[] offset = requireIntPair(anchor.get("offset"), context + " `offset`");
            dx = offset[0];
            dy = offset[1];
        }
        StrictJson.warnUnknownKeys(anchor, ANCHOR_KEYS, context);
        return new AnchorPlacement(target, edge, align, dx, dy);
    }

    private static Edge parseEdge(String value, String name) {
        return switch (value) {
            case "left" -> Edge.LEFT;
            case "right" -> Edge.RIGHT;
            case "top" -> Edge.TOP;
            case "bottom" -> Edge.BOTTOM;
            default -> throw new IllegalArgumentException("Region `" + name
                + "` anchor `edge` must be one of left, right, top, bottom, got: " + value);
        };
    }

    private static Align parseAlign(String value, String name, String field) {
        return switch (value) {
            case "start" -> Align.START;
            case "center" -> Align.CENTER;
            case "end" -> Align.END;
            default -> throw new IllegalArgumentException("Region `" + name
                + "` `" + field + "` must be one of start, center, end, got: " + value);
        };
    }

    private static ParsedContent parseContent(String type, JsonObject object, String name, Set<String> allNames) {
        return switch (type) {
            case "tooltip" -> parseTooltip(object, name);
            case "item" -> parseItem(object, name);
            case "container" -> parseContainer(object, name);
            case "hud" -> parseHud(object, name);
            case "row" -> parseArrangement(ArrangementKind.ROW, object, name, allNames, false);
            case "column" -> parseArrangement(ArrangementKind.COLUMN, object, name, allNames, false);
            case "grid" -> parseArrangement(ArrangementKind.GRID, object, name, allNames, true);
            default -> throw new IllegalArgumentException("Region `" + name + "` has unknown type `" + type
                + "` (expected tooltip, item, container, hud, row, column, or grid)");
        };
    }

    private static ParsedContent parseTooltip(JsonObject object, String name) {
        String context = "Tooltip region `" + name + "`";
        boolean hasNbt = object.has("nbt");
        boolean hasSimple = object.has("rarity") || object.has("lore") || object.has("max_line_length");
        if (hasNbt && hasSimple) {
            throw new IllegalArgumentException(context + " mixes `nbt` with simple fields; use one or the other");
        }
        if (hasNbt) {
            JsonElement nbt = object.get("nbt");
            if (!nbt.isJsonObject()) {
                throw new IllegalArgumentException(context + " `nbt` must be an object");
            }
            return new ParsedContent(new TooltipContent(nbt.getAsJsonObject(), null, null, null, null), TOOLTIP_KEYS);
        }

        String rarity = null;
        if (object.has("rarity")) {
            rarity = StrictJson.requireString(object, "rarity", context);
            if (Rarity.byName(rarity) == null) {
                throw new IllegalArgumentException(context + " has unknown rarity `" + rarity + "`");
            }
        }
        String lore = object.has("lore") ? StrictJson.requireString(object, "lore", context) : null;
        Integer maxLineLength = null;
        if (object.has("max_line_length")) {
            maxLineLength = StrictJson.requireInt(object, "max_line_length");
            if (maxLineLength < 1) {
                throw new IllegalArgumentException(context + " `max_line_length` must be at least 1, got: " + maxLineLength);
            }
        }
        // The region name doubles as the tooltip title (the region already names itself uniquely).
        return new ParsedContent(new TooltipContent(null, name, rarity, lore, maxLineLength), TOOLTIP_KEYS);
    }

    private static ParsedContent parseItem(JsonObject object, String name) {
        String context = "Item region `" + name + "`";
        boolean hasItem = object.has("item");
        boolean hasModel = object.has("model");
        if (hasItem && hasModel) {
            throw new IllegalArgumentException(context + " sets both `item` and `model`; use one");
        }
        if (!hasItem && !hasModel) {
            throw new IllegalArgumentException(context + " requires `item` or `model`");
        }
        String item = null;
        String model = null;
        if (hasItem) {
            item = StrictJson.requireString(object, "item", context);
            if (item.isBlank()) {
                throw new IllegalArgumentException(context + " `item` must not be blank");
            }
        } else {
            model = StrictJson.requireString(object, "model", context);
            if (model.isBlank()) {
                throw new IllegalArgumentException(context + " `model` must not be blank");
            }
        }
        Integer customModelData = object.has("custom_model_data") ? StrictJson.requireInt(object, "custom_model_data") : null;
        boolean enchanted = StrictJson.requireBoolean(object, "enchanted", context);
        boolean animatedTextures = StrictJson.requireBoolean(object, "animated_textures", context);
        return new ParsedContent(new ItemContent(item, model, customModelData, enchanted, animatedTextures), ITEM_KEYS);
    }

    private static ParsedContent parseContainer(JsonObject object, String name) {
        String context = "Container region `" + name + "`";
        if (!object.has("recipe")) {
            throw new IllegalArgumentException(context + " requires `recipe`");
        }
        JsonElement recipe = object.get("recipe");
        if (!recipe.isJsonObject()) {
            throw new IllegalArgumentException(context + " `recipe` must be an object");
        }
        String recipeJson = new Gson().toJson(recipe);
        // Validate eagerly so a malformed recipe fails at construction (the recipe parser names
        // the offending recipe key; we add the region for context).
        try {
            MinecraftContainerGenerator.fromRecipe(recipeJson);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(context + " " + exception.getMessage(), exception);
        }
        boolean animatedTextures = StrictJson.requireBoolean(object, "animated_textures", context);
        return new ParsedContent(new ContainerContent(recipeJson, animatedTextures), CONTAINER_KEYS);
    }

    private static ParsedContent parseHud(JsonObject object, String name) {
        String context = "Hud region `" + name + "`";
        if (!object.has("lines")) {
            throw new IllegalArgumentException(context + " requires `lines`");
        }
        JsonElement linesElement = object.get("lines");
        if (!linesElement.isJsonArray()) {
            throw new IllegalArgumentException(context + " `lines` must be an array");
        }
        JsonArray linesArray = linesElement.getAsJsonArray();
        if (linesArray.isEmpty()) {
            throw new IllegalArgumentException(context + " `lines` must not be empty");
        }
        List<List<TitleRun>> lines = new ArrayList<>(linesArray.size());
        for (JsonElement lineElement : linesArray) {
            if (!lineElement.isJsonArray()) {
                throw new IllegalArgumentException(context + " each line must be an array of run objects");
            }
            List<TitleRun> runs = new ArrayList<>();
            for (JsonElement runElement : lineElement.getAsJsonArray()) {
                runs.add(parseRun(runElement, context));
            }
            lines.add(runs);
        }

        Integer guiWidth = null;
        if (object.has("gui_width")) {
            guiWidth = StrictJson.requireInt(object, "gui_width");
            if (guiWidth < 1) {
                throw new IllegalArgumentException(context + " `gui_width` must be at least 1, got: " + guiWidth);
            }
        }
        Boolean textShadow = object.has("text_shadow")
            ? StrictJson.requireBoolean(object, "text_shadow", context)
            : null;
        return new ParsedContent(new HudContent(lines, guiWidth, textShadow), HUD_KEYS);
    }

    private static TitleRun parseRun(JsonElement element, String context) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(context + " run must be an object, got: " + element);
        }
        JsonObject run = element.getAsJsonObject();
        String runContext = context + " run";
        // Unknown keys are tolerated in the scene format (logged, not rejected); the run's fields
        // are then validated by the same parser the recipe title format uses.
        StrictJson.warnUnknownKeys(run, RUN_KEYS, runContext);
        return MinecraftContainerGenerator.parseRunFields(run, runContext);
    }

    private static ParsedContent parseArrangement(ArrangementKind kind, JsonObject object, String name,
                                                  Set<String> allNames, boolean grid) {
        String context = (grid ? "Grid" : kind == ArrangementKind.ROW ? "Row" : "Column") + " region `" + name + "`";
        if (!object.has("regions")) {
            throw new IllegalArgumentException(context + " requires `regions`");
        }
        JsonElement regionsElement = object.get("regions");
        if (!regionsElement.isJsonArray()) {
            throw new IllegalArgumentException(context + " `regions` must be a non-empty array");
        }
        JsonArray regionsArray = regionsElement.getAsJsonArray();
        if (regionsArray.isEmpty()) {
            throw new IllegalArgumentException(context + " `regions` must not be empty");
        }
        List<Region> nested = new ArrayList<>(regionsArray.size());
        for (JsonElement element : regionsArray) {
            nested.add(parseRegion(element, true, allNames));
        }

        int spacing = 6;
        if (object.has("spacing")) {
            spacing = StrictJson.requireInt(object, "spacing");
            if (spacing < 0) {
                throw new IllegalArgumentException(context + " `spacing` must not be negative, got: " + spacing);
            }
        }
        Align alignment = object.has("alignment")
            ? parseAlign(StrictJson.requireString(object, "alignment", context), name, "alignment")
            : Align.CENTER;

        int columns = 0;
        boolean slotCell = false;
        if (grid) {
            if (!object.has("columns")) {
                throw new IllegalArgumentException(context + " requires `columns`");
            }
            columns = StrictJson.requireInt(object, "columns");
            if (columns < 1) {
                throw new IllegalArgumentException(context + " `columns` must be at least 1, got: " + columns);
            }
            if (object.has("cell")) {
                String cell = StrictJson.requireString(object, "cell", context);
                if (!cell.equals("slot")) {
                    throw new IllegalArgumentException(context + " `cell` must be \"slot\", got: " + cell);
                }
                slotCell = true;
            }
        }
        return new ParsedContent(
            new ArrangementContent(kind, nested, spacing, alignment, columns, slotCell),
            grid ? GRID_KEYS : ARRANGEMENT_KEYS);
    }

    /**
     * Validates the top-level anchor graph: every anchor target must be a top-level region and the
     * anchor pointers must not form a cycle. Both are hard rejections naming the region.
     */
    private static void validateAnchors(List<Region> regions) {
        Set<String> topLevelNames = new HashSet<>();
        for (Region region : regions) {
            topLevelNames.add(region.name());
        }
        Map<String, String> anchorTarget = new HashMap<>();
        for (Region region : regions) {
            if (region.placement() instanceof AnchorPlacement anchor) {
                if (!topLevelNames.contains(anchor.target())) {
                    throw new IllegalArgumentException(
                        "Region `" + region.name() + "` anchors to unknown region `" + anchor.target() + "`");
                }
                anchorTarget.put(region.name(), anchor.target());
            }
        }
        // The graph is functional (each node has at most one outgoing edge), so revisiting a node
        // while following pointers is a cycle.
        for (String start : anchorTarget.keySet()) {
            Set<String> seen = new HashSet<>();
            String current = start;
            while (current != null && anchorTarget.containsKey(current)) {
                if (!seen.add(current)) {
                    throw new IllegalArgumentException("Anchor cycle detected at region `" + current + "`");
                }
                current = anchorTarget.get(current);
            }
        }
    }

    private static int[] requireIntPair(JsonElement element, String context) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 2) {
            throw new IllegalArgumentException(context + " must be an [x, y] integer pair");
        }
        JsonArray array = element.getAsJsonArray();
        return new int[]{requireIntElement(array.get(0), context), requireIntElement(array.get(1), context)};
    }

    private static int requireIntElement(JsonElement element, String context) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(context + " must be an [x, y] integer pair");
        }
        double value = element.getAsDouble();
        int intValue = (int) value;
        if (intValue != value) {
            throw new IllegalArgumentException(context + " must be an [x, y] integer pair, got: " + value);
        }
        return intValue;
    }

    /** A parsed region content together with the content-specific keys allowed on its region object. */
    private record ParsedContent(Content content, Set<String> contentKeys) {
    }
}
