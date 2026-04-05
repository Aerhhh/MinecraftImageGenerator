package net.aerh.jigsaw.spi;

import net.aerh.jigsaw.api.data.DataRegistry;
import net.aerh.jigsaw.api.data.RegistryKey;

/**
 * SPI contract for contributing a {@link DataRegistry} to the engine.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} or explicit registration.
 */
public interface DataRegistryProvider {

    /**
     * The key under which the registry produced by {@link #create()} will be stored.
     */
    RegistryKey<?> key();

    /**
     * Creates and returns the registry. Called once during engine initialization.
     */
    DataRegistry<?> create();
}
