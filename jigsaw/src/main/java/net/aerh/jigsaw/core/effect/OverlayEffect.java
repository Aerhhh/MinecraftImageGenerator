package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;
import net.aerh.jigsaw.api.overlay.Overlay;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.core.overlay.OverlayRegistry;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies an overlay to the item's base image using the registered {@link OverlayRenderer}.
 *
 * <p>This effect runs at priority 50 (before glint at 100) and only applies when the
 * context contains an {@code "overlayData"} metadata entry of type {@link Overlay}.
 *
 * <p>The tint color is read from the {@code "overlayColor"} metadata key (type {@link Integer}).
 * If absent the color defaults to opaque white ({@code 0xFFFFFFFF}).
 */
public final class OverlayEffect implements ImageEffect {

    private static final String ID = "overlay";
    private static final int PRIORITY = 50;

    static final String META_OVERLAY_DATA = "overlayData";
    static final String META_OVERLAY_COLOR = "overlayColor";
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private final OverlayRegistry registry;

    /**
     * Creates a new {@link OverlayEffect} backed by the given registry.
     *
     * @param registry the overlay renderer registry to look renderers up from; must not be {@code null}
     */
    public OverlayEffect(OverlayRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(EffectContext context) {
        return context.metadata(META_OVERLAY_DATA, Overlay.class).isPresent();
    }

    @Override
    public EffectContext apply(EffectContext context) {
        Overlay overlay = context.metadata(META_OVERLAY_DATA, Overlay.class)
                .orElseThrow(() -> new IllegalStateException("overlayData metadata not found"));

        int color = context.metadata(META_OVERLAY_COLOR, Integer.class)
                .orElse(DEFAULT_COLOR);

        Optional<OverlayRenderer> rendererOpt = registry.getRenderer(overlay.rendererType());
        if (rendererOpt.isEmpty()) {
            // No renderer registered for this type; return context unchanged
            return context;
        }

        BufferedImage result = rendererOpt.get().render(context.image(), overlay, color);
        return context.withImage(result);
    }
}
