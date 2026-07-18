package net.aerh.imagegenerator.impl.scene;

import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.GifBytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the declarative JSON scene layer: strict schema rejection (every message naming the
 * offender), unknown-key tolerance, placement and anchor geometry, z-order, nested arrangements,
 * the slot-cell grid pitch, and the member-generator bindings.
 */
@DisplayName("JSON scene layer")
class JsonSceneGeneratorTest {

    private static final int RED = 0xFFFF0000;
    private static final int BIG = 0xFF123456;

    @TempDir
    Path packDir;

    // testpack:item/simple renders 256x256 solid red; testpack:item/big renders 256x256 solid BIG.
    private static final int ITEM = 256;

    // ------------------------------------------------------------- helpers

    private record Pack(PackId id, PackRepository repository) {
    }

    private Pack registerPack(Consumer<Path> writer, String name) {
        writer.accept(packDir);
        PackRepository repository = new PackRepository();
        PackId id = repository.register(name, PackSource.directory(packDir, PackLimits.fromSystemProperties()));
        return new Pack(id, repository);
    }

    private static IllegalArgumentException reject(String json) {
        return assertThrows(IllegalArgumentException.class, () -> MinecraftSceneGenerator.fromScene(json));
    }

    private static void assertMessageNames(IllegalArgumentException exception, String offender) {
        assertTrue(exception.getMessage().contains(offender),
            "message should name `" + offender + "` but was: " + exception.getMessage());
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    // ------------------------------------------------------------ rejections

    @Test
    @DisplayName("a missing schema_version is rejected, naming the field")
    void schemaVersionRequired() {
        assertMessageNames(reject("""
            {"regions":[{"name":"a","type":"item","item":"x"}]}"""), "schema_version");
    }

    @Test
    @DisplayName("a schema_version above the supported max is rejected, naming the version")
    void schemaVersionTooNew() {
        IllegalArgumentException exception = reject("""
            {"schema_version":2,"regions":[{"name":"a","type":"item","item":"x"}]}""");
        assertMessageNames(exception, "schema_version");
        assertMessageNames(exception, "2");
    }

    @Test
    @DisplayName("a non-positive schema_version is rejected, naming the field")
    void schemaVersionBelowMinimum() {
        IllegalArgumentException exception = reject("""
            {"schema_version":0,"regions":[{"name":"a","type":"item","item":"x"}]}""");
        assertMessageNames(exception, "schema_version");
        assertMessageNames(exception, "0");
    }

    @Test
    @DisplayName("duplicate JSON keys are rejected, naming the key")
    void duplicateJsonKeys() {
        assertMessageNames(reject("""
            {"schema_version":1,"schema_version":1,"regions":[{"name":"a","type":"item","item":"x"}]}"""),
            "schema_version");
    }

    @Test
    @DisplayName("duplicate region names are rejected, naming the name")
    void duplicateRegionNames() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"dup","type":"item","item":"x"},
              {"name":"dup","type":"item","item":"y"}]}"""), "dup");
    }

    @Test
    @DisplayName("an anchor to an unknown region is rejected, naming the target")
    void unknownAnchorTarget() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"a","type":"item","item":"x","anchor":{"to":"ghost","edge":"right"}}]}"""), "ghost");
    }

    @Test
    @DisplayName("an anchor cycle a -> b -> a is rejected, naming a region in the cycle")
    void anchorCycle() {
        IllegalArgumentException exception = reject("""
            {"schema_version":1,"regions":[
              {"name":"a","type":"item","item":"x","anchor":{"to":"b","edge":"right"}},
              {"name":"b","type":"item","item":"y","anchor":{"to":"a","edge":"right"}}]}""");
        assertTrue(exception.getMessage().contains("a") || exception.getMessage().contains("b"),
            exception.getMessage());
        assertTrue(exception.getMessage().toLowerCase().contains("cycle"), exception.getMessage());
    }

    @Test
    @DisplayName("an invalid anchor edge is rejected, naming the edge field")
    void invalidEdge() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"a","type":"item","item":"x"},
              {"name":"b","type":"item","item":"y","anchor":{"to":"a","edge":"sideways"}}]}"""), "edge");
    }

    @Test
    @DisplayName("an invalid alignment is rejected, naming the alignment field")
    void invalidAlignment() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"r","type":"row","alignment":"middle",
               "regions":[{"name":"c","type":"item","item":"x"}]}]}"""), "alignment");
    }

    @Test
    @DisplayName("an invalid anchor align names the anchor's align field, not alignment")
    void invalidAnchorAlign() {
        IllegalArgumentException exception = reject("""
            {"schema_version":1,"regions":[
              {"name":"a","type":"item","item":"x"},
              {"name":"b","type":"item","item":"y",
               "anchor":{"to":"a","edge":"right","align":"middle"}}]}""");
        // The anchor sub-key is `align`, so the message must name it and not the arrangement's
        // `alignment` field, which does not exist on an anchor.
        assertMessageNames(exception, "`align`");
        assertFalse(exception.getMessage().contains("`alignment`"),
            "message must not name the non-existent anchor field `alignment`: " + exception.getMessage());
    }

    @Test
    @DisplayName("a malformed HUD run font is rejected at parse, naming the resource location rule")
    void hudRunMalformedFont() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"hud","type":"hud","lines":[[{"text":"x","font":"Bad Font!!"}]]}]}"""),
            "resource location");
    }

    @Test
    @DisplayName("declaring both at and anchor is rejected, naming both")
    void bothAtAndAnchor() {
        IllegalArgumentException exception = reject("""
            {"schema_version":1,"regions":[
              {"name":"a","type":"item","item":"x","at":[0,0],"anchor":{"to":"a","edge":"right"}}]}""");
        assertMessageNames(exception, "at");
        assertMessageNames(exception, "anchor");
    }

    @Test
    @DisplayName("mixing nbt with simple tooltip fields is rejected, naming nbt")
    void nbtMixedWithSimpleFields() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"tip","type":"tooltip","nbt":{"components":{}},"lore":"x"}]}"""), "nbt");
    }

    @Test
    @DisplayName("a nested region carrying at/anchor/z is rejected, naming the offending key")
    void nestedRegionWithPlacement() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"r","type":"row","regions":[
                {"name":"c","type":"item","item":"x","at":[0,0]}]}]}"""), "at");
    }

    @Test
    @DisplayName("a grid without columns is rejected, naming columns")
    void gridWithoutColumns() {
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[
              {"name":"g","type":"grid","regions":[{"name":"c","type":"item","item":"x"}]}]}"""), "columns");
    }

    @Test
    @DisplayName("content after the document is rejected, naming the scene document")
    void trailingContent() {
        // A second top-level value is rejected: the strict reader refuses to render only the first
        // of two concatenated scenes. The message names the scene document.
        assertMessageNames(reject("""
            {"schema_version":1,"regions":[{"name":"a","type":"item","item":"x"}]} {"second":1}"""), "scene");
    }

    // ---------------------------------------------------------- tolerance

    @Test
    @DisplayName("unknown keys anywhere are tolerated: the scene parses and renders")
    void unknownKeysTolerated() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");
        GeneratedObject result = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"bogus":true,"regions":[
              {"name":"a","type":"item","item":"testpack:item/simple","mystery":5}]}""",
            pack.id(), pack.repository()).render(null);
        assertFalse(result.isAnimated());
        assertEquals(ITEM, result.getImage().getWidth());
        assertEquals(ITEM, result.getImage().getHeight());
    }

    // ------------------------------------------------------------ geometry

    @Test
    @DisplayName("explicit at placements position members relatively and scale by the pixel size")
    void atPlacementScalesWithPixelSize() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");

        BufferedImage scaleOne = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"scale":1,"margin":0,"regions":[
              {"name":"a","type":"item","item":"testpack:item/simple","at":[0,0]},
              {"name":"b","type":"item","item":"testpack:item/big","at":[200,0]}]}""",
            pack.id(), pack.repository()).render(null).getImage();
        // pixelSize 2: b lands at x = 200 * 2 = 400
        assertEquals(400 + ITEM, scaleOne.getWidth());
        assertEquals(ITEM, scaleOne.getHeight());
        assertEquals(RED, scaleOne.getRGB(0, 0));
        assertEquals(BIG, scaleOne.getRGB(400, 0));
        assertEquals(0, alpha(scaleOne, 300, 0), "the gap between the members is transparent");

        BufferedImage scaleTwo = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"scale":2,"margin":0,"regions":[
              {"name":"a","type":"item","item":"testpack:item/simple","at":[0,0]},
              {"name":"b","type":"item","item":"testpack:item/big","at":[200,0]}]}""",
            pack.id(), pack.repository()).render(null).getImage();
        // pixelSize 4: b lands at x = 200 * 4 = 800 (the item itself does not scale)
        assertEquals(800 + ITEM, scaleTwo.getWidth());
        assertEquals(RED, scaleTwo.getRGB(0, 0));
        assertEquals(BIG, scaleTwo.getRGB(800, 0));
    }

    @Test
    @DisplayName("every anchor edge attaches a member outside the target's matching side")
    void anchorEdges() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");

        BufferedImage right = anchored(pack, "right");
        assertEquals(2 * ITEM, right.getWidth());
        assertEquals(ITEM, right.getHeight());
        assertEquals(RED, right.getRGB(0, 0));
        assertEquals(BIG, right.getRGB(ITEM, 0));

        BufferedImage left = anchored(pack, "left");
        assertEquals(2 * ITEM, left.getWidth());
        // b's right edge is flush against a's left edge, so after normalization b sits at x = 0
        assertEquals(BIG, left.getRGB(0, 0));
        assertEquals(RED, left.getRGB(ITEM, 0));

        BufferedImage bottom = anchored(pack, "bottom");
        assertEquals(ITEM, bottom.getWidth());
        assertEquals(2 * ITEM, bottom.getHeight());
        assertEquals(RED, bottom.getRGB(0, 0));
        assertEquals(BIG, bottom.getRGB(0, ITEM));

        BufferedImage top = anchored(pack, "top");
        assertEquals(2 * ITEM, top.getHeight());
        assertEquals(BIG, top.getRGB(0, 0));
        assertEquals(RED, top.getRGB(0, ITEM));
    }

    private BufferedImage anchored(Pack pack, String edge) {
        return MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"margin":0,"regions":[
              {"name":"a","type":"item","item":"testpack:item/simple","at":[0,0]},
              {"name":"b","type":"item","item":"testpack:item/big","anchor":{"to":"a","edge":"%s"}}]}"""
            .formatted(edge), pack.id(), pack.repository()).render(null).getImage();
    }

    @Test
    @DisplayName("center and end alignment offset the member on the anchor's perpendicular axis")
    void anchorAlignment() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");
        // The container target is 264 GUI-canvas px tall (176x132 GUI px at pixelSize 2); the item
        // member is 256 px tall, so center offsets it by (264 - 256) / 2 = 4 and end by 8.
        BufferedImage center = alignedRight(pack, "center");
        assertEquals(RED, center.getRGB(352, 4), "center align drops the member 4 px");
        assertEquals(0, alpha(center, 352, 3), "nothing above the centered member");

        BufferedImage end = alignedRight(pack, "end");
        assertEquals(RED, end.getRGB(352, 8), "end align drops the member 8 px");
        assertEquals(0, alpha(end, 352, 7), "nothing above the end-aligned member");
    }

    private BufferedImage alignedRight(Pack pack, String align) {
        return MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"margin":0,"regions":[
              {"name":"box","type":"container","recipe":{"rows":1},"at":[0,0]},
              {"name":"it","type":"item","item":"testpack:item/simple",
               "anchor":{"to":"box","edge":"right","align":"%s"}}]}"""
            .formatted(align), pack.id(), pack.repository()).render(null).getImage();
    }

    @Test
    @DisplayName("an anchor offset shifts the member last, in scaled GUI px")
    void anchorOffset() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");
        BufferedImage image = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"margin":0,"regions":[
              {"name":"a","type":"item","item":"testpack:item/simple","at":[0,0]},
              {"name":"b","type":"item","item":"testpack:item/big",
               "anchor":{"to":"a","edge":"right","offset":[10,20]}}]}""",
            pack.id(), pack.repository()).render(null).getImage();
        // b sits at (256 + 10*2, 0 + 20*2) = (276, 40)
        assertEquals(BIG, image.getRGB(276, 40));
        assertEquals(RED, image.getRGB(0, 0));
    }

    @Test
    @DisplayName("margin insets the whole layout by scaled GUI px on every side")
    void marginInset() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");
        BufferedImage image = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"scale":1,"margin":5,"regions":[
              {"name":"a","type":"item","item":"testpack:item/simple","at":[0,0]}]}""",
            pack.id(), pack.repository()).render(null).getImage();
        assertEquals(ITEM + 20, image.getWidth(), "5 GUI px * pixelSize 2 on both sides");
        assertEquals(ITEM + 20, image.getHeight());
        assertEquals(RED, image.getRGB(10, 10));
        assertEquals(0, alpha(image, 0, 0), "the margin is transparent");
        assertEquals(0, alpha(image, 9, 9));
    }

    @Test
    @DisplayName("z orders the draw stack, with declaration order breaking ties")
    void zOrderAndDeclarationTie() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");

        BufferedImage higherWins = overlap(pack, 0, 5);
        assertEquals(BIG, higherWins.getRGB(0, 0), "the higher-z member draws on top");

        BufferedImage lowerLoses = overlap(pack, 5, 0);
        assertEquals(RED, lowerLoses.getRGB(0, 0), "raising a's z puts it on top");

        BufferedImage tie = overlap(pack, 0, 0);
        assertEquals(BIG, tie.getRGB(0, 0), "equal z draws in declaration order, b last");
    }

    private BufferedImage overlap(Pack pack, int za, int zb) {
        return MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"margin":0,"regions":[
              {"name":"a","type":"item","item":"testpack:item/simple","at":[0,0],"z":%d},
              {"name":"b","type":"item","item":"testpack:item/big","at":[0,0],"z":%d}]}"""
            .formatted(za, zb), pack.id(), pack.repository()).render(null).getImage();
    }

    @Test
    @DisplayName("a nested row lays its members out with the default spacing in scaled GUI px")
    void nestedRow() {
        Pack pack = registerPack(FixturePacks::writeDefaultPack, "test:scene");
        BufferedImage image = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"scale":1,"margin":0,"regions":[
              {"name":"r","type":"row","regions":[
                {"name":"c1","type":"item","item":"testpack:item/simple"},
                {"name":"c2","type":"item","item":"testpack:item/big"}]}]}""",
            pack.id(), pack.repository()).render(null).getImage();
        // default spacing 6 GUI px * pixelSize 2 = 12 canvas px between the two 256 px members
        assertEquals(ITEM + 12 + ITEM, image.getWidth());
        assertEquals(ITEM, image.getHeight());
        assertEquals(RED, image.getRGB(0, 0));
        assertEquals(BIG, image.getRGB(ITEM + 12, 0));
        assertEquals(0, alpha(image, ITEM, 0), "the spacing gap is transparent");
    }

    @Test
    @DisplayName("a slot-cell grid pads every cell to the 18 GUI-px slot pitch")
    void slotCellGridPitch() {
        // Two blank HUD members are each 2x26 canvas px - smaller than the 18 GUI-px (36 canvas px)
        // slot cell, so both cells pad to 36x36 and the two-column grid is 72x36.
        BufferedImage image = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"scale":1,"regions":[
              {"name":"g","type":"grid","columns":2,"cell":"slot","spacing":0,"regions":[
                {"name":"h1","type":"hud","lines":[[]],"gui_width":1},
                {"name":"h2","type":"hud","lines":[[]],"gui_width":1}]}]}""")
            .render(null).getImage();
        assertEquals(72, image.getWidth());
        assertEquals(36, image.getHeight());
    }

    // ------------------------------------------------------------- bindings

    @Test
    @DisplayName("a HUD region renders")
    void hudRenders() {
        GeneratedObject result = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"regions":[
              {"name":"hud","type":"hud","lines":[[{"text":"Hello","color":"#ffffff"}]]}]}""")
            .render(null);
        assertFalse(result.isAnimated());
        assertTrue(result.getImage().getWidth() > 0);
        assertTrue(result.getImage().getHeight() > 0);
    }

    @Test
    @DisplayName("a simple-form tooltip renders")
    void tooltipSimpleFormRenders() {
        GeneratedObject result = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"regions":[
              {"name":"My Item","type":"tooltip","rarity":"epic","lore":"&7First line\\n&7Second line"}]}""")
            .render(null);
        assertFalse(result.isAnimated());
        assertTrue(result.getImage().getWidth() > 0);
        assertTrue(result.getImage().getHeight() > 0);
    }

    @Test
    @DisplayName("a container region renders from a minimal recipe against a pack")
    void containerRenders() {
        Pack pack = registerPack(FixturePacks::writeContainerPack, "test:scenecontainer");
        GeneratedObject result = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"regions":[
              {"name":"menu","type":"container",
               "recipe":{"rows":1,"slots":{"1":"testpack:item/marker"}}}]}""",
            pack.id(), pack.repository()).render(null);
        assertFalse(result.isAnimated());
        // 176 GUI px wide, 132 GUI px tall, at pixelSize 2
        assertEquals(352, result.getImage().getWidth());
        assertEquals(264, result.getImage().getHeight());
    }

    @Test
    @DisplayName("an animated container region turns the whole scene into a GIF")
    void animatedContainerMakesSceneAnimated() {
        Pack pack = registerPack(FixturePacks::writeAnimatedContainerPack, "test:sceneanim");
        GeneratedObject result = MinecraftSceneGenerator.fromScene("""
            {"schema_version":1,"regions":[
              {"name":"menu","type":"container","recipe":{"rows":1},"animated_textures":true}]}""",
            pack.id(), pack.repository()).render(null);
        assertTrue(result.isAnimated());
        List<Integer> delays = GifBytes.frameDelaysCentiseconds(result.getGifData());
        assertFalse(delays.isEmpty());
        // Two 256x256 background frames at frametime 2 (2 ticks = 100 ms = 10 centiseconds each)
        assertTrue(delays.stream().allMatch(delay -> delay == 10), "each frame holds 10 centiseconds: " + delays);
    }
}
