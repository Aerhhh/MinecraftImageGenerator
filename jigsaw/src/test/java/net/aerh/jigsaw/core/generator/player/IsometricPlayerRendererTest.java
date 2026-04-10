package net.aerh.jigsaw.core.generator.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class IsometricPlayerRendererTest {

    private static BufferedImage createTestSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                skin.setRGB(x, y, 0xFF808080);
            }
        }
        // Head front - red
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, 0xFFFF0000);
            }
        }
        // Body front - green
        for (int y = 20; y < 32; y++) {
            for (int x = 20; x < 28; x++) {
                skin.setRGB(x, y, 0xFF00FF00);
            }
        }
        return skin;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void render_producesNonNullImage(boolean slim) {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricPlayerRenderer.render(skin, slim);
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isGreaterThan(0);
        assertThat(result.getHeight()).isGreaterThan(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void render_outputDimensionsAreReasonable(boolean slim) {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricPlayerRenderer.render(skin, slim);
        assertThat(result.getWidth()).isGreaterThan(50);
        assertThat(result.getHeight()).isGreaterThan(50);
    }

    @Test
    void render_imageContainsNonTransparentPixels() {
        BufferedImage skin = createTestSkin();
        BufferedImage result = IsometricPlayerRenderer.render(skin, false);

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
    void render_classicAndSlimProduceDifferentImages() {
        BufferedImage skin = createTestSkin();
        // Color arm regions brightly
        for (int y = 20; y < 32; y++) {
            for (int x = 40; x < 56; x++) {
                skin.setRGB(x, y, 0xFFFF0000);
            }
        }
        for (int y = 48; y < 64; y++) {
            for (int x = 32; x < 56; x++) {
                skin.setRGB(x, y, 0xFF0000FF);
            }
        }

        BufferedImage classic = IsometricPlayerRenderer.render(skin, false);
        BufferedImage slim = IsometricPlayerRenderer.render(skin, true);

        int classicPixels = countNonTransparentPixels(classic);
        int slimPixels = countNonTransparentPixels(slim);

        assertThat(classicPixels).isNotEqualTo(slimPixels);
    }

    @Test
    void render_withArmorSet_producesNonNullImage() {
        BufferedImage skin = createTestSkin();
        ArmorTexture armorTexture = new ArmorTexture();
        ArmorSet armor = ArmorSet.builder().helmet("iron").build();

        BufferedImage result = IsometricPlayerRenderer.render(skin, false, armor, armorTexture);
        assertThat(result).isNotNull();
    }

    @Test
    void render_withFullArmorSet_producesNonNullImage() {
        BufferedImage skin = createTestSkin();
        ArmorTexture armorTexture = new ArmorTexture();
        ArmorSet armor = ArmorSet.builder()
                .helmet("diamond")
                .chestplate("diamond")
                .leggings("diamond")
                .boots("diamond")
                .build();

        BufferedImage result = IsometricPlayerRenderer.render(skin, false, armor, armorTexture);
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isGreaterThan(100);
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
}
