package net.aerh.jigsaw.api.data;

import java.util.Objects;

/**
 * A type-safe key used to identify and retrieve a {@link DataRegistry}.
 *
 * <p>Keys are distinguished by both their name and the type of data they represent. Use
 * {@link #of(String, Class)} to create a key, then pass it to
 * {@link net.aerh.jigsaw.api.Engine#registry(RegistryKey)} to obtain the corresponding registry:
 *
 * <pre>{@code
 * RegistryKey<MyType> KEY = RegistryKey.of("my_types", MyType.class);
 * DataRegistry<MyType> registry = engine.registry(KEY);
 * }</pre>
 *
 * @param <T>  the type of objects stored in the registry identified by this key
 * @param name the unique name identifying this registry (e.g. {@code "rarities"})
 * @param type the class of objects stored in the registry
 *
 * @see DataRegistry
 */
public record RegistryKey<T>(String name, Class<T> type) {

    /**
     * Compact constructor that validates that neither {@code name} nor {@code type} is {@code null}.
     *
     * @throws NullPointerException if {@code name} or {@code type} is {@code null}
     */
    public RegistryKey {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Creates a new {@code RegistryKey} with the given name and type.
     *
     * @param <T>  the registry value type
     * @param name the unique name for the registry; must not be {@code null}
     * @param type the class of the registry value type; must not be {@code null}
     * @return a new {@code RegistryKey}
     */
    public static <T> RegistryKey<T> of(String name, Class<T> type) {
        return new RegistryKey<>(name, type);
    }
}
