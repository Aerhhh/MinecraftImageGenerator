package net.aerh.jigsaw.api.text;

/**
 * Configuration for a {@link TextRenderer} call.
 *
 * @param shadow              Whether to render a drop shadow beneath each glyph.
 * @param border              Whether to render a solid border around the text image.
 * @param centeredText        Whether each line should be horizontally centered.
 * @param scaleFactor         Integer scale multiplier applied to all coordinates and dimensions.
 * @param alpha               Global alpha (0-255) applied to the entire rendered output.
 * @param padding             Pixel padding added to all sides of the output image.
 * @param firstLinePadding    Additional top padding applied before the first line only.
 * @param maxLineLength       Maximum pixel width of a single line before wrapping or truncation.
 * @param animationFrameCount Number of frames to generate for obfuscation animation.
 * @param frameDelayMs        Delay in milliseconds between animation frames.
 * @see TextRenderer
 */
public record TextRenderOptions(
        boolean shadow,
        boolean border,
        boolean centeredText,
        int scaleFactor,
        int alpha,
        int padding,
        int firstLinePadding,
        int maxLineLength,
        int animationFrameCount,
        int frameDelayMs
) {

    /** Default animation frame count. */
    public static final int DEFAULT_ANIMATION_FRAME_COUNT = 10;

    /** Default frame delay in milliseconds. */
    public static final int DEFAULT_FRAME_DELAY_MS = 50;

    /**
     * Compatibility constructor without animation parameters.
     */
    public TextRenderOptions(boolean shadow, boolean border, boolean centeredText,
                             int scaleFactor, int alpha, int padding,
                             int firstLinePadding, int maxLineLength) {
        this(shadow, border, centeredText, scaleFactor, alpha, padding,
            firstLinePadding, maxLineLength, DEFAULT_ANIMATION_FRAME_COUNT, DEFAULT_FRAME_DELAY_MS);
    }

    /**
     * Returns the default render options: shadow and border enabled, not centered, scale 1,
     * full opacity, 7px padding, 13px first-line padding, 38px max line length.
     */
    public static TextRenderOptions defaults() {
        return new TextRenderOptions(true, true, false, 1, 255, 7, 13, 38,
            DEFAULT_ANIMATION_FRAME_COUNT, DEFAULT_FRAME_DELAY_MS);
    }
}
