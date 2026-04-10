package net.aerh.jigsaw.core.util;

/**
 * Shared color utility methods used across the rendering pipeline.
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /**
     * Clamps an integer value to the inclusive range [0, 255].
     *
     * @param v the value to clamp
     *
     * @return the clamped value
     */
    public static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Extracts the normalized RGB components from a packed ARGB integer.
     * <p>
     * Returns a three-element array {@code [r, g, b]} where each component is in [0.0, 1.0].
     *
     * @param color the packed ARGB color integer
     *
     * @return a float array of length 3 containing the normalized [r, g, b] components
     */
    public static float[] extractTintRgb(int color) {
        return new float[]{
            ((color >> 16) & 0xFF) / 255f,
            ((color >> 8) & 0xFF) / 255f,
            (color & 0xFF) / 255f
        };
    }
}
