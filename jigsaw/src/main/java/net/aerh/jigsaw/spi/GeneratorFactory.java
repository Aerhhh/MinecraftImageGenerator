package net.aerh.jigsaw.spi;

import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorType;

/**
 * SPI contract for contributing a {@link Generator} implementation.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} or explicit registration.
 */
public interface GeneratorFactory {

    /**
     * Unique identifier for this factory (e.g. {@code "mig:item"}).
     */
    String id();

    /**
     * The type of generator produced by this factory.
     */
    GeneratorType type();

    /**
     * Creates and returns a new generator instance.
     */
    Generator<?, ?> create();
}
