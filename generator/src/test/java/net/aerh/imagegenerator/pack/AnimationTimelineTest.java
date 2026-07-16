package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTimelineTest {

    @Test
    void singleSourceStepsMirrorItsFrames() {
        AnimationTimeline timeline = AnimationTimeline.of(List.of(List.of(3, 3, 3)));

        assertEquals(9, timeline.cycleTicks());
        assertFalse(timeline.truncated());
        assertEquals(3, timeline.steps().size());
        for (int position = 0; position < 3; position++) {
            AnimationTimeline.Step step = timeline.steps().get(position);
            assertEquals(3, step.durationTicks());
            assertEquals(List.of(position), step.framePositions());
        }
        assertEquals(List.of(150, 150, 150), timeline.stepDelaysMillis());
        assertEquals(List.of(3, 3, 3), timeline.stepTicks());
    }

    @Test
    void longHoldFramesListProducesOneLongStep() {
        // The shiny-strip timing shape: 17 two-tick frames plus a 100-tick hold on frame 0.
        List<Integer> ticks = new ArrayList<>(Collections.nCopies(17, 2));
        ticks.add(100);
        AnimationTimeline timeline = AnimationTimeline.of(List.of(ticks));

        assertEquals(134, timeline.cycleTicks());
        assertEquals(18, timeline.steps().size());
        assertEquals(100, timeline.steps().getLast().durationTicks());
        assertEquals(5000, timeline.stepDelaysMillis().getLast(), "100 ticks hold 5000 ms");
        assertEquals(100, timeline.stepDelaysMillis().getFirst(), "2 ticks are 100 ms (10 GIF centiseconds)");
    }

    @Test
    void twoSourcesSampleAtEveryFrameChange() {
        // Cycles 6 (two 3-tick frames) and 4 (two 2-tick frames): LCM 12, boundaries at every
        // multiple of 2 or 3 below 12.
        AnimationTimeline timeline = AnimationTimeline.of(List.of(List.of(3, 3), List.of(2, 2)));

        assertEquals(12, timeline.cycleTicks());
        assertFalse(timeline.truncated());
        List<Integer> starts = List.of(0, 2, 3, 4, 6, 8, 9, 10);
        assertEquals(starts.size(), timeline.steps().size());
        int tick = 0;
        for (int index = 0; index < starts.size(); index++) {
            assertEquals(starts.get(index), tick, "step " + index + " start");
            AnimationTimeline.Step step = timeline.steps().get(index);
            assertEquals((tick % 6) / 3, step.framePositions().get(0), "source 0 position at tick " + tick);
            assertEquals((tick % 4) / 2, step.framePositions().get(1), "source 1 position at tick " + tick);
            tick += step.durationTicks();
        }
        assertEquals(12, tick, "durations sum to the cycle");
    }

    @Test
    void coprimeCyclesMeetAtTheirProduct() {
        // 3-tick and 7-tick single-step... two frames each: cycles 3 and 7 need frame lists
        // summing to those lengths.
        AnimationTimeline timeline = AnimationTimeline.of(List.of(List.of(1, 2), List.of(3, 4)));

        assertEquals(21, timeline.cycleTicks());
        assertFalse(timeline.truncated());
        int total = timeline.steps().stream().mapToInt(AnimationTimeline.Step::durationTicks).sum();
        assertEquals(21, total);
    }

    @Test
    void stepCountCapTruncatesDeterministically() {
        AnimationTimeline timeline = AnimationTimeline.of(List.of(Collections.nCopies(200, 1)));

        assertTrue(timeline.truncated());
        assertEquals(AnimationTimeline.MAX_ANIMATION_FRAMES, timeline.steps().size());
        assertEquals(AnimationTimeline.MAX_ANIMATION_FRAMES, timeline.cycleTicks(),
            "the first dropped step's start ends the truncated cycle");
        for (AnimationTimeline.Step step : timeline.steps()) {
            assertEquals(1, step.durationTicks(), "kept steps keep their natural durations");
        }
    }

    @Test
    void cycleCapTruncatesDeterministically() {
        // Cycles 601 and 2: the LCM (1202) exceeds MAX_CYCLE_TICKS, so the global cycle caps at
        // 1200. Source [1, 1] changes frame every tick, so boundaries land at every integer in
        // [0, 1200) - 1200 of them - and the step cap then truncates to the first 120, each one
        // tick long, ending the cycle at tick 120. The exact shape is fully determined; pinning
        // it kills mutants that return zero steps, cap at the wrong boundary, or mistime the
        // final capped step.
        AnimationTimeline timeline = AnimationTimeline.of(List.of(List.of(300, 301), List.of(1, 1)));

        assertTrue(timeline.truncated());
        assertEquals(AnimationTimeline.MAX_ANIMATION_FRAMES, timeline.steps().size());
        assertEquals(AnimationTimeline.MAX_ANIMATION_FRAMES, timeline.cycleTicks(),
            "120 one-tick steps end the truncated cycle at tick 120");
        for (AnimationTimeline.Step step : timeline.steps()) {
            assertEquals(1, step.durationTicks(), "every kept step is one tick");
        }
        assertEquals(AnimationTimeline.MAX_ANIMATION_FRAMES * AnimationTimeline.MILLIS_PER_TICK,
            timeline.stepDelaysMillis().stream().mapToInt(Integer::intValue).sum(),
            "per-step delays sum to cycleTicks * 50 ms");
    }

    @Test
    void identicalTimelinesAreDeterministic() {
        List<List<Integer>> sources = List.of(List.of(3, 3), List.of(2, 2));
        assertEquals(AnimationTimeline.of(sources).steps(), AnimationTimeline.of(sources).steps());
    }

    @Test
    void rejectsMissingAndInvalidSources() {
        assertThrows(IllegalArgumentException.class, () -> AnimationTimeline.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> AnimationTimeline.of(List.of(List.of())));
        assertThrows(IllegalArgumentException.class, () -> AnimationTimeline.of(List.of(List.of(0))));
        assertThrows(IllegalArgumentException.class, () -> AnimationTimeline.of(List.of(List.of(-3))));
    }
}
