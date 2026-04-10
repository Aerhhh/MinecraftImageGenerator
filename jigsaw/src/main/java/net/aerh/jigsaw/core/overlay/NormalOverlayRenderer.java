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

        int tintR = (color >> 16) & 0xFF;
        int tintG = (color >> 8) & 0xFF;
        int tintB = color & 0xFF;

        // Extract default colors for de-tinting if available (e.g. leather armor's brown base)
        int[] defaultColors = overlay.defaultColors();
        int defR = 255, defG = 255, defB = 255;
        boolean deTint = defaultColors != null && defaultColors.length > 0;
        if (deTint) {
            int dc = defaultColors[0];
            defR = (dc >> 16) & 0xFF;
            defG = (dc >> 8) & 0xFF;
            defB = dc & 0xFF;
        }

        BufferedImage finalBase;
        BufferedImage finalOverlay;

        if (isOverlayMode) {
            // OVERLAY mode: tint the overlay texture, draw on top of unchanged base
            finalBase = base;
            finalOverlay = tintImage(overlayTex, tintR, tintG, tintB, defR, defG, defB, deTint, w, h);
        } else {
            // BASE mode: tint the base texture, draw untinted overlay on top
            finalBase = tintImage(base, tintR, tintG, tintB, defR, defG, defB, deTint, w, h);
            finalOverlay = overlayTex;
        }

        return compositeImages(finalBase, finalOverlay, w, h);
    }

    /**
     * Tints an image pixel-by-pixel. When de-tinting is enabled, divides out the default
     * color before applying the desired color: {@code (src / default) * desired}. This is
     * necessary for textures like leather armor where the base texture has a baked-in color.
     * When de-tinting is disabled, uses simple multiplicative: {@code (src / 255) * desired}.
     */
    private static BufferedImage tintImage(BufferedImage src,
                                           int tintR, int tintG, int tintB,
                                           int defR, int defG, int defB,
                                           boolean deTint,
                                           int targetW, int targetH) {
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int pixel = getWrappedPixel(src, x, y);
                int a = (pixel >> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }

                int srcR = (pixel >> 16) & 0xFF;
                int srcG = (pixel >> 8) & 0xFF;
                int srcB = pixel & 0xFF;

                int r, g, b;
                if (deTint) {
                    // De-tint then re-tint: (source / default) * desired
                    r = defR > 0
                        ? ColorUtil.clamp((int) Math.round((srcR * tintR) / (double) defR))
                        : (int) Math.round((srcR / 255.0) * tintR);
                    g = defG > 0
                        ? ColorUtil.clamp((int) Math.round((srcG * tintG) / (double) defG))
                        : (int) Math.round((srcG / 255.0) * tintG);
                    b = defB > 0
                        ? ColorUtil.clamp((int) Math.round((srcB * tintB) / (double) defB))
                        : (int) Math.round((srcB / 255.0) * tintB);
                } else {
                    // Simple multiplicative: (source / 255) * desired
                    r = (int) Math.round((srcR / 255.0) * tintR);
                    g = (int) Math.round((srcG / 255.0) * tintG);
                    b = (int) Math.round((srcB / 255.0) * tintB);
                }

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
