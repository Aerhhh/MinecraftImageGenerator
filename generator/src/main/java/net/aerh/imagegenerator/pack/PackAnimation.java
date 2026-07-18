package net.aerh.imagegenerator.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackLoadException;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A resolved texture animation: the flipbook's keyframe sequence with per-frame times in ticks,
 * bound to the decoded sheet so frames crop on demand. Instances are produced per resolution
 * call by the pack layer; crops memoize per distinct flipbook index, so repeated timeline steps
 * showing the same frame share one crop.
 *
 * <p><b>Playback positions vs keyframes:</b> {@link #frames()} lists the source keyframes, while
 * playback is enumerated as {@link #frameTicks() positions} - the units the {@link
 * AnimationTimeline} samples and {@link #frameImage(int)} indexes. For a nearest-frame animation
 * the two coincide one-to-one. For an {@code interpolate: true} animation each keyframe expands
 * into one position PER TICK: the vanilla client linearly cross-fades the current keyframe into
 * the next every tick, so position {@code p} within a keyframe of time {@code t} blends the
 * keyframe toward its successor by {@code subTick / t} (0, 1/t, ... (t-1)/t). Nearest-frame
 * animations are byte-identical to before - their positions carry no blend.
 *
 * <p><b>Blend:</b> per pixel the R/G/B channels linearly interpolate with vanilla's truncating
 * mix ({@code (int)((1-delta)*from + delta*to)}); alpha is taken from the CURRENT keyframe, the
 * vanilla {@code SpriteContents} convention - a fully opaque animation stays opaque, so the GIF
 * encoder never drops a cross-faded pixel for partial alpha.
 *
 * <p><b>Cap interaction:</b> per-tick expansion is bounded at {@link
 * AnimationTimeline#MAX_CYCLE_TICKS} positions - beyond that the shared timeline truncates the
 * cycle anyway, so a pack declaring an enormous frametime cannot explode the position list. A
 * truncated interpolated cycle loops early, deterministically, and reports the truncated length
 * from {@link #cycleTicks()}.
 *
 * <p>Frame images are shared, memoized instances: callers must treat them as immutable (draw
 * them, never paint into them).
 */
@Slf4j
public final class PackAnimation {

    /** One keyframe of the animation: the flipbook frame index and how long it shows, in ticks. */
    public record Frame(int index, int timeTicks) {
    }

    /**
     * One playback position: the flipbook frame to show ({@code fromIndex}), the frame to blend
     * toward ({@code toIndex}, equal to {@code fromIndex} for nearest-frame playback), the blend
     * weight in {@code [0, 1)} and how long the position lasts in ticks.
     */
    private record Position(int fromIndex, int toIndex, float delta, int timeTicks) {
    }

    private final List<Frame> frames;
    private final List<Position> positions;
    private final int frameWidth;
    private final int frameHeight;
    private final boolean interpolate;
    private final int cycleTicks;
    private final BufferedImage sheet;
    private final ConcurrentHashMap<Integer, BufferedImage> croppedByIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, BufferedImage> blendedByPosition = new ConcurrentHashMap<>();

    private PackAnimation(List<Frame> frames, int frameWidth, int frameHeight, boolean interpolate,
                          BufferedImage sheet) {
        this.frames = frames;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.interpolate = interpolate;
        this.sheet = sheet;
        this.positions = buildPositions(frames, interpolate);
        int cycle = 0;
        for (Position position : positions) {
            cycle += position.timeTicks();
        }
        this.cycleTicks = cycle;
    }

    /**
     * Expands the keyframes into playback positions. Nearest-frame playback maps each keyframe to
     * one position with no blend. Interpolated playback expands each keyframe of time {@code t}
     * into {@code t} one-tick positions cross-fading toward the next keyframe (wrapping at the
     * cycle end), bounded at {@link AnimationTimeline#MAX_CYCLE_TICKS} positions so an outsized
     * frametime cannot explode the list before the timeline truncates the cycle.
     */
    private static List<Position> buildPositions(List<Frame> frames, boolean interpolate) {
        if (!interpolate) {
            List<Position> positions = new ArrayList<>(frames.size());
            for (Frame frame : frames) {
                positions.add(new Position(frame.index(), frame.index(), 0.0f, frame.timeTicks()));
            }
            return List.copyOf(positions);
        }
        List<Position> positions = new ArrayList<>();
        boolean truncated = false;
        outer:
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            int nextIndex = frames.get((i + 1) % frames.size()).index();
            for (int subTick = 0; subTick < frame.timeTicks(); subTick++) {
                if (positions.size() >= AnimationTimeline.MAX_CYCLE_TICKS) {
                    truncated = true;
                    break outer;
                }
                positions.add(new Position(frame.index(), nextIndex,
                    (float) subTick / (float) frame.timeTicks(), 1));
            }
        }
        if (truncated) {
            log.warn("Interpolated animation expands beyond {} ticks; truncating the cross-fade "
                + "(loops early, deterministic)", AnimationTimeline.MAX_CYCLE_TICKS);
        }
        return List.copyOf(positions);
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
            meta.interpolate(), sheet));
    }

    /**
     * The source keyframe sequence in playback order. For nearest-frame animations these map
     * one-to-one to the {@link #frameTicks() playback positions}; for interpolated animations the
     * positions expand per tick, so this list is the shorter keyframe view (see the class javadoc).
     */
    public List<Frame> frames() {
        return frames;
    }

    /**
     * The per-position frame times in ticks - the shape {@link AnimationTimeline} consumes and the
     * domain of {@link #frameImage(int)}. Nearest-frame animations return one entry per keyframe;
     * interpolated animations return one one-tick entry per cross-faded tick.
     */
    public List<Integer> frameTicks() {
        return positions.stream().map(Position::timeTicks).toList();
    }

    /** Total cycle length in ticks (the sum of every playback position's time). */
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

    /** The parsed {@code interpolate} flag; playback cross-fades per tick when set. */
    public boolean interpolate() {
        return interpolate;
    }

    /**
     * The frame image at a playback position (an index into {@link #frameTicks()}). A
     * nearest-frame position crops its flipbook frame from the sheet on first use, memoized per
     * distinct flipbook index; an interpolated position cross-fades its two keyframes and memoizes
     * the blend per position. Treat the returned image as immutable - it is shared across every
     * position showing the same frame or blend.
     *
     * @throws IndexOutOfBoundsException when the position is outside the playback sequence
     */
    public BufferedImage frameImage(int position) {
        Position playback = positions.get(position);
        if (playback.delta() == 0.0f || playback.fromIndex() == playback.toIndex()) {
            return rawFrame(playback.fromIndex());
        }
        return blendedByPosition.computeIfAbsent(position, key -> blend(playback));
    }

    /** The uncropped-sheet crop of one flipbook frame, memoized per index. */
    private BufferedImage rawFrame(int index) {
        return croppedByIndex.computeIfAbsent(index,
            key -> TextureDecoder.frame(sheet, frameWidth, frameHeight, key));
    }

    /**
     * The per-pixel cross-fade of a position's two keyframes: R/G/B linearly interpolated with
     * vanilla's truncating mix, alpha taken from the current keyframe (see the class javadoc).
     */
    private BufferedImage blend(Position playback) {
        BufferedImage from = rawFrame(playback.fromIndex());
        BufferedImage to = rawFrame(playback.toIndex());
        BufferedImage blended = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                blended.setRGB(x, y, lerpArgb(from.getRGB(x, y), to.getRGB(x, y), playback.delta()));
            }
        }
        return blended;
    }

    /**
     * Linearly interpolates the R/G/B channels of two ARGB pixels by {@code delta} in
     * {@code [0, 1)}, keeping the {@code from} pixel's alpha - vanilla's {@code SpriteContents}
     * interpolation, so an opaque animation stays opaque and never surfaces partial-alpha pixels
     * the GIF encoder would drop.
     */
    private static int lerpArgb(int from, int to, float delta) {
        int red = mix(delta, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int green = mix(delta, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int blue = mix(delta, from & 0xFF, to & 0xFF);
        return (from & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    /** Vanilla's truncating channel mix: {@code (int)((1 - delta) * from + delta * to)}. */
    private static int mix(float delta, int from, int to) {
        return (int) ((1.0f - delta) * from + delta * to);
    }
}
