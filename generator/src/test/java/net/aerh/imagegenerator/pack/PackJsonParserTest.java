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
}
