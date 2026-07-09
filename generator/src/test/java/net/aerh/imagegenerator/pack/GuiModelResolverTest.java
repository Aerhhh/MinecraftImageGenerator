package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiModelResolverTest {

    private static final ItemModelNode GUI_MODEL = new ItemModelNode.ModelLeaf("testpack:item/gui");
    private static final ItemModelNode HAND_MODEL = new ItemModelNode.ModelLeaf("testpack:item/hand");

    @Test
    void modelLeafResolvesToItsRef() {
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(GUI_MODEL));
    }

    @Test
    void conditionResolvesToOnFalse() {
        ItemModelNode node = new ItemModelNode.ConditionNode("using_item", HAND_MODEL, GUI_MODEL);
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void selectOnDisplayContextPicksGuiCase() {
        ItemModelNode node = new ItemModelNode.SelectNode("display_context",
            List.of(new ItemModelNode.SelectNode.Case(Set.of("gui", "ground"), GUI_MODEL)), HAND_MODEL);
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void selectWithoutGuiCaseFallsBack() {
        ItemModelNode node = new ItemModelNode.SelectNode("display_context",
            List.of(new ItemModelNode.SelectNode.Case(Set.of("head"), HAND_MODEL)), GUI_MODEL);
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void selectOnOtherPropertyUsesFallback() {
        ItemModelNode node = new ItemModelNode.SelectNode("local_time",
            List.of(new ItemModelNode.SelectNode.Case(Set.of("night"), HAND_MODEL)), GUI_MODEL);
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void selectWithNoMatchAndNoFallbackThrows() {
        ItemModelNode node = new ItemModelNode.SelectNode("display_context",
            List.of(new ItemModelNode.SelectNode.Case(Set.of("head"), HAND_MODEL)), null);
        assertThrows(PackResolveException.class, () -> GuiModelResolver.resolveGui(node));
    }

    @Test
    void rangeDispatchAtValueZeroUsesFallbackWhenAllThresholdsPositive() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("cooldown", 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(0.5, HAND_MODEL)), GUI_MODEL);
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void rangeDispatchPicksLastEntryWithThresholdAtOrBelowZero() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("damage", 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(-1.0, HAND_MODEL),
                new ItemModelNode.RangeDispatchNode.Entry(0.0, GUI_MODEL),
                new ItemModelNode.RangeDispatchNode.Entry(0.5, HAND_MODEL)), null);
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void rangeDispatchWithNoApplicableEntryAndNoFallbackThrows() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("cooldown", 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(0.5, HAND_MODEL)), null);
        assertThrows(PackResolveException.class, () -> GuiModelResolver.resolveGui(node));
    }

    @Test
    void compositeCollectsAllLayersInOrder() {
        ItemModelNode node = new ItemModelNode.CompositeNode(List.of(GUI_MODEL, HAND_MODEL));
        assertEquals(List.of("testpack:item/gui", "testpack:item/hand"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void nestedDispatchResolves() {
        // range_dispatch(cooldown) -> select(display_context) -> model, mirroring the pack's katanas
        ItemModelNode select = new ItemModelNode.SelectNode("display_context",
            List.of(new ItemModelNode.SelectNode.Case(Set.of("gui"), GUI_MODEL)), HAND_MODEL);
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("cooldown", 1.0, List.of(), select);
        assertEquals(List.of("testpack:item/gui"), GuiModelResolver.resolveGui(node));
    }

    @Test
    void unsupportedNodeThrowsWithTypeName() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> GuiModelResolver.resolveGui(new ItemModelNode.UnsupportedNode("special")));
        assertTrue(exception.getMessage().contains("special"));
    }
}
