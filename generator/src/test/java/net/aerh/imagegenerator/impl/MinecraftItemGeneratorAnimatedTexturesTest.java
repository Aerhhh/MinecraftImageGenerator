package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.effect.EffectPipeline;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.GifBytes;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the item generator's animated-textures path: animated pack textures produce animated GIF
 * output with per-frame delays, while the flag stays inert everywhere else.
 */
class MinecraftItemGeneratorAnimatedTexturesTest {

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void registerFixturePack() {
        FixturePacks.writeDefaultPack(packDir);
        repository = new PackRepository();
        packId = repository.register("test:pack", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftItemGenerator.Builder packBuilder(String itemRef) {
        return new MinecraftItemGenerator.Builder()
            .withPack(packId)
            .withPackRepository(repository)
            .withItem(itemRef);
    }

    @Test
    void animatedSpriteRendersOneFramePerTimelineStep() {
        GeneratedObject generated = packBuilder("testpack:item/animated")
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        assertEquals(3, generated.getAnimationFrames().size());
        // frametime 3 = 150 ms per frame; frames list [2, 0, 1] = blue, red, green.
        assertEquals(List.of(150, 150, 150), generated.getFrameDelaysMs());
        assertEquals(150, generated.getFrameDelayMs());
        int[] expected = {0xFF0000FF, 0xFFFF0000, 0xFF00FF00};
        for (int frame = 0; frame < 3; frame++) {
            BufferedImage image = generated.getAnimationFrames().get(frame);
            assertEquals(256, image.getWidth());
            assertEquals(expected[frame], image.getRGB(128, 128), "frame " + frame);
        }
    }

    @Test
    void mixedFrameTimesHoldInTheEncodedGif() {
        GeneratedObject generated = packBuilder("testpack:item/animated_hold")
            .build()
            .generate();

        // Frames [0 (2t), 1 (2t), 0 (100t)]: 100/100/5000 ms, i.e. 10/10/500 GIF centiseconds.
        assertEquals(List.of(100, 100, 5000), generated.getFrameDelaysMs());
        assertEquals(List.of(10, 10, 500), GifBytes.frameDelaysCentiseconds(generated.getGifData()));
        assertEquals(3, generated.getAnimationFrames().size());
        assertEquals(1, GifBytes.applicationExtensionCount(generated.getGifData()),
            "the variable-delay GIF carries exactly one NETSCAPE loop block, not one per frame");
        ImageAssertions.assertPixelsEqual(
            generated.getAnimationFrames().get(0), generated.getAnimationFrames().get(2),
            "the hold frame repeats frame 0");
    }

    @Test
    void anItemWithoutAnimatedTexturesStaysStatic() {
        GeneratedObject generated = packBuilder("testpack:item/simple").build().generate();

        assertFalse(generated.isAnimated(), "a pack item with no animation mcmeta renders a static image");
        assertNull(generated.getFrameDelaysMs());
    }

    @Test
    void animatedRendersAreByteIdenticalAcrossRuns() {
        byte[] first = packBuilder("testpack:item/animated_hold")
            .build().generate().getGifData();
        byte[] second = packBuilder("testpack:item/animated_hold")
            .build().generate().getGifData();
        assertArrayEquals(first, second);
    }

    @Test
    void enchantGlintIsSuppressedOnTheAnimatedPath() {
        GeneratedObject generated = packBuilder("testpack:item/animated")
            .isEnchanted(true)
            .build()
            .generate();

        assertEquals(3, generated.getAnimationFrames().size(),
            "the texture timeline drives the output; no 182-frame glint animation");
        assertEquals(0xFF0000FF, generated.getAnimationFrames().getFirst().getRGB(128, 128),
            "no glint tint is applied");
    }

    @Test
    void animatedElementsModelAnimatesOnTheSamePath(@TempDir Path elementsDir) {
        FixturePacks.writeElementsPack(elementsDir);
        PackRepository elementsRepository = new PackRepository();
        PackId elementsPackId = elementsRepository.register("test:elements",
            PackSource.directory(elementsDir, PackLimits.fromSystemProperties()));

        GeneratedObject generated = new MinecraftItemGenerator.Builder()
            .withPack(elementsPackId)
            .withPackRepository(elementsRepository)
            .withItem("testpack:item/animated_quad")
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        assertEquals(3, generated.getAnimationFrames().size());
        assertEquals(List.of(150, 150, 150), generated.getFrameDelaysMs());
        int[] expected = {0xFF0000FF, 0xFFFF0000, 0xFF00FF00};
        for (int frame = 0; frame < 3; frame++) {
            assertEquals(expected[frame], generated.getAnimationFrames().get(frame).getRGB(128, 128),
                "frame " + frame);
        }
    }
}
