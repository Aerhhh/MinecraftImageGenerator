package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectPipelineTest {

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private static EffectContext baseContext() {
        return EffectContext.builder()
                .image(blankImage())
                .itemId("minecraft:diamond_sword")
                .build();
    }

    /** A test effect that records execution order via a shared list. */
    private static ImageEffect trackingEffect(String id, int priority, List<String> log) {
        return new ImageEffect() {
            @Override public String id() { return id; }
            @Override public int priority() { return priority; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                log.add(id);
                return ctx;
            }
        };
    }

    /** An effect that only applies when enchanted. */
    private static ImageEffect enchantedOnlyEffect(String id, int priority, List<String> log) {
        return new ImageEffect() {
            @Override public String id() { return id; }
            @Override public int priority() { return priority; }
            @Override public boolean appliesTo(EffectContext ctx) { return ctx.enchanted(); }
            @Override public EffectContext apply(EffectContext ctx) {
                log.add(id);
                return ctx;
            }
        };
    }

    // Test 1: Effects execute in ascending priority order
    @Test
    void execute_runsEffectsInAscendingPriorityOrder() {
        List<String> log = new ArrayList<>();
        ImageEffect high = trackingEffect("high", 300, log);
        ImageEffect low = trackingEffect("low", 100, log);
        ImageEffect mid = trackingEffect("mid", 200, log);

        EffectPipeline pipeline = EffectPipeline.builder()
                .add(high)
                .add(low)
                .add(mid)
                .build();

        pipeline.execute(baseContext());

        assertThat(log).containsExactly("low", "mid", "high");
    }

    // Test 2: Non-applicable effects are skipped
    @Test
    void execute_skipsNonApplicableEffects() {
        List<String> log = new ArrayList<>();
        ImageEffect always = trackingEffect("always", 100, log);
        ImageEffect enchantedOnly = enchantedOnlyEffect("enchanted_only", 200, log);

        EffectPipeline pipeline = EffectPipeline.builder()
                .add(always)
                .add(enchantedOnly)
                .build();

        EffectContext notEnchanted = EffectContext.builder()
                .image(blankImage())
                .itemId("minecraft:iron_sword")
                .enchanted(false)
                .build();

        pipeline.execute(notEnchanted);

        assertThat(log).containsExactly("always");
        assertThat(log).doesNotContain("enchanted_only");
    }

    // Test 3: Empty pipeline returns the input context unchanged
    @Test
    void execute_emptyPipelineReturnsInputContextUnchanged() {
        EffectPipeline pipeline = EffectPipeline.builder().build();
        EffectContext ctx = baseContext();

        EffectContext result = pipeline.execute(ctx);

        assertThat(result.image()).isSameAs(ctx.image());
        assertThat(result.itemId()).isEqualTo(ctx.itemId());
    }

    // Test 4: Pipeline composition via then() runs all effects in correct priority order
    @Test
    void then_composesTwoPipelinesInPriorityOrder() {
        List<String> log = new ArrayList<>();

        EffectPipeline first = EffectPipeline.builder()
                .add(trackingEffect("a", 100, log))
                .add(trackingEffect("b", 300, log))
                .build();

        EffectPipeline second = EffectPipeline.builder()
                .add(trackingEffect("c", 200, log))
                .add(trackingEffect("d", 400, log))
                .build();

        EffectPipeline composed = first.then(second);
        composed.execute(baseContext());

        assertThat(log).containsExactly("a", "c", "b", "d");
    }

    // Test 5: Builder is reusable - building twice yields independent pipelines
    @Test
    void builder_buildingTwiceProducesIndependentPipelines() {
        List<String> log1 = new ArrayList<>();
        List<String> log2 = new ArrayList<>();

        EffectPipeline.Builder builder = EffectPipeline.builder()
                .add(trackingEffect("x", 1, log1));

        EffectPipeline p1 = builder.build();

        // Add another effect to the builder - should not affect p1
        builder.add(trackingEffect("y", 2, log2));
        EffectPipeline p2 = builder.build();

        p1.execute(baseContext());
        assertThat(log1).containsExactly("x");

        // p2 has both effects
        p2.execute(baseContext());
        // log1 accumulates from p2's "x" too
        assertThat(log1).hasSize(2); // "x" ran again
        assertThat(log2).containsExactly("y");
    }

    // Test 6: Pipeline is immutable - adding to builder after build does not change pipeline
    @Test
    void pipeline_isImmutableAfterBuild() {
        List<String> log = new ArrayList<>();

        EffectPipeline.Builder builder = EffectPipeline.builder()
                .add(trackingEffect("first", 1, log));

        EffectPipeline pipeline = builder.build();

        // Mutate builder after build
        builder.add(trackingEffect("second", 2, log));

        pipeline.execute(baseContext());

        // Only "first" should have run in the already-built pipeline
        assertThat(log).containsExactly("first");
    }

    // Test 7: null context throws NullPointerException
    @Test
    void execute_nullContextThrowsNullPointerException() {
        EffectPipeline pipeline = EffectPipeline.builder().build();
        assertThatThrownBy(() -> pipeline.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    // Test 8: Effects with equal priority maintain stable relative insertion order
    @Test
    void execute_equalPriorityEffectsMaintainInsertionOrder() {
        List<String> log = new ArrayList<>();

        EffectPipeline pipeline = EffectPipeline.builder()
                .add(trackingEffect("first", 100, log))
                .add(trackingEffect("second", 100, log))
                .add(trackingEffect("third", 100, log))
                .build();

        pipeline.execute(baseContext());

        assertThat(log).containsExactly("first", "second", "third");
    }

    // Test 9: Effect context propagates through pipeline (later effects see earlier effects' output)
    @Test
    void execute_contextPropagatesThroughPipeline() {
        // First effect adds metadata; second effect reads it
        ImageEffect setter = new ImageEffect() {
            @Override public String id() { return "setter"; }
            @Override public int priority() { return 100; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                return ctx.withMetadata("flag", "set");
            }
        };

        final String[] captured = {null};
        ImageEffect reader = new ImageEffect() {
            @Override public String id() { return "reader"; }
            @Override public int priority() { return 200; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                captured[0] = ctx.metadata("flag", String.class).orElse(null);
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder()
                .add(setter)
                .add(reader)
                .build();

        pipeline.execute(baseContext());

        assertThat(captured[0]).isEqualTo("set");
    }
}
