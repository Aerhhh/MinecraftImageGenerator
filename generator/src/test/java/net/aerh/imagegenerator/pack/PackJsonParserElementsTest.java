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
        assertNull(element.rotation());
        assertTrue(element.shade(), "shade defaults true");
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
    void elementRotationParsesAngleAxisOriginAndRescale() {
        ModelInfo zeroAngle = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":0,"axis":"y","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertEquals(new ModelElement.Rotation(0, ModelElement.Axis.Y, 8, 8, 8, false),
            zeroAngle.elements().get(0).rotation(), "a declared angle-0 entry parses as a no-op rotation");
        assertFalse(zeroAngle.elements().get(0).hasActiveRotation());

        ModelInfo tilted = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":45,"axis":"z","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertEquals(new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, false),
            tilted.elements().get(0).rotation());
        assertTrue(tilted.elements().get(0).hasActiveRotation());

        ModelInfo rescaled = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":-22.5,"axis":"x","origin":[4,0,12],"rescale":true},
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertEquals(new ModelElement.Rotation(-22.5f, ModelElement.Axis.X, 4, 0, 12, true),
            rescaled.elements().get(0).rotation());
    }

    @Test
    void elementRotationAcceptsFreeAngles() {
        // Modern vanilla (1.21.6+) lifted the legacy 22.5-degree-step whitelist; packs authored
        // for the current client family ship arbitrary angles and must keep loading.
        ModelInfo ten = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":10,"axis":"y","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertEquals(new ModelElement.Rotation(10, ModelElement.Axis.Y, 8, 8, 8, false),
            ten.elements().get(0).rotation());
        assertTrue(ten.elements().get(0).hasActiveRotation());

        ModelInfo ninety = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":90,"axis":"y","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertEquals(90f, ninety.elements().get(0).rotation().angle());
    }

    @Test
    void nonFiniteElementRotationAngleIsRejected() {
        // 1e40 overflows float to Infinity; a non-finite angle would poison the rotation
        // matrix with NaN geometry, so it fails at parse with a message naming the real defect.
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":1e40,"axis":"y","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}"""));
    }

    @Test
    void elementRotationRequiresAxisAndOrigin() {
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":45,"origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}"""), "missing axis");
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":45,"axis":"z"},
              "faces":{"south":{"texture":"#a"}}}]}"""), "missing origin");
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":45,"axis":"w","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}"""), "unknown axis");
        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],
              "rotation":{"angle":45,"axis":"Z","origin":[8,8,8]},
              "faces":{"south":{"texture":"#a"}}}]}"""), "vanilla axis names are lowercase");
    }

    @Test
    void elementShadeParsesAndValidates() {
        ModelInfo unshaded = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],"shade":false,
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertFalse(unshaded.elements().get(0).shade());

        ModelInfo shaded = parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],"shade":true,
              "faces":{"south":{"texture":"#a"}}}]}""");
        assertTrue(shaded.elements().get(0).shade());

        assertThrows(PackLoadException.class, () -> parseModel("""
            {"elements":[{"from":[0,0,0],"to":[16,16,1],"shade":"no",
              "faces":{"south":{"texture":"#a"}}}]}"""));
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
    void dyeTintParsesItsRequiredDefault() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"minecraft:dye","default":3368703}]}}""");
        ItemModelNode.ModelLeaf leaf = assertInstanceOf(ItemModelNode.ModelLeaf.class, definition.model());
        assertEquals(List.of(new ItemModelNode.TintSpec.Dye(0x3366FF)), leaf.tints());
    }

    @Test
    void dyeTintWithoutDefaultIsRejected() {
        // The default is REQUIRED per the vanilla format - and it is the only color this
        // library can apply, so silently substituting white would hide the transcription error.
        assertThrows(PackLoadException.class, () -> parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"minecraft:dye"}]}}"""));
    }

    @Test
    void unknownTintTypeParsesAsUnsupported() {
        ItemDefinition definition = parseItem("""
            {"model":{"type":"model","model":"t:x","tints":[{"type":"minecraft:team","default":0}]}}""");
        ItemModelNode.ModelLeaf leaf = assertInstanceOf(ItemModelNode.ModelLeaf.class, definition.model());
        assertEquals(List.of(new ItemModelNode.TintSpec.Unsupported("team")), leaf.tints());
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

    @Test
    void rangeDispatchNormalizeDefaultsTrueAndParsesFalse() {
        // The vanilla minecraft:damage default is normalize:true (damage fraction).
        ItemDefinition defaulted = parseItem("""
            {"model":{"type":"range_dispatch","property":"minecraft:damage",
              "entries":[{"threshold":0.5,"model":{"type":"model","model":"t:a"}}]}}""");
        assertTrue(assertInstanceOf(ItemModelNode.RangeDispatchNode.class, defaulted.model()).normalize());

        ItemDefinition raw = parseItem("""
            {"model":{"type":"range_dispatch","property":"minecraft:damage","normalize":false,
              "entries":[{"threshold":3,"model":{"type":"model","model":"t:a"}}]}}""");
        assertFalse(assertInstanceOf(ItemModelNode.RangeDispatchNode.class, raw.model()).normalize());
    }

    @Test
    void wrongTypedNormalizeIsRejected() {
        assertThrows(PackLoadException.class, () -> parseItem("""
            {"model":{"type":"range_dispatch","property":"minecraft:damage","normalize":"nope",
              "entries":[{"threshold":1,"model":{"type":"model","model":"t:a"}}]}}"""));
    }
}
