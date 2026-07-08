package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackJsonParserTest {

    private static ItemModelNode parse(String json) {
        return PackJsonParser.parseItemDefinition(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesPlainModelLeaf() {
        ItemModelNode node = parse("""
            {"model":{"type":"minecraft:model","model":"testpack:item/simple"}}""");
        assertEquals(new ItemModelNode.ModelLeaf("testpack:item/simple"), node);
    }

    @Test
    void normalizesUnprefixedTypeStrings() {
        ItemModelNode node = parse("""
            {"model":{"type":"condition","property":"using_item",
              "on_true":{"type":"model","model":"testpack:item/in_hand"},
              "on_false":{"type":"model","model":"testpack:item/simple"}}}""");
        ItemModelNode.ConditionNode condition = assertInstanceOf(ItemModelNode.ConditionNode.class, node);
        assertEquals("using_item", condition.property());
        assertEquals(new ItemModelNode.ModelLeaf("testpack:item/simple"), condition.onFalse());
    }

    @Test
    void parsesSelectWithStringAndArrayWhen() {
        ItemModelNode node = parse("""
            {"model":{"type":"minecraft:select","property":"minecraft:display_context",
              "cases":[{"when":["gui","ground"],"model":{"type":"model","model":"testpack:item/simple"}},
                       {"when":"head","model":{"type":"model","model":"testpack:item/other"}}],
              "fallback":{"type":"model","model":"testpack:item/in_hand"}}}""");
        ItemModelNode.SelectNode select = assertInstanceOf(ItemModelNode.SelectNode.class, node);
        assertEquals("display_context", select.property());
        assertEquals(2, select.cases().size());
        assertTrue(select.cases().get(0).when().contains("gui"));
        assertTrue(select.cases().get(1).when().contains("head"));
        assertEquals(new ItemModelNode.ModelLeaf("testpack:item/in_hand"), select.fallback());
    }

    @Test
    void parsesRangeDispatchWithEntriesAndFallback() {
        ItemModelNode node = parse("""
            {"model":{"type":"range_dispatch","property":"cooldown","scale":0.05,
              "entries":[{"threshold":0.5,"model":{"type":"model","model":"testpack:item/in_hand"}}],
              "fallback":{"type":"model","model":"testpack:item/simple"}}}""");
        ItemModelNode.RangeDispatchNode range = assertInstanceOf(ItemModelNode.RangeDispatchNode.class, node);
        assertEquals(0.05, range.scale());
        assertEquals(1, range.entries().size());
        assertEquals(0.5, range.entries().get(0).threshold());
    }

    @Test
    void parsesCompositeInOrder() {
        ItemModelNode node = parse("""
            {"model":{"type":"composite","models":[
              {"type":"model","model":"testpack:item/base"},
              {"type":"model","model":"testpack:item/overlay"}]}}""");
        ItemModelNode.CompositeNode composite = assertInstanceOf(ItemModelNode.CompositeNode.class, node);
        assertEquals(new ItemModelNode.ModelLeaf("testpack:item/base"), composite.models().get(0));
        assertEquals(new ItemModelNode.ModelLeaf("testpack:item/overlay"), composite.models().get(1));
    }

    @Test
    void unknownTypeBecomesUnsupportedNode() {
        ItemModelNode node = parse("""
            {"model":{"type":"minecraft:special","base":"minecraft:item/template_shulker_box"}}""");
        assertEquals(new ItemModelNode.UnsupportedNode("special"), node);
    }

    @Test
    void rejectsMissingModelMember() {
        assertThrows(PackLoadException.class, () -> parse("{\"not_model\":{}}"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(PackLoadException.class, () -> parse("{nope"));
    }

    @Test
    void rejectsNodeWithoutType() {
        assertThrows(PackLoadException.class, () -> parse("{\"model\":{\"model\":\"testpack:item/x\"}}"));
    }

    @Test
    void selectWhenNullIsRejected() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"model":{"type":"select","property":"display_context",
              "cases":[{"when":null,"model":{"type":"model","model":"t:x"}}]}}"""));
    }

    @Test
    void selectWhenObjectIsRejected() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"model":{"type":"select","property":"display_context",
              "cases":[{"when":{},"model":{"type":"model","model":"t:x"}}]}}"""));
    }

    @Test
    void selectWhenArrayWithNullElementIsRejected() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"model":{"type":"select","property":"display_context",
              "cases":[{"when":["gui",null],"model":{"type":"model","model":"t:x"}}]}}"""));
    }

    @Test
    void rangeDispatchMissingThresholdIsRejected() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"model":{"type":"range_dispatch","property":"cooldown",
              "entries":[{"model":{"type":"model","model":"t:x"}}]}}"""));
    }

    @Test
    void rangeDispatchWrongTypedScaleIsRejected() {
        assertThrows(PackLoadException.class, () -> parse("""
            {"model":{"type":"range_dispatch","property":"cooldown","scale":{},
              "entries":[{"threshold":0.5,"model":{"type":"model","model":"t:x"}}]}}"""));
    }

    @Test
    void lenientJsonIsRejected() {
        assertThrows(PackLoadException.class, () -> parse("{model: {type: 'model', model: 'x'}}"));
    }

    @Test
    void parsesModelWithParentAndLayer0() {
        ModelInfo model = PackJsonParser.parseModel("""
            {"parent":"item/paper","textures":{"layer0":"testpack:item/simple"}}""".getBytes(StandardCharsets.UTF_8));
        assertEquals("item/paper", model.parentRef());
        assertEquals("testpack:item/simple", model.layer0Ref());
    }

    @Test
    void parsesModelWithoutLayer0() {
        ModelInfo model = PackJsonParser.parseModel("""
            {"parent":"testpack:item/base"}""".getBytes(StandardCharsets.UTF_8));
        assertEquals("testpack:item/base", model.parentRef());
        assertEquals(null, model.layer0Ref());
    }

    @Test
    void animationFirstFrameIsFirstListEntryNotIndexZero() {
        AnimationMeta meta = PackJsonParser.parseAnimationMeta("""
            {"animation":{"frametime":3,"frames":[4,3,2,{"index":0,"time":81}]}}""".getBytes(StandardCharsets.UTF_8));
        assertEquals(4, meta.firstFrameIndex());
    }

    @Test
    void animationWithoutFramesListDefaultsToFrameZero() {
        AnimationMeta meta = PackJsonParser.parseAnimationMeta("""
            {"animation":{"frametime":5}}""".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, meta.firstFrameIndex());
    }

    @Test
    void animationParsesExplicitFrameSize() {
        AnimationMeta meta = PackJsonParser.parseAnimationMeta("""
            {"animation":{"width":16,"height":32}}""".getBytes(StandardCharsets.UTF_8));
        assertEquals(16, meta.frameWidth());
        assertEquals(32, meta.frameHeight());
    }

    @Test
    void rejectsMcmetaWithoutAnimationObject() {
        assertThrows(PackLoadException.class,
            () -> PackJsonParser.parseAnimationMeta("{\"gui\":{}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void trailingContentIsRejected() {
        assertThrows(PackLoadException.class,
            () -> PackJsonParser.parseModel("{\"model\":{\"type\":\"model\",\"model\":\"t:x\"}} extra".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void frameEntryObjectMissingIndexIsRejected() {
        assertThrows(PackLoadException.class, () -> PackJsonParser.parseAnimationMeta(
            "{\"animation\":{\"frames\":[{\"time\":81}]}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void frameEntryNonNumericIsRejected() {
        assertThrows(PackLoadException.class, () -> PackJsonParser.parseAnimationMeta(
            "{\"animation\":{\"frames\":[\"a\"]}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void modelParentWrongTypedIsRejected() {
        assertThrows(PackLoadException.class,
            () -> PackJsonParser.parseModel("{\"parent\":{}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void modelLayer0WrongTypedIsRejected() {
        assertThrows(PackLoadException.class,
            () -> PackJsonParser.parseModel("{\"textures\":{\"layer0\":[]}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void animationWidthWrongTypedIsRejected() {
        assertThrows(PackLoadException.class, () -> PackJsonParser.parseAnimationMeta(
            "{\"animation\":{\"width\":\"abc\"}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void modelParentExplicitNullIsNull() {
        ModelInfo model = PackJsonParser.parseModel("{\"parent\":null}".getBytes(StandardCharsets.UTF_8));
        assertEquals(null, model.parentRef());
    }
}
