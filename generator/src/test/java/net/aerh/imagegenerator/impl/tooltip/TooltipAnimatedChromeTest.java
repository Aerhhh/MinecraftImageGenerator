package net.aerh.imagegenerator.impl.tooltip;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.GifBytes;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the tooltip generator's animated-chrome path: animated tooltip style sprites (the
 * "shiny" frame strips) drive an animated GIF, coexisting with the obfuscated-text animation.
 */
class TooltipAnimatedChromeTest {

    @TempDir
    Path packDir;

    private PackId register(PackRepository repository, Path root) {
        return repository.register("test:pack", PackSource.directory(root, PackLimits.fromSystemProperties()));
    }

    private MinecraftTooltipGenerator.Builder builder(PackRepository repository, PackId packId, String style, String lore) {
        return new MinecraftTooltipGenerator.Builder()
            .withName("Animated")
            .withRarity(Rarity.byName("NONE"))
            .withItemLore(lore)
            .withPack(packId)
            .withPackRepository(repository)
            .withTooltipStyle(style);
    }

    @Test
    void animatedStyleAnimatesTheChrome() {
        FixturePacks.writeDefaultPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = builder(repository, packId, "testpack:anim", "Lore line")
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        // The anim style background: frametime 2, frames [2, 0, 1] - three 100 ms steps.
        assertEquals(3, generated.getAnimationFrames().size());
        assertEquals(List.of(100, 100, 100), generated.getFrameDelaysMs());
        BufferedImage first = generated.getAnimationFrames().get(0);
        BufferedImage second = generated.getAnimationFrames().get(1);
        assertEquals(first.getWidth(), second.getWidth());
        boolean chromeChanged = false;
        for (int y = 0; y < first.getHeight() && !chromeChanged; y++) {
            for (int x = 0; x < first.getWidth() && !chromeChanged; x++) {
                chromeChanged = first.getRGB(x, y) != second.getRGB(x, y);
            }
        }
        assertTrue(chromeChanged, "the animated background changes between frames");
    }

    @Test
    void shinyStripLongHoldSurvivesIntoTheGif() {
        FixturePacks.writeTallAnimatedTooltipPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = builder(repository, packId, "testpack:tallstrip", "Lore line")
            .withAnimatedTextures(true)
            .build()
            .generate();

        // Frames [0..16 at 2 ticks, then frame 0 held 100 ticks]: 18 steps, the last 5000 ms.
        assertEquals(18, generated.getAnimationFrames().size());
        List<Integer> expectedDelays = new ArrayList<>(Collections.nCopies(17, 100));
        expectedDelays.add(5000);
        assertEquals(expectedDelays, generated.getFrameDelaysMs());
        List<Integer> centiseconds = GifBytes.frameDelaysCentiseconds(generated.getGifData());
        assertEquals(18, centiseconds.size());
        assertEquals(10, centiseconds.getFirst());
        assertEquals(500, centiseconds.getLast());
        ImageAssertions.assertPixelsEqual(
            generated.getAnimationFrames().getFirst(), generated.getAnimationFrames().getLast(),
            "the hold frame shows strip frame 0 again");
    }

    @Test
    void obfuscatedTextTicksOnTheSharedTimeline() {
        FixturePacks.writeDefaultPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = builder(repository, packId, "testpack:anim", "&kAB")
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        // Chrome cycle 6 ticks (3 frames x 2) and the obfuscation ticker's 10 one-tick frames:
        // LCM 30, sampled every tick because the obfuscation changes every tick.
        assertEquals(30, generated.getAnimationFrames().size());
        assertEquals(Collections.nCopies(30, 50), generated.getFrameDelaysMs());
    }

