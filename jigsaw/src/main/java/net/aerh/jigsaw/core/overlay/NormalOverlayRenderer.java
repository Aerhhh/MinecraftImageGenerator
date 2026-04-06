package net.aerh.jigsaw.core.overlay;

import net.aerh.jigsaw.api.overlay.ColorMode;
import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.core.util.ColorUtil;
import net.aerh.jigsaw.core.util.GraphicsUtil;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Renders an overlay using simple multiplicative color tinting.
 *
 * <p>The rendering logic depends on the overlay's {@link ColorMode}:
 * <ul>
 *   <li><b>OVERLAY mode</b> (potions, arrows, firework stars): The overlay texture is tinted
 *       with the color using simple multiplication ({@code (src / 255) * desired}), then
 *       composited on top of the base image.</li>
 *   <li><b>BASE mode</b> (leather armor): The base item texture is tinted with the color
 *       using simple multiplication, then the overlay is composited on top untinted.
 *       This means the base layer gets colored while the overlay (e.g. armor trim details)
 *       stays as-is.</li>
 * </ul>
 */
final class NormalOverlayRenderer implements OverlayRenderer {

    @Override
    public String type() {
        return "normal";
    }

    @Override
    public BufferedImage render(BufferedImage base, Overlay overlay, int color) {
        int w = base.getWidth();
        int h = base.getHeight();

        BufferedImage overlayTex = overlay.texture();
        boolean isOverlayMode = overlay.colorMode() == ColorMode.OVERLAY;

        float[] tint = ColorUtil.extractTintRgb(color);
        float tintR = tint[0];
        float tintG = tint[1];
        float tintB = tint[2];

        BufferedImage finalBase;
        BufferedImage finalOverlay;

        if (isOverlayMode) {
            // OVERLAY mode: tint the overlay texture, draw on top of unchanged base
            finalBase = base;
            finalOverlay = tintImage(overlayTex, tintR, tintG, tintB, w, h);
        } else {
            // BASE mode: tint the base texture, draw untinted overlay on top
            finalBase = tintImage(base, tintR, tintG, tintB, w, h);
            finalOverlay = overlayTex;
        }

        return compositeImages(finalBase, finalOverlay, w, h);
    }

    /**
     * Tints an image by multiplying each pixel's RGB channels by the given tint factors.
     * Simple multiplicative: {@code (src / 255.0) * (tint * 255)} = {@code src * tint}.
     */
    private static BufferedImage tintImage(BufferedImage src, float tintR, float tintG, float tintB,
                                           int targetW, int targetH) {
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int pixel = getWrappedPixel(src, x, y);
                int a = (pixel >> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }

                int r = ColorUtil.clamp(Math.round(((pixel >> 16) & 0xFF) * tintR));
                int g = ColorUtil.clamp(Math.round(((pixel >> 8) & 0xFF) * tintG));
                int b = ColorUtil.clamp(Math.round((pixel & 0xFF) * tintB));

                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /**
     * Composites the overlay on top of the base using alpha blending.
     */
    private static BufferedImage compositeImages(BufferedImage finalBase, BufferedImage finalOverlay,
                                                 int w, int h) {
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        GraphicsUtil.disableAntialiasing(g);
        try {
            g.drawImage(finalBase, 0, 0, w, h, null);
            g.drawImage(finalOverlay, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    private static int getWrappedPixel(BufferedImage img, int x, int y) {
        int wx = x % img.getWidth();
        int wy = y % img.getHeight();
        return img.getRGB(wx, wy);
    }
}
