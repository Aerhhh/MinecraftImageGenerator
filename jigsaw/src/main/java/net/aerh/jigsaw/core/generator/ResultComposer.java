package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.core.util.GraphicsUtil;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that composes multiple {@link GeneratorResult}s into a single image.
 *
 * <p>Layout rules:
 * <ul>
 *   <li>{@link CompositeRequest.Layout#VERTICAL}: images are stacked top-to-bottom with
 *       {@code padding} pixels between them and a {@code 15px} border around the whole canvas.</li>
 *   <li>{@link CompositeRequest.Layout#HORIZONTAL}: images are placed side by side with the
 *       same spacing and border.</li>
 * </ul>
 *
 * <p>If any input is animated, all inputs are aligned to the same frame count by looping
 * shorter animations to match the longest. Static inputs are repeated unchanged on every frame.
 */
public final class ResultComposer {

    /** Fixed outer border added on all sides around the composed image. */
    private static final int OUTER_BORDER = 15;

    private ResultComposer() {
        // utility class
    }

    /**
     * Composes the given results into a single {@link GeneratorResult}.
     *
     * <p>Returns a minimal 1x1 transparent image if the result list is empty.
     *
     * @param results   the results to compose; must not be {@code null}
     * @param layout    the layout direction; must not be {@code null}
     * @param padding   the pixel gap between adjacent results; must be {@code >= 0}
     * @return a composed static or animated image
     */
    public static GeneratorResult compose(
            List<GeneratorResult> results,
            CompositeRequest.Layout layout,
            int padding) {

        if (results.isEmpty()) {
            BufferedImage empty = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            return new GeneratorResult.StaticImage(empty);
        }

        boolean isVertical = layout == CompositeRequest.Layout.VERTICAL;
        boolean anyAnimated = results.stream().anyMatch(GeneratorResult::isAnimated);

        if (anyAnimated) {
            return composeAnimated(results, isVertical, padding);
        }
        return composeStatic(results, isVertical, padding);
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
        GraphicsUtil.disableAntialiasing(g);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int cursor = 0;
        for (BufferedImage img : images) {
            if (vertical) {
                // Center horizontally within the total width
                int xOffset = (totalW - img.getWidth()) / 2;
                g.drawImage(img, OUTER_BORDER + xOffset, OUTER_BORDER + cursor, null);
                cursor += img.getHeight() + padding;
            } else {
                // Center vertically within the total height
                int yOffset = (totalH - img.getHeight()) / 2;
                g.drawImage(img, OUTER_BORDER + cursor, OUTER_BORDER + yOffset, null);
                cursor += img.getWidth() + padding;
            }
        }

        g.dispose();
        return canvas;
    }
}
