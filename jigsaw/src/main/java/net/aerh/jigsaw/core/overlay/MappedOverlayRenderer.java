package net.aerh.jigsaw.core.overlay;

import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.core.util.GraphicsUtil;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Renders an overlay by drawing it directly on top of the base image using
 * standard alpha compositing (SRC_OVER). No tint is applied regardless of
 * the overlay's {@link net.aerh.jigsaw.api.overlay.ColorMode}.
 * <p>
 * This is suitable for overlays that are fully pre-colored (e.g. bow overlays,
 * crossbow charge overlays).
 */
final class MappedOverlayRenderer implements OverlayRenderer {

    @Override
    public String type() {
        return "mapped";
    }

    @Override
    public BufferedImage render(BufferedImage base, Overlay overlay, int color) {
        int w = base.getWidth();
        int h = base.getHeight();

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        GraphicsUtil.disableAntialiasing(g);
        try {
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(base, 0, 0, null);
            g.drawImage(overlay.texture(), 0, 0, w, h, null);
        } finally {
            g.dispose();
        }

        return result;
    }
}
