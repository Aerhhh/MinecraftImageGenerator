package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.AnimationTimeline;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the inventory generator's animated-textures path: texture-animated slot items drive one
 * shared scene timeline (LCM of the item cycles), static scene parts stay identical across
 * frames, and the flag stays inert without animated content.
 */
class MinecraftInventoryGeneratorAnimatedTexturesTest {

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void registerElementsPack() {
        FixturePacks.writeElementsPack(packDir);
        repository = new PackRepository();
        packId = repository.register("test:pack", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftInventoryGenerator.Builder builder(String inventoryString) {
        return new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(2)
            .withInventoryString(inventoryString)
            .withPack(packId)
            .withPackRepository(repository);
    }

    @Test
    void twoItemsWithDifferentCyclesShareTheLcmTimeline() {
        // animated_quad cycles in 9 ticks (3 frames x 3), animated_quad_fast in 4 (2 x 2):
        // LCM 36, sampled at every multiple of 2 or 3 - 24 scene steps.
        GeneratedObject inventory = builder(
            "testpack:item/animated_quad:1%%testpack:item/animated_quad_fast:2")
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(inventory.isAnimated());
        assertEquals(24, inventory.getAnimationFrames().size());
        assertNotNull(inventory.getFrameDelaysMs());
        int totalMs = inventory.getFrameDelaysMs().stream().mapToInt(Integer::intValue).sum();
        assertEquals(36 * AnimationTimeline.MILLIS_PER_TICK, totalMs, "delays sum to the 36-tick cycle");
        assertNotNull(inventory.getGifData());

        // Slot pixel pins: at scene tick t, slot 1 shows animated_quad's frames-list position
        // (t mod 9) / 3 over [2, 0, 1] (blue, red, green) and slot 2 animated_quad_fast's
        // (t mod 4) / 2 over default order (orange, azure).
        int scaleFactor = MinecraftInventoryGenerator.getScaleFactor();
        int slotSize = 18 * scaleFactor;
        int borderSize = 7 * scaleFactor;
        int itemSize = 16 * scaleFactor;
        int padding = (slotSize - itemSize) / 2;
        int slot1CenterX = borderSize + padding + itemSize / 2;
        int slot2CenterX = borderSize + slotSize + padding + itemSize / 2;
        int centerY = borderSize + padding + itemSize / 2;
        int[] quadColors = {0xFF0000FF, 0xFFFF0000, 0xFF00FF00};
        int[] fastColors = {0xFFFF8800, 0xFF0088FF};

        int tick = 0;
        for (int frame = 0; frame < inventory.getAnimationFrames().size(); frame++) {
            BufferedImage image = inventory.getAnimationFrames().get(frame);
            assertEquals(quadColors[(tick % 9) / 3], image.getRGB(slot1CenterX, centerY),
                "slot 1 at tick " + tick);
            assertEquals(fastColors[(tick % 4) / 2], image.getRGB(slot2CenterX, centerY),
                "slot 2 at tick " + tick);
            tick += inventory.getFrameDelaysMs().get(frame) / AnimationTimeline.MILLIS_PER_TICK;
        }
    }

    @Test
    void animatedFlatSpriteSlotItemAnimatesThroughTheItemPipeline() {
        // A FLAT (layer0 sprite) animated slot item, not an elements model: it flows through the
        // item pipeline (getFrameDelaysMs carried into the scene timeline), not the elements
        // raster path. A mutant that dropped its per-frame delays would leave the scene static.
        GeneratedObject inventory = builder("testpack:item/animated_sprite:1")
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(inventory.isAnimated());
        // sprite_flipbook: frametime 3, default order, 3 frames -> cycle 9, three 150 ms steps.
        assertEquals(3, inventory.getAnimationFrames().size());
        assertEquals(List.of(150, 150, 150), inventory.getFrameDelaysMs());
        int scaleFactor = MinecraftInventoryGenerator.getScaleFactor();
        int slotSize = 18 * scaleFactor;
        int borderSize = 7 * scaleFactor;
        int itemSize = 16 * scaleFactor;
        int padding = (slotSize - itemSize) / 2;
        int centerX = borderSize + padding + itemSize / 2;
        int centerY = borderSize + padding + itemSize / 2;
        assertEquals(0xFFCC0000, inventory.getAnimationFrames().get(0).getRGB(centerX, centerY), "frame 0 red");
        assertEquals(0xFF00CC00, inventory.getAnimationFrames().get(1).getRGB(centerX, centerY), "frame 1 green");
        assertEquals(0xFF0000CC, inventory.getAnimationFrames().get(2).getRGB(centerX, centerY), "frame 2 blue");
    }

    @Test
    void staticScenePartsAreIdenticalAcrossFrames() {
        GeneratedObject inventory = builder(
            "testpack:item/animated_quad:1%%testpack:item/plain_sprite:2")
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(inventory.isAnimated());
        int scaleFactor = MinecraftInventoryGenerator.getScaleFactor();
        int slotSize = 18 * scaleFactor;
        int borderSize = 7 * scaleFactor;
        int itemSize = 16 * scaleFactor;
        int padding = (slotSize - itemSize) / 2;
        int slot2CenterX = borderSize + slotSize + padding + itemSize / 2;
        int centerY = borderSize + padding + itemSize / 2;

        BufferedImage first = inventory.getAnimationFrames().getFirst();
        for (BufferedImage frame : inventory.getAnimationFrames()) {
            assertEquals(0xFFAA5500, frame.getRGB(slot2CenterX, centerY), "the static slot item never changes");
            assertEquals(first.getRGB(0, 0), frame.getRGB(0, 0), "the border chrome never changes");
        }
    }

    @Test
    void flagOffRendersTheAnimatedItemStatic() {
        GeneratedObject inventory = builder("testpack:item/animated_quad:1").build().generate();

        assertFalse(inventory.isAnimated());
        int scaleFactor = MinecraftInventoryGenerator.getScaleFactor();
        int slotSize = 18 * scaleFactor;
        int borderSize = 7 * scaleFactor;
        int itemSize = 16 * scaleFactor;
        int padding = (slotSize - itemSize) / 2;
        assertEquals(0xFF0000FF,
            inventory.getImage().getRGB(borderSize + padding + itemSize / 2, borderSize + padding + itemSize / 2),
            "the static render shows the first-frame crop (flipbook frame 2, blue)");
    }

    @Test
    void cacheKeysDifferAcrossTheAnimatedTexturesFlag() {
        MinecraftInventoryGenerator off = builder("testpack:item/animated_quad:1").build();
        MinecraftInventoryGenerator on = builder("testpack:item/animated_quad:1")
            .withAnimatedTextures(true).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(off), GeneratorCacheKey.fromGenerator(on),
            "the flag changes rendered output, so it must enter the render cache key");
    }

    @Test
    void flagOnWithoutAnimatedItemsRendersTheExactStaticImage() {
        BufferedImage off = builder("testpack:item/plain_sprite:1").build().generate().getImage();
        GeneratedObject on = builder("testpack:item/plain_sprite:1")
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertFalse(on.isAnimated());
        ImageAssertions.assertPixelsEqual(off, on.getImage(), "static scenes are untouched");
    }
}
