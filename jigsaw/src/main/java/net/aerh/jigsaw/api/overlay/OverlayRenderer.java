package net.aerh.jigsaw.api.overlay;

import java.awt.image.BufferedImage;

/**
 * Strategy for compositing an {@link Overlay} onto a base item image.
 * <p>
 * Implementations are keyed by {@link #type()} and registered via the SPI.
 *
 * @see Overlay
 * @see net.aerh.jigsaw.spi.OverlayRendererFactory
 */
public interface OverlayRenderer {

    /**
     * The renderer type key (e.g. {@code "mig:normal"}, {@code "mig:dual_layer"}).
     * Must match the {@code rendererType} field on {@link Overlay} records this renderer handles.
     */
    String type();

    /**
     * Renders the overlay onto the base image and returns the composited result.
     *
     * @param base    The item's base texture image.
     * @param overlay The overlay definition to apply.
     * @param color   The tint color as a packed ARGB integer. Ignored when {@link Overlay#colorMode()} is {@link ColorMode#BASE}.
     * @return A new image with the overlay applied.
     */
    BufferedImage render(BufferedImage base, Overlay overlay, int color);
}
