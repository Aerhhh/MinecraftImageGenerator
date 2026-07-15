package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.aerh.imagegenerator.testsupport.CustomModelDatas.colors;
import static net.aerh.imagegenerator.testsupport.CustomModelDatas.flags;
import static net.aerh.imagegenerator.testsupport.CustomModelDatas.floats;
import static net.aerh.imagegenerator.testsupport.CustomModelDatas.strings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wave 4: custom_model_data evaluation and tint sources in {@link GuiModelResolver}. */
class GuiModelResolverDataTest {

    private static final ItemModelNode LOW = new ItemModelNode.ModelLeaf("t:low");
    private static final ItemModelNode MID = new ItemModelNode.ModelLeaf("t:mid");
    private static final ItemModelNode HIGH = new ItemModelNode.ModelLeaf("t:high");
    private static final ItemModelNode FALLBACK = new ItemModelNode.ModelLeaf("t:fallback");

    private static List<String> refs(ItemModelNode node, CustomModelData data) {
        return GuiModelResolver.resolveGui(node, data).stream()
            .map(GuiModelResolver.GuiModel::modelRef)
            .toList();
    }

    @Test
    void rangeDispatchPicksGreatestThresholdAtOrBelowValue() {
        // Deliberately unsorted entries: vanilla sorts by threshold, so picking must be by
        // greatest threshold, not JSON position.
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 0, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(2.0, HIGH),
                new ItemModelNode.RangeDispatchNode.Entry(0.5, LOW),
                new ItemModelNode.RangeDispatchNode.Entry(1.0, MID)), FALLBACK);
        assertEquals(List.of("t:mid"), refs(node, floats(1.5f)));
        assertEquals(List.of("t:high"), refs(node, floats(99.0f)));
        assertEquals(List.of("t:fallback"), refs(node, floats(0.25f)),
            "value below every threshold uses the fallback");
    }

    @Test
    void rangeDispatchExactlyEqualThresholdMatches() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 0, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(2.0, HIGH)), FALLBACK);
        assertEquals(List.of("t:high"), refs(node, floats(2.0f)));
    }

    @Test
    void rangeDispatchComparesInFloatSpaceLikeVanilla() {
        // 0.7 parses to a JSON double ABOVE 0.7f, while the supplied float widens to a double
        // BELOW it; comparing in double would skip the entry on exact-equal data. Vanilla
        // compares float-to-float (Codec.FLOAT thresholds), so 0.7f <= 0.7f matches.
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 0, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(0.7, HIGH)), FALLBACK);
        assertEquals(List.of("t:high"), refs(node, floats(0.7f)),
            "exact-equality at a non-dyadic threshold must match in float space");
    }

    @Test
    void rangeDispatchLaterEqualThresholdWins() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 0, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(1.0, LOW),
                new ItemModelNode.RangeDispatchNode.Entry(1.0, MID)), null);
        assertEquals(List.of("t:mid"), refs(node, floats(1.0f)));
    }

    @Test
    void rangeDispatchAppliesScale() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 0, 0.5,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(1.0, HIGH)), FALLBACK);
        assertEquals(List.of("t:high"), refs(node, floats(2.0f)), "2.0 * 0.5 = 1.0 meets the threshold");
        assertEquals(List.of("t:fallback"), refs(node, floats(1.5f)), "1.5 * 0.5 = 0.75 does not");
    }

    @Test
    void rangeDispatchReadsTheIndexedFloat() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 1, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(1.0, HIGH)), FALLBACK);
        assertEquals(List.of("t:high"), refs(node, floats(0.0f, 5.0f)));
        assertEquals(List.of("t:fallback"), refs(node, floats(5.0f, 0.0f)));
    }

    @Test
    void rangeDispatchMissingFloatEvaluatesAtZero() {
        // Vanilla's range property returns primitive float, so a missing component or index
        // reads 0.0F - any threshold <= 0 still matches, exactly like the pre-wave resolver.
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 0, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(-100.0, LOW)), FALLBACK);
        assertEquals(List.of("t:low"), refs(node, CustomModelData.EMPTY),
            "missing float evaluates at 0, selecting the negative-threshold entry over the fallback");
    }

    @Test
    void rangeDispatchMissingFloatBelowEveryThresholdWithoutFallbackThrows() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("custom_model_data", 0, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(1.0, HIGH)), null);
        assertThrows(PackResolveException.class, () -> GuiModelResolver.resolveGui(node, CustomModelData.EMPTY),
            "value 0 matches no positive threshold and there is no fallback");
    }

    @Test
    void rangeDispatchOnOtherPropertiesStillEvaluatesAtZero() {
        ItemModelNode node = new ItemModelNode.RangeDispatchNode("cooldown", 0, 1.0,
            List.of(new ItemModelNode.RangeDispatchNode.Entry(0.0, MID),
                new ItemModelNode.RangeDispatchNode.Entry(0.5, HIGH)), FALLBACK);
        assertEquals(List.of("t:mid"), refs(node, floats(99.0f)),
            "floats only feed custom_model_data dispatches; other properties stay 0");
    }

    @Test
    void selectMatchesTheIndexedString() {
        ItemModelNode node = new ItemModelNode.SelectNode("custom_model_data", 0,
            List.of(new ItemModelNode.SelectNode.Case(Set.of("ruby"), LOW),
                new ItemModelNode.SelectNode.Case(Set.of("opal", "jade"), MID)), FALLBACK);
        assertEquals(List.of("t:low"), refs(node, strings("ruby")));
        assertEquals(List.of("t:mid"), refs(node, strings("jade")));
        assertEquals(List.of("t:fallback"), refs(node, strings("amber")));
    }

    @Test
    void selectMissingStringUsesFallback() {
        ItemModelNode node = new ItemModelNode.SelectNode("custom_model_data", 2,
            List.of(new ItemModelNode.SelectNode.Case(Set.of("ruby"), LOW)), FALLBACK);
        assertEquals(List.of("t:fallback"), refs(node, strings("ruby")),
            "index 2 misses a one-entry strings list");
    }

    @Test
    void selectWithNoMatchAndNoFallbackThrows() {
        ItemModelNode node = new ItemModelNode.SelectNode("custom_model_data", 0,
            List.of(new ItemModelNode.SelectNode.Case(Set.of("ruby"), LOW)), null);
        assertThrows(PackResolveException.class, () -> GuiModelResolver.resolveGui(node, strings("amber")));
    }

    @Test
    void selectOnDisplayContextIgnoresStrings() {
        ItemModelNode node = new ItemModelNode.SelectNode("display_context", 0,
            List.of(new ItemModelNode.SelectNode.Case(Set.of("gui"), LOW)), FALLBACK);
        assertEquals(List.of("t:low"), refs(node, strings("whatever")));
    }

    @Test
    void conditionReadsTheIndexedFlag() {
        ItemModelNode node = new ItemModelNode.ConditionNode("custom_model_data", 1, MID, LOW);
        assertEquals(List.of("t:mid"), refs(node, flags(false, true)));
        assertEquals(List.of("t:low"), refs(node, flags(true, false)));
    }

    @Test
    void conditionMissingFlagIsFalse() {
        ItemModelNode node = new ItemModelNode.ConditionNode("custom_model_data", 0, MID, LOW);
        assertEquals(List.of("t:low"), refs(node, CustomModelData.EMPTY));
    }

    @Test
    void conditionOnOtherPropertiesStaysOnFalse() {
        ItemModelNode node = new ItemModelNode.ConditionNode("using_item", 0, MID, LOW);
        assertEquals(List.of("t:low"), refs(node, flags(true)),
            "flags only feed custom_model_data conditions; other properties stay false");
    }

    @Test
    void constantTintEvaluates() {
        List<ItemModelNode.TintSpec> tints = List.of(new ItemModelNode.TintSpec.Constant(0xFF8000));
        assertEquals(List.of(0xFF8000), GuiModelResolver.evaluateTints(tints, CustomModelData.EMPTY));
    }

    @Test
    void customModelDataTintReadsColorsList() {
        List<ItemModelNode.TintSpec> tints = List.of(new ItemModelNode.TintSpec.CustomModelDataTint(1, 0x111111));
        assertEquals(List.of(0x00FF00), GuiModelResolver.evaluateTints(tints, colors(0xFF0000, 0x00FF00)));
    }

    @Test
    void customModelDataTintMissingColorUsesDeclaredDefault() {
        List<ItemModelNode.TintSpec> tints = List.of(new ItemModelNode.TintSpec.CustomModelDataTint(5, 0x123456));
        assertEquals(List.of(0x123456), GuiModelResolver.evaluateTints(tints, colors(0xFF0000)));
    }

    @Test
    void unsupportedTintSourceThrowsOnStrictEvaluation() {
        List<ItemModelNode.TintSpec> tints = List.of(new ItemModelNode.TintSpec.Unsupported("dye"));
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> GuiModelResolver.evaluateTints(tints, CustomModelData.EMPTY));
        assertTrue(exception.getMessage().contains("dye"));
    }

    @Test
    void unsupportedTintSourceIsWhiteOnLenientEvaluation() {
        // The flat sprite branch rendered dye/potion-tinted items fine (untinted) before tint
        // parsing existed; lenient evaluation preserves that instead of hard-failing.
        List<ItemModelNode.TintSpec> tints = List.of(
            new ItemModelNode.TintSpec.Unsupported("dye"),
            new ItemModelNode.TintSpec.Constant(0xFF8000));
        assertEquals(List.of(0xFFFFFF, 0xFF8000),
            GuiModelResolver.evaluateTintsLenient(tints, CustomModelData.EMPTY),
            "unsupported entries turn white; supported entries in the same list still evaluate");
    }

    @Test
    void resolveGuiKeepsTintSpecsUnevaluated() {
        // Resolution must not evaluate tints: the sprite and elements branches apply different
        // strictness, so an unsupported source may not throw before the branch is known.
        ItemModelNode node = new ItemModelNode.ModelLeaf("t:x",
            List.of(new ItemModelNode.TintSpec.Unsupported("dye")));
        List<GuiModelResolver.GuiModel> models = GuiModelResolver.resolveGui(node, CustomModelData.EMPTY);
        assertEquals(List.of(new ItemModelNode.TintSpec.Unsupported("dye")), models.get(0).tints());
    }

    @Test
    void compositePreservesPerLeafTints() {
        ItemModelNode node = new ItemModelNode.CompositeNode(List.of(
            new ItemModelNode.ModelLeaf("t:a", List.of(new ItemModelNode.TintSpec.Constant(1))),
            new ItemModelNode.ModelLeaf("t:b")));
        List<GuiModelResolver.GuiModel> models = GuiModelResolver.resolveGui(node, CustomModelData.EMPTY);
        assertEquals(List.of(new ItemModelNode.TintSpec.Constant(1)), models.get(0).tints());
        assertEquals(List.of(), models.get(1).tints());
    }

    @Test
    void legacyRefOnlyOverloadStillResolves() {
        ItemModelNode node = new ItemModelNode.ConditionNode("custom_model_data", 0, MID, LOW);
        assertEquals(List.of("t:low"), GuiModelResolver.resolveGui(node),
            "the ref-only overload evaluates with empty data");
    }
}
