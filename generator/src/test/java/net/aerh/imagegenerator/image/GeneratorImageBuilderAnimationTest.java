package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.GifBytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the multi-generator composite builder's handling of PER-FRAME delays: a variable-delay
 * pack texture animation (a shiny hold) composited next to a static generator must keep its
 * authored timing, not collapse to a single uniform delay.
 */
class GeneratorImageBuilderAnimationTest {

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

    private MinecraftItemGenerator.Builder item(String itemRef) {
        return new MinecraftItemGenerator.Builder()
            .withPack(packId)
            .withPackRepository(repository)
            .withItem(itemRef);
    }

    @Test
    void variableDelayAnimationKeepsItsTimingWhenComposited() {
        // animated_hold is a per-frame-delay animation: frames [0 (2t), 1 (2t), 0 (100t)] ->
        // 100/100/5000 ms. Composited next to a static item, the composite must preserve those
        // per-frame delays (the uniform maxFrames/first-delay path would flatten the 5-second
        // hold to 100 ms).
        GeneratedObject composite = new GeneratorImageBuilder()
            .addGenerator(item("testpack:item/animated_hold").withAnimatedTextures(true).build())
            .addGenerator(item("testpack:item/simple").build())
            .build();

        assertTrue(composite.isAnimated());
        assertEquals(3, composite.getAnimationFrames().size());
        assertEquals(List.of(100, 100, 5000), composite.getFrameDelaysMs(),
            "the composite honors the animation's per-frame delays");
        assertEquals(List.of(10, 10, 500), GifBytes.frameDelaysCentiseconds(composite.getGifData()));
        assertEquals(1, GifBytes.applicationExtensionCount(composite.getGifData()),
            "one NETSCAPE loop block for the whole composite GIF");
    }

    @Test
    void compositedVariableDelayAnimationIsDeterministic() {
        byte[] first = new GeneratorImageBuilder()
            .addGenerator(item("testpack:item/animated_hold").withAnimatedTextures(true).build())
            .addGenerator(item("testpack:item/simple").build())
            .build()
            .getGifData();
        byte[] second = new GeneratorImageBuilder()
            .addGenerator(item("testpack:item/animated_hold").withAnimatedTextures(true).build())
            .addGenerator(item("testpack:item/simple").build())
            .build()
            .getGifData();
        assertArrayEquals(first, second);
    }
}