    @Test
    void obfuscatedPackGlyphTicksPerFrameOnAnimatedChrome() {
        // The obfuscation-coexistence pins above use built-in-font &k, whose substitution is
        // ThreadLocalRandom and never consumes the seeded frame index. This pins the actual
        // wiring: a PACK-glyph obfuscated run over animated chrome, whose substitution IS seeded
        // by the obfuscation ticker position drawLinesInternal receives.
        FixturePacks.writeAnimatedGlyphTooltipPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = builder(repository, packId, "testpack:anim", "&k\uE000\uE001")
            .withAnimatedTextures(true)
            .build()
            .generate();

        assertTrue(generated.isAnimated());
        List<BufferedImage> frames = generated.getAnimationFrames();
        // Chrome cycle 6 ticks, obfuscation ticker 10 one-tick frames: LCM 30 one-tick steps.
        assertEquals(30, frames.size());
        // Steps 0, 6, 12, 18, 24 all show chrome background position 0 (the theme layer is
        // identical and memoized), so their ONLY possible difference is the seeded per-frame glyph
        // substitution (obfuscation ticks 0, 6, 2, 8, 4 there). A mutant that froze the
        // obfuscation frame index - a constant 0, or the chrome position (also 0 at each) - would
        // make them pixel-identical; the seeded ticker makes at least one differ.
        boolean glyphVaries = false;
        for (int step : new int[]{6, 12, 18, 24}) {
            if (!ImageAssertions.pixelsEqual(frames.get(0), frames.get(step))) {
                glyphVaries = true;
                break;
            }
        }
        assertTrue(glyphVaries,
            "at a fixed chrome position the obfuscated pack glyph changes across obfuscation ticks");

        // Pack-glyph obfuscation is seeded, so two renders are byte-identical (unlike built-in &k).
        byte[] second = builder(repository, packId, "testpack:anim", "&k\uE000\uE001")
            .withAnimatedTextures(true).build().generate().getGifData();
        assertArrayEquals(generated.getGifData(), second);
    }

    @Test
    void chromeOnlyAnimationIsDeterministic() {
        FixturePacks.writeDefaultPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        byte[] first = builder(repository, packId, "testpack:anim", "Lore line")
            .withAnimatedTextures(true).build().generate().getGifData();
        byte[] second = builder(repository, packId, "testpack:anim", "Lore line")
            .withAnimatedTextures(true).build().generate().getGifData();
        assertArrayEquals(first, second);
    }

    @Test
    void flagOffKeepsTheAnimatedStyleStaticFirstFrame() {
        FixturePacks.writeDefaultPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject withoutFlag = builder(repository, packId, "testpack:anim", "Lore line")
            .build()
            .generate();

        assertFalse(withoutFlag.isAnimated(), "the flag gates the animated chrome");
    }

    @Test
    void staticStyleWithFlagRendersTheExactStaticImage() {
        FixturePacks.writeDefaultPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject off = builder(repository, packId, "testpack:fancy", "Lore line").build().generate();
        GeneratedObject on = builder(repository, packId, "testpack:fancy", "Lore line")
            .withAnimatedTextures(true).build().generate();

        assertFalse(on.isAnimated());
        ImageAssertions.assertPixelsEqual(off.getImage(), on.getImage(), "static styles are untouched");
    }

    @Test
    void cacheKeysDifferAcrossTheAnimatedTexturesFlag() {
        FixturePacks.writeDefaultPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        MinecraftTooltipGenerator off = builder(repository, packId, "testpack:anim", "Lore line").build();
        MinecraftTooltipGenerator on = builder(repository, packId, "testpack:anim", "Lore line")
            .withAnimatedTextures(true).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(off), GeneratorCacheKey.fromGenerator(on),
            "the flag changes rendered output, so it must enter the render cache key");
    }

    @Test
    void chromeFramesFollowTheMcmetaFrameOrder() {
        FixturePacks.writeDefaultPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = register(repository, packDir);

        GeneratedObject generated = builder(repository, packId, "testpack:anim", "Lore line")
            .withAnimatedTextures(true)
            .build()
            .generate();

        // The anim background's frames list is [2, 0, 1] over red/green/blue flipbook frames:
        // step 0 shows blue, step 1 red. Any pixel showing the background proves the order.
        BufferedImage frame0 = generated.getAnimationFrames().get(0);
        BufferedImage frame1 = generated.getAnimationFrames().get(1);
        boolean pinned = false;
        for (int y = 0; y < frame0.getHeight() && !pinned; y++) {
            for (int x = 0; x < frame0.getWidth() && !pinned; x++) {
                if (frame0.getRGB(x, y) == 0xFF0000FF) {
                    assertEquals(0xFFFF0000, frame1.getRGB(x, y),
                        "the pixel showing blue in step 0 shows red in step 1");
                    pinned = true;
                }
            }
        }
        assertTrue(pinned, "the animated background is visible through the frame sprite");
    }
}
