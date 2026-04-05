package net.aerh.jigsaw.core.text;

/**
 * Configuration for {@link TextLayoutEngine#layout} calls.
 *
 * @param maxWidth    Maximum character count per line before wrapping.
 * @param centerAlign Whether lines should be center-aligned.
 * @param scaleFactor Integer scale multiplier applied to computed dimensions.
 */
public record TextLayoutOptions(int maxWidth, boolean centerAlign, int scaleFactor) {

    public TextLayoutOptions {
        if (maxWidth <= 0) {
            throw new IllegalArgumentException("maxWidth must be positive, got: " + maxWidth);
        }
        if (scaleFactor <= 0) {
            throw new IllegalArgumentException("scaleFactor must be positive, got: " + scaleFactor);
        }
    }

    /**
     * Default options: 80-character width, no centering, scale factor 1.
     */
    public static TextLayoutOptions defaults() {
        return new TextLayoutOptions(80, false, 1);
    }
}
