package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

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
}
