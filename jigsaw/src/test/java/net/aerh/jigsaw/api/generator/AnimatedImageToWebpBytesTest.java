package net.aerh.jigsaw.api.generator;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link GeneratorResult.AnimatedImage#toWebpBytes()}.
 */
class AnimatedImageToWebpBytesTest {

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void toWebpBytes_singleFrameProducesNonEmptyByteArray() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 50);

        byte[] webp = animated.toWebpBytes();

        assertThat(webp).isNotNull().isNotEmpty();
    }

    @Test
    void toWebpBytes_multipleFramesProducesNonEmptyByteArray() {
        GeneratorResult.AnimatedImage animated = new GeneratorResult.AnimatedImage(
                List.of(blankImage(), blankImage(), blankImage()), 33);

        byte[] webp = animated.toWebpBytes();

        assertThat(webp).isNotNull().isNotEmpty();
    }

    @Test
    void toWebpBytes_outputStartsWithWebpMagicBytes() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 50);

        byte[] webp = animated.toWebpBytes();

        // WebP files start with a RIFF container: "RIFF" at bytes 0-3,
        // then a 4-byte little-endian file-size field, then "WEBP" at bytes 8-11.
        assertThat(webp.length).isGreaterThanOrEqualTo(12);
        assertThat(new String(webp, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4)).isEqualTo("WEBP");
    }

    @Test
    void toWebpBytes_doesNotThrowForValidInput() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 100);

        assertThatCode(animated::toWebpBytes).doesNotThrowAnyException();
    }

    @Test
    void toWebpBytes_calledTwiceProducesSameResult() {
        GeneratorResult.AnimatedImage animated =
                new GeneratorResult.AnimatedImage(List.of(blankImage()), 50);

        byte[] first = animated.toWebpBytes();
        byte[] second = animated.toWebpBytes();

        // If this proves flaky due to non-deterministic metadata (e.g., embedded timestamps),
        // relax to: assertThat(first.length).isEqualTo(second.length);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void toWebpBytes_largerImageProducesLargerOutput() {
        BufferedImage small = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        BufferedImage large = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        byte[] smallWebp = new GeneratorResult.AnimatedImage(List.of(small), 50).toWebpBytes();
        byte[] largeWebp = new GeneratorResult.AnimatedImage(List.of(large), 50).toWebpBytes();

        assertThat(largeWebp.length).isGreaterThan(smallWebp.length);
    }
}
