package net.aerh.jigsaw.api.data;

import java.util.Collection;
import java.util.Optional;

/**
 * A keyed registry that maps string IDs to typed data objects.
 *
 * <p>Registries are identified by a {@link RegistryKey} and can be retrieved from the engine via
 * {@link net.aerh.jigsaw.api.Engine#registry(RegistryKey)}. Type-safe usage example:
 *
 * <pre>{@code
 * RegistryKey<Rarity> key = RegistryKey.of("rarities", Rarity.class);
 * DataRegistry<Rarity> registry = engine.registry(key);
 * Rarity epic = registry.get("EPIC").orElseThrow();
 * }</pre>
 *
 * @param <T> the type of objects stored in this registry
 *
 * @see RegistryKey
 */
public interface DataRegistry<T> {

    /**
     * Returns the value registered under the given ID, or empty if no value is registered for it.
     *
     * @param id the string identifier to look up
     * @return an {@link Optional} containing the value, or empty if absent
     */
    Optional<T> get(String id);

    /**
     * Returns all values currently registered in this registry.
     *
     * @return an unmodifiable view of all registered values
     */
    Collection<T> values();

    /**
     * Returns the {@link RegistryKey} that identifies this registry.
     *
     * @return the registry key
     */
    RegistryKey<T> key();

    /**
     * Registers a value under the given ID, replacing any existing entry with the same ID.
     *
     * @param id    the string identifier for the value; must not be {@code null}
     * @param value the value to register; must not be {@code null}
     */
    void register(String id, T value);

    /**
     * Returns the number of entries currently in this registry.
     *
     * @return the number of registered entries
     */
    int size();

    /**
     * Returns {@code true} if this registry contains no entries.
     *
     * @return {@code true} if empty, {@code false} otherwise
     */
    boolean isEmpty();
}
