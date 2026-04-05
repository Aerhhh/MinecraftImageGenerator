package net.aerh.jigsaw.api.generator;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorResultTest {

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    // --- StaticImage ---

    @Test
    void staticImage_isNotAnimated() {
        GeneratorResult result = new GeneratorResult.StaticImage(blankImage());
        assertThat(result.isAnimated()).isFalse();
    }

    @Test
    void staticImage_firstFrameReturnsTheImage() {
        BufferedImage image = blankImage();
        GeneratorResult result = new GeneratorResult.StaticImage(image);
        assertThat(result.firstFrame()).isSameAs(image);
    }

    // --- AnimatedImage ---

    @Test
    void animatedImage_isAnimated() {
        GeneratorResult result = new GeneratorResult.AnimatedImage(List.of(blankImage()), 50);
        assertThat(result.isAnimated()).isTrue();
    }

    @Test
    void animatedImage_firstFrameReturnsFirstFrame() {
        BufferedImage first = blankImage();
        BufferedImage second = blankImage();
        GeneratorResult result = new GeneratorResult.AnimatedImage(List.of(first, second), 50);
        assertThat(result.firstFrame()).isSameAs(first);
    }

    @Test
    void animatedImage_framesAreImmutableCopy() {
        BufferedImage image = blankImage();
        List<BufferedImage> mutableFrames = new ArrayList<>();
        mutableFrames.add(image);

        GeneratorResult.AnimatedImage animated = new GeneratorResult.AnimatedImage(mutableFrames, 50);
        mutableFrames.add(blankImage()); // mutate original list after construction

        assertThat(animated.frames()).hasSize(1);
    }

    @Test
    void animatedImage_rejectsEmptyFramesList() {
        assertThatThrownBy(() -> new GeneratorResult.AnimatedImage(List.of(), 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one frame");
    }

    // --- GenerationContext ---

    @Test
    void generationContext_defaults_hasSkipCacheFalse() {
        GenerationContext ctx = GenerationContext.defaults();
        assertThat(ctx.skipCache()).isFalse();
    }

    @Test
    void generationContext_builder_skipCacheTrue() {
        GenerationContext ctx = GenerationContext.builder()
                .skipCache(true)
                .build();
        assertThat(ctx.skipCache()).isTrue();
    }
}
