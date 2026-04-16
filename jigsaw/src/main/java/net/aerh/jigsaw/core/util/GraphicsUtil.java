package net.aerh.jigsaw.core.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Shared Graphics2D utility methods used across the rendering pipeline.
 */
public final class GraphicsUtil {

    private GraphicsUtil() {
    }

    /**
     * Disables both geometry and text anti-aliasing on the given {@link Graphics2D} context.
     * <p>
     * This produces the pixel-exact Minecraft aesthetic rather than smooth edges.
     *
     * @param g the graphics context to configure; must not be {@code null}
     */
    public static void disableAntialiasing(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }
}
