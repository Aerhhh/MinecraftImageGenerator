package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.item.InventoryItem;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.CustomModelDatas;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wave 4: elements-model slot items in the container compositor, including oversized art
 * anchored on the slot center. Geometry at scaleFactor 1 (pixelSize 2, no title): the GUI rect
 * is 352x336 canvas px for 3 rows; slot 14 (row 1, column 4) has its interior origin at GUI
 * (80, 36) = canvas (160, 72).
 */
class MinecraftContainerGeneratorElementsTest {

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void registerFixturePack() {
        FixturePacks.writeElementsPack(packDir);
        repository = new PackRepository();
        packId = repository.register("test:elements", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftContainerGenerator.Builder builder() {
        return new MinecraftContainerGenerator.Builder()
            .withRows(3)
            .withPack(packId)
            .withPackRepository(repository);
    }

    @Test
    void elementsItemRendersDirectlyAtThePixelSize() {
        BufferedImage canvas = builder().withSlot(14, "testpack:item/flat").build().generate().getImage();
        assertEquals(352, canvas.getWidth());
        assertEquals(336, canvas.getHeight());
        assertEquals(0xFFFF0000, canvas.getRGB(168, 88), "paint's red half fills the slot's left side");
        assertEquals(0xFF0000FF, canvas.getRGB(184, 88), "paint's blue half fills the slot's right side");
    }

    @Test
    void oversizedArtAnchorsOnTheSlotCenterAndSpansNeighbors() {
        BufferedImage canvas = builder().withSlot(14, "testpack:item/oversized").build().generate().getImage();
        assertEquals(352, canvas.getWidth(), "the canvas never expands for oversized item art");
        assertEquals(336, canvas.getHeight());
        // The 32x32 GUI px art spans gui [-8, 24) around the slot, reaching into both
        // horizontal neighbors.
        assertEquals(0xFFFF0000, canvas.getRGB(150, 88), "red half reaches the left neighbor slot");
        assertEquals(0xFF0000FF, canvas.getRGB(200, 88), "blue half reaches the right neighbor slot");
    }

    @Test
    void oversizedArtInACornerSlotClipsAtTheCanvasEdgeWithoutExpanding() {
        BufferedImage canvas = builder().withSlot(1, "testpack:item/oversized").build().generate().getImage();
        assertEquals(352, canvas.getWidth());
        assertEquals(336, canvas.getHeight());
        // Slot 1 interior is at GUI (8, 18); the art's top-left corner lands at GUI (0, 10),
        // canvas (0, 20).
        assertEquals(0xFFFF0000, canvas.getRGB(0, 40), "the art reaches the canvas edge");
    }

    @Test
    void spriteItemsKeepTheClassicSlotPipeline() {
        BufferedImage canvas = builder().withSlot(14, "testpack:item/plain_sprite").build().generate().getImage();
        assertEquals(0xFFAA5500, canvas.getRGB(176, 88), "layer0 sprite fills the slot interior");
    }

    @Test
    void stackCountBadgeStillDrawsOverElementsItems() {
        BufferedImage single = builder().withSlot(14, "testpack:item/flat").build().generate().getImage();
        BufferedImage stacked = builder().withSlot(14, "testpack:item/flat:5").build().generate().getImage();
        ImageAssertions.assertPixelsDiffer(single, stacked, "the count badge must alter the stacked render");
    }

    @Test
    void modifiersOnElementsItemsAreIgnoredButStillRender() {
        BufferedImage canvas = builder().withSlot(14, "testpack:item/flat,enchant").build().generate().getImage();
        assertEquals(0xFFFF0000, canvas.getRGB(168, 88), "the raster renders; the enchant modifier is a no-op");
    }

    @Test
    void brokenElementsItemsFailLoudly() {
        MinecraftContainerGenerator generator = builder().withSlot(14, "testpack:item/unknown_tint").build();
        assertThrows(PackResolveException.class, generator::generate);
    }

    @Test
    void elementRotationsRenderInASlotWithoutAnyFlag() {
        // The rotated fixture's 45-degree element rotation renders through the orthographic
        // pipeline in strict mode: the white diamond (side-shaded 0.8, no gui_light declared)
        // covers the slot center while the original quad corners rotate away.
        BufferedImage canvas = builder().withSlot(14, "testpack:item/rotated").build().generate().getImage();
        assertEquals(0xFFCCCCCC, canvas.getRGB(176, 88), "the diamond covers the slot center");
    }

    @Test
    void customModelDataDispatchInASlotEvaluatesWithEmptyData() {
        // Slot specs carry no custom model data, so the gauge item resolves through its
        // missing-float fallback (the gray quad) - the evaluation table's fallback row, live in
        // a container slot.
        BufferedImage canvas = builder().withSlot(14, "testpack:item/gauge").build().generate().getImage();
        assertEquals(0xFF808080, canvas.getRGB(176, 88));
    }

    @Test
    void slotCustomModelDataDrivesTheDispatch() {
        // The same gauge item in two slots: slot 13 without data renders the gray fallback,
        // slot 14 with charge 2.0 dispatches the blue quad - custom model data reaches the
        // container-side elements evaluation end to end and is keyed per slot.
        BufferedImage canvas = builder()
            .withSlot(13, "testpack:item/gauge")
            .withSlot(14, "testpack:item/gauge", CustomModelDatas.floats(2.0f))
            .build().generate().getImage();
        assertEquals(0xFF808080, canvas.getRGB(140, 88), "slot 13 keeps the empty-data fallback");
        assertEquals(0xFF0000FF, canvas.getRGB(176, 88), "slot 14 dispatches on its own data");
    }

    @Test
    void slotCustomModelDataDrivesFlatSpriteDispatch() {
        // sprite_named selects between LAYER0 SPRITE models on custom_model_data string 0: the
        // data-evaluated Sprite result must render, not be discarded for a data-less re-resolve
        // through the legacy path (which would paint the blue fallback in both slots).
        BufferedImage canvas = builder()
            .withSlot(13, "testpack:item/sprite_named")
            .withSlot(14, "testpack:item/sprite_named", CustomModelDatas.strings("ruby"))
            .build().generate().getImage();
        assertEquals(0xFF0000FF, canvas.getRGB(140, 88), "slot 13 renders the fallback blue sprite");
        assertEquals(0xFFFF0000, canvas.getRGB(176, 88), "slot 14 renders the ruby-selected red sprite");
    }

    @Test
    void reSettingASlotResetsItsCustomModelData() {
        BufferedImage canvas = builder()
            .withSlot(14, "testpack:item/gauge", CustomModelDatas.floats(2.0f))
            .withSlot(14, "testpack:item/gauge")
            .build().generate().getImage();
        assertEquals(0xFF808080, canvas.getRGB(176, 88), "the replacement slot spec carries no data");
    }

    @Test
    void packItemPathContainingPlayerHeadStillRendersElements() {
        // Only the exact vanilla player_head id belongs to the dedicated head pipeline; a pack
        // ref merely containing the substring is an ordinary elements model.
        BufferedImage canvas = builder().withSlot(14, "testpack:item/player_head_frame")
            .build().generate().getImage();
        assertEquals(0xFFFF0000, canvas.getRGB(168, 88), "the elements model renders, not a head");
        assertEquals(0xFF0000FF, canvas.getRGB(184, 88));
    }

    @Test
    void renderingIsDeterministic() {
        BufferedImage first = builder().withSlot(14, "testpack:item/oversized").build().generate().getImage();
        BufferedImage second = builder().withSlot(14, "testpack:item/oversized").build().generate().getImage();
        ImageAssertions.assertPixelsEqual(first, second, "repeat container render");
    }

    @Test
    void fullGuiRotationsRenderTheRotatedSlotItem() {
        // badspin's [30,225,0] shows the north face (backpaint) as a true rotated
        // parallelogram: at pixelSize 2 the slot pixel at slot-local gui (12.25, 8.25)
        // inverse-maps to face fractions (0.38, 0.26) and samples backpaint's yellow left
        // half, while slot-local gui (4.25, 8.25) falls outside the face and shows the
        // container background through.
        BufferedImage rotated = builder().withSlot(14, "testpack:item/badspin")
            .withFullGuiRotations(true).build().generate().getImage();
        BufferedImage empty = builder().build().generate().getImage();
        assertEquals(0xFFFFFF00, rotated.getRGB(184, 88), "backpaint lands right of the pivot");
        assertEquals(empty.getRGB(168, 88), rotated.getRGB(168, 88),
            "the vacated slot area shows the untouched container background");
    }

    @Test
    void unsupportedRotationInASlotStillThrowsWithoutTheFlag() {
        MinecraftContainerGenerator generator = builder().withSlot(14, "testpack:item/badspin").build();
        assertThrows(PackResolveException.class, generator::generate);
    }

    @Test
    void cacheKeysDifferAcrossFullGuiRotations() {
        MinecraftContainerGenerator strict = builder().withSlot(14, "testpack:item/badspin").build();
        MinecraftContainerGenerator full = builder().withSlot(14, "testpack:item/badspin")
            .withFullGuiRotations(true).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(strict), GeneratorCacheKey.fromGenerator(full),
            "the full-rotation flag changes rendered pixels, so it must enter the render cache key");
    }

    @Test
    void ignoredModifiersWarningCoversDurabilityAndExtraContent() {
        // The shared warn helper both composites route through: it must fire for EVERY ignored
        // modifier the classic pipeline would apply - the extra content flags AND a
        // durability-only spec whose bar is silently dropped for elements renders.
        assertNull(MinecraftContainerGenerator.warnIgnoredElementsModifiers(
            new InventoryItem(1, 1, "testpack:item/flat", null, null)));
        assertNull(MinecraftContainerGenerator.warnIgnoredElementsModifiers(
            new InventoryItem(1, 1, "testpack:item/flat", " ", null)), "blank content is no modifier");
        assertEquals("enchant", MinecraftContainerGenerator.warnIgnoredElementsModifiers(
            new InventoryItem(1, 1, "testpack:item/flat", "enchant", null)));
        assertEquals("durability 50%", MinecraftContainerGenerator.warnIgnoredElementsModifiers(
            new InventoryItem(1, 1, "testpack:item/flat", null, 50)),
            "a durability-only spec warns too: its bar is dropped for elements renders");
        assertEquals("enchant, durability 50%", MinecraftContainerGenerator.warnIgnoredElementsModifiers(
            new InventoryItem(1, 1, "testpack:item/flat", "enchant", 50)));
    }
}
