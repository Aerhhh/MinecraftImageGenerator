package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class GlintEffectTest {

    private GlintEffect glint;

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    @BeforeEach
    void setUp() {
        glint = new GlintEffect();
    }

    // Test 1: id is "glint"
    @Test
    void id_isGlint() {
        assertThat(glint.id()).isEqualTo("glint");
    }

    // Test 2: priority is 100
    @Test
    void priority_is100() {
        assertThat(glint.priority()).isEqualTo(100);
    }

    // Test 3: appliesTo returns true when enchanted
    @Test
    void appliesTo_returnsTrueWhenEnchanted() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(true)
                .build();

        assertThat(glint.appliesTo(ctx)).isTrue();
    }

    // Test 4: appliesTo returns false when not enchanted
    @Test
    void appliesTo_returnsFalseWhenNotEnchanted() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(false)
                .build();

        assertThat(glint.appliesTo(ctx)).isFalse();
    }

    // Test 5: apply produces animation frames (non-empty animationFrames list)
    @Test
    void apply_producesAnimationFrames() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(true)
                .build();

        EffectContext result = glint.apply(ctx);

        assertThat(result.animationFrames()).isNotEmpty();
    }

    // Test 6: apply produces 182 frames (30 FPS * 6 second loop)
    @Test
    void apply_produces182Frames() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(true)
                .build();

        EffectContext result = glint.apply(ctx);

        assertThat(result.animationFrames()).hasSize(182);
    }

    // Test 7: apply sets frame delay to 33ms
    @Test
    void apply_setsFrameDelayTo33Ms() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(true)
                .build();

        EffectContext result = glint.apply(ctx);

        assertThat(result.frameDelayMs()).isEqualTo(33);
    }

    // Test 8: Frames have same dimensions as input image
    @Test
    void apply_framesHaveSameDimensionsAsInput() {
        BufferedImage base = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        EffectContext ctx = EffectContext.builder()
                .image(base)
                .enchanted(true)
                .build();

        EffectContext result = glint.apply(ctx);

        for (BufferedImage frame : result.animationFrames()) {
            assertThat(frame.getWidth()).isEqualTo(32);
            assertThat(frame.getHeight()).isEqualTo(32);
        }
    }

    // Test 9: Original context is not mutated
    @Test
    void apply_doesNotMutateOriginalContext() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(true)
                .build();

        int originalFrameCount = ctx.animationFrames().size();
        glint.apply(ctx);

        assertThat(ctx.animationFrames()).hasSize(originalFrameCount);
    }

    // Test 10: appliesTo returns false when context has enchanted=false even with animation frames
    @Test
    void appliesTo_falseRegardlessOfOtherContextFields() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(false)
                .itemId("minecraft:diamond_sword")
                .hovered(true)
                .build();

        assertThat(glint.appliesTo(ctx)).isFalse();
    }

    // Test 11: Each animation frame is a TYPE_INT_ARGB image
    @Test
    void apply_framesAreArgbType() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .enchanted(true)
                .build();

        EffectContext result = glint.apply(ctx);

        for (BufferedImage frame : result.animationFrames()) {
            assertThat(frame.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
        }
    }

    // Test 12: Parallel frame generation produces deterministic output
    @Test
    void apply_parallelFrameGenerationIsDeterministic() {
        BufferedImage input = coloredImage(16, 16);
        EffectContext ctx = EffectContext.builder()
                .image(input)
                .enchanted(true)
                .build();

        EffectContext result1 = glint.apply(ctx);
        EffectContext result2 = glint.apply(ctx);

        assertThat(result1.animationFrames()).hasSameSizeAs(result2.animationFrames());
        for (int i = 0; i < result1.animationFrames().size(); i++) {
            assertPixelsEqual(result1.animationFrames().get(i), result2.animationFrames().get(i),
                    "Frame " + i + " differs between runs");
        }
    }

    // Test 13: Concurrent calls to apply() from multiple threads produce correct results
    @Test
    void apply_concurrentCallsProduceCorrectResults() throws Exception {
        int threadCount = 8;
        BufferedImage input = coloredImage(16, 16);
        EffectContext ctx = EffectContext.builder()
                .image(input)
                .enchanted(true)
                .build();

        // Get a reference result
        EffectContext reference = glint.apply(ctx);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<EffectContext>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> glint.apply(ctx)));
            }

            for (Future<EffectContext> future : futures) {
                EffectContext result = future.get();
                assertThat(result.animationFrames()).hasSameSizeAs(reference.animationFrames());
                for (int i = 0; i < result.animationFrames().size(); i++) {
                    assertPixelsEqual(result.animationFrames().get(i), reference.animationFrames().get(i),
                            "Concurrent result frame " + i + " differs from reference");
                }
            }
        }
    }

    /**
     * Creates a non-blank test image with visible pixels so the glint effect has something to blend with.
     */
    private static BufferedImage coloredImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(100, 150, 200, 255));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return img;
    }

    private static void assertPixelsEqual(BufferedImage a, BufferedImage b, String message) {
        assertThat(a.getWidth()).as(message + " (width)").isEqualTo(b.getWidth());
        assertThat(a.getHeight()).as(message + " (height)").isEqualTo(b.getHeight());
        int[] pixelsA = a.getRGB(0, 0, a.getWidth(), a.getHeight(), null, 0, a.getWidth());
        int[] pixelsB = b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth());
        assertThat(pixelsA).as(message).isEqualTo(pixelsB);
    }
}
