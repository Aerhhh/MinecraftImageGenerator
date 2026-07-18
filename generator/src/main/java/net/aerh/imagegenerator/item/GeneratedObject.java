package net.aerh.imagegenerator.item;

import lombok.Getter;
import net.aerh.imagegenerator.pack.AnimationTimeline;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

@Getter
public class GeneratedObject {

    protected final BufferedImage image;
    private final byte[] gifData;
    private final OutputType outputType;
    @Getter
    private final List<BufferedImage> animationFrames;
    @Getter
    private final int frameDelayMs;
    /**
     * Per-frame delays in milliseconds for animations whose frames hold for DIFFERENT times
     * (tick-timed pack texture animations); null on the uniform-delay paths, where
     * {@link #getFrameDelayMs()} applies to every frame.
     */
    @Getter
    @Nullable
    private final List<Integer> frameDelaysMs;

    /**
     * Constructor for static PNGs
     *
     * @param image The image to be used for the PNG output
     */
    public GeneratedObject(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null for PNG output");
        }
        this.image = image;
        this.gifData = null;
        this.outputType = OutputType.PNG;
        this.animationFrames = null; // No frames for static images
        this.frameDelayMs = 0; // No delay for static images
        this.frameDelaysMs = null;
    }

    /**
     * Constructor for animated GIFs
     *
     * @param gifData      The byte array of the GIF data
     * @param frames       The list of frames to be used for the GIF output
     * @param frameDelayMs The delay between frames in milliseconds
     */
    public GeneratedObject(byte[] gifData, List<BufferedImage> frames, int frameDelayMs) {
        if (gifData == null || gifData.length == 0) {
            throw new IllegalArgumentException("GIF data cannot be null or empty");
        }

        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty for GIF output");
        }

        if (frameDelayMs <= 0) {
            throw new IllegalArgumentException("Frame delay must be greater than 0 for GIF output");
        }

        this.image = frames.getFirst();
        this.gifData = gifData;
        this.outputType = OutputType.GIF;
        this.animationFrames = List.copyOf(frames);
        this.frameDelayMs = frameDelayMs;
        this.frameDelaysMs = null;
    }

    /**
     * Constructor for animated GIFs with PER-FRAME delays - the tick-timed pack texture
     * animation shape, where frames legitimately hold for different times (e.g. a long hold on
     * frame 0). {@link #getFrameDelayMs()} reports the first frame's delay;
     * {@link #getFrameDelaysMs()} carries the full list.
     *
     * @param gifData       The byte array of the GIF data
     * @param frames        The list of frames used for the GIF output
     * @param frameDelaysMs One delay per frame, in milliseconds, all positive
     */
    public GeneratedObject(byte[] gifData, List<BufferedImage> frames, List<Integer> frameDelaysMs) {
        if (gifData == null || gifData.length == 0) {
            throw new IllegalArgumentException("GIF data cannot be null or empty");
        }

        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty for GIF output");
        }

        if (frameDelaysMs == null || frameDelaysMs.size() != frames.size()) {
            throw new IllegalArgumentException("Frame delays must match the frame count");
        }

        for (Integer delay : frameDelaysMs) {
            if (delay == null || delay <= 0) {
                throw new IllegalArgumentException("Every frame delay must be greater than 0, got: " + delay);
            }
        }

        this.image = frames.getFirst();
        this.gifData = gifData;
        this.outputType = OutputType.GIF;
        this.animationFrames = List.copyOf(frames);
        this.frameDelaysMs = List.copyOf(frameDelaysMs);
        this.frameDelayMs = this.frameDelaysMs.getFirst();
    }

    /**
     * Check if the generated object is animated
     *
     * @return true if the object is animated (GIF), false otherwise (PNG)
     */
    public boolean isAnimated() {
        return this.outputType == OutputType.GIF;
    }

    /**
     * This animation as one shared-timeline source: the per-frame delays converted to ticks, or
     * - on the uniform-delay paths - the single delay repeated once per frame. This is the shape
     * {@link AnimationTimeline#of(List)} consumes when compositing multiple objects.
     *
     * @return one tick duration per animation frame
     * @throws IllegalStateException when the object is not animated
     */
    public List<Integer> timelineTicks() {
        if (!isAnimated()) {
            throw new IllegalStateException("Static objects have no animation timeline");
        }
        if (frameDelaysMs != null) {
            return frameDelaysMs.stream().map(AnimationTimeline::millisToTicks).toList();
        }
        return Collections.nCopies(animationFrames.size(), AnimationTimeline.millisToTicks(frameDelayMs));
    }

    /**
     * Enum representing the output type of the generated object.
     */
    public enum OutputType {
        PNG, GIF
    }
}
