package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.exception.GeneratorValidationException;

/**
 * The shared pixel budget every renderer checks before it allocates a canvas. Request options and
 * text reach the renderers from untrusted callers, and a canvas costs four bytes per pixel per frame,
 * so an unchecked size turns a short input into gigabytes of heap. Checking the size up front fails
 * the one request with a {@link GeneratorValidationException} instead of exhausting the JVM.
 *
 * <p>A size that passes is also reserved against {@link RenderBudget}, the budget shared by all
 * renders running at once.</p>
 *
 * <p>Both limits are read from system properties on each check, so a host can raise or lower them:</p>
 * <ul>
 *     <li>{@value #MAX_CANVAS_PIXELS_PROPERTY} - pixels in one canvas (default
 *     {@value #DEFAULT_MAX_CANVAS_PIXELS}, about 100 MB of ARGB)</li>
 *     <li>{@value #MAX_ANIMATION_PIXELS_PROPERTY} - pixels summed over every frame of one animation
 *     (default {@value #DEFAULT_MAX_ANIMATION_PIXELS}; a full chest with the 182-frame enchant glint
 *     needs about 256 million)</li>
 * </ul>
 */
public final class RenderLimits {

    public static final String MAX_CANVAS_PIXELS_PROPERTY = "generator.render.maxCanvasPixels";
    public static final String MAX_ANIMATION_PIXELS_PROPERTY = "generator.render.maxAnimationPixels";

    public static final long DEFAULT_MAX_CANVAS_PIXELS = 25_000_000L;
    public static final long DEFAULT_MAX_ANIMATION_PIXELS = 400_000_000L;

    private RenderLimits() {
    }

    /** The most pixels one canvas may hold. */
    public static long maxCanvasPixels() {
        return positiveProperty(MAX_CANVAS_PIXELS_PROPERTY, DEFAULT_MAX_CANVAS_PIXELS);
    }

    /** The most pixels all frames of one animation may hold together. */
    public static long maxAnimationPixels() {
        return positiveProperty(MAX_ANIMATION_PIXELS_PROPERTY, DEFAULT_MAX_ANIMATION_PIXELS);
    }

    /**
     * Rejects a single canvas that is over the canvas budget. Takes {@code long} sizes so callers can
     * pass arithmetic that would overflow an {@code int}.
     *
     * @throws GeneratorValidationException if either side is not positive or the area is over budget
     */
    public static void checkCanvas(long width, long height) {
        checkFrames(width, height, 1);
    }

    /**
     * Rejects an animation whose canvas is over the canvas budget or whose frames together are over
     * the animation budget.
     *
     * @throws GeneratorValidationException if the size is not positive or over either budget
     */
    public static void checkFrames(long width, long height, long frameCount) {
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            throw new GeneratorValidationException("Image size %sx%s is out of range",
                String.valueOf(width), String.valueOf(height));
        }

        long maxCanvas = maxCanvasPixels();
        long area = width * height;
        if (area > maxCanvas) {
            throw new GeneratorValidationException(
                "Image is too large to render: %sx%s is over the limit of %s pixels",
                String.valueOf(width), String.valueOf(height), String.valueOf(maxCanvas));
        }

        long frames = Math.max(1, frameCount);
        long maxAnimation = maxAnimationPixels();
        // area fits in 2^62 and frames is small, but guard the product anyway
        if (frames > 1 && area > maxAnimation / frames) {
            throw new GeneratorValidationException(
                "Animation is too large to render: %s frames of %sx%s is over the limit of %s pixels in total",
                String.valueOf(frames), String.valueOf(width), String.valueOf(height), String.valueOf(maxAnimation));
        }
        // Every caller allocates right after this check, so this is also where the render takes its
        // share of the budget all running renders have between them.
        RenderBudget.reserve(area * frames);
    }

    private static long positiveProperty(String property, long fallback) {
        Long configured = Long.getLong(property);
        return configured != null && configured > 0 ? configured : fallback;
    }
}
