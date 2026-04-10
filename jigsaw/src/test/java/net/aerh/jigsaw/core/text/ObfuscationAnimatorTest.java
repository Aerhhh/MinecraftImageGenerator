package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.TextRenderOptions;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;
import net.aerh.jigsaw.core.font.DefaultFontRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObfuscationAnimatorTest {

    private static MinecraftTextRenderer renderer;

    @BeforeAll
    static void initRenderer() {
        renderer = new MinecraftTextRenderer(DefaultFontRegistry.withBuiltins());
    }

    private static TextStyle obfuscatedStyle() {
        return TextStyle.DEFAULT.withObfuscated(true);
    }

    private static TextStyle normalStyle() {
        return TextStyle.DEFAULT;
    }

    private static TextLayout layoutWithSegment(String text, TextStyle style) {
        TextSegment segment = new TextSegment(text, style);
        TextLine line = new TextLine(List.of(segment), text.length());
        return new TextLayout(List.of(line), text.length(), 1);
    }

    // --- Frame count ---

    @Test
    void animate_producesCorrectNumberOfFrames() {
        TextLayout layout = layoutWithSegment("Hello", obfuscatedStyle());
        ObfuscationAnimator animator = new ObfuscationAnimator(
                renderer, layout, TextRenderOptions.defaults(),10, 100);

        GeneratorResult.AnimatedImage result = animator.animate();

        assertThat(result.frames()).hasSize(10);
    }

    @Test
    void animate_defaultFrameCountIs10() {
        TextLayout layout = layoutWithSegment("Hi", obfuscatedStyle());
        ObfuscationAnimator animator = new ObfuscationAnimator(renderer, layout, TextRenderOptions.defaults());

        GeneratorResult.AnimatedImage result = animator.animate();

        assertThat(result.frames()).hasSize(ObfuscationAnimator.DEFAULT_FRAME_COUNT);
    }

    // --- Frame delay ---

    @Test
    void animate_frameDelayIsPreserved() {
        TextLayout layout = layoutWithSegment("Test", obfuscatedStyle());
        int expectedDelay = 150;
        ObfuscationAnimator animator = new ObfuscationAnimator(
                renderer, layout, TextRenderOptions.defaults(),5, expectedDelay);

        GeneratorResult.AnimatedImage result = animator.animate();

        assertThat(result.frameDelayMs()).isEqualTo(expectedDelay);
    }

    // --- Frame dimensions ---

    @Test
    void animate_allFramesHaveSameDimensions() {
        TextLayout layout = layoutWithSegment("ABCDE", obfuscatedStyle());
        ObfuscationAnimator animator = new ObfuscationAnimator(
                renderer, layout, TextRenderOptions.defaults(),5, 100);

        GeneratorResult.AnimatedImage result = animator.animate();

        BufferedImage first = result.frames().getFirst();
        for (BufferedImage frame : result.frames()) {
            assertThat(frame.getWidth()).isEqualTo(first.getWidth());
            assertThat(frame.getHeight()).isEqualTo(first.getHeight());
        }
    }

    // --- Non-obfuscated text is unchanged ---

    @Test
    void animate_nonObfuscatedTextHasSameDimensionsAcrossFrames() {
        // Non-obfuscated text should produce frames of identical dimensions
        TextSegment normal = new TextSegment("Hello", normalStyle());
        TextSegment obfuscated = new TextSegment("World", obfuscatedStyle());
        TextLine line = new TextLine(List.of(normal, obfuscated), 10);
        TextLayout layout = new TextLayout(List.of(line), 10, 1);

        ObfuscationAnimator animator = new ObfuscationAnimator(
                renderer, layout, TextRenderOptions.defaults(),5, 100);

        GeneratorResult.AnimatedImage result = animator.animate();

        // All frames should have consistent dimensions since the segment count and widths don't change
        BufferedImage first = result.frames().getFirst();
        for (BufferedImage frame : result.frames()) {
            assertThat(frame.getWidth()).isEqualTo(first.getWidth());
            assertThat(frame.getHeight()).isEqualTo(first.getHeight());
        }
    }

    // --- Result type ---

    @Test
    void animate_returnsAnimatedImage() {
        TextLayout layout = layoutWithSegment("ABC", obfuscatedStyle());
        ObfuscationAnimator animator = new ObfuscationAnimator(
                renderer, layout, TextRenderOptions.defaults(),3, 100);

        GeneratorResult.AnimatedImage result = animator.animate();

        assertThat(result).isNotNull();
        assertThat(result.frames()).isNotEmpty();
    }

    // --- Empty layout ---

    @Test
    void animate_emptyLayoutProducesFrames() {
        TextLayout empty = new TextLayout(List.of(), 0, 0);
        ObfuscationAnimator animator = new ObfuscationAnimator(
                renderer, empty, TextRenderOptions.defaults(), 3, 100);

        GeneratorResult.AnimatedImage result = animator.animate();

        assertThat(result.frames()).hasSize(3);
    }

    // --- Null guards ---

    @Test
    void constructor_nullRendererThrows() {
        TextLayout layout = layoutWithSegment("X", obfuscatedStyle());
        assertThatThrownBy(() -> new ObfuscationAnimator(null, layout, TextRenderOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_nullLayoutThrows() {
        assertThatThrownBy(() -> new ObfuscationAnimator(renderer, null, TextRenderOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_nullOptionsThrows() {
        TextLayout layout = layoutWithSegment("X", obfuscatedStyle());
        assertThatThrownBy(() -> new ObfuscationAnimator(renderer, layout, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_zeroFrameCountThrows() {
        TextLayout layout = layoutWithSegment("X", obfuscatedStyle());
        assertThatThrownBy(() -> new ObfuscationAnimator(
                renderer, layout, TextRenderOptions.defaults(),0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_zeroFrameDelayThrows() {
        TextLayout layout = layoutWithSegment("X", obfuscatedStyle());
        assertThatThrownBy(() -> new ObfuscationAnimator(
                renderer, layout, TextRenderOptions.defaults(),5, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
