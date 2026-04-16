package net.aerh.jigsaw.api.generator;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link GeneratorResult.AnimatedImage#toGifBytes()}.
 */
class AnimatedImageToGifBytesTest {

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void toGifBytes_singleFrameProducesNonEmptyByteArray() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 50);

        byte[] gif = animated.toGifBytes();

        assertThat(gif).isNotNull().isNotEmpty();
    }

    @Test
    void toGifBytes_multipleFramesProducesNonEmptyByteArray() {
        GeneratorResult.AnimatedImage animated = new GeneratorResult.AnimatedImage(
                List.of(blankImage(), blankImage(), blankImage()), 33);

        byte[] gif = animated.toGifBytes();

        assertThat(gif).isNotNull().isNotEmpty();
    }

    @Test
    void toGifBytes_outputStartsWithGifMagicBytes() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 50);

        byte[] gif = animated.toGifBytes();

        // GIF files start with "GIF87a" or "GIF89a"
        assertThat(new String(gif, 0, 3)).isEqualTo("GIF");
    }

    @Test
    void toGifBytes_doesNotThrowForValidInput() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 100);

        assertThatCode(animated::toGifBytes).doesNotThrowAnyException();
    }

    @Test
    void toGifBytes_calledTwiceProducesSameResult() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 50);

        byte[] first = animated.toGifBytes();
        byte[] second = animated.toGifBytes();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void toGifBytes_largerImageProducesLargerOutput() {
        BufferedImage small = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        BufferedImage large = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        byte[] smallGif = new GeneratorResult.AnimatedImage(List.of(small), 50).toGifBytes();
        byte[] largeGif = new GeneratorResult.AnimatedImage(List.of(large), 50).toGifBytes();

        assertThat(largeGif.length).isGreaterThan(smallGif.length);
    }
}
