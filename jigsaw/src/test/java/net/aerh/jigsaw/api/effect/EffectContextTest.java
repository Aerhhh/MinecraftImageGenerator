package net.aerh.jigsaw.api.effect;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EffectContextTest {

    private static BufferedImage blankImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    // Test 1: Builder creates context with all fields
    @Test
    void builder_createsContextWithAllFields() {
        BufferedImage image = blankImage();

        EffectContext ctx = EffectContext.builder()
                .image(image)
                .frameDelayMs(50)
                .itemId("minecraft:diamond_sword")
                .enchanted(true)
                .hovered(false)
                .build();

        assertThat(ctx.image()).isSameAs(image);
        assertThat(ctx.frameDelayMs()).isEqualTo(50);
        assertThat(ctx.itemId()).isEqualTo("minecraft:diamond_sword");
        assertThat(ctx.enchanted()).isTrue();
        assertThat(ctx.hovered()).isFalse();
        assertThat(ctx.animationFrames()).isEmpty();
        assertThat(ctx.metadata()).isEmpty();
    }

    // Test 2: Context with animation frames
    @Test
    void builder_createsContextWithAnimationFrames() {
        BufferedImage frame1 = blankImage();
        BufferedImage frame2 = blankImage();

        EffectContext ctx = EffectContext.builder()
                .image(frame1)
                .animationFrames(List.of(frame1, frame2))
                .frameDelayMs(100)
                .itemId("minecraft:clock")
                .build();

        assertThat(ctx.animationFrames()).hasSize(2);
        assertThat(ctx.animationFrames().get(0)).isSameAs(frame1);
        assertThat(ctx.animationFrames().get(1)).isSameAs(frame2);
    }

    // Test 3: Metadata storage and retrieval - withMetadata returns new instance, original unchanged
    @Test
    void withMetadata_returnsNewInstanceOriginalUnchanged() {
        BufferedImage image = blankImage();
        EffectContext original = EffectContext.builder()
                .image(image)
                .itemId("minecraft:bow")
                .build();

        EffectContext withMeta = original.withMetadata("pull", 0.5);

        // Original is unchanged
        assertThat(original.metadata()).isEmpty();

        // New context has the metadata
        assertThat(withMeta.metadata("pull", Double.class)).contains(0.5);
        assertThat(withMeta.itemId()).isEqualTo("minecraft:bow");
    }

    // Test 4: withImage returns new context with updated image but same other fields
    @Test
    void withImage_returnsNewContextWithUpdatedImageSameOtherFields() {
        BufferedImage originalImage = blankImage();
        BufferedImage newImage = blankImage();

        EffectContext original = EffectContext.builder()
                .image(originalImage)
                .itemId("minecraft:iron_sword")
                .enchanted(true)
                .frameDelayMs(25)
                .build();

        EffectContext updated = original.withImage(newImage);

        assertThat(updated.image()).isSameAs(newImage);
        assertThat(updated.itemId()).isEqualTo("minecraft:iron_sword");
        assertThat(updated.enchanted()).isTrue();
        assertThat(updated.frameDelayMs()).isEqualTo(25);

        // Original image is unchanged
        assertThat(original.image()).isSameAs(originalImage);
    }
}
