package net.aerh.jigsaw.core.overlay;

import net.aerh.jigsaw.api.overlay.ColorMode;
import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.core.util.ColorUtil;

import java.awt.image.BufferedImage;

/**
 * Renders an overlay by multiplying each overlay pixel's RGB channels by the
 * normalized tint color, then compositing the result over the base image.
 * <p>
 * When {@link ColorMode#BASE} is specified the tint is ignored and the overlay
 * is drawn without modification.
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
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        BufferedImage overlayTex = overlay.texture();
        boolean applyTint = overlay.colorMode() == ColorMode.OVERLAY;

        float[] tint = applyTint ? ColorUtil.extractTintRgb(color) : new float[]{1f, 1f, 1f};
        float tintR = tint[0];
        float tintG = tint[1];
        float tintB = tint[2];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int basePixel = base.getRGB(x, y);
                int overlayPixel = getWrappedPixel(overlayTex, x, y);

                int oA = (overlayPixel >> 24) & 0xFF;
                if (oA == 0) {
                    result.setRGB(x, y, basePixel);
                    continue;
                }

                int oR = ColorUtil.clamp(Math.round(((overlayPixel >> 16) & 0xFF) * tintR));
                int oG = ColorUtil.clamp(Math.round(((overlayPixel >> 8) & 0xFF) * tintG));
                int oB = ColorUtil.clamp(Math.round((overlayPixel & 0xFF) * tintB));

                // Alpha-composite overlay over base
                int bA = (basePixel >> 24) & 0xFF;
                int bR = (basePixel >> 16) & 0xFF;
                int bG = (basePixel >> 8) & 0xFF;
                int bB = basePixel & 0xFF;

                float oAlpha = oA / 255f;
                float bAlpha = bA / 255f;
                float outAlpha = oAlpha + bAlpha * (1f - oAlpha);

                int rR, rG, rB, rA;
                if (outAlpha == 0f) {
                    rR = rG = rB = rA = 0;
                } else {
                    rR = ColorUtil.clamp(Math.round((oR * oAlpha + bR * bAlpha * (1f - oAlpha)) / outAlpha));
                    rG = ColorUtil.clamp(Math.round((oG * oAlpha + bG * bAlpha * (1f - oAlpha)) / outAlpha));
                    rB = ColorUtil.clamp(Math.round((oB * oAlpha + bB * bAlpha * (1f - oAlpha)) / outAlpha));
                    rA = ColorUtil.clamp(Math.round(outAlpha * 255f));
                }

                result.setRGB(x, y, (rA << 24) | (rR << 16) | (rG << 8) | rB);
            }
        }

        return result;
    }

    private static int getWrappedPixel(BufferedImage img, int x, int y) {
        int wx = x % img.getWidth();
        int wy = y % img.getHeight();
        return img.getRGB(wx, wy);
    }

}
