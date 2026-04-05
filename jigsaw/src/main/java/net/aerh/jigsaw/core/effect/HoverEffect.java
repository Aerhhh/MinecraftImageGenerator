package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;

import java.awt.image.BufferedImage;

/**
 * Applies a hover highlight to an item image.
 * <ul>
 *   <li>Opaque pixels (alpha {@code > 0}) are brightened by blending 50% toward white.</li>
 *   <li>Fully transparent pixels (alpha {@code == 0}) are filled with slot gray (RGB 197, 197, 197,
 *       fully opaque), matching the Minecraft inventory slot hover highlight.</li>
 * </ul>
 * <p>
 * The original image is never mutated; a new image is always returned.
 */
public final class HoverEffect implements ImageEffect {

    private static final String ID = "hover";
    private static final int PRIORITY = 200;

    /** Slot background gray used to fill transparent pixels on hover. */
    private static final int SLOT_GRAY = 197;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(EffectContext context) {
        return context.hovered();
    }

    @Override
    public EffectContext apply(EffectContext context) {
        BufferedImage src = context.image();
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = src.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;

                if (alpha == 0) {
                    // Fill fully transparent pixels with slot gray
                    out.setRGB(x, y, (0xFF << 24) | (SLOT_GRAY << 16) | (SLOT_GRAY << 8) | SLOT_GRAY);
                } else {
                    // Brighten: 50% blend toward white
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;

                    r = (r + 255) / 2;
                    g = (g + 255) / 2;
                    b = (b + 255) / 2;

                    out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }

        return context.withImage(out);
    }
}
