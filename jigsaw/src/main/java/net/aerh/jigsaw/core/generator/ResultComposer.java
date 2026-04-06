package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

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
        return compose(results, layout, padding, false);
    }

    /**
     * Composes the given results into a single {@link GeneratorResult}.
     *
     * <p>Returns a minimal 1x1 transparent image if the result list is empty.
     *
     * <p>When {@code autoScale} is {@code true}, each sub-result is scaled so that all heights
     * match the tallest component before compositing (nearest-neighbor interpolation is used).
     *
     * @param results   the results to compose; must not be {@code null}
     * @param layout    the layout direction; must not be {@code null}
     * @param padding   the pixel gap between adjacent results; must be {@code >= 0}
     * @param autoScale when {@code true}, scale all sub-results to match the tallest height
     * @return a composed static or animated image
     */
    public static GeneratorResult compose(
            List<GeneratorResult> results,
            CompositeRequest.Layout layout,
            int padding,
            boolean autoScale) {

        if (results.isEmpty()) {
            BufferedImage empty = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            return new GeneratorResult.StaticImage(empty);
        }

        List<GeneratorResult> effective = autoScale ? autoScaleToTallest(results) : results;
        boolean isVertical = layout == CompositeRequest.Layout.VERTICAL;
        boolean anyAnimated = effective.stream().anyMatch(GeneratorResult::isAnimated);

        if (anyAnimated) {
            return composeAnimated(effective, isVertical, padding);
        }
        return composeStatic(effective, isVertical, padding);
    }

    /**
     * Scales all results so their heights match the tallest result.
     * Each result is scaled proportionally (preserving aspect ratio by matching height).
     */
    private static List<GeneratorResult> autoScaleToTallest(List<GeneratorResult> results) {
        int maxHeight = results.stream()
                .mapToInt(r -> r.firstFrame().getHeight())
                .max()
                .orElse(0);

        if (maxHeight == 0) {
            return results;
        }

        List<GeneratorResult> scaled = new ArrayList<>(results.size());
        for (GeneratorResult result : results) {
            int h = result.firstFrame().getHeight();
            if (h == maxHeight || h == 0) {
                scaled.add(result);
            } else {
                double factor = (double) maxHeight / h;
                scaled.add(scaleResult(result, factor));
            }
        }
        return scaled;
    }

    /**
     * Scales a {@link GeneratorResult} by the given factor (nearest-neighbor interpolation).
     */
    private static GeneratorResult scaleResult(GeneratorResult result, double factor) {
        if (result instanceof GeneratorResult.AnimatedImage anim) {
            List<BufferedImage> frames = new ArrayList<>(anim.frames().size());
            for (BufferedImage frame : anim.frames()) {
                frames.add(ImageUtil.upscaleImage(frame, factor));
            }
            return new GeneratorResult.AnimatedImage(frames, anim.frameDelayMs());
        }
        return new GeneratorResult.StaticImage(ImageUtil.upscaleImage(result.firstFrame(), factor));
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
