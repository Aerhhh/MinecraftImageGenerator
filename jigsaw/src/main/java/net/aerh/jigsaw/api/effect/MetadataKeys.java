package net.aerh.jigsaw.api.effect;

/**
 * Constants for well-known metadata keys used in the effect pipeline.
 *
 * <p>Centralizing these strings prevents typos and makes it easy to find all usages.
 */
public final class MetadataKeys {

    /**
     * Durability fraction in [0.0, 1.0] stored as a {@link Double}.
     */
    public static final String DURABILITY_PERCENT = "durabilityPercent";

    /**
     * The {@link net.aerh.jigsaw.api.overlay.Overlay} instance to render.
     */
    public static final String OVERLAY_DATA = "overlayData";

    /**
     * Packed ARGB tint color for the overlay, stored as an {@link Integer}.
     */
    public static final String OVERLAY_COLOR = "overlayColor";

    private MetadataKeys() {
    }
}
