package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A resolved texture animation: the flipbook's frame sequence with per-frame times in ticks,
 * bound to the decoded sheet so frames crop on demand. Instances are produced per resolution
 * call by the pack layer; frames memoize per distinct flipbook index, so repeated timeline
 * steps showing the same frame share one crop.
 *
 * <p>The {@code interpolate} flag is parsed and carried but rendering treats every animation as
 * nearest-frame - a documented approximation; interpolated packs animate with hard frame steps.
 *
 * <p>Frame images are shared, memoized instances: callers must treat them as immutable (draw
 * them, never paint into them).
 */
public final class PackAnimation {

    /** One step of the animation: the flipbook frame index and how long it shows, in ticks. */
    public record Frame(int index, int timeTicks) {
    }

    private final List<Frame> frames;
    private final int frameWidth;
    private final int frameHeight;
    private final boolean interpolate;
    private final int cycleTicks;
    private final BufferedImage sheet;
    private final ConcurrentHashMap<Integer, BufferedImage> croppedByIndex = new ConcurrentHashMap<>();

    private PackAnimation(List<Frame> frames, int frameWidth, int frameHeight, boolean interpolate,
                          int cycleTicks, BufferedImage sheet) {
        this.frames = frames;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.interpolate = interpolate;
        this.cycleTicks = cycleTicks;
        this.sheet = sheet;
    }

    /**
     * Resolves an animation against its decoded (uncropped) sheet: the explicit frames list, or
     * the default order of every flipbook frame in row-major grid order (left to right, then top
     * to bottom) when the list is absent or empty. Multiple grid columns arise only when the
     * frame width is narrower than the sheet (an explicit {@code width} override); a plain
     * vertical flipbook is a single column.
     *
     * @return the resolved animation, or empty when the animation is effectively static (fewer
     *     than two frame entries)
     * @throws PackLoadException when the meta is invalid against the sheet (a frame index or the
     *                           frame size falls outside the texture, or the cycle length
     *                           overflows) - callers apply the decode path's warn-and-fallback
     *                           policy
     */
    static Optional<PackAnimation> resolve(BufferedImage sheet, AnimationMeta meta) {
        int frameWidth = TextureDecoder.frameWidth(sheet, meta);
        int frameHeight = TextureDecoder.frameHeight(sheet, meta);
        if (frameWidth <= 0 || frameHeight <= 0 || frameWidth > sheet.getWidth() || frameHeight > sheet.getHeight()) {
            throw new PackLoadException("Animation frame size %sx%s is outside texture bounds (%sx%s)",
                String.valueOf(frameWidth), String.valueOf(frameHeight),
                String.valueOf(sheet.getWidth()), String.valueOf(sheet.getHeight()));
        }
        // Vanilla cuts the sheet into a grid of frame-sized cells and plays them row-major
        // (left to right, then top to bottom); a single-column vertical flipbook is the columns
        // == 1 case. See TextureDecoder.frame for the matching crop.
        int flipbookFrames = (sheet.getWidth() / frameWidth) * (sheet.getHeight() / frameHeight);
        List<Frame> frames = new ArrayList<>();
        if (meta.frames() == null || meta.frames().isEmpty()) {
            for (int index = 0; index < flipbookFrames; index++) {
                frames.add(new Frame(index, meta.defaultFrameTimeTicks()));
            }
        } else {
            for (AnimationMeta.FrameEntry entry : meta.frames()) {
                if (entry.index() < 0 || entry.index() >= flipbookFrames) {
                    throw new PackLoadException("Animation frame %s is outside texture bounds (%sx%s, frame %sx%s)",
                        String.valueOf(entry.index()), String.valueOf(sheet.getWidth()),
                        String.valueOf(sheet.getHeight()), String.valueOf(frameWidth), String.valueOf(frameHeight));
                }
                frames.add(new Frame(entry.index(), entry.timeTicks()));
            }
        }
        if (frames.size() < 2) {
            return Optional.empty();
        }
        long cycle = 0;
        for (Frame frame : frames) {
            cycle += frame.timeTicks();
        }
        if (cycle > Integer.MAX_VALUE) {
            throw new PackLoadException("Animation cycle of %s ticks overflows the supported range",
                String.valueOf(cycle));
        }
        return Optional.of(new PackAnimation(List.copyOf(frames), frameWidth, frameHeight,
            meta.interpolate(), (int) cycle, sheet));
    }

    /** The frame sequence in playback order; positions index into this list. */
    public List<Frame> frames() {
        return frames;
    }

    /** The per-position frame times in ticks - the shape {@link AnimationTimeline} consumes. */
    public List<Integer> frameTicks() {
        return frames.stream().map(Frame::timeTicks).toList();
    }

    /** Total cycle length in ticks (the sum of every frame time). */
    public int cycleTicks() {
        return cycleTicks;
    }

    /**
     * The effective frame width in pixels: the mcmeta {@code width} override, or the texture
     * width when absent (the post-default crop size, not the raw sheet width). The sheet holds
     * {@code sheetWidth / frameWidth} grid columns.
     */
    public int frameWidth() {
        return frameWidth;
    }

    /**
     * The effective frame height in pixels: the mcmeta {@code height} override, or the frame
     * width when absent (the library's square-frame default). The sheet holds
     * {@code sheetHeight / frameHeight} grid rows.
     */
    public int frameHeight() {
        return frameHeight;
    }

    /** The parsed {@code interpolate} flag; rendering is nearest-frame regardless. */
    public boolean interpolate() {
        return interpolate;
    }

    /**
     * The frame image at a playback position (an index into {@link #frames()}), cropped from
     * the sheet on first use and memoized per distinct flipbook index. Treat the returned image
     * as immutable - it is shared across positions showing the same flipbook frame.
     *
     * @throws IndexOutOfBoundsException when the position is outside the frame sequence
     */
    public BufferedImage frameImage(int position) {
        Frame frame = frames.get(position);
        return croppedByIndex.computeIfAbsent(frame.index(),
            index -> TextureDecoder.frame(sheet, frameWidth, frameHeight, index));
    }
}
