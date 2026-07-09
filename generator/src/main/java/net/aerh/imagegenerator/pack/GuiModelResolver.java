package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;
import net.aerh.imagegenerator.exception.PackResolveException;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves an item definition node tree for a static GUI render (display_context=gui, all boolean
 * properties false, numeric properties 0), producing the ordered list of model refs to composite.
 */
@UtilityClass
class GuiModelResolver {

    public static List<String> resolveGui(ItemModelNode root) {
        List<String> modelRefs = new ArrayList<>();
        collect(root, modelRefs);
        return List.copyOf(modelRefs);
    }

    private static void collect(ItemModelNode node, List<String> out) {
        switch (node) {
            case ItemModelNode.ModelLeaf leaf -> out.add(leaf.modelRef());
            case ItemModelNode.ConditionNode condition -> collect(condition.onFalse(), out);
            case ItemModelNode.SelectNode select -> collect(resolveSelect(select), out);
            case ItemModelNode.RangeDispatchNode range -> collect(resolveRangeDispatch(range), out);
            case ItemModelNode.CompositeNode composite -> composite.models()
                .forEach(child -> collect(child, out));
            case ItemModelNode.UnsupportedNode unsupported -> throw new PackResolveException(
                "Item definition uses unsupported node type '%s'", unsupported.type());
        }
    }

    private static ItemModelNode resolveSelect(ItemModelNode.SelectNode select) {
        if ("display_context".equals(select.property())) {
            for (ItemModelNode.SelectNode.Case selectCase : select.cases()) {
                if (selectCase.when().contains("gui")) {
                    return selectCase.model();
                }
            }
        }
        if (select.fallback() != null) {
            return select.fallback();
        }
        throw new PackResolveException("Select on '%s' has no gui case and no fallback",
            select.property());
    }

    private static ItemModelNode resolveRangeDispatch(ItemModelNode.RangeDispatchNode range) {
        // Static GUI render evaluates every numeric property as 0: pick the LAST entry whose
        // threshold <= 0, per vanilla range_dispatch semantics, else the fallback.
        ItemModelNode result = null;
        for (ItemModelNode.RangeDispatchNode.Entry entry : range.entries()) {
            if (entry.threshold() <= 0) {
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
}
