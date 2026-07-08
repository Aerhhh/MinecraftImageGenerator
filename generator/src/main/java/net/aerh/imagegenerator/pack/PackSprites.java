package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Normalizes pack sprites onto the fixed-size canvas the rest of the library expects (the vanilla
 * atlas invariant is 256x256 per sprite): nearest-neighbor integer upscale, centered; sources
 * larger than the canvas scale down to fit.
 */
@UtilityClass
public class PackSprites {

    public static BufferedImage scaleToCanvas(BufferedImage sprite, int canvasSize) {
        int maxSide = Math.max(sprite.getWidth(), sprite.getHeight());
        int width;
        int height;
        if (maxSide > canvasSize) {
            double factor = (double) canvasSize / maxSide;
            width = Math.max(1, (int) Math.round(sprite.getWidth() * factor));
            height = Math.max(1, (int) Math.round(sprite.getHeight() * factor));
        } else {
            int factor = Math.max(1, canvasSize / maxSide);
            width = sprite.getWidth() * factor;
            height = sprite.getHeight() * factor;
        }
        BufferedImage canvas = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(sprite, (canvasSize - width) / 2, (canvasSize - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }
}
