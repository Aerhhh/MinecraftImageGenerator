package net.aerh.jigsaw.core.cache;

import java.util.Objects;

/**
 * A typed wrapper around a cache key string.
 *
 * <p>Use {@link #of(Object)} to derive a key from any object via its {@link Object#toString()} representation.
 *
 * @param value the raw string value of this key; must not be {@code null}
 */
public record CacheKey(String value) {

    public CacheKey {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Creates a {@link CacheKey} from the given object's {@link Object#toString()} representation.
     *
     * @param obj the object to derive the key from; must not be {@code null}
     * @return a new {@link CacheKey}
     */
    public static CacheKey of(Object obj) {
        Objects.requireNonNull(obj, "obj must not be null");
        return new CacheKey(obj.toString());
    }
}
