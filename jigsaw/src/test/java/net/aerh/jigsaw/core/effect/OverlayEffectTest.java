package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.overlay.ColorMode;
import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.core.overlay.OverlayRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlayEffectTest {

    private OverlayRegistry registry;
    private OverlayEffect effect;

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private static Overlay makeOverlay(String rendererType) {
        return new Overlay("test:item", blankImage(), ColorMode.BASE, rendererType);
    }

    @BeforeEach
    void setUp() {
        registry = OverlayRegistry.withDefaults();
        effect = new OverlayEffect(registry);
    }

    // --- id and priority ---

    @Test
    void id_isOverlay() {
        assertThat(effect.id()).isEqualTo("overlay");
    }

    @Test
    void priority_is50() {
        assertThat(effect.priority()).isEqualTo(50);
    }

    // --- appliesTo ---

    @Test
    void appliesTo_returnsFalseWhenNoOverlayMetadata() {
        EffectContext ctx = EffectContext.builder().image(blankImage()).build();
        assertThat(effect.appliesTo(ctx)).isFalse();
    }

    @Test
    void appliesTo_returnsTrueWhenOverlayMetadataPresent() {
        Overlay overlay = makeOverlay("normal");
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .metadata(Map.of(OverlayEffect.META_OVERLAY_DATA, overlay))
                .build();

        assertThat(effect.appliesTo(ctx)).isTrue();
    }

    // --- apply delegates to correct renderer ---

    @Test
    void apply_delegatesToNormalRenderer() {
        Overlay overlay = makeOverlay("normal");
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .metadata(Map.of(OverlayEffect.META_OVERLAY_DATA, overlay))
                .build();

        // Should not throw; normal renderer is registered by default
        EffectContext result = effect.apply(ctx);

        assertThat(result.image()).isNotNull();
    }

    @Test
    void apply_unknownRendererTypeReturnsContextUnchanged() {
        Overlay overlay = makeOverlay("nonexistent_renderer_type");
        BufferedImage original = blankImage();
        EffectContext ctx = EffectContext.builder()
                .image(original)
                .metadata(Map.of(OverlayEffect.META_OVERLAY_DATA, overlay))
                .build();

        EffectContext result = effect.apply(ctx);

        // Image should be the same object since no renderer was found
        assertThat(result.image()).isSameAs(original);
    }

    @Test
    void apply_usesOverlayColorMetadataWhenPresent() {
        Overlay overlay = makeOverlay("normal");
        int tintColor = 0xFF00FF00; // Bright green
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .metadata(Map.of(
                        OverlayEffect.META_OVERLAY_DATA, overlay,
                        OverlayEffect.META_OVERLAY_COLOR, tintColor
                ))
                .build();

        // Should not throw; uses the tint color
        EffectContext result = effect.apply(ctx);
        assertThat(result.image()).isNotNull();
    }

    @Test
    void apply_defaultsToWhiteTintWhenColorMetadataAbsent() {
        // Using ColorMode.OVERLAY means tint is applied; without metadata it should default to white
        Overlay overlay = new Overlay("test:item", blankImage(), ColorMode.OVERLAY, "normal");
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .metadata(Map.of(OverlayEffect.META_OVERLAY_DATA, overlay))
                .build();

        // Should not throw
        EffectContext result = effect.apply(ctx);
        assertThat(result.image()).isNotNull();
    }

    // --- Null guard ---

    @Test
    void constructor_nullRegistryThrows() {
        assertThatThrownBy(() -> new OverlayEffect(null))
                .isInstanceOf(NullPointerException.class);
    }
}
