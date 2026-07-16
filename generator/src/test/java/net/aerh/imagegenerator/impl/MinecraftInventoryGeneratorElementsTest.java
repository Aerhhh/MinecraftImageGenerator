package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.item.InventoryItem;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Elements-model slot items in the INVENTORY composite, pinned for parity with
 * {@link MinecraftContainerGeneratorElementsTest} against the same fixture pack. Geometry for a
 * bordered 1x1 inventory at the composite's static scale factor {@code sf}: the slot rect
 * origin is (7sf, 7sf) and the 16-GUI-px item box origin is (8sf, 8sf).
 *
 * <p>The composites deliberately differ on {@code oversized_in_gui}: the container anchors the
 * full-extent art on the slot center (overflow spans neighboring slots), while the inventory
 * clips every slot visual at its item box.
 */
class MinecraftInventoryGeneratorElementsTest {

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

    private BufferedImage renderInventory(String inventoryString) {
        return new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(1)
            .withPack(packId)
            .withPackRepository(repository)
            .withInventoryString(inventoryString)
            .build()
            .generate()
            .getImage();
    }

    @Test
    void elementsItemRendersInTheItemBox() {
        int sf = MinecraftInventoryGenerator.getScaleFactor();
        BufferedImage image = renderInventory("testpack:item/flat:1");
        assertEquals(0xFFFF0000, image.getRGB(12 * sf, 16 * sf), "paint's red half fills the box's left side");
        assertEquals(0xFF0000FF, image.getRGB(20 * sf, 16 * sf), "paint's blue half fills the box's right side");
    }

    @Test
    void oversizedArtClipsAtTheSlotBoxInInventories() {
        // The oversized flag changes nothing in an inventory: the render is pixel-identical to
        // the same model WITHOUT the flag, both showing the middle of the scale-2 art.
        ImageAssertions.assertPixelsEqual(
            renderInventory("testpack:item/clipped:1"),
            renderInventory("testpack:item/oversized:1"),
            "inventory render of oversized vs clipped model");
    }

    @Test
    void sameFixtureItemSpansNeighborsInContainersButClipsInInventories() {
        // The documented composite difference, pinned against ONE fixture item.
        BufferedImage container = new MinecraftContainerGenerator.Builder()
            .withRows(3)
            .withPack(packId)
            .withPackRepository(repository)
            .withSlot(14, "testpack:item/oversized")
            .build().generate().getImage();
        assertEquals(0xFFFF0000, container.getRGB(150, 88),
            "container: the red half reaches the left neighbor slot");

        int sf = MinecraftInventoryGenerator.getScaleFactor();
        BufferedImage inventory = renderInventory("testpack:item/oversized:1");
        assertEquals(0xFFFF0000, inventory.getRGB(8 * sf, 16 * sf),
            "inventory: the art starts at the item box edge");
        assertNotEquals(0xFFFF0000, inventory.getRGB(8 * sf - 2, 16 * sf),
            "inventory: no art escapes the item box");
    }

    @Test
    void modifiersOnElementsItemsAreIgnoredButStillRender() {
        int sf = MinecraftInventoryGenerator.getScaleFactor();
        BufferedImage image = renderInventory("testpack:item/flat,enchant:1");
        assertEquals(0xFFFF0000, image.getRGB(12 * sf, 16 * sf),
            "the raster renders; the enchant modifier is a no-op like in the container");
    }

    @Test
    void durabilityOnlyElementsSpecRendersTheRasterWithoutADurabilityBar() {
        // Durability is an ignored modifier for elements renders like enchant and hover: the
        // slot visual is pixel-identical to the unmodified spec (no bar drawn), and the shared
        // ignored-modifiers warning covers the durability-only case (pinned in
        // MinecraftContainerGeneratorElementsTest).
        MinecraftInventoryGenerator generator = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(1)
            .withPack(packId)
            .withPackRepository(repository)
            .build();
        InventoryItem plain = new InventoryItem(1, 1, "testpack:item/flat", null, null);
        InventoryItem durabilityOnly = new InventoryItem(1, 1, "testpack:item/flat", null, 50);
        generator.processItem(plain, null);
        generator.processItem(durabilityOnly, null);
        ImageAssertions.assertPixelsEqual(plain.getItemImage(), durabilityOnly.getItemImage(),
            "durability-only elements spec renders the bare raster");
    }

    @Test
    void spriteItemsKeepTheClassicSlotPipeline() {
        int sf = MinecraftInventoryGenerator.getScaleFactor();
        BufferedImage image = renderInventory("testpack:item/plain_sprite:1");
        assertEquals(0xFFAA5500, image.getRGB(16 * sf, 16 * sf), "layer0 sprite fills the item box");
    }

    @Test
    void packItemPathContainingPlayerHeadStillRendersElements() {
        // Parity with the container: only the exact vanilla player_head id routes to the head
        // pipeline; a pack ref merely containing the substring renders its elements model.
        int sf = MinecraftInventoryGenerator.getScaleFactor();
        BufferedImage image = renderInventory("testpack:item/player_head_frame:1");
        assertEquals(0xFFFF0000, image.getRGB(12 * sf, 16 * sf), "the elements model renders, not a head");
        assertEquals(0xFF0000FF, image.getRGB(20 * sf, 16 * sf));
    }

    @Test
    void renderingIsDeterministic() {
        ImageAssertions.assertPixelsEqual(
            renderInventory("testpack:item/oversized:1"),
            renderInventory("testpack:item/oversized:1"),
            "repeat inventory render");
    }
}
