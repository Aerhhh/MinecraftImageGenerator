package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;
import net.aerh.jigsaw.api.effect.MetadataKeys;

import java.awt.image.BufferedImage;

/**
 * Draws a Minecraft-style durability bar at the bottom of the item image.
 * <p>
 * The bar color transitions from green (high durability) through yellow (mid) to
 * red (low), matching vanilla Minecraft's item durability display. A 1-pixel-tall
 * black background bar spans the full width; the colored bar sits on top and is
 * proportionally wide.
 * <p>
 * Requires the {@code "durabilityPercent"} metadata key to be present and of type
 * {@link Double} (value in range {@code [0.0, 1.0]}).
 */
public final class DurabilityBarEffect implements ImageEffect {

    private static final String ID = "durability_bar";
    private static final int PRIORITY = 300;
    private static final String META_KEY = MetadataKeys.DURABILITY_PERCENT;

    /** Height in pixels of the black background bar. */
    private static final int BAR_HEIGHT = 2;
    /** Height in pixels of the colored durability bar. */
    private static final int COLORED_BAR_HEIGHT = 1;

    /**
     * Returns the unique identifier for this effect.
     *
     * @return {@code "durability_bar"}
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Returns the priority of this effect. Lower values are applied first.
     *
     * @return {@code 300}
     */
    @Override
    public int priority() {
        return PRIORITY;
    }

    /**
     * Returns {@code true} if the context contains a {@code "durabilityPercent"} metadata entry.
     *
     * @param context the current effect context
     *
     * @return whether the durability bar should be drawn
     */
    @Override
    public boolean appliesTo(EffectContext context) {
        return context.metadata(META_KEY, Double.class).isPresent();
    }

    /**
     * Draws the durability bar onto the item image and returns the modified context.
     *
     * @param context the current effect context; must contain {@code "durabilityPercent"} metadata
     * @return the updated context with the durability bar drawn
     */
    @Override
    public EffectContext apply(EffectContext context) {
        double durability = context.metadata(META_KEY, Double.class)
                .orElseThrow(() -> new IllegalStateException("durabilityPercent metadata not found"));

        durability = Math.max(0.0, Math.min(1.0, durability));

        BufferedImage src = context.image();
        int w = src.getWidth();
        int h = src.getHeight();

        // Copy the source image
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, src.getRGB(x, y));
            }
        }

        // Draw the black background bar (full width, 2px tall at bottom)
        int blackArgb = 0xFF000000;
        for (int row = h - BAR_HEIGHT; row < h; row++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, row, blackArgb);
            }
        }

        // Draw the colored durability bar (1px tall, proportional width)
        int barWidth = Math.max(1, (int) Math.round(durability * w));
        int barColor = computeBarColor(durability);

        int coloredRow = h - COLORED_BAR_HEIGHT;
        for (int x = 0; x < barWidth; x++) {
            out.setRGB(x, coloredRow, barColor);
        }

        return context.withImage(out);
    }

    /**
     * Computes a green-to-yellow-to-red color based on the durability fraction.
     * <ul>
     *   <li>1.0 = fully green (0, 255, 0)</li>
     *   <li>0.5 = fully yellow (255, 255, 0)</li>
     *   <li>0.0 = fully red (255, 0, 0)</li>
     * </ul>
     */
    private static int computeBarColor(double durability) {
        int r, g;
        if (durability >= 0.5) {
            // Lerp from yellow (0.5) to green (1.0): red decreases from 255 to 0
            double t = (durability - 0.5) / 0.5;
            r = (int) Math.round((1.0 - t) * 255);
            g = 255;
        } else {
            // Lerp from red (0.0) to yellow (0.5): green increases from 0 to 255
            double t = durability / 0.5;
            r = 255;
            g = (int) Math.round(t * 255);
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }
}
