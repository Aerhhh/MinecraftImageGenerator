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
 * Pins the container generator's animated-textures path: an animated pack container background
 * and texture-animated slot items drive one shared scene timeline; static scenes and the
 * flag-off path render exactly as before.
 */
class MinecraftContainerGeneratorAnimationTest {

    @TempDir
    Path packDir;

    private PackId register(PackRepository repository, Path root) {
        return repository.register("test:pack", PackSource.directory(root, PackLimits.fromSystemProperties()));
    }

    @Test
    void animatedContainerBackgroundAnimatesTheScene() {
        FixturePacks.writeAnimatedContainerPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = new MinecraftContainerGenerator.Builder()
            .withRows(1)
            .withSlot(1, "testpack:item/marker")
            .withPack(packId)
            .withPackRepository(repository)
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        assertEquals(2, generated.getAnimationFrames().size());
        assertEquals(List.of(100, 100), generated.getFrameDelaysMs());
        assertNotNull(generated.getGifData());

        // pixelSize 2: a background pixel outside the slot grid cycles with the flipbook, the
        // static marker item in slot 1 (interior origin GUI (8, 18)) never changes.
        BufferedImage frame0 = generated.getAnimationFrames().get(0);
        BufferedImage frame1 = generated.getAnimationFrames().get(1);
        assertEquals(0xFF113355, frame0.getRGB(300, 4));
        assertEquals(0xFF553311, frame1.getRGB(300, 4));
        int itemCenterX = (8 + 8) * 2;
        int itemCenterY = (18 + 8) * 2;
        assertEquals(0xFFAA5500, frame0.getRGB(itemCenterX, itemCenterY));
        assertEquals(0xFFAA5500, frame1.getRGB(itemCenterX, itemCenterY));
    }

    @Test
    void animatedSlotItemsWithDifferentCyclesShareTheLcmTimeline() {
        FixturePacks.writeElementsPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = new MinecraftContainerGenerator.Builder()
            .withRows(1)
            .withSlot(1, "testpack:item/animated_quad")
            .withSlot(2, "testpack:item/animated_quad_fast:3")
            .withSlot(3, "testpack:item/plain_sprite")
            .withPack(packId)
            .withPackRepository(repository)
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        // Cycles 9 and 4: LCM 36, sampled at every multiple of 2 or 3 - 24 scene steps.
        assertEquals(24, generated.getAnimationFrames().size());
        int totalMs = generated.getFrameDelaysMs().stream().mapToInt(Integer::intValue).sum();
        assertEquals(36 * AnimationTimeline.MILLIS_PER_TICK, totalMs);

        int[] quadColors = {0xFF0000FF, 0xFFFF0000, 0xFF00FF00};
        int[] fastColors = {0xFFFF8800, 0xFF0088FF};
        int pixelSize = 2;
        int slot1X = (8 + 8) * pixelSize;
        int slot2X = (8 + 18 + 8) * pixelSize;
        int slot3X = (8 + 36 + 8) * pixelSize;
        int slotY = (18 + 8) * pixelSize;

        int tick = 0;
        for (int frame = 0; frame < generated.getAnimationFrames().size(); frame++) {
            BufferedImage image = generated.getAnimationFrames().get(frame);
            assertEquals(quadColors[(tick % 9) / 3], image.getRGB(slot1X, slotY), "slot 1 at tick " + tick);
            assertEquals(fastColors[(tick % 4) / 2], image.getRGB(slot2X, slotY), "slot 2 at tick " + tick);
            assertEquals(0xFFAA5500, image.getRGB(slot3X, slotY), "static slot 3 at tick " + tick);
            tick += generated.getFrameDelaysMs().get(frame) / AnimationTimeline.MILLIS_PER_TICK;
        }
    }

    @Test
    void animatedFlatSpriteSlotItemAnimatesThroughTheItemPipeline() {
        // A FLAT (layer0 sprite) animated slot item, not an elements model: it drives the scene
        // through the item pipeline / AnimatedImage slot path. A mutant that returned a
        // StaticImage for it (or dropped its per-frame delays) would leave the scene static.
        FixturePacks.writeElementsPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = new MinecraftContainerGenerator.Builder()
            .withRows(1)
            .withSlot(1, "testpack:item/animated_sprite")
            .withPack(packId)
            .withPackRepository(repository)
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        // sprite_flipbook: frametime 3, default order, 3 frames -> cycle 9, three 150 ms steps.
        assertEquals(3, generated.getAnimationFrames().size());
        assertEquals(List.of(150, 150, 150), generated.getFrameDelaysMs());
        int centerX = (8 + 8) * 2;
        int centerY = (18 + 8) * 2;
        assertEquals(0xFFCC0000, generated.getAnimationFrames().get(0).getRGB(centerX, centerY), "frame 0 red");
        assertEquals(0xFF00CC00, generated.getAnimationFrames().get(1).getRGB(centerX, centerY), "frame 1 green");
        assertEquals(0xFF0000CC, generated.getAnimationFrames().get(2).getRGB(centerX, centerY), "frame 2 blue");
    }

    @Test
    void flagOnWithoutAnimatedContentRendersTheExactStaticImage() {
        FixturePacks.writeContainerArtPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject off = new MinecraftContainerGenerator.Builder()
            .withRows(2)
            .withPack(packId)
            .withPackRepository(repository)
            .build()
            .generate();
        GeneratedObject on = new MinecraftContainerGenerator.Builder()
            .withRows(2)
            .withPack(packId)
            .withPackRepository(repository)
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertFalse(on.isAnimated());
        ImageAssertions.assertPixelsEqual(off.getImage(), on.getImage(), "static scenes are untouched");
    }

    @Test
    void cacheKeysDifferAcrossTheAnimatedTexturesFlag() {
        MinecraftContainerGenerator off = new MinecraftContainerGenerator.Builder()
            .withRows(1).withSlot(1, "testpack:item/animated_quad").build();
        MinecraftContainerGenerator on = new MinecraftContainerGenerator.Builder()
            .withRows(1).withSlot(1, "testpack:item/animated_quad")
            .withAnimatedTextures(true).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(off), GeneratorCacheKey.fromGenerator(on),
            "the flag changes rendered output, so it must enter the render cache key");
    }

    @Test
    void flagOffRendersTheAnimatedBackgroundStatic() {
        FixturePacks.writeAnimatedContainerPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = new MinecraftContainerGenerator.Builder()
            .withRows(1)
            .withPack(packId)
            .withPackRepository(repository)
            .build()
            .generate();

        assertFalse(generated.isAnimated());
        assertEquals(0xFF113355, generated.getImage().getRGB(300, 4), "the first-frame crop paints");
    }
}
