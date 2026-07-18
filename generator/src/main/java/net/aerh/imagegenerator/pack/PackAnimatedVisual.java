package net.aerh.imagegenerator.pack;

import java.util.List;

/**
 * An item's GUI visual resolved across its own animation timeline: one {@link PackItemVisual}
 * per timeline step plus each step's duration in ticks. The timeline is the least common
 * multiple of the item's animated texture cycles, sampled where any texture changes frame and
 * capped like every timeline (see {@link AnimationTimeline}). Every step is the same visual
 * kind (all sprites or all elements rasters) - the kind an equivalent static resolve returns.
 *
 * @param steps     one resolved visual per timeline step, in playback order
 * @param stepTicks the matching step durations in ticks ({@code steps.size()} entries)
 */
public record PackAnimatedVisual(List<PackItemVisual> steps, List<Integer> stepTicks) {

    public PackAnimatedVisual {
        if (steps == null || stepTicks == null || steps.isEmpty() || steps.size() != stepTicks.size()) {
            throw new IllegalArgumentException("steps and stepTicks must be non-empty and the same size");
        }
        steps = List.copyOf(steps);
        stepTicks = List.copyOf(stepTicks);
    }
}
