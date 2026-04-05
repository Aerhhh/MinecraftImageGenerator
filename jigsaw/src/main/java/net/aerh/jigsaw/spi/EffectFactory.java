package net.aerh.jigsaw.spi;

import net.aerh.jigsaw.api.effect.ImageEffect;

/**
 * SPI contract for contributing an {@link ImageEffect} implementation.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} or explicit registration.
 *
 * @see net.aerh.jigsaw.api.effect.ImageEffect
 */
public interface EffectFactory {

    /**
     * Unique identifier for the effect produced by this factory (e.g. {@code "mig:glint"}).
     */
    String id();

    /**
     * Creates and returns a new effect instance.
     */
    ImageEffect create();
}
