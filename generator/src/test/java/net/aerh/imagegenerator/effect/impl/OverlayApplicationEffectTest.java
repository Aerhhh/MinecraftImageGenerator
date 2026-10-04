package net.aerh.imagegenerator.effect.impl;

import net.aerh.imagegenerator.effect.EffectContext;
import net.aerh.imagegenerator.spritesheet.OverlayLoader;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OverlayApplicationEffectTest {

    private final OverlayApplicationEffect effect = new OverlayApplicationEffect(OverlayLoader.getInstance());

    @Test
    void packVisualsSkipTheOverlay() {
        BufferedImage base = leatherHelmet();
        BufferedImage result = effect.apply(context(base, true)).getImage();
        ImageAssertions.assertPixelsEqual(base, result, "a pack visual is left untouched");
    }

    @Test
    void vanillaVisualsStillGetTheOverlay() {
        // The same input without the pack flag is recolored, so the skip above is the flag's doing.
        BufferedImage base = leatherHelmet();
        BufferedImage result = effect.apply(context(base, false)).getImage();
        assertFalse(samePixels(base, result), "the red overlay recolors the vanilla helmet");
    }

    @Test
    void missingFlagCountsAsVanilla() {
        BufferedImage base = leatherHelmet();
        EffectContext context = new EffectContext.Builder()
            .withImage(base)
            .withItemId("leather_helmet")
            .putMetadata("color", "red")
            .build();
        assertFalse(samePixels(base, effect.apply(context).getImage()));
    }

    private static EffectContext context(BufferedImage image, boolean packVisual) {
        return new EffectContext.Builder()
            .withImage(image)
            .withItemId("leather_helmet")
            .putMetadata("color", "red")
            .putMetadata(OverlayApplicationEffect.PACK_VISUAL_METADATA, packVisual)
            .build();
    }

    private static BufferedImage leatherHelmet() {
        BufferedImage texture = Spritesheet.getTexture("leather_helmet");
        assertNotNull(texture, "the vanilla spritesheet has a leather helmet");
        return texture;
    }

    private static boolean samePixels(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
