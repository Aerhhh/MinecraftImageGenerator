package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.MetadataKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class DurabilityBarEffectTest {

    private DurabilityBarEffect durabilityBar;

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private static EffectContext contextWithDurability(double percent) {
        return EffectContext.builder()
                .image(blankImage())
                .build()
            .withMetadata(MetadataKeys.DURABILITY_PERCENT, percent);
    }

    @BeforeEach
    void setUp() {
        durabilityBar = new DurabilityBarEffect();
    }

    // Test 1: id is "durability_bar"
    @Test
    void id_isDurabilityBar() {
        assertThat(durabilityBar.id()).isEqualTo("durability_bar");
    }

    // Test 2: priority is 300
    @Test
    void priority_is300() {
        assertThat(durabilityBar.priority()).isEqualTo(300);
    }

    // Test 3: appliesTo returns true when durabilityPercent metadata is present
    @Test
    void appliesTo_returnsTrueWhenDurabilityPercentPresent() {
        EffectContext ctx = contextWithDurability(0.5);
        assertThat(durabilityBar.appliesTo(ctx)).isTrue();
    }

    // Test 4: appliesTo returns false when durabilityPercent metadata is absent
    @Test
    void appliesTo_returnsFalseWhenDurabilityPercentAbsent() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .build();

        assertThat(durabilityBar.appliesTo(ctx)).isFalse();
    }

    // Test 5: appliesTo returns false when wrong metadata type is stored
    @Test
    void appliesTo_returnsFalseWhenWrongMetadataType() {
        EffectContext ctx = EffectContext.builder()
                .image(blankImage())
                .build()
            .withMetadata(MetadataKeys.DURABILITY_PERCENT, "not-a-double");

        assertThat(durabilityBar.appliesTo(ctx)).isFalse();
    }

    // Test 6: apply returns a new context with an image
    @Test
    void apply_returnsNewContextWithImage() {
        EffectContext ctx = contextWithDurability(0.5);
        EffectContext result = durabilityBar.apply(ctx);

        assertThat(result).isNotSameAs(ctx);
        assertThat(result.image()).isNotNull();
    }

    // Test 7: Output image has same dimensions as input
    @Test
    void apply_outputHasSameDimensionsAsInput() {
        EffectContext ctx = contextWithDurability(0.75);
        EffectContext result = durabilityBar.apply(ctx);

        assertThat(result.image().getWidth()).isEqualTo(16);
        assertThat(result.image().getHeight()).isEqualTo(16);
    }

    // Test 8: apply with full durability (1.0) draws green bar
    @Test
    void apply_fullDurabilityDrawsGreenBar() {
        EffectContext ctx = contextWithDurability(1.0);
        EffectContext result = durabilityBar.apply(ctx);
        BufferedImage img = result.image();

        // The bar is drawn at the bottom, check the last 2 rows
        boolean foundGreenish = false;
        for (int x = 0; x < img.getWidth(); x++) {
            int pixel = img.getRGB(x, img.getHeight() - 1);
            int alpha = (pixel >> 24) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int red = (pixel >> 16) & 0xFF;
            if (alpha > 0 && green > red) {
                foundGreenish = true;
                break;
            }
        }

        assertThat(foundGreenish).isTrue();
    }

    // Test 9: apply with low durability (0.1) draws red bar
    @Test
    void apply_lowDurabilityDrawsRedBar() {
        EffectContext ctx = contextWithDurability(0.1);
        EffectContext result = durabilityBar.apply(ctx);
        BufferedImage img = result.image();

        // Check bottom rows for reddish pixels
        boolean foundReddish = false;
        for (int x = 0; x < img.getWidth(); x++) {
            int pixel = img.getRGB(x, img.getHeight() - 1);
            int alpha = (pixel >> 24) & 0xFF;
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            if (alpha > 0 && red > green) {
                foundReddish = true;
                break;
            }
        }

        assertThat(foundReddish).isTrue();
    }

    // Test 10: apply draws a black background bar behind the colored bar
    @Test
    void apply_drawsBlackBackgroundBarAtBottom() {
        // Use a fully white image so black pixels are clearly from the bar
        BufferedImage white = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                white.setRGB(x, y, 0xFFFFFFFF);
            }
        }

        EffectContext ctx = EffectContext.builder()
                .image(white)
                .build()
            .withMetadata(MetadataKeys.DURABILITY_PERCENT, 0.5);

        EffectContext result = durabilityBar.apply(ctx);
        BufferedImage img = result.image();

        // There should be at least one black pixel in the bottom rows
        boolean foundBlack = false;
        for (int y = img.getHeight() - 2; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int pixel = img.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;
                if (alpha > 0 && red == 0 && green == 0 && blue == 0) {
                    foundBlack = true;
                    break;
                }
            }
        }

        assertThat(foundBlack).isTrue();
    }

    // Test 11: Original context is not mutated
    @Test
    void apply_doesNotMutateOriginalContext() {
        EffectContext ctx = contextWithDurability(0.5);
        BufferedImage originalImage = ctx.image();

        durabilityBar.apply(ctx);

        assertThat(ctx.image()).isSameAs(originalImage);
    }

    // Test 12: apply with durability exactly 0.0 still produces a valid result
    @Test
    void apply_zeroDurabilityProducesValidResult() {
        EffectContext ctx = contextWithDurability(0.0);
        EffectContext result = durabilityBar.apply(ctx);

        assertThat(result.image()).isNotNull();
        assertThat(result.image().getWidth()).isEqualTo(16);
        assertThat(result.image().getHeight()).isEqualTo(16);
    }

    // Test 13: apply with mid durability (0.5) draws yellowish bar
    @Test
    void apply_midDurabilityDrawsYellowishBar() {
        EffectContext ctx = contextWithDurability(0.5);
        EffectContext result = durabilityBar.apply(ctx);
        BufferedImage img = result.image();

        // At ~50%, we expect yellow (red + green, no blue)
        boolean foundYellowish = false;
        for (int x = 0; x < img.getWidth(); x++) {
            int pixel = img.getRGB(x, img.getHeight() - 1);
            int alpha = (pixel >> 24) & 0xFF;
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int blue = pixel & 0xFF;
            // Yellow has high red and green with low blue
            if (alpha > 0 && red > 100 && green > 100 && blue < 50) {
                foundYellowish = true;
                break;
            }
        }

        assertThat(foundYellowish).isTrue();
    }
}
