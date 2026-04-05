package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeGeneratorTest {

    private static final int OUTER_BORDER = 15;

    private CompositeGenerator generator;

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

    @BeforeEach
    void setUp() {
        generator = new CompositeGenerator();
    }

    // --- Empty request ---

    @Test
    void render_emptyResultsReturnsMinimalImage() throws RenderException {
        CompositeRequest request = CompositeRequest.builder().build();
        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
    }

    // --- VERTICAL layout ---

    @Test
    void render_verticalTwoStaticImagesStacksHeights() throws RenderException {
        GeneratorResult a = staticImage(50, 30);
        GeneratorResult b = staticImage(50, 20);
        int padding = 4;

        CompositeRequest request = CompositeRequest.builder()
                .result(a)
                .result(b)
                .layout(CompositeRequest.Layout.VERTICAL)
                .padding(padding)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        // Height = outerBorder * 2 + imageHeights + gaps
        int expectedH = OUTER_BORDER * 2 + 30 + 20 + padding;
        assertThat(result.firstFrame().getHeight()).isEqualTo(expectedH);
    }

    @Test
    void render_verticalTwoStaticImagesWidthIsMaxOfBoth() throws RenderException {
        GeneratorResult a = staticImage(60, 20);
        GeneratorResult b = staticImage(40, 20);

        CompositeRequest request = CompositeRequest.builder()
                .result(a)
                .result(b)
                .layout(CompositeRequest.Layout.VERTICAL)
                .padding(0)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        int expectedW = OUTER_BORDER * 2 + 60;
        assertThat(result.firstFrame().getWidth()).isEqualTo(expectedW);
    }

    // --- HORIZONTAL layout ---

    @Test
    void render_horizontalTwoStaticImagesAddsWidths() throws RenderException {
        GeneratorResult a = staticImage(40, 30);
        GeneratorResult b = staticImage(60, 30);
        int padding = 4;

        CompositeRequest request = CompositeRequest.builder()
                .result(a)
                .result(b)
                .layout(CompositeRequest.Layout.HORIZONTAL)
                .padding(padding)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        int expectedW = OUTER_BORDER * 2 + 40 + 60 + padding;
        assertThat(result.firstFrame().getWidth()).isEqualTo(expectedW);
    }

    @Test
    void render_horizontalTwoStaticImagesHeightIsMaxOfBoth() throws RenderException {
        GeneratorResult a = staticImage(40, 30);
        GeneratorResult b = staticImage(40, 50);

        CompositeRequest request = CompositeRequest.builder()
                .result(a)
                .result(b)
                .layout(CompositeRequest.Layout.HORIZONTAL)
                .padding(0)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        int expectedH = OUTER_BORDER * 2 + 50;
        assertThat(result.firstFrame().getHeight()).isEqualTo(expectedH);
    }

    // --- Padding is included in dimensions ---

    @Test
    void render_paddingIsIncludedInVerticalDimensions() throws RenderException {
        GeneratorResult a = staticImage(10, 10);
        GeneratorResult b = staticImage(10, 10);

        int padding = 8;
        CompositeRequest withPad = CompositeRequest.builder()
                .result(a).result(b)
                .layout(CompositeRequest.Layout.VERTICAL)
                .padding(padding).build();

        CompositeRequest zeroPad = CompositeRequest.builder()
                .result(a).result(b)
                .layout(CompositeRequest.Layout.VERTICAL)
                .padding(0).build();

        int hWith = generator.render(withPad, GenerationContext.defaults()).firstFrame().getHeight();
        int hZero = generator.render(zeroPad, GenerationContext.defaults()).firstFrame().getHeight();

        assertThat(hWith).isEqualTo(hZero + padding);
    }

    // --- Static result ---

    @Test
    void render_twoStaticImagesProducesStaticResult() throws RenderException {
        GeneratorResult a = staticImage(20, 20);
        GeneratorResult b = staticImage(20, 20);

        CompositeRequest request = CompositeRequest.builder()
                .result(a).result(b)
                .layout(CompositeRequest.Layout.VERTICAL)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result.isAnimated()).isFalse();
    }

    // --- Animated composition ---

    @Test
    void render_anyAnimatedInputProducesAnimatedResult() throws RenderException {
        GeneratorResult staticR = staticImage(20, 20);
        GeneratorResult animR = animatedImage(20, 20, 5, 100);

        CompositeRequest request = CompositeRequest.builder()
                .result(staticR).result(animR)
                .layout(CompositeRequest.Layout.VERTICAL)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result.isAnimated()).isTrue();
    }

    @Test
    void render_animatedResultHasMaxFrameCount() throws RenderException {
        GeneratorResult anim5 = animatedImage(20, 20, 5, 50);
        GeneratorResult anim10 = animatedImage(20, 20, 10, 50);

        CompositeRequest request = CompositeRequest.builder()
                .result(anim5).result(anim10)
                .layout(CompositeRequest.Layout.VERTICAL)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.AnimatedImage.class);
        assertThat(((GeneratorResult.AnimatedImage) result).frames()).hasSize(10);
    }

    // --- Single result passthrough ---

    @Test
    void render_singleStaticResultProducesStaticImage() throws RenderException {
        GeneratorResult single = staticImage(32, 32);

        CompositeRequest request = CompositeRequest.builder()
                .result(single)
                .layout(CompositeRequest.Layout.VERTICAL)
                .padding(0)
                .build();

        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result.isAnimated()).isFalse();
        assertThat(result.firstFrame().getWidth()).isEqualTo(OUTER_BORDER * 2 + 32);
        assertThat(result.firstFrame().getHeight()).isEqualTo(OUTER_BORDER * 2 + 32);
    }

    // --- inputType / outputType ---

    @Test
    void inputType_returnsCompositeRequestClass() {
        assertThat(generator.inputType()).isEqualTo(CompositeRequest.class);
    }

    @Test
    void outputType_returnsGeneratorResultClass() {
        assertThat(generator.outputType()).isEqualTo(GeneratorResult.class);
    }

    // --- Null guards ---

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> generator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        CompositeRequest request = CompositeRequest.builder().build();
        assertThatThrownBy(() -> generator.render(request, null))
                .isInstanceOf(NullPointerException.class);
    }
}
