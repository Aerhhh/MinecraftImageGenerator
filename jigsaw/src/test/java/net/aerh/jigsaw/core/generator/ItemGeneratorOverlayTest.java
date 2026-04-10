package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;
import net.aerh.jigsaw.api.effect.MetadataKeys;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.core.effect.OverlayEffect;
import net.aerh.jigsaw.core.overlay.OverlayLoader;
import net.aerh.jigsaw.core.overlay.OverlayRegistry;
import net.aerh.jigsaw.core.sprite.AtlasSpriteProvider;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ItemGeneratorOverlayTest {

    private static SpriteProvider spriteProvider;
    private static OverlayLoader overlayLoader;

    @BeforeAll
    static void init() {
        spriteProvider = AtlasSpriteProvider.fromDefaults();
        overlayLoader = OverlayLoader.fromDefaults();
    }

    @Test
    void render_leatherHelmetInjectsOverlayMetadata() throws RenderException {
        AtomicReference<Overlay> capturedOverlay = new AtomicReference<>();
        AtomicReference<Integer> capturedColor = new AtomicReference<>();

        ImageEffect captureEffect = new ImageEffect() {
            @Override public String id() { return "capture"; }
            @Override public int priority() { return 1; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                ctx.metadata(MetadataKeys.OVERLAY_DATA, Overlay.class).ifPresent(capturedOverlay::set);
                ctx.metadata(MetadataKeys.OVERLAY_COLOR, Integer.class).ifPresent(capturedColor::set);
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(captureEffect).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline, overlayLoader);

        ItemRequest request = ItemRequest.builder()
                .itemId("leather_helmet")
                .dyeColor(0xFF0000) // red
                .build();

        gen.render(request, GenerationContext.defaults());

        assertThat(capturedOverlay.get()).isNotNull();
        assertThat(capturedOverlay.get().texture()).isNotNull();
        assertThat(capturedColor.get()).isEqualTo(0xFF0000);
    }

    @Test
    void render_diamondSwordDoesNotInjectOverlayMetadata() throws RenderException {
        AtomicReference<Overlay> capturedOverlay = new AtomicReference<>();

        ImageEffect captureEffect = new ImageEffect() {
            @Override public String id() { return "capture"; }
            @Override public int priority() { return 1; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                ctx.metadata(MetadataKeys.OVERLAY_DATA, Overlay.class).ifPresent(capturedOverlay::set);
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(captureEffect).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline, overlayLoader);

        gen.render(ItemRequest.builder().itemId("diamond_sword").build(), GenerationContext.defaults());

        assertThat(capturedOverlay.get()).isNull();
    }

    @Test
    void render_leatherHelmetWithNoDyeUsesDefault() throws RenderException {
        AtomicReference<Integer> capturedColor = new AtomicReference<>();

        ImageEffect captureEffect = new ImageEffect() {
            @Override public String id() { return "capture"; }
            @Override public int priority() { return 1; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                ctx.metadata(MetadataKeys.OVERLAY_COLOR, Integer.class).ifPresent(capturedColor::set);
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(captureEffect).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline, overlayLoader);

        // No dye color set - should fall back to default (10511680 for leather armor)
        gen.render(ItemRequest.builder().itemId("leather_helmet").build(), GenerationContext.defaults());

        assertThat(capturedColor.get()).isNotNull();
        assertThat(capturedColor.get()).isEqualTo(10511680); // default leather color
    }

    @Test
    void render_potionInjectsOverlayMetadata() throws RenderException {
        AtomicReference<Overlay> capturedOverlay = new AtomicReference<>();

        ImageEffect captureEffect = new ImageEffect() {
            @Override public String id() { return "capture"; }
            @Override public int priority() { return 1; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                ctx.metadata(MetadataKeys.OVERLAY_DATA, Overlay.class).ifPresent(capturedOverlay::set);
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(captureEffect).build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline, overlayLoader);

        gen.render(ItemRequest.builder().itemId("potion").build(), GenerationContext.defaults());

        assertThat(capturedOverlay.get()).isNotNull();
    }

    @Test
    void render_withOverlayEffectProducesResult() throws RenderException {
        OverlayRegistry registry = OverlayRegistry.withDefaults();
        EffectPipeline pipeline = EffectPipeline.builder()
                .add(new OverlayEffect(registry))
                .build();
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline, overlayLoader);

        ItemRequest request = ItemRequest.builder()
                .itemId("leather_helmet")
                .dyeColor(0x0000FF) // blue
                .build();

        GeneratorResult result = gen.render(request, GenerationContext.defaults());

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
    }

    @Test
    void render_nullOverlayLoaderSkipsOverlays() throws RenderException {
        AtomicReference<Overlay> capturedOverlay = new AtomicReference<>();

        ImageEffect captureEffect = new ImageEffect() {
            @Override public String id() { return "capture"; }
            @Override public int priority() { return 1; }
            @Override public boolean appliesTo(EffectContext ctx) { return true; }
            @Override public EffectContext apply(EffectContext ctx) {
                ctx.metadata(MetadataKeys.OVERLAY_DATA, Overlay.class).ifPresent(capturedOverlay::set);
                return ctx;
            }
        };

        EffectPipeline pipeline = EffectPipeline.builder().add(captureEffect).build();
        // No overlay loader - uses the 2-arg constructor
        ItemGenerator gen = new ItemGenerator(spriteProvider, pipeline);

        gen.render(ItemRequest.builder().itemId("leather_helmet").build(), GenerationContext.defaults());

        assertThat(capturedOverlay.get()).isNull();
    }
}
