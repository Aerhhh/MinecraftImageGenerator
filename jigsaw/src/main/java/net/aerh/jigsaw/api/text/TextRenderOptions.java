package net.aerh.jigsaw.api.text;

/**
 * Configuration for a {@link TextRenderer} call.
 *
 * @param shadow          Whether to render a drop shadow beneath each glyph.
 * @param border          Whether to render a solid border around the text image.
 * @param centeredText    Whether each line should be horizontally centered.
 * @param scaleFactor     Integer scale multiplier applied to all coordinates and dimensions.
 * @param alpha           Global alpha (0-255) applied to the entire rendered output.
 * @param padding         Pixel padding added to all sides of the output image.
 * @param firstLinePadding Additional top padding applied before the first line only.
 * @param maxLineLength   Maximum pixel width of a single line before wrapping or truncation.
 */
public record TextRenderOptions(
        boolean shadow,
        boolean border,
        boolean centeredText,
        int scaleFactor,
        int alpha,
        int padding,
        int firstLinePadding,
        int maxLineLength
) {

    /**
     * Returns the default render options: shadow and border enabled, not centered, scale 1,
     * full opacity, 7px padding, 13px first-line padding, 38px max line length.
     */
    public static TextRenderOptions defaults() {
        return new TextRenderOptions(true, true, false, 1, 255, 7, 13, 38);
    }
}
