package net.aerh.jigsaw.api.overlay;

/**
 * Controls how the overlay color is applied during rendering.
 *
 * @see Overlay
 * @see OverlayRenderer
 */
public enum ColorMode {

    /**
     * The overlay is rendered using only the base texture colors, ignoring any tint.
     */
    BASE,

    /**
     * The overlay is tinted with the item's dye or team color.
     */
    OVERLAY
}
