package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.exception.RenderException;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composes multiple {@link GeneratorResult}s into a single image.
 *
 * <p>Layout rules:
 * <ul>
 *   <li>{@link CompositeRequest.Layout#VERTICAL}: images are stacked top-to-bottom with
 *       {@code padding} pixels between them and a {@code 15px} border around the whole canvas.</li>
 *   <li>{@link CompositeRequest.Layout#HORIZONTAL}: images are placed side by side with the
 *       same spacing and border.</li>
 * </ul>
 *
 * <p>If any input is animated all inputs are aligned to the same frame count by looping
 * shorter animations to match the longest. Static inputs are repeated unchanged on every frame.
 */
public final class CompositeGenerator implements Generator<CompositeRequest, GeneratorResult> {

    /** Fixed outer border added on all sides around the composed image. */
    private static final int OUTER_BORDER = 15;

    @Override
    public GeneratorResult render(CompositeRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (input.results().isEmpty()) {
            // Return a minimal 1x1 transparent image for empty requests
            BufferedImage empty = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            return new GeneratorResult.StaticImage(empty);
        }

        List<GeneratorResult> results = input.results();
        int padding = input.padding();
        boolean isVertical = input.layout() == CompositeRequest.Layout.VERTICAL;

        boolean anyAnimated = results.stream().anyMatch(GeneratorResult::isAnimated);

        if (anyAnimated) {
            return composeAnimated(results, isVertical, padding);
        }
        return composeStatic(results, isVertical, padding);
    }

    @Override
    public Class<CompositeRequest> inputType() {
        return CompositeRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    private static GeneratorResult composeStatic(
            List<GeneratorResult> results, boolean vertical, int padding) {

        List<BufferedImage> frames = results.stream()
                .map(GeneratorResult::firstFrame)
                .toList();

        BufferedImage composed = compositeFrames(frames, vertical, padding);
        return new GeneratorResult.StaticImage(composed);
    }

    private static GeneratorResult composeAnimated(
            List<GeneratorResult> results, boolean vertical, int padding) {

        // Determine max frame count and the frame delay from the first animated result
        int maxFrames = 1;
        int frameDelayMs = 33;
        for (GeneratorResult r : results) {
            if (r instanceof GeneratorResult.AnimatedImage anim) {
                maxFrames = Math.max(maxFrames, anim.frames().size());
                frameDelayMs = anim.frameDelayMs();
            }
        }

        List<BufferedImage> outputFrames = new ArrayList<>(maxFrames);

        for (int f = 0; f < maxFrames; f++) {
            final int frameIndex = f;
            List<BufferedImage> slice = results.stream()
                    .map(r -> frameAt(r, frameIndex))
                    .toList();
            outputFrames.add(compositeFrames(slice, vertical, padding));
        }

        return new GeneratorResult.AnimatedImage(outputFrames, frameDelayMs);
    }

    /**
     * Returns the frame at the given index, looping if the result has fewer frames.
     */
    private static BufferedImage frameAt(GeneratorResult result, int index) {
        if (result instanceof GeneratorResult.AnimatedImage anim) {
            return anim.frames().get(index % anim.frames().size());
        }
        return result.firstFrame();
    }

    /**
     * Composites a list of images into a single image using the specified layout.
     */
    private static BufferedImage compositeFrames(
            List<BufferedImage> images, boolean vertical, int padding) {

        // Compute canvas size
        int totalW, totalH;
        if (vertical) {
            totalW = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(0);
            int innerH = images.stream().mapToInt(BufferedImage::getHeight).sum();
            int gaps = Math.max(0, images.size() - 1) * padding;
            totalH = innerH + gaps;
        } else {
            int innerW = images.stream().mapToInt(BufferedImage::getWidth).sum();
            int gaps = Math.max(0, images.size() - 1) * padding;
            totalW = innerW + gaps;
            totalH = images.stream().mapToInt(BufferedImage::getHeight).max().orElse(0);
        }

        int canvasW = totalW + OUTER_BORDER * 2;
        int canvasH = totalH + OUTER_BORDER * 2;

        BufferedImage canvas = new BufferedImage(
                Math.max(1, canvasW), Math.max(1, canvasH), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int cursor = 0;
        for (BufferedImage img : images) {
            if (vertical) {
                g.drawImage(img, OUTER_BORDER, OUTER_BORDER + cursor, null);
                cursor += img.getHeight() + padding;
            } else {
                g.drawImage(img, OUTER_BORDER + cursor, OUTER_BORDER, null);
                cursor += img.getWidth() + padding;
            }
        }

        g.dispose();
        return canvas;
    }
}
