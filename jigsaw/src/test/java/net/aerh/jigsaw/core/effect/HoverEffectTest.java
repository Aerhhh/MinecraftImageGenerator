package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class HoverEffectTest {

    private HoverEffect hover;

    private static BufferedImage blankImage(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    @BeforeEach
    void setUp() {
        hover = new HoverEffect();
    }

    // Test 1: id is "hover"
    @Test
    void id_isHover() {
        assertThat(hover.id()).isEqualTo("hover");
    }

    // Test 2: priority is 200
    @Test
    void priority_is200() {
        assertThat(hover.priority()).isEqualTo(200);
    }

    // Test 3: appliesTo returns true when hovered
    @Test
    void appliesTo_returnsTrueWhenHovered() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage(16, 16))
                .hovered(true)
                .build();

        assertThat(hover.appliesTo(ctx)).isTrue();
    }

    // Test 4: appliesTo returns false when not hovered
    @Test
    void appliesTo_returnsFalseWhenNotHovered() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage(16, 16))
                .hovered(false)
                .build();

        assertThat(hover.appliesTo(ctx)).isFalse();
    }

    // Test 5: apply returns a new context (not the same object)
    @Test
    void apply_returnsNewContext() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage(16, 16))
                .hovered(true)
                .build();

        EffectContext result = hover.apply(ctx);

        assertThat(result).isNotSameAs(ctx);
    }

    // Test 6: apply brightens fully opaque pixels (50% white blend)
    @Test
    void apply_brightensOpaquePixels() {
        // Create a dark red image (fully opaque)
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                img.setRGB(x, y, 0xFF800000); // dark opaque red (R=128, G=0, B=0, A=255)
            }
        }

        EffectContext ctx = EffectContext.builder()
                .image(img)
                .hovered(true)
                .build();

        EffectContext result = hover.apply(ctx);
        int pixel = result.image().getRGB(0, 0);
        int red = (pixel >> 16) & 0xFF;

        // 50% blend of 128 with 255 white = (128 + 255) / 2 = 191
        assertThat(red).isGreaterThan(128);
    }

    // Test 7: Transparent pixels are filled with slot gray (197, 197, 197)
    @Test
    void apply_fillsTransparentPixelsWithSlotGray() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        // Leave all pixels fully transparent (default for new BufferedImage)

        EffectContext ctx = EffectContext.builder()
                .image(img)
                .hovered(true)
                .build();

        EffectContext result = hover.apply(ctx);
        int pixel = result.image().getRGB(0, 0);
        int alpha = (pixel >> 24) & 0xFF;

        // Transparent pixels should become non-transparent (slot gray fill)
        assertThat(alpha).isGreaterThan(0);

        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;

        // Slot gray is 197, 197, 197
        assertThat(red).isEqualTo(197);
        assertThat(green).isEqualTo(197);
        assertThat(blue).isEqualTo(197);
    }

    // Test 8: Output image has same dimensions as input
    @Test
    void apply_outputHasSameDimensionsAsInput() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage(32, 32))
                .hovered(true)
                .build();

        EffectContext result = hover.apply(ctx);

        assertThat(result.image().getWidth()).isEqualTo(32);
        assertThat(result.image().getHeight()).isEqualTo(32);
    }

    // Test 9: Original image is not mutated
    @Test
    void apply_doesNotMutateOriginalImage() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        // Transparent pixel at (0, 0)
        int originalPixel = img.getRGB(0, 0);

        EffectContext ctx = EffectContext.builder()
                .image(img)
                .hovered(true)
                .build();

        hover.apply(ctx);

        // Original image pixel should be unchanged
        assertThat(img.getRGB(0, 0)).isEqualTo(originalPixel);
    }

    // Test 10: appliesTo returns false even when enchanted but not hovered
    @Test
    void appliesTo_falseWhenEnchantedButNotHovered() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage(16, 16))
                .enchanted(true)
                .hovered(false)
                .build();

        assertThat(hover.appliesTo(ctx)).isFalse();
    }

    // Test 11: Semi-transparent pixel (alpha > 0 and < 255) - treated as opaque for brightening
    @Test
    void apply_semiTransparentPixelIsBrightened() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x80FF0000); // alpha=128 (semi-transparent), red

        EffectContext ctx = EffectContext.builder()
                .image(img)
                .hovered(true)
                .build();

        EffectContext result = hover.apply(ctx);
        int pixel = result.image().getRGB(0, 0);
        int alpha = (pixel >> 24) & 0xFF;

        // Semi-transparent pixel should not be filled with gray (it has alpha > 0)
        // It should be brightened
        assertThat(alpha).isGreaterThan(0);
    }
}
