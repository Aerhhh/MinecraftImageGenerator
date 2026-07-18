package net.aerh.imagegenerator.pack;

import java.util.List;
import java.util.Set;

/**
 * Parsed node tree of a modern (1.21.4+) item model definition. Only node types needed for
 * static GUI rendering are modeled; everything else parses to {@link UnsupportedNode} and fails
 * loudly at resolve time.
 *
 * <p>{@code condition}, {@code select} and {@code range_dispatch} nodes carry their optional
 * {@code index} field (default 0) so {@code custom_model_data} properties can address the
 * matching {@link CustomModelData} list entry at resolve time.
 */
sealed interface ItemModelNode
    permits ItemModelNode.ModelLeaf, ItemModelNode.ConditionNode, ItemModelNode.SelectNode,
    ItemModelNode.RangeDispatchNode, ItemModelNode.CompositeNode, ItemModelNode.UnsupportedNode {

    /**
     * A tint source attached to a model leaf; list entry {@code i} tints element faces declaring
     * {@code tintindex i}. Only the constant, custom_model_data and dye sources are supported;
     * any other type parses to {@link Unsupported} and fails loudly at resolve time.
     */
    sealed interface TintSpec permits TintSpec.Constant, TintSpec.CustomModelDataTint, TintSpec.Dye,
        TintSpec.Unsupported {

        record Constant(int rgb) implements TintSpec {
        }

        /**
         * Reads {@code colors[index]} from the supplied {@link CustomModelData}; a missing entry
         * uses {@code defaultRgb} (white when the JSON declares no {@code default}).
         */
        record CustomModelDataTint(int index, int defaultRgb) implements TintSpec {
        }

        /**
         * The {@code minecraft:dye} source: vanilla reads the item's {@code dyed_color}
         * component and falls back to the REQUIRED {@code default} color. This library carries
         * no per-item dye data, so the default always applies.
         */
        record Dye(int defaultRgb) implements TintSpec {
        }

        record Unsupported(String type) implements TintSpec {
        }
    }

    record ModelLeaf(String modelRef, List<TintSpec> tints) implements ItemModelNode {

        public ModelLeaf(String modelRef) {
            this(modelRef, List.of());
        }
    }

    record ConditionNode(String property, int index, ItemModelNode onTrue, ItemModelNode onFalse) implements ItemModelNode {

        public ConditionNode(String property, ItemModelNode onTrue, ItemModelNode onFalse) {
            this(property, 0, onTrue, onFalse);
        }
    }

    record SelectNode(String property, int index, List<Case> cases, ItemModelNode fallback) implements ItemModelNode {

        public SelectNode(String property, List<Case> cases, ItemModelNode fallback) {
            this(property, 0, cases, fallback);
        }

        public record Case(Set<String> when, ItemModelNode model) {
        }
    }

    /**
     * @param normalize the {@code minecraft:damage} normalization flag (vanilla default true:
     *                  the property reads the 0..1 damage fraction; false reads raw damage).
     *                  Parsed for every range_dispatch but only consulted by the damage property.
     */
    record RangeDispatchNode(String property, int index, double scale, boolean normalize, List<Entry> entries,
                             ItemModelNode fallback) implements ItemModelNode {

        public RangeDispatchNode(String property, int index, double scale, List<Entry> entries, ItemModelNode fallback) {
            this(property, index, scale, true, entries, fallback);
        }

        public RangeDispatchNode(String property, double scale, List<Entry> entries, ItemModelNode fallback) {
            this(property, 0, scale, entries, fallback);
        }

        public record Entry(double threshold, ItemModelNode model) {
        }
    }

    record CompositeNode(List<ItemModelNode> models) implements ItemModelNode {
    }

    record UnsupportedNode(String type) implements ItemModelNode {
    }
}
