package net.aerh.jigsaw.api;

import net.aerh.jigsaw.api.effect.ImageEffect;
import net.aerh.jigsaw.api.font.FontProvider;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.spi.NbtFormatHandler;

/**
 * A builder that constructs a fully configured {@link Engine} instance.
 *
 * <p>By default, the engine is built with all defaults enabled (sprite provider,
 * data registries, built-in effects, font registry, overlay registry). Call
 * {@link #noDefaults()} to opt out of defaults and configure everything manually.
 */
public interface EngineBuilder {

    /**
     * Disables all defaults so only the components registered via this builder are used.
     *
     * @return this builder
     */
    EngineBuilder noDefaults();

    /**
     * Adds an additional {@link ImageEffect} to the effect pipeline.
     *
     * @param effect the effect to add; must not be {@code null}
     * @return this builder
     */
    EngineBuilder effect(ImageEffect effect);

    /**
     * Registers an additional {@link NbtFormatHandler}.
     *
     * @param handler the handler to register; must not be {@code null}
     * @return this builder
     */
    EngineBuilder nbtHandler(NbtFormatHandler handler);

    /**
     * Registers an additional {@link OverlayRenderer}.
     *
     * @param renderer the renderer to register; must not be {@code null}
     * @return this builder
     */
    EngineBuilder overlayRenderer(OverlayRenderer renderer);

    /**
     * Registers an additional {@link FontProvider}.
     *
     * @param provider the font provider to register; must not be {@code null}
     * @return this builder
     */
    EngineBuilder fontProvider(FontProvider provider);

    /**
     * Builds and returns the configured {@link Engine}.
     *
     * @return a new {@link Engine} instance
     */
    Engine build();
}
