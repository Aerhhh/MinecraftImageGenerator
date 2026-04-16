package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResultComposer} image composition logic.
 */
class CompositeGeneratorTest {

    private static final int OUTER_BORDER = 15;

    private static GeneratorResult.StaticImage staticImage(int width, int height) {
        return new GeneratorResult.StaticImage(
                new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
    }

    private static GeneratorResult.AnimatedImage animatedImage(int width, int height, int frames, int delayMs) {
        List<BufferedImage> frameList = new java.util.ArrayList<>();
        for (int i = 0; i < frames; i++) {
            frameList.add(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
        }
        return new GeneratorResult.AnimatedImage(frameList, delayMs);
    }

    // --- Empty request ---

    @Test
    void compose_emptyResultsReturnsMinimalImage() {
        GeneratorResult result = ResultComposer.compose(
                List.of(), CompositeRequest.Layout.VERTICAL, 4);

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
    }

    // --- VERTICAL layout ---

    @Test
    void compose_verticalTwoStaticImagesStacksHeights() {
        GeneratorResult a = staticImage(50, 30);
        GeneratorResult b = staticImage(50, 20);
        int padding = 4;

        GeneratorResult result = ResultComposer.compose(
                List.of(a, b), CompositeRequest.Layout.VERTICAL, padding);

        int expectedH = OUTER_BORDER * 2 + 30 + 20 + padding;
        assertThat(result.firstFrame().getHeight()).isEqualTo(expectedH);
    }

    @Test
    void compose_verticalTwoStaticImagesWidthIsMaxOfBoth() {
        GeneratorResult a = staticImage(60, 20);
        GeneratorResult b = staticImage(40, 20);

        GeneratorResult result = ResultComposer.compose(
                List.of(a, b), CompositeRequest.Layout.VERTICAL, 0);

        int expectedW = OUTER_BORDER * 2 + 60;
        assertThat(result.firstFrame().getWidth()).isEqualTo(expectedW);
    }

    // --- HORIZONTAL layout ---

    @Test
    void compose_horizontalTwoStaticImagesAddsWidths() {
        GeneratorResult a = staticImage(40, 30);
        GeneratorResult b = staticImage(60, 30);
        int padding = 4;

        GeneratorResult result = ResultComposer.compose(
                List.of(a, b), CompositeRequest.Layout.HORIZONTAL, padding);

        int expectedW = OUTER_BORDER * 2 + 40 + 60 + padding;
        assertThat(result.firstFrame().getWidth()).isEqualTo(expectedW);
    }

    @Test
    void compose_horizontalTwoStaticImagesHeightIsMaxOfBoth() {
        GeneratorResult a = staticImage(40, 30);
        GeneratorResult b = staticImage(40, 50);

        GeneratorResult result = ResultComposer.compose(
                List.of(a, b), CompositeRequest.Layout.HORIZONTAL, 0);

        int expectedH = OUTER_BORDER * 2 + 50;
        assertThat(result.firstFrame().getHeight()).isEqualTo(expectedH);
    }

    // --- Padding is included in dimensions ---

    @Test
    void compose_paddingIsIncludedInVerticalDimensions() {
        GeneratorResult a = staticImage(10, 10);
        GeneratorResult b = staticImage(10, 10);
        int padding = 8;

        int hWith = ResultComposer.compose(
                List.of(a, b), CompositeRequest.Layout.VERTICAL, padding)
                .firstFrame().getHeight();
        int hZero = ResultComposer.compose(
                List.of(a, b), CompositeRequest.Layout.VERTICAL, 0)
                .firstFrame().getHeight();

        assertThat(hWith).isEqualTo(hZero + padding);
    }

    // --- Static result ---

    @Test
    void compose_twoStaticImagesProducesStaticResult() {
        GeneratorResult a = staticImage(20, 20);
        GeneratorResult b = staticImage(20, 20);

        GeneratorResult result = ResultComposer.compose(
                List.of(a, b), CompositeRequest.Layout.VERTICAL, 4);

        assertThat(result.isAnimated()).isFalse();
    }

    // --- Animated composition ---

    @Test
    void compose_anyAnimatedInputProducesAnimatedResult() {
        GeneratorResult staticR = staticImage(20, 20);
        GeneratorResult animR = animatedImage(20, 20, 5, 100);

        GeneratorResult result = ResultComposer.compose(
                List.of(staticR, animR), CompositeRequest.Layout.VERTICAL, 4);

        assertThat(result.isAnimated()).isTrue();
    }

    @Test
    void compose_animatedResultHasMaxFrameCount() {
        GeneratorResult anim5 = animatedImage(20, 20, 5, 50);
        GeneratorResult anim10 = animatedImage(20, 20, 10, 50);

        GeneratorResult result = ResultComposer.compose(
                List.of(anim5, anim10), CompositeRequest.Layout.VERTICAL, 4);

        assertThat(result).isInstanceOf(GeneratorResult.AnimatedImage.class);
        assertThat(((GeneratorResult.AnimatedImage) result).frames()).hasSize(10);
    }

    // --- Single result passthrough ---

    @Test
    void compose_singleStaticResultProducesStaticImage() {
        GeneratorResult single = staticImage(32, 32);

        GeneratorResult result = ResultComposer.compose(
                List.of(single), CompositeRequest.Layout.VERTICAL, 0);

        assertThat(result.isAnimated()).isFalse();
        assertThat(result.firstFrame().getWidth()).isEqualTo(OUTER_BORDER * 2 + 32);
        assertThat(result.firstFrame().getHeight()).isEqualTo(OUTER_BORDER * 2 + 32);
    }
}
