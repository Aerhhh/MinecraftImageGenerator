package net.aerh.imagegenerator.cache;

import net.aerh.imagegenerator.item.GeneratedObject;

import java.util.function.Supplier;

/**
 * Interface for generators that support caching.
 * Provides cache key generation and automatic cache-or-compute logic.
 * <p>
 * By default, uses {@link Object#toString()} as the cache key. Generators using
 * Lombok's {@code @ToString} will automatically include all fields. Override
 * {@link #cacheKey()} for custom key logic.
 */
public interface Cacheable {

    /**
     * Returns a stable cache key representing this object's configuration.
     * Two objects with identical configuration must produce identical keys.
     * <p>
     * Default implementation uses {@code toString()}, which works well with
     * Lombok's {@code @ToString} annotation.
     *
     * @return cache key string
     */
    default String cacheKey() {
        return toString();
    }

    /**
     * Returns the cached result if present, otherwise computes and caches the result.
     *
     * @param computeFunction the function to compute the result if not cached
     * @return the generated object (possibly from cache)
     */
    default GeneratedObject cachedOrCompute(Supplier<GeneratedObject> computeFunction) {
        String key = cacheKey();
        GeneratedObject cached = GeneratorCache.getGeneratedObject(key);
        if (cached != null) {
            return cached;
        }

        GeneratedObject result = computeFunction.get();
        GeneratorCache.putGeneratedObject(key, result);
        return result;
    }
}
