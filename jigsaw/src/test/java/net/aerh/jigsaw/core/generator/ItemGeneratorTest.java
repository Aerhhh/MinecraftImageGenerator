package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.effect.DurabilityBarEffect;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.core.sprite.AtlasSpriteProvider;
import net.aerh.jigsaw.exception.RenderException;
import net.aerh.jigsaw.exception.UnknownItemException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemGeneratorTest {

    private static SpriteProvider spriteProvider;

    private EffectPipeline emptyPipeline;
    private ItemGenerator generator;

    @BeforeAll
    static void initSpriteProvider() {
        spriteProvider = AtlasSpriteProvider.fromDefaults();
    }

    @BeforeEach
    void setUp() {
        emptyPipeline = EffectPipeline.builder().build();
        generator = new ItemGenerator(spriteProvider, emptyPipeline);
    }

    // --- Known item ---

    @Test
    void render_knownItemReturnsStaticImage() throws RenderException {
        ItemRequest request = ItemRequest.builder("diamond_sword").build();
        GeneratorResult result = generator.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
        assertThat(result.firstFrame().getHeight()).isGreaterThan(0);
        assertThat(result.isAnimated()).isFalse();
    }

    // --- Unknown item ---

    @Test
    void render_unknownItemThrowsRenderException() {
        ItemRequest request = ItemRequest.builder("totally_unknown_item_xyz").build();

        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class)
                .hasCauseInstanceOf(UnknownItemException.class);
    }

    @Test
    void render_unknownItemRenderExceptionContainsItemId() {
        ItemRequest request = ItemRequest.builder("missing_item").build();

        assertThatThrownBy(() -> generator.render(request, GenerationContext.defaults()))
                .isInstanceOf(RenderException.class)
                .satisfies(ex -> {
                    RenderException renderEx = (RenderException) ex;
                    assertThat(renderEx.getContext()).containsKey("itemId");
                    assertThat(renderEx.getContext().get("itemId")).isEqualTo("missing_item");
                });
    }

    // --- bigImage upscaling ---

    @Test
    void render_bigImageUpscalesByFactorOfTen() throws RenderException {
        ItemRequest normal = ItemRequest.builder("diamond_sword").build();
        ItemRequest big = ItemRequest.builder("diamond_sword").bigImage(true).build();

        GeneratorResult normalResult = generator.render(normal, GenerationContext.defaults());
        GeneratorResult bigResult = generator.render(big, GenerationContext.defaults());

        int normalW = normalResult.firstFrame().getWidth();
        int bigW = bigResult.firstFrame().getWidth();

        assertThat(bigW).isEqualTo(normalW * 10);
    }

    // --- Effect pipeline execution ---

    @Test
    void render_effectPipelineIsExecuted() throws RenderException {
        List<String> log = new ArrayList<>();

        ImageEffect trackingEffect = new ImageEffect() {
            @Override public String id() { return "tracker"; }
            @Override public int priority() { return 100; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                log.add("executed");
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(trackingEffect).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline);

        gen.render(ItemRequest.builder("diamond_sword").build(), GenerationContext.defaults());

        assertThat(log).containsExactly("executed");
    }

    @Test
    void render_enchantedItemWithGlintReturnsAnimatedResult() throws RenderException {
        EffectPipeline glintPipeline = EffectPipeline.builder()
                .add(new net.aerh.jigsaw.core.effect.GlintEffect())
                .build();

        ItemGenerator gen = new ItemGenerator(spriteProvider, glintPipeline);
        ItemRequest request = ItemRequest.builder("diamond_sword").enchanted(true).build();

        GeneratorResult result = gen.render(request, GenerationContext.defaults());

        assertThat(result.isAnimated()).isTrue();
        assertThat(result).isInstanceOf(GeneratorResult.AnimatedImage.class);
    }

    @Test
    void render_durabilityMetadataIsPassedToPipeline() throws RenderException {
        // DurabilityBarEffect should apply when durabilityPercent is present
        final boolean[] applied = {false};

        ImageEffect durabilityDetector = new ImageEffect() {
            @Override public String id() { return "detector"; }
            @Override public int priority() { return 0; }
            @Override public boolean appliesTo(EffectContext ctx) {
                return ctx.metadata("durabilityPercent", Double.class).isPresent();
            }
            @Override public EffectContext apply(EffectContext ctx) {
                applied[0] = true;
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(durabilityDetector).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline);

        ItemRequest request = ItemRequest.builder("diamond_sword")
                .durabilityPercent(0.5)
                .build();

        gen.render(request, GenerationContext.defaults());

        assertThat(applied[0]).isTrue();
    }

    // --- inputType / outputType ---

    @Test
    void inputType_returnsItemRequestClass() {
        assertThat(generator.inputType()).isEqualTo(ItemRequest.class);
    }

    @Test
    void outputType_returnsGeneratorResultClass() {
        assertThat(generator.outputType()).isEqualTo(GeneratorResult.class);
    }

    // --- null guards ---

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> generator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        ItemRequest request = ItemRequest.builder("diamond_sword").build();
        assertThatThrownBy(() -> generator.render(request, null))
                .isInstanceOf(NullPointerException.class);
    }
}
