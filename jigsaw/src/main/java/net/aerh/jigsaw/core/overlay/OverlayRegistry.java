package net.aerh.jigsaw.core.overlay;

import net.aerh.jigsaw.api.overlay.OverlayRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A mutable registry that maps renderer type strings to {@link OverlayRenderer} instances.
 * <p>
 * Obtain a pre-populated instance via {@link #withDefaults()}, or start empty and
 * {@link #register} renderers manually.
 */
public final class OverlayRegistry {

    private final Map<String, OverlayRenderer> renderers;

    private OverlayRegistry() {
        this.renderers = new HashMap<>();
    }

    /**
     * Returns a new registry pre-populated with the three built-in renderers:
     * {@code "normal"}, {@code "mapped"}, and {@code "dual_layer"}.
     */
    public static OverlayRegistry withDefaults() {
        OverlayRegistry registry = new OverlayRegistry();
        registry.register(new NormalOverlayRenderer());
        registry.register(new MappedOverlayRenderer());
        registry.register(new DualLayerOverlayRenderer());
        return registry;
    }

    /**
     * Registers a renderer. If a renderer with the same {@link OverlayRenderer#type()} already
     * exists it is replaced.
     *
     * @param renderer the renderer to register; must not be {@code null}
     */
    public void register(OverlayRenderer renderer) {
        Objects.requireNonNull(renderer, "renderer must not be null");
        renderers.put(renderer.type(), renderer);
    }

    /**
     * Returns the renderer registered under {@code type}, or {@link Optional#empty()} if none exists.
     *
     * @param type the renderer type key to look up; must not be {@code null}
     */
    public Optional<OverlayRenderer> getRenderer(String type) {
        Objects.requireNonNull(type, "type must not be null");
        return Optional.ofNullable(renderers.get(type));
    }
}
