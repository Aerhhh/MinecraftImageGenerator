package net.aerh.imagegenerator.impl.scene;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.AnimationTimeline;
import net.aerh.imagegenerator.util.AnimatedGifEncoder;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws placed {@link GeneratedObject}s onto one transparent canvas, static or animated. This is
 * the placement core every scene arrangement (and, later, declarative scene layouts) renders
 * through: members carry absolute canvas positions, and list order is the z-order - later
 * members draw above earlier ones.
 * <p>
 * When any member is animated, every animated member becomes one source on a shared
 * {@link AnimationTimeline} (per-frame delays converted to ticks via
 * {@link GeneratedObject#timelineTicks()}), the canvas is composed once per timeline step, and
 * the honest per-step delays go through {@link AnimatedGifEncoder}. Static members draw their
 * single image in every step. A member's footprint is its first frame's size; every frame draws
 * at the member's fixed position.
 */
final class SceneCompositor {

    private SceneCompositor() {
    }

    /**
     * One member with its resolved output and absolute canvas position.
     *
     * @param object the member's rendered output
     * @param x      the left edge on the canvas, in canvas px
     * @param y      the top edge on the canvas, in canvas px
     */
    record PlacedObject(GeneratedObject object, int x, int y) {
    }

    /**
     * Composes the placed members onto one canvas: a static PNG when nothing animates, otherwise
     * an animated GIF on the members' shared tick timeline.
     *
     * @param placed the members with their canvas positions, in z-order
     * @param width  the canvas width in px
     * @param height the canvas height in px
     * @return the composed object
     * @throws IOException        when GIF encoding fails
     * @throws GeneratorException when the canvas dimensions are not positive
     */
    static GeneratedObject compose(List<PlacedObject> placed, int width, int height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new GeneratorException("Calculated scene dimensions are invalid (width=" + width + ", height=" + height + ")");
        }

        List<List<Integer>> sources = new ArrayList<>();
        for (PlacedObject member : placed) {
            if (member.object().isAnimated()) {
                sources.add(member.object().timelineTicks());
            }
        }
        if (sources.isEmpty()) {
            return new GeneratedObject(composeFrame(placed, width, height, null));
        }
        AnimationTimeline timeline = AnimationTimeline.of(sources);

        List<BufferedImage> compositeFrames = new ArrayList<>(timeline.steps().size());
        for (AnimationTimeline.Step step : timeline.steps()) {
            compositeFrames.add(composeFrame(placed, width, height, step));
        }

        List<Integer> delaysMs = timeline.stepDelaysMillis();
        byte[] gifData = AnimatedGifEncoder.encode(compositeFrames, delaysMs);
        return new GeneratedObject(gifData, compositeFrames, delaysMs);
    }

    /**
     * Draws every member at its position for one timeline step ({@code null} step composes the
     * static frame). Animated members pick the step's frame position in placement order - the
     * same order their tick lists entered the timeline; static members always draw their single
     * image.
     */
    private static BufferedImage composeFrame(List<PlacedObject> placed, int width, int height,
                                              AnimationTimeline.Step step) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            int animatedIndex = 0;
            for (PlacedObject member : placed) {
                BufferedImage image;
                if (step != null && member.object().isAnimated()) {
                    image = member.object().getAnimationFrames().get(step.framePositions().get(animatedIndex));
                    animatedIndex++;
                } else {
                    image = member.object().getImage();
                }
                graphics.drawImage(image, member.x(), member.y(), null);
            }
        } finally {
            graphics.dispose();
        }
        return canvas;
    }
}
