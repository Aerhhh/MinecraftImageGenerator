package net.aerh.imagegenerator.util;

import net.aerh.imagegenerator.exception.GeneratorTimeoutException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The uniform-delay encoder replaced a call into Marmalade so that it can be cancelled. Its output
 * must stay byte-for-byte what that call produced, or every animated image would change.
 */
class AnimatedGifEncoderUniformTest {

    private static List<BufferedImage> frames(int type, int count, boolean partialAlpha) {
        List<BufferedImage> frames = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BufferedImage frame = new BufferedImage(48, 32, type);
            Graphics2D graphics = frame.createGraphics();
            graphics.setColor(new Color(40 + index * 20, 200 - index * 15, 90, 255));
            graphics.fillRect(index * 3, 2, 20, 20);
            if (partialAlpha) {
                graphics.setColor(new Color(255, 255, 255, 120));
                graphics.fillOval(10, 5 + index, 18, 18);
            }
            graphics.dispose();
            frames.add(frame);
        }
        return frames;
    }

    @Test
    void matchesTheEncoderItReplacedForArgbFrames() throws IOException {
        List<BufferedImage> frames = frames(BufferedImage.TYPE_INT_ARGB, 8, false);
        assertArrayEquals(ImageUtil.toGifBytes(frames, 30, true), AnimatedGifEncoder.encode(frames, 30));
    }

    @Test
    void matchesTheEncoderItReplacedForPartlyTransparentFrames() throws IOException {
        List<BufferedImage> frames = frames(BufferedImage.TYPE_INT_ARGB, 6, true);
        assertArrayEquals(ImageUtil.toGifBytes(frames, 50, true), AnimatedGifEncoder.encode(frames, 50));
    }

    @Test
    void matchesTheEncoderItReplacedForOtherImageTypes() throws IOException {
        for (int type : new int[]{BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_4BYTE_ABGR, BufferedImage.TYPE_3BYTE_BGR}) {
            List<BufferedImage> frames = frames(type, 4, type == BufferedImage.TYPE_4BYTE_ABGR);
            assertArrayEquals(ImageUtil.toGifBytes(frames, 100, true), AnimatedGifEncoder.encode(frames, 100), "image type " + type);
        }
    }

    @Test
    void matchesTheEncoderItReplacedForDelaysUnderOneCentisecond() throws IOException {
        List<BufferedImage> frames = frames(BufferedImage.TYPE_INT_ARGB, 3, false);
        assertArrayEquals(ImageUtil.toGifBytes(frames, 5, true), AnimatedGifEncoder.encode(frames, 5));
    }

    @Test
    void theListFormWithEqualDelaysProducesTheSameBytes() throws IOException {
        List<BufferedImage> frames = frames(BufferedImage.TYPE_INT_ARGB, 4, false);
        assertArrayEquals(ImageUtil.toGifBytes(frames, 30, true), AnimatedGifEncoder.encode(frames, List.of(30, 30, 30, 30)));
    }

    @Test
    void stopsBetweenFramesWhenTheRenderIsCancelled() {
        List<BufferedImage> frames = frames(BufferedImage.TYPE_INT_ARGB, 4, false);
        Thread.currentThread().interrupt();
        try {
            assertThrows(GeneratorTimeoutException.class, () -> AnimatedGifEncoder.encode(frames, 30));
        } finally {
            // Clear the flag so it does not leak into the next test on this thread.
            Thread.interrupted();
        }
    }
}
