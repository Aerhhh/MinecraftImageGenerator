package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * An ordered, immutable sequence of {@link ImageEffect}s that are applied
 * in ascending priority order to an {@link EffectContext}.
 * <p>
 * Build via {@link #builder()}; the pipeline is frozen at {@link Builder#build()} time.
 * Two pipelines can be merged with {@link #then(EffectPipeline)}, which produces a new
 * pipeline whose effects are sorted by priority across both sources.
 */
public final class EffectPipeline {

    /** Effects sorted by priority ascending, with insertion order as tiebreaker. */
    private final List<ImageEffect> effects;

    private EffectPipeline(List<ImageEffect> sortedEffects) {
        this.effects = List.copyOf(sortedEffects);
    }

    /**
     * Runs each applicable effect in priority order, threading the context through.
     *
     * @param context the initial context; must not be {@code null}
     * @return the final context after all applicable effects have been applied
     */
    public EffectContext execute(EffectContext context) {
        Objects.requireNonNull(context, "context must not be null");
        EffectContext current = context;
        for (ImageEffect effect : effects) {
            if (effect.appliesTo(current)) {
                current = effect.apply(current);
            }
        }
        return current;
    }

    /**
     * Composes this pipeline with {@code other}, returning a new pipeline that
     * contains all effects from both, sorted by priority.
     *
     * @param other the pipeline to append; must not be {@code null}
     * @return a new combined pipeline
     */
    public EffectPipeline then(EffectPipeline other) {
        Objects.requireNonNull(other, "other must not be null");
        List<ImageEffect> combined = new ArrayList<>(this.effects.size() + other.effects.size());
        combined.addAll(this.effects);
        combined.addAll(other.effects);
        combined.sort(Comparator.comparingInt(ImageEffect::priority));
        return new EffectPipeline(combined);
    }

    /**
     * Returns a new, empty {@link Builder}.
     *
     * @return a fresh pipeline builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EffectPipeline}.
     *
     * <p>Add effects via {@link #add(ImageEffect)} and call {@link #build()} to produce an
     * immutable pipeline. The builder may be reused after calling {@code build()}.
     */
    public static final class Builder {

        /** Insertion-ordered list; we snapshot this at build() time. */
        private final List<ImageEffect> pending = new ArrayList<>();

        private Builder() {}

        /**
         * Adds an effect to this builder. Effects are later sorted by priority at build time.
         *
         * @param effect the effect to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder add(ImageEffect effect) {
            Objects.requireNonNull(effect, "effect must not be null");
            pending.add(effect);
            return this;
        }

        /**
         * Builds an immutable {@link EffectPipeline}. The snapshot is taken at the moment
         * this method is called; subsequent calls to {@link #add} do not affect the returned pipeline.
         * <p>
         * Effects with equal priority retain the order in which they were added.
         *
         * @return a new, immutable pipeline
         */
        public EffectPipeline build() {
            // Stable sort: preserves insertion order for equal priorities.
            List<ImageEffect> sorted = new ArrayList<>(pending);
            sorted.sort(Comparator.comparingInt(ImageEffect::priority));
            return new EffectPipeline(sorted);
        }
    }
}
