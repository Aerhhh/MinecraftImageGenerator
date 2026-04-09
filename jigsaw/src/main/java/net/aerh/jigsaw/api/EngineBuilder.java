package net.aerh.jigsaw.api;

import net.aerh.jigsaw.api.data.DataRegistry;
import net.aerh.jigsaw.api.data.RegistryKey;
import net.aerh.jigsaw.api.effect.ImageEffect;
import net.aerh.jigsaw.api.font.FontProvider;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.spi.NbtFormatHandler;

/**
 * A builder that constructs a fully configured {@link Engine} instance.
 *
 * <p>By default, the engine is built with all defaults enabled (sprite provider,
 * data registries, built-in effects, font registry, overlay registry). Call
 * {@link #noDefaults()} to opt out of defaults and configure everything manually.
 *
 * @see Engine
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
     * Sets a custom {@link SpriteProvider} to use for loading item and block textures.
     * If not called, the engine defaults to the built-in texture atlas.
     *
     * @param provider the sprite provider; must not be {@code null}
     * @return this builder
     */
    EngineBuilder spriteProvider(SpriteProvider provider);

    /**
     * Registers a {@link DataRegistry} that will be available via {@link Engine#registry(RegistryKey)}.
     * If a registry with the same key name is already registered, the new one replaces it.
     *
     * @param <T>      the type of objects stored in the registry
     * @param registry the data registry to register; must not be {@code null}
     * @return this builder
     */
    <T> EngineBuilder registry(DataRegistry<T> registry);

    /**
     * Sets a custom slot texture for inventory rendering.
     * If not called, the engine uses the bundled default slot texture.
     *
     * @param slotTexture the slot texture image; must not be {@code null}
     * @return this builder
     */
    EngineBuilder slotTexture(java.awt.image.BufferedImage slotTexture);

    /**
     * Builds and returns the configured {@link Engine}.
     *
     * @return a new {@link Engine} instance
     */
    Engine build();
}
