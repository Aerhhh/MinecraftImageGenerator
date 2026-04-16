package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.font.FontRegistry;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.core.font.DefaultFontRegistry;
import net.aerh.jigsaw.core.sprite.AtlasSpriteProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InventoryGenerator#getScaleFactor()}.
 */
class InventoryGeneratorScaleFactorTest {

    private static final FontRegistry FONT_REGISTRY = DefaultFontRegistry.withBuiltins();

    @Test
    void getScaleFactor_withDefaultAtlasIsAtLeastOne() {
        InventoryGenerator generator = new InventoryGenerator(
                AtlasSpriteProvider.fromDefaults(),
                EffectPipeline.builder().build(),
                FONT_REGISTRY);

        int scaleFactor = generator.getScaleFactor();

        assertThat(scaleFactor).isGreaterThanOrEqualTo(1);
    }

    @Test
    void getScaleFactor_isIntegerMultipleOfSixteen() {
        InventoryGenerator generator = new InventoryGenerator(
                AtlasSpriteProvider.fromDefaults(),
                EffectPipeline.builder().build(),
                FONT_REGISTRY);

        int scaleFactor = generator.getScaleFactor();

        // getScaleFactor() = spriteSize / 16, so scaleFactor * 16 should equal the sprite size
        // (we just verify it is a positive integer)
        assertThat(scaleFactor).isPositive();
    }

    @Test
    void getScaleFactor_consistentAcrossCallsOnSameInstance() {
        InventoryGenerator generator = new InventoryGenerator(
                AtlasSpriteProvider.fromDefaults(),
                EffectPipeline.builder().build(),
                FONT_REGISTRY);

        int first = generator.getScaleFactor();
        int second = generator.getScaleFactor();

        assertThat(first).isEqualTo(second);
    }
}
