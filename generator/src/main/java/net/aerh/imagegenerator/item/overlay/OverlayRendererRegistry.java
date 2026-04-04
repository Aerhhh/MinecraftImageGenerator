package net.aerh.imagegenerator.item.overlay;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.overlay.renderer.DualLayerOverlayRenderer;
import net.aerh.imagegenerator.item.overlay.renderer.MappedOverlayRenderer;
import net.aerh.imagegenerator.item.overlay.renderer.NormalOverlayRenderer;
import net.hypixel.nerdbot.marmalade.pattern.Registry;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Registry for OverlayRenderer implementations.
 */
@Slf4j
@UtilityClass
public class OverlayRendererRegistry {

    private static final Registry<String, OverlayRenderer> RENDERERS =
        new Registry<>(Registry.DuplicatePolicy.OVERWRITE, true);

    static {
        registerRenderer(new NormalOverlayRenderer());
        registerRenderer(new MappedOverlayRenderer());
        registerRenderer(new DualLayerOverlayRenderer());
    }

    /**
     * Register an overlay renderer.
     * The renderer will be accessible by its type name (case-insensitive).
     * If a renderer with the same type name is already registered, it will be overwritten.
     *
     * @param renderer The renderer to register
     */
    public static void registerRenderer(OverlayRenderer renderer) {
        RENDERERS.register(renderer.getTypeName(), renderer);
        log.info("Registered overlay renderer: {}", renderer.getTypeName());
    }

    /**
     * Get a renderer by type name.
     *
     * @param typeName The renderer type name (case-insensitive)
     *
     * @return Optional containing the renderer if found, empty otherwise
     */
    public static Optional<OverlayRenderer> getRenderer(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return Optional.empty();
        }

        return RENDERERS.get(typeName);
    }

    /**
     * Get a renderer by type name, throwing an exception if not found.
     *
     * @param typeName The renderer type name (case-insensitive)
     *
     * @return The renderer
     *
     * @throws GeneratorException if the renderer is not found
     */
    public static OverlayRenderer getRendererOrThrow(String typeName) {
        try {
            return RENDERERS.getOrThrow(typeName);
        } catch (NoSuchElementException e) {
            throw new GeneratorException("Unknown overlay type: " + typeName);
        }
    }
}
