package net.aerh.jigsaw.core.generator.body;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IsometricBodyRendererTest {

    /**
     * Creates a minimal 64x64 test skin with distinguishable regions.
     * Face region (8,8)-(16,16) is red, body front (20,20)-(28,32) is green.
     */
    private static BufferedImage createTestSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        // Fill the entire skin with a base color so all faces have something to render
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                skin.setRGB(x, y, 0xFF808080); // gray
            }
        }
        // Face region - red
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, 0xFFFF0000);
            }
        }
        // Body front region - green
        for (int y = 20; y < 32; y++) {
            for (int x = 20; x < 28; x++) {
                skin.setRGB(x, y, 0xFF00FF00);
            }
        }
        return skin;
    }

    @ParameterizedTest
    @EnumSource(SkinModel.class)
    void render_producesNonNullImage(SkinModel model) {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricBodyRenderer.render(skin, model, List.of());
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isGreaterThan(0);
        assertThat(result.getHeight()).isGreaterThan(0);
    }

    @ParameterizedTest
    @EnumSource(SkinModel.class)
    void render_outputDimensionsMatchExpected(SkinModel model) {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricBodyRenderer.render(skin, model, List.of());
        // Output should be canvas / downscale = 3200/4 = 800
        assertThat(result.getWidth()).isEqualTo(800);
        assertThat(result.getHeight()).isEqualTo(800);
    }

    @Test
    void render_withCustomRotation_producesNonNullImage() {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricBodyRenderer.render(skin, SkinModel.CLASSIC,
                Math.PI / 4, -Math.PI / 3, Math.PI / 8, List.of());
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(800);
    }

    @Test
    void render_withZeroRotation_producesNonNullImage() {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricBodyRenderer.render(skin, SkinModel.CLASSIC,
                0, 0, 0, List.of());
        assertThat(result).isNotNull();
    }

    @Test
    void render_classicAndSlimProduceDifferentImages() {
        // Use a skin with bright colored arm regions so slim vs classic width difference is visible
        BufferedImage skin = createTestSkin();
        // Color right arm front region (44,20)-(48,32) bright red for classic
        for (int y = 20; y < 32; y++) {
            for (int x = 40; x < 56; x++) {
                skin.setRGB(x, y, 0xFFFF0000);
            }
        }
        // Color left arm region (32,52)-(48,64) bright blue
        for (int y = 48; y < 64; y++) {
            for (int x = 32; x < 56; x++) {
                skin.setRGB(x, y, 0xFF0000FF);
            }
        }

        BufferedImage classic = IsometricBodyRenderer.render(skin, SkinModel.CLASSIC, List.of());
        BufferedImage slim = IsometricBodyRenderer.render(skin, SkinModel.SLIM, List.of());

        // Count non-transparent pixels to verify geometry differs
        int classicPixels = countNonTransparentPixels(classic);
        int slimPixels = countNonTransparentPixels(slim);

        // Slim arms are narrower, so the total rendered area should be different
        assertThat(classicPixels).isNotEqualTo(slimPixels);
    }

    private static int countNonTransparentPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >> 24) & 0xFF) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void render_imageContainsNonTransparentPixels() {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricBodyRenderer.render(skin, SkinModel.CLASSIC, List.of());

        boolean hasNonTransparent = false;
        for (int y = 0; y < result.getHeight() && !hasNonTransparent; y++) {
            for (int x = 0; x < result.getWidth() && !hasNonTransparent; x++) {
                if (((result.getRGB(x, y) >> 24) & 0xFF) > 0) {
                    hasNonTransparent = true;
                }
            }
        }
        assertThat(hasNonTransparent).isTrue();
    }

    @Test
    void render_withArmorPiece_producesNonNullImage() {
        BufferedImage skin = createTestSkin();
        BufferedImage armorTexture = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        // Fill armor texture with blue
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                armorTexture.setRGB(x, y, 0xFF0000FF);
            }
        }

        ArmorPiece helmet = ArmorPiece.of(ArmorSlot.HELMET, armorTexture);
        BufferedImage result = IsometricBodyRenderer.render(skin, SkinModel.CLASSIC, List.of(helmet));
        assertThat(result).isNotNull();
    }

    @Test
    void render_withAllArmorSlots_producesNonNullImage() {
        BufferedImage skin = createTestSkin();
        BufferedImage armorLayer1 = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        BufferedImage armorLayer2 = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                armorLayer1.setRGB(x, y, 0xFF0000FF);
                armorLayer2.setRGB(x, y, 0xFFFF00FF);
            }
        }

        List<ArmorPiece> armor = List.of(
                ArmorPiece.of(ArmorSlot.HELMET, armorLayer1),
                ArmorPiece.of(ArmorSlot.CHESTPLATE, armorLayer1),
                ArmorPiece.of(ArmorSlot.LEGGINGS, armorLayer2),
                ArmorPiece.of(ArmorSlot.BOOTS, armorLayer1)
        );

        BufferedImage result = IsometricBodyRenderer.render(skin, SkinModel.CLASSIC, armor);
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(800);
    }
}
