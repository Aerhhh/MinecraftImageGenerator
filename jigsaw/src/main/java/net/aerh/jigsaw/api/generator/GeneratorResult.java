package net.aerh.jigsaw.api.generator;

import java.awt.image.BufferedImage;
import java.util.List;

public sealed interface GeneratorResult {

    BufferedImage firstFrame();

    boolean isAnimated();

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

    record AnimatedImage(List<BufferedImage> frames, int frameDelayMs) implements GeneratorResult {

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
    }
}
