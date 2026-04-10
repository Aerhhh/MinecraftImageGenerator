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

    /**
     * Returns the unique identifier for this effect.
     *
     * @return {@code "hover"}
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Returns the priority of this effect. Lower values are applied first.
     *
     * @return {@code 200}
     */
    @Override
    public int priority() {
        return PRIORITY;
    }

    /**
     * Returns {@code true} if the item is currently hovered.
     *
     * @param context the current effect context
     *
     * @return whether the hover highlight applies
     */
    @Override
    public boolean appliesTo(EffectContext context) {
        return context.hovered();
    }

    /**
     * Applies the hover highlight to the image and returns the modified context.
     *
     * @param context the current effect context
     * @return the updated context with the brightened image
     */
    /** Pre-computed fully opaque slot gray pixel. */
    private static final int SLOT_GRAY_PIXEL = (0xFF << 24) | (SLOT_GRAY << 16) | (SLOT_GRAY << 8) | SLOT_GRAY;

    @Override
    public EffectContext apply(EffectContext context) {
        BufferedImage src = context.image();
        int w = src.getWidth();
        int h = src.getHeight();

        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int alpha = (pixel >>> 24) & 0xFF;

            if (alpha == 0) {
                pixels[i] = SLOT_GRAY_PIXEL;
            } else {
                int r = (((pixel >> 16) & 0xFF) + 255) / 2;
                int g = (((pixel >> 8) & 0xFF) + 255) / 2;
                int b = ((pixel & 0xFF) + 255) / 2;
                pixels[i] = (alpha << 24) | (r << 16) | (g << 8) | b;
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return context.withImage(out);
    }
}
