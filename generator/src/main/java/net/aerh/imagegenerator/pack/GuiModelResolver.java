package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackResolveException;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves an item definition node tree for a static GUI render (display_context=gui), producing
 * the ordered list of models to composite together with their unevaluated tint sources.
 *
 * <p>Nodes with {@code property: custom_model_data} evaluate against the supplied
 * {@link CustomModelData}: {@code range_dispatch} reads {@code floats[index] * scale} - a missing
 * float reads {@code 0.0f}, because vanilla's range property returns primitive float and
 * structurally cannot signal absence - and picks the greatest threshold at or below the value in
 * FLOAT precision (vanilla stores thresholds, scale and property values as floats; comparing in
 * double would miss exact-equality matches at non-dyadic thresholds like 0.7). Later equal
 * thresholds win, matching vanilla draw-order tie-breaking. {@code select} matches
 * {@code strings[index]} against its cases, and {@code condition} reads {@code flags[index]}
 * (false when missing). Every other boolean property evaluates false and every other numeric
 * property evaluates 0, exactly as before.
 *
 * <p>Tint sources stay UNevaluated on the returned {@link GuiModel}s: the two consuming branches
 * evaluate them with different strictness ({@link #evaluateTints} for elements renders, which
 * fail loudly on unsupported sources; {@link #evaluateTintsLenient} for flat sprite layers, which
 * warn and skip so vanilla-style items carrying dye/potion tint sources keep rendering like they
 * did before tints were parsed at all).
 */
@Slf4j
@UtilityClass
class GuiModelResolver {

    private static final String CUSTOM_MODEL_DATA = "custom_model_data";
    /** The tint applied when a face's tintindex has no tint source: white, a no-op multiply. */
    static final int WHITE = 0xFFFFFF;

    /**
     * One resolved model layer: the model reference plus its raw tint sources (indexed by face
     * {@code tintindex}; evaluate through {@link #evaluateTints} or
     * {@link #evaluateTintsLenient}).
     */
    record GuiModel(String modelRef, List<ItemModelNode.TintSpec> tints) {
    }

    /** Flat-sprite convenience: model refs only, evaluated with no custom model data. */
    public static List<String> resolveGui(ItemModelNode root) {
        return resolveGui(root, CustomModelData.EMPTY).stream().map(GuiModel::modelRef).toList();
    }

    /**
     * Resolves the node tree against the supplied custom model data into the ordered model
     * layers to composite, tint sources unevaluated (see the class javadoc).
     */
    public static List<GuiModel> resolveGui(ItemModelNode root, CustomModelData data) {
        List<GuiModel> models = new ArrayList<>();
        collect(root, data, models);
        return List.copyOf(models);
    }

    private static void collect(ItemModelNode node, CustomModelData data, List<GuiModel> out) {
        switch (node) {
            case ItemModelNode.ModelLeaf leaf -> out.add(new GuiModel(leaf.modelRef(), leaf.tints()));
            case ItemModelNode.ConditionNode condition -> collect(resolveCondition(condition, data), data, out);
            case ItemModelNode.SelectNode select -> collect(resolveSelect(select, data), data, out);
            case ItemModelNode.RangeDispatchNode range -> collect(resolveRangeDispatch(range, data), data, out);
            case ItemModelNode.CompositeNode composite -> composite.models()
                .forEach(child -> collect(child, data, out));
            case ItemModelNode.UnsupportedNode unsupported -> throw new PackResolveException(
                "Item definition uses unsupported node type '%s'", unsupported.type());
        }
    }

    private static ItemModelNode resolveCondition(ItemModelNode.ConditionNode condition, CustomModelData data) {
        if (CUSTOM_MODEL_DATA.equals(condition.property())) {
            boolean flag = condition.index() >= 0 && condition.index() < data.flags().size()
                && data.flags().get(condition.index());
            return flag ? condition.onTrue() : condition.onFalse();
        }
        // Every other boolean property evaluates false for a static GUI render.
        return condition.onFalse();
    }

    private static ItemModelNode resolveSelect(ItemModelNode.SelectNode select, CustomModelData data) {
        if ("display_context".equals(select.property())) {
            for (ItemModelNode.SelectNode.Case selectCase : select.cases()) {
                if (selectCase.when().contains("gui")) {
                    return selectCase.model();
                }
            }
        } else if (CUSTOM_MODEL_DATA.equals(select.property())) {
            String value = select.index() >= 0 && select.index() < data.strings().size()
                ? data.strings().get(select.index()) : null;
            if (value != null) {
                for (ItemModelNode.SelectNode.Case selectCase : select.cases()) {
                    if (selectCase.when().contains(value)) {
                        return selectCase.model();
                    }
                }
            }
        }
        if (select.fallback() != null) {
            return select.fallback();
        }
        throw new PackResolveException("Select on '%s' has no applicable case and no fallback",
            select.property());
    }

    private static ItemModelNode resolveRangeDispatch(ItemModelNode.RangeDispatchNode range, CustomModelData data) {
        // Float precision throughout, matching vanilla exactly: thresholds, scale and property
        // values are all floats in the client, and exact-equality matches (the "threshold 0.7
        // with value 0.7f" case) only work when neither side is widened to double. A missing
        // float reads 0.0f - vanilla's range property returns primitive float, so absence is
        // structurally indistinguishable from zero (this also preserves the pre-wave behavior
        // of evaluating every range_dispatch at 0).
        float value;
        if (CUSTOM_MODEL_DATA.equals(range.property())) {
            float raw = range.index() >= 0 && range.index() < data.floats().size()
                ? data.floats().get(range.index()) : 0.0f;
            value = raw * (float) range.scale();
        } else {
            // Every other numeric property evaluates 0 for a static GUI render.
            value = 0.0f;
        }
        ItemModelNode result = null;
        float best = Float.NEGATIVE_INFINITY;
        for (ItemModelNode.RangeDispatchNode.Entry entry : range.entries()) {
            float threshold = (float) entry.threshold();
            if (threshold <= value && threshold >= best) {
                best = threshold;
                result = entry.model();
            }
        }
        if (result == null) {
            result = range.fallback();
        }
        if (result == null) {
            throw new PackResolveException(
                "Range dispatch on '%s' has no applicable entry and no fallback",
                range.property());
        }
        return result;
    }

    /**
     * Evaluates tint sources to packed {@code 0xRRGGBB} colors for an elements render, failing
     * loudly on unsupported source types (the elements path is new, so no previously-working
     * render regresses).
     *
     * @throws PackResolveException on an unsupported tint source type
     */
    static List<Integer> evaluateTints(List<ItemModelNode.TintSpec> tints, CustomModelData data) {
        return evaluateTints(tints, data, true);
    }

    /**
     * Evaluates tint sources for a flat sprite layer, warning and substituting white (a no-op
     * multiply) for unsupported source types: vanilla-style layer0 items commonly carry
     * dye/potion/map_color sources, and those items rendered fine (untinted) before tint parsing
     * existed - a hard failure here would regress them.
     */
    static List<Integer> evaluateTintsLenient(List<ItemModelNode.TintSpec> tints, CustomModelData data) {
        return evaluateTints(tints, data, false);
    }

    private static List<Integer> evaluateTints(List<ItemModelNode.TintSpec> tints, CustomModelData data,
                                               boolean failOnUnsupported) {
        if (tints.isEmpty()) {
            return List.of();
        }
        List<Integer> colors = new ArrayList<>(tints.size());
        for (ItemModelNode.TintSpec tint : tints) {
            colors.add(switch (tint) {
                case ItemModelNode.TintSpec.Constant constant -> constant.rgb();
                case ItemModelNode.TintSpec.CustomModelDataTint cmd ->
                    cmd.index() >= 0 && cmd.index() < data.colors().size()
                        ? data.colors().get(cmd.index()) & WHITE : cmd.defaultRgb();
                case ItemModelNode.TintSpec.Unsupported unsupported -> {
                    if (failOnUnsupported) {
                        throw new PackResolveException(
                            "Item definition uses unsupported tint source type '%s'", unsupported.type());
                    }
                    log.warn("Ignoring unsupported tint source type '{}' on a flat sprite layer", unsupported.type());
                    yield WHITE;
                }
            });
        }
        return List.copyOf(colors);
    }
}
