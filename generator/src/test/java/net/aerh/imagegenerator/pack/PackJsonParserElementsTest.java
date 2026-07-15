package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wave 4 parsing: elements, display.gui, gui_light, oversized_in_gui, tints and index fields. */
class PackJsonParserElementsTest {

    private static ModelInfo parseModel(String json) {
        return PackJsonParser.parseModel(json.getBytes(StandardCharsets.UTF_8));
    }

    private static ItemDefinition parseItem(String json) {
        return PackJsonParser.parseItemDefinitionInfo(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesElementWithAllFaceFields() {
        ModelInfo model = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "faces":{
                "south":{"uv":[0,0,16,16],"texture":"#front","rotation":90,"tintindex":0},
                "north":{"texture":"#back"},
                "east":{"texture":"#side"},"west":{"texture":"#side"},
                "up":{"texture":"#side"},"down":{"texture":"#side"}}}]}""");
        List<ModelElement> elements = model.elements();
        assertEquals(1, elements.size());
        ModelElement element = elements.get(0);
        assertEquals(0, element.fromX());
        assertEquals(16, element.toY());
        assertEquals(1, element.toZ());
        assertEquals(0, element.rotationAngle());
        assertEquals(6, element.faces().size());
        ModelElement.Face south = element.faces().get(ModelElement.Direction.SOUTH);
        assertEquals(new ModelElement.FaceUv(0, 0, 16, 16), south.uv());
        assertEquals("#front", south.textureRef());
        assertEquals(90, south.rotation());
        assertEquals(0, south.tintIndex());
        ModelElement.Face north = element.faces().get(ModelElement.Direction.NORTH);
        assertNull(north.uv(), "absent uv stays null so the renderer derives the bounds projection");
        assertEquals(0, north.rotation());
        assertEquals(-1, north.tintIndex());
    }

    @Test
    void reversedAndFractionalUvParses() {
        ModelInfo model = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "faces":{"south":{"uv":[15.5,16,0.25,0],"texture":"#a"}}}]}""");
        ModelElement.Face face = model.elements().get(0).faces().get(ModelElement.Direction.SOUTH);
        assertEquals(new ModelElement.FaceUv(15.5f, 16, 0.25f, 0), face.uv());
    }

    @Test
    void faceRotationOutsideQuarterTurnsIsRejected() {
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "faces":{"south":{"texture":"#a","rotation":45}}}]}"""));
    }

    @Test
    void elementRotationParsesWithAngle() {
        ModelInfo zeroAngle = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":0,"axis":"y","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertEquals(0, zeroAngle.elements().get(0).rotationAngle());

        ModelInfo tilted = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":45,"axis":"z","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertEquals(45, tilted.elements().get(0).rotationAngle(),
            "non-zero angles parse; they fail loudly at resolve, not at register");
    }

    @Test
    void elementCoordinatesOutsideVanillaBoundsAreRejected() {
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[-17,0,0],"to":[16,16,1],"faces":{}}]}"""));
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,33,1],"faces":{}}]}"""));
    }

    @Test
    void elementCoordinatesBeyondTheSlotButWithinBoundsParse() {
        ModelInfo model = parseModel("""
            {"elements":[{"from":[-16,-16,-16],"to":[32,32,32],"faces":{}}]}""");
        assertEquals(-16, model.elements().get(0).fromX());
        assertEquals(32, model.elements().get(0).toZ());
    }

    @Test
    void unknownFaceDirectionIsRejected() {
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "faces":{"souths":{"texture":"#a"}}}]}"""));
    }

    @Test
    void faceWithoutTextureIsRejected() {
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "faces":{"south":{"uv":[0,0,16,16]}}}]}"""));
    }

    @Test
    void wrongLengthVectorsAreRejected() {
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0],"to":[16,16,1],"faces":{}}]}"""));
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "faces":{"south":{"uv":[0,0,16],"texture":"#a"}}}]}"""));
    }

    @Test
    void displayGuiParsesWithDefaults() {
        ModelInfo model = parseModel("""
            {"display":{"gui":{"scale":[2,2,2]}}}""");
        assertEquals(new GuiTransform(0, 0, 0, 0, 0, 0, 2, 2, 2), model.guiTransform());
    }

    @Test
    void displayGuiParsesAllVectors() {
        ModelInfo model = parseModel("""
            {"display":{"gui":{"rotation":[0,180,0],"translation":[1,-2,3],"scale":[0.5,1,1]}}}""");
        assertEquals(new GuiTransform(0, 180, 0, 1, -2, 3, 0.5f, 1, 1), model.guiTransform());
    }

    @Test
    void displayWithoutGuiEntryIsNull() {
        assertNull(parseModel("""
            {"display":{"firstperson_righthand":{"scale":[2,2,2]}}}""").guiTransform(),
            "the gui entry inherits from the parent chain as a whole when absent");
        assertNull(parseModel("{}").guiTransform());
    }

    @Test
    void guiLightParsesAndValidates() {
        assertEquals("front", parseModel("{\"gui_light\":\"front\"}").guiLight());
        assertEquals("side", parseModel("{\"gui_light\":\"side\"}").guiLight());
        assertNull(parseModel("{}").guiLight());
        assertThrows(PackLoadException.class, () -> parseModel("{\"gui_light\":\"back\"}"));
    }

    @Test
    void fullTextureMapParses() {
        ModelInfo model = parseModel("""
            {"textures":{"layer0":"t:a","front":"t:b","back":"#front"}}""");
        assertEquals("t:a", model.layer0Ref());
        assertEquals(Map.of("layer0", "t:a", "front", "t:b", "back", "#front"), model.textures());
    }

    @Test
    void nonStringTextureEntriesAreSkippedNotFatal() {
        // Pre-elements parsing only ever read layer0, so a stray non-string entry never
        // failed a model; the full-map parser must stay as tolerant (the skipped key fails
        // loudly at resolve time if a face actually references it).
        ModelInfo model = parseModel("""
            {"textures":{"layer0":"t:a","weird":3}}""");
        assertEquals("t:a", model.layer0Ref());
        assertEquals(Map.of("layer0", "t:a"), model.textures());
    }

    @Test
    void nonStringLayer0StillFailsLikeBefore() {
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"textures":{"layer0":3}}"""));
    }

    @Test
    void modelWithoutElementsHasNullElements() {
        assertNull(parseModel("{\"parent\":\"t:x\"}").elements());
    }

    @Test
    void oversizedInGuiDefaultsFalse() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x"}}""");
        assertFalse(definition.oversizedInGui());
    }

    @Test
    void oversizedInGuiParsesTrue() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x"},"oversized_in_gui":true}""");
        assertTrue(definition.oversizedInGui());
    }

    @Test
    void oversizedInGuiWrongTypedIsRejected() {
        assertThrows(PackLoadException.class, () -> parseItem("""
            {"model":{"type":"model","model":"t:x"},"oversized_in_gui":"yes"}"""));
    }

    @Test
    void constantTintParsesPackedIntAndFloatArray() {
        ItemDefinition packed = parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"minecraft:constant","value":16744448}]}}""");
        ItemModelNode.ModelLeaf leaf = assertInstanceOf(ItemModelNode.ModelLeaf.class, packed.model());
        assertEquals(List.of(new ItemModelNode.TintSpec.Constant(0xFF8000)), leaf.tints());

        ItemDefinition floats = parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"constant","value":[1.0,0.5,0.0]}]}}""");
        ItemModelNode.ModelLeaf floatLeaf = assertInstanceOf(ItemModelNode.ModelLeaf.class, floats.model());
        assertEquals(List.of(new ItemModelNode.TintSpec.Constant(0xFF8000)), floatLeaf.tints());
    }

    @Test
    void customModelDataTintParsesIndexAndDefault() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x",
              "tints":[{"type":"minecraft:custom_model_data","index":2,"default":255}]}}""");
        ItemModelNode.ModelLeaf leaf = assertInstanceOf(ItemModelNode.ModelLeaf.class, definition.model());
        assertEquals(List.of(new ItemModelNode.TintSpec.CustomModelDataTint(2, 0x0000FF)), leaf.tints());
    }

    @Test
    void customModelDataTintDefaultsToWhiteAndIndexZero() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"custom_model_data"}]}}""");
        ItemModelNode.ModelLeaf leaf = assertInstanceOf(ItemModelNode.ModelLeaf.class, definition.model());
        assertEquals(List.of(new ItemModelNode.TintSpec.CustomModelDataTint(0, 0xFFFFFF)), leaf.tints());
    }

    @Test
    void unknownTintTypeParsesAsUnsupported() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"minecraft:dye","default":0}]}}""");
        ItemModelNode.ModelLeaf leaf = assertInstanceOf(ItemModelNode.ModelLeaf.class, definition.model());
        assertEquals(List.of(new ItemModelNode.TintSpec.Unsupported("dye")), leaf.tints());
    }

    @Test
    void malformedTintValueIsRejected() {
        assertThrows(PackLoadException.class, () -> parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"constant","value":"red"}]}}"""));
        assertThrows(PackLoadException.class, () -> parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"constant","value":[1.0,0.5]}]}}"""));
        assertThrows(PackLoadException.class, () -> parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"constant"}]}}"""));
    }

    @Test
    void modelLeafWithoutTintsHasEmptyList() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x"}}""");
        assertEquals(new ItemModelNode.ModelLeaf("t:x"), definition.model(),
            "the tint-free leaf equals the legacy two-arg record shape");
    }

    @Test
    void dispatchNodesParseIndexField() {
        ItemDefinition condition = parseItem("""
            {"model":{"type":"condition","property":"custom_model_data","index":3,
              "on_true":{"type":"model","model":"t:a"},"on_false":{"type":"model","model":"t:b"}}}""");
        assertEquals(3, assertInstanceOf(ItemModelNode.ConditionNode.class, condition.model()).index());

        ItemDefinition select = parseItem("""
            {"model":{"type":"select","property":"custom_model_data","index":1,
              "cases":[{"when":"x","model":{"type":"model","model":"t:a"}}],
              "fallback":{"type":"model","model":"t:b"}}}""");
        assertEquals(1, assertInstanceOf(ItemModelNode.SelectNode.class, select.model()).index());

        ItemDefinition range = parseItem("""
            {"model":{"type":"range_dispatch","property":"custom_model_data","index":2,
              "entries":[{"threshold":1,"model":{"type":"model","model":"t:a"}}]}}""");
        assertEquals(2, assertInstanceOf(ItemModelNode.RangeDispatchNode.class, range.model()).index());
    }

    @Test
    void dispatchIndexDefaultsToZero() {
        ItemDefinition range = parseItem("""
            {"model":{"type":"range_dispatch","property":"custom_model_data",
              "entries":[{"threshold":1,"model":{"type":"model","model":"t:a"}}]}}""");
        assertEquals(0, assertInstanceOf(ItemModelNode.RangeDispatchNode.class, range.model()).index());
    }

    @Test
    void fractionalDispatchIndexIsRejected() {
        assertThrows(PackLoadException.class, () -> parseItem("""
            {"model":{"type":"range_dispatch","property":"custom_model_data","index":1.5,
              "entries":[{"threshold":1,"model":{"type":"model","model":"t:a"}}]}}"""));
    }
}
