package net.aerh.jigsaw.core.overlay;

import net.aerh.jigsaw.api.overlay.ColorMode;
import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.core.util.ColorUtil;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Renders a dual-layer overlay: first draws the base texture, then composites a
 * tint-multiplied overlay on top.
 * <p>
 * This is the standard approach for leather armor, where one layer carries the base
 * color and a second layer carries fixed decorations. When {@link ColorMode#BASE}
 * is set, the tint is not applied to the overlay layer.
 */
final class DualLayerOverlayRenderer implements OverlayRenderer {

    @Override
    public String type() {
        return "dual_layer";
    }

    private static BufferedImage tintImage(BufferedImage src, float r, float g, float b, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcX = x % src.getWidth();
                int srcY = y % src.getHeight();
                int pixel = src.getRGB(srcX, srcY);

                int a = (pixel >> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }

                int pr = ColorUtil.clamp(Math.round(((pixel >> 16) & 0xFF) * r));
                int pg = ColorUtil.clamp(Math.round(((pixel >> 8) & 0xFF) * g));
                int pb = ColorUtil.clamp(Math.round((pixel & 0xFF) * b));

                out.setRGB(x, y, (a << 24) | (pr << 16) | (pg << 8) | pb);
            }
        }
        return out;
    }

    @Override
    public BufferedImage render(BufferedImage base, Overlay overlay, int color) {
        int w = base.getWidth();
        int h = base.getHeight();

        boolean applyTint = overlay.colorMode() == ColorMode.OVERLAY;
        float[] tint = applyTint ? ColorUtil.extractTintRgb(color) : new float[]{1f, 1f, 1f};
        float tintR = tint[0];
        float tintG = tint[1];
        float tintB = tint[2];

        // Build a tinted copy of the overlay texture
        BufferedImage tinted = tintImage(overlay.texture(), tintR, tintG, tintB, w, h);

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(base, 0, 0, null);
            g.drawImage(tinted, 0, 0, null);
        } finally {
            g.dispose();
        }

        return result;
    }

}
