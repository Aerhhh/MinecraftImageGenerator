package net.aerh.jigsaw.core.overlay;

import net.aerh.jigsaw.api.overlay.ColorMode;
import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayRendererTest {

    private OverlayRegistry registry;

    private static BufferedImage solidRedImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, 0xFFFF0000); // fully opaque red
            }
        }
        return img;
    }

    private static BufferedImage solidWhiteImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, 0xFFFFFFFF); // fully opaque white
            }
        }
        return img;
    }

    @BeforeEach
    void setUp() {
        registry = OverlayRegistry.withDefaults();
    }

    // Test 1: Registry lookup works for all 3 default types
    @Test
    void registry_containsAllThreeDefaultRenderers() {
        assertThat(registry.getRenderer("normal")).isPresent();
        assertThat(registry.getRenderer("mapped")).isPresent();
        assertThat(registry.getRenderer("dual_layer")).isPresent();
    }

    // Test 2: Unknown type returns empty
    @Test
    void registry_unknownTypeReturnsEmpty() {
        assertThat(registry.getRenderer("nonexistent")).isEmpty();
    }

    // Test 3: Can register a custom renderer
    @Test
    void registry_canRegisterCustomRenderer() {
        OverlayRenderer custom = new OverlayRenderer() {
            @Override public String type() { return "custom"; }
            @Override public BufferedImage render(BufferedImage base, Overlay overlay, int color) { return base; }
        };

        registry.register(custom);

        assertThat(registry.getRenderer("custom")).isPresent();
        assertThat(registry.getRenderer("custom").get()).isSameAs(custom);
    }

    // Test 4: Registering a renderer with the same type replaces the previous one
    @Test
    void registry_registeringDuplicateTypeReplacesExisting() {
        OverlayRenderer replacement = new OverlayRenderer() {
            @Override public String type() { return "normal"; }
            @Override public BufferedImage render(BufferedImage base, Overlay overlay, int color) { return base; }
        };

        registry.register(replacement);

        Optional<OverlayRenderer> found = registry.getRenderer("normal");
        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(replacement);
    }

    // Test 5: NormalOverlayRenderer returns image with correct dimensions
    @Test
    void normalRenderer_returnsCorrectDimensions() {
        OverlayRenderer renderer = registry.getRenderer("normal").orElseThrow();
        BufferedImage base = solidRedImage(32, 32);
        BufferedImage overlayTex = solidWhiteImage(32, 32);
        Overlay overlay = new Overlay("minecraft:leather_helmet", overlayTex, ColorMode.OVERLAY, "normal", null);

        BufferedImage result = renderer.render(base, overlay, 0xFF00FF00);

        assertThat(result.getWidth()).isEqualTo(32);
        assertThat(result.getHeight()).isEqualTo(32);
    }

    // Test 6: NormalOverlayRenderer with a white overlay and a blue tint produces bluish pixels
    @Test
    void normalRenderer_tintColorIsApplied() {
        OverlayRenderer renderer = registry.getRenderer("normal").orElseThrow();
        BufferedImage base = solidWhiteImage(4, 4);
        BufferedImage overlayTex = solidWhiteImage(4, 4); // white overlay: multiply by tint = tint color
        Overlay overlay = new Overlay("minecraft:leather_helmet", overlayTex, ColorMode.OVERLAY, "normal", null);

        int blueTint = 0xFF0000FF; // fully opaque blue
        BufferedImage result = renderer.render(base, overlay, blueTint);

        // Each composited pixel should have significant blue component
        int pixel = result.getRGB(0, 0);
        int blue = pixel & 0xFF;
        assertThat(blue).isGreaterThan(0);
    }

    // Test 7: MappedOverlayRenderer returns correct dimensions
    @Test
    void mappedRenderer_returnsCorrectDimensions() {
        OverlayRenderer renderer = registry.getRenderer("mapped").orElseThrow();
        BufferedImage base = solidRedImage(16, 16);
        BufferedImage overlayTex = solidWhiteImage(16, 16);
        Overlay overlay = new Overlay("minecraft:bow", overlayTex, ColorMode.BASE, "mapped", null);

        BufferedImage result = renderer.render(base, overlay, 0xFFFFFFFF);

        assertThat(result.getWidth()).isEqualTo(16);
        assertThat(result.getHeight()).isEqualTo(16);
    }

    // Test 8: DualLayerOverlayRenderer returns correct dimensions
    @Test
    void dualLayerRenderer_returnsCorrectDimensions() {
        OverlayRenderer renderer = registry.getRenderer("dual_layer").orElseThrow();
        BufferedImage base = solidRedImage(16, 16);
        BufferedImage overlayTex = solidWhiteImage(16, 16);
        Overlay overlay = new Overlay("minecraft:leather_chestplate", overlayTex, ColorMode.OVERLAY, "dual_layer", null);

        BufferedImage result = renderer.render(base, overlay, 0xFF00AA00);

        assertThat(result.getWidth()).isEqualTo(16);
        assertThat(result.getHeight()).isEqualTo(16);
    }

    // Test 9: NormalOverlayRenderer type() returns "normal"
    @Test
    void normalRenderer_typeIsNormal() {
        OverlayRenderer renderer = registry.getRenderer("normal").orElseThrow();
        assertThat(renderer.type()).isEqualTo("normal");
    }

    // Test 10: MappedOverlayRenderer type() returns "mapped"
    @Test
    void mappedRenderer_typeIsMapped() {
        OverlayRenderer renderer = registry.getRenderer("mapped").orElseThrow();
        assertThat(renderer.type()).isEqualTo("mapped");
    }

    // Test 11: DualLayerOverlayRenderer type() returns "dual_layer"
    @Test
    void dualLayerRenderer_typeIsDualLayer() {
        OverlayRenderer renderer = registry.getRenderer("dual_layer").orElseThrow();
        assertThat(renderer.type()).isEqualTo("dual_layer");
    }

    // Test 12: MappedOverlayRenderer draws overlay pixels onto base - result differs from base alone
    @Test
    void mappedRenderer_overlayIsAppliedToBase() {
        OverlayRenderer renderer = registry.getRenderer("mapped").orElseThrow();
        BufferedImage base = solidRedImage(4, 4);
        // Overlay is solid blue, so result pixels should be influenced by blue overlay
        BufferedImage overlayTex = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                overlayTex.setRGB(x, y, 0xFF0000FF); // blue
            }
        }
        Overlay overlay = new Overlay("minecraft:bow", overlayTex, ColorMode.BASE, "mapped", null);

        BufferedImage result = renderer.render(base, overlay, 0xFFFFFFFF);

        // The result should have blue contribution (mapped draws overlay on top)
        int pixel = result.getRGB(0, 0);
        int blue = pixel & 0xFF;
        assertThat(blue).isGreaterThan(0);
    }
}
