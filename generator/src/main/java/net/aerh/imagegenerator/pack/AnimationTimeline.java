package net.aerh.imagegenerator.pack;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A shared animation timeline over one or more cyclic sources (animated textures, an
 * obfuscation ticker, per-item animation cycles): the global cycle is the least common multiple
 * of the source cycles, sampled at every tick where ANY source changes frame - equivalent to
 * sampling at the greatest common divisor of the step times with consecutive identical steps
 * merged, so a frames list like {@code [0..16, {index: 0, time: 100}]} produces one long-hold
 * step rather than fifty repeats.
 *
 * <p><b>Caps, deterministic:</b> the global cycle is capped at {@link #MAX_CYCLE_TICKS} and the
 * step count at {@link #MAX_ANIMATION_FRAMES}; when either is exceeded the timeline truncates
 * to the cap with a warning, keeping the leading steps unchanged. A truncated timeline still
 * loops (the tail of the cycle is simply dropped).
 *
 * <p>One tick is 50 ms ({@link #MILLIS_PER_TICK}) - 5 GIF centiseconds per tick.
 */
@Slf4j
public final class AnimationTimeline {

    /** Maximum steps a timeline may produce; longer cycles truncate with a warning. */
    public static final int MAX_ANIMATION_FRAMES = 120;

    /** Maximum global cycle length in ticks (60 seconds); longer cycles truncate with a warning. */
    public static final int MAX_CYCLE_TICKS = 1_200;

    /** Milliseconds per Minecraft tick. */
    public static final int MILLIS_PER_TICK = 50;

    /**
     * Converts a tick duration to milliseconds ({@code ticks * }{@link #MILLIS_PER_TICK}) - the
     * single tick-to-delay conversion every animated path shares, so the GIF timing rule lives
     * in one place.
     */
    public static int ticksToMillis(int ticks) {
        return ticks * MILLIS_PER_TICK;
    }

    /**
     * Converts a millisecond delay back to whole ticks (floor, minimum one) - the inverse of
     * {@link #ticksToMillis(int)}, used where a composite reads a generated object's per-frame
     * delays back into the tick durations a scene timeline consumes. A delay that is an exact
     * multiple of {@link #MILLIS_PER_TICK} (every tick-timed pack animation) round-trips exactly.
     */
    public static int millisToTicks(int delayMs) {
        return Math.max(1, delayMs / MILLIS_PER_TICK);
    }

    /**
     * One timeline step: how long it lasts in ticks and, per source (input order), the frame
     * position the source shows during the step - an index into that source's frame-times list.
     */
    public record Step(int durationTicks, List<Integer> framePositions) {
    }

    private final List<Step> steps;
    private final int cycleTicks;
    private final boolean truncated;

    private AnimationTimeline(List<Step> steps, int cycleTicks, boolean truncated) {
        this.steps = steps;
        this.cycleTicks = cycleTicks;
        this.truncated = truncated;
    }

    /**
     * Builds the timeline for the given sources. Each source is its list of frame times in
     * ticks, in playback order (see {@link PackAnimation#frameTicks()}).
     *
     * @throws IllegalArgumentException when no source is given, a source is empty, or a frame
     *                                  time is not positive
     */
    public static AnimationTimeline of(List<List<Integer>> sourceFrameTicks) {
        if (sourceFrameTicks == null || sourceFrameTicks.isEmpty()) {
            throw new IllegalArgumentException("At least one animation source is required");
        }
        long[] cycles = new long[sourceFrameTicks.size()];
        for (int i = 0; i < sourceFrameTicks.size(); i++) {
            List<Integer> ticks = sourceFrameTicks.get(i);
            if (ticks == null || ticks.isEmpty()) {
                throw new IllegalArgumentException("Animation source " + i + " has no frames");
            }
            long cycle = 0;
            for (Integer time : ticks) {
                if (time == null || time < 1) {
                    throw new IllegalArgumentException(
                        "Animation source " + i + " has a non-positive frame time: " + time);
                }
                cycle += time;
            }
            cycles[i] = cycle;
        }

        boolean truncated = false;
        long globalCycle = 1;
        for (long cycle : cycles) {
            globalCycle = lcm(globalCycle, cycle);
            if (globalCycle > MAX_CYCLE_TICKS) {
                globalCycle = MAX_CYCLE_TICKS;
                truncated = true;
                break;
            }
        }
        if (truncated) {
            log.warn("Animation cycle exceeds {} ticks; truncating the timeline (loops early, deterministic)",
                MAX_CYCLE_TICKS);
        }

        // Every tick where any source changes frame, within [0, globalCycle).
        TreeSet<Integer> boundaries = new TreeSet<>();
        for (List<Integer> ticks : sourceFrameTicks) {
            long tick = 0;
            int position = 0;
            while (tick < globalCycle) {
                boundaries.add((int) tick);
                tick += ticks.get(position);
                position = (position + 1) % ticks.size();
            }
        }

        List<Integer> starts = new ArrayList<>(boundaries);
        int cycleTicks = (int) globalCycle;
        if (starts.size() > MAX_ANIMATION_FRAMES) {
            // The first dropped step's start becomes the (early) cycle end, so every kept step
            // retains its natural duration.
            cycleTicks = starts.get(MAX_ANIMATION_FRAMES);
            starts = starts.subList(0, MAX_ANIMATION_FRAMES);
            truncated = true;
            log.warn("Animation timeline exceeds {} steps; truncating the timeline (loops early, deterministic)",
                MAX_ANIMATION_FRAMES);
        }

        List<Step> steps = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : cycleTicks;
            List<Integer> positions = new ArrayList<>(sourceFrameTicks.size());
            for (int source = 0; source < sourceFrameTicks.size(); source++) {
                positions.add(positionAt(sourceFrameTicks.get(source), cycles[source], start));
            }
            steps.add(new Step(end - start, List.copyOf(positions)));
        }
        return new AnimationTimeline(List.copyOf(steps), cycleTicks, truncated);
    }

    /** The frame position a source shows at an absolute tick (walks the cycle once). */
    private static int positionAt(List<Integer> frameTicks, long sourceCycle, long tick) {
        long within = tick % sourceCycle;
        long cursor = 0;
        for (int position = 0; position < frameTicks.size(); position++) {
            cursor += frameTicks.get(position);
            if (within < cursor) {
                return position;
            }
        }
        // Unreachable: within < sourceCycle = sum of all frame times.
        return frameTicks.size() - 1;
    }

    private static long lcm(long a, long b) {
        return a / gcd(a, b) * b;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long swap = a % b;
            a = b;
            b = swap;
        }
        return a;
    }

    /** The timeline steps, in playback order. */
    public List<Step> steps() {
        return steps;
    }

    /** The (possibly truncated) global cycle length in ticks - the sum of all step durations. */
    public int cycleTicks() {
        return cycleTicks;
    }

    /** Whether either cap truncated the timeline. */
    public boolean truncated() {
        return truncated;
    }

    /** Per-step delays in milliseconds ({@code durationTicks * 50}), the GIF encoding shape. */
    public List<Integer> stepDelaysMillis() {
        return steps.stream().map(step -> ticksToMillis(step.durationTicks())).toList();
    }

    /** Per-step durations in ticks - the shape a larger (scene) timeline consumes as a source. */
    public List<Integer> stepTicks() {
        return steps.stream().map(Step::durationTicks).toList();
    }
}
