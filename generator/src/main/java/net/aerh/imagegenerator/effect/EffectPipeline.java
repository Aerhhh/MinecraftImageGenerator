package net.aerh.imagegenerator.effect;

import net.hypixel.nerdbot.marmalade.pattern.Pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline for executing multiple effects in sequence.
 * Applies effects in the priority they are defined.
 * Delegates execution to a Marmalade {@link Pipeline}, adapting each
 * {@link ImageEffect} to a {@link Pipeline.Stage} via a wrapper that
 * bridges the EffectResult return type back to EffectContext.
 */
public class EffectPipeline {

    private final Pipeline<EffectContext> pipeline;

    private EffectPipeline(Pipeline<EffectContext> pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Execute all effects in the pipeline.
     * Effects are applied in priority order, with each effect receiving
     * the output of the previous one.
     *
     * @param initialContext The initial {@link EffectContext context} with the base image
     *
     * @return Final {@link EffectContext context} after all effects have been applied
     */
    public EffectContext execute(EffectContext initialContext) {
        return pipeline.execute(initialContext);
    }

    public static class Builder {
        private final List<ImageEffect> effects = new ArrayList<>();

        /**
         * Add an {@link ImageEffect effect} to the pipeline.
         *
         * @param effect The {@link ImageEffect effect} to add
         *
         * @return This builder
         */
        public Builder addEffect(ImageEffect effect) {
            if (effect != null) {
                effects.add(effect);
            }

            return this;
        }

        /**
         * Build the effect pipeline.
         *
         * @return New {@link EffectPipeline} instance
         */
        public EffectPipeline build() {
            Pipeline.Builder<EffectContext> pipelineBuilder = Pipeline.builder();

            for (ImageEffect effect : effects) {
                pipelineBuilder.addStage(new Pipeline.Stage<EffectContext>() {
                    @Override
                    public EffectContext apply(EffectContext context) {
                        return effect.apply(context).toContext(context);
                    }

                    @Override
                    public int getPriority() {
                        return effect.getPriority();
                    }

                    @Override
                    public boolean canApply(EffectContext context) {
                        return effect.canApply(context);
                    }

                    @Override
                    public String getName() {
                        return effect.getName();
                    }
                });
            }

            return new EffectPipeline(pipelineBuilder.build());
        }
    }
}