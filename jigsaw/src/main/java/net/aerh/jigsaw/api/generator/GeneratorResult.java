package net.aerh.jigsaw.api.generator;

import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The output of a rendering operation, which is either a single static image or an animated
 * sequence of frames.
 *
 * <p>This is a sealed interface with two permitted implementations: {@link StaticImage} and
 * {@link AnimatedImage}. Use Java pattern matching to handle each case:
 *
 * <pre>{@code
 * GeneratorResult result = engine.renderItem("diamond_sword");
 *
 * switch (result) {
 *     case GeneratorResult.StaticImage s  -> saveAsPng(s.image());
 *     case GeneratorResult.AnimatedImage a -> saveAsGif(a.frames(), a.frameDelayMs());
 * }
 * }</pre>
 *
 * <p>If you only need the first frame regardless of animation state, call {@link #firstFrame()}.
 *
 * @see StaticImage
 * @see AnimatedImage
 */
public sealed interface GeneratorResult {

    /**
     * Returns the first (or only) frame of this result.
     * For a {@link StaticImage} this is the sole image; for an {@link AnimatedImage} this is
     * the first frame in the sequence.
     *
     * @return the first frame; never {@code null}
     */
    BufferedImage firstFrame();

    /**
     * Returns {@code true} if this result contains multiple animation frames.
     *
     * @return {@code true} for {@link AnimatedImage}, {@code false} for {@link StaticImage}
     */
    boolean isAnimated();

    /**
     * A single-frame (non-animated) rendering result.
     *
     * @param image the rendered image; must not be {@code null}
     */
    record StaticImage(BufferedImage image) implements GeneratorResult {

        @Override
        public BufferedImage firstFrame() {
            return image;
        }

        @Override
        public boolean isAnimated() {
            return false;
        }
    }

    /**
     * A multi-frame animated rendering result.
     *
     * <p>The compact constructor validates that the frame list is non-empty and makes a defensive
     * copy of the supplied list.
     *
     * @param frames       the ordered list of animation frames; must not be empty
     * @param frameDelayMs the delay between frames in milliseconds
     *
     * @throws IllegalArgumentException if {@code frames} is empty
     */
    record AnimatedImage(List<BufferedImage> frames, int frameDelayMs) implements GeneratorResult {

        /**
         * Compact constructor that validates the frame list is non-empty and copies it defensively.
         *
         * @throws IllegalArgumentException if {@code frames} is empty
         */
        public AnimatedImage {
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("Animated image must have at least one frame");
            }
            frames = List.copyOf(frames);
        }

        @Override
        public BufferedImage firstFrame() {
            return frames.getFirst();
        }

        @Override
        public boolean isAnimated() {
            return true;
        }

        /**
         * Encodes all frames into an animated GIF and returns the raw bytes.
         *
         * <p>The GIF is encoded with the frame delay stored in this result and is set to loop
         * indefinitely.
         *
         * @return the GIF-encoded byte array; never {@code null} or empty
         * @throws UncheckedIOException if GIF encoding fails
         */
        public byte[] toGifBytes() {
            try {
                return ImageUtil.toGifBytes(frames, frameDelayMs, true);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to encode animated GIF", e);
            }
        }

        /**
         * Encodes all frames into an animated WebP and returns the raw bytes.
         *
         * <p>The WebP is encoded with the frame delay stored in this result and is set to loop
         * indefinitely. Uses lossless compression to preserve pixel-art fidelity.
         *
         * @return the WebP-encoded byte array; never {@code null} or empty
         *
         * @throws UncheckedIOException if WebP encoding fails
         */
        public byte[] toWebpBytes() {
            try {
                return ImageUtil.toWebpBytes(frames, frameDelayMs, true);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to encode animated WebP", e);
            }
        }
    }
}
