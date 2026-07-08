package net.aerh.imagegenerator.pack;

import java.util.List;
import java.util.Set;

/**
 * Parsed node tree of a modern (1.21.4+) item model definition. Only node types needed for
 * static GUI rendering are modeled; everything else parses to {@link UnsupportedNode} and fails
 * loudly at resolve time.
 */
public sealed interface ItemModelNode
    permits ItemModelNode.ModelLeaf, ItemModelNode.ConditionNode, ItemModelNode.SelectNode,
    ItemModelNode.RangeDispatchNode, ItemModelNode.CompositeNode, ItemModelNode.UnsupportedNode {

    record ModelLeaf(String modelRef) implements ItemModelNode {
    }

    record ConditionNode(String property, ItemModelNode onTrue, ItemModelNode onFalse) implements ItemModelNode {
    }

    record SelectNode(String property, List<Case> cases, ItemModelNode fallback) implements ItemModelNode {

        public record Case(Set<String> when, ItemModelNode model) {
        }
    }

    record RangeDispatchNode(String property, double scale, List<Entry> entries, ItemModelNode fallback) implements ItemModelNode {

        public record Entry(double threshold, ItemModelNode model) {
        }
    }

    record CompositeNode(List<ItemModelNode> models) implements ItemModelNode {
    }

    record UnsupportedNode(String type) implements ItemModelNode {
    }
}
