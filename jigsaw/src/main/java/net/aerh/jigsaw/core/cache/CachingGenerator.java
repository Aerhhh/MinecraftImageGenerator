package net.aerh.jigsaw.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.exception.RenderException;

import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link Generator} decorator that caches results in a Caffeine cache.
 *
 * <p>When {@link GenerationContext#skipCache()} is {@code true}, the cache is bypassed entirely:
 * the delegate is called directly and the result is not stored.
 *
 * @param <I> input type
 * @param <O> output type
 */
public final class CachingGenerator<I, O> implements Generator<I, O> {

    private final Generator<I, O> delegate;
    private final Function<I, CacheKey> keyFunction;
    private final Cache<String, O> cache;

    /**
     * Creates a new caching generator.
     *
     * @param delegate    the underlying generator to delegate to; must not be {@code null}
     * @param keyFunction a function that derives a {@link CacheKey} from an input; must not be {@code null}
     * @param maxSize     the maximum number of entries the cache should hold
     */
    public CachingGenerator(Generator<I, O> delegate, Function<I, CacheKey> keyFunction, long maxSize) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(keyFunction, "keyFunction must not be null");

        this.delegate = delegate;
        this.keyFunction = keyFunction;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public O render(I input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (context.skipCache()) {
            return delegate.render(input, context);
        }

        String key = keyFunction.apply(input).value();
        O cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        O result = delegate.render(input, context);
        cache.put(key, result);
        return result;
    }

    @Override
    public Class<I> inputType() {
        return delegate.inputType();
    }

    @Override
    public Class<O> outputType() {
        return delegate.outputType();
    }

    /**
     * Returns the current number of entries in the cache.
     */
    public long cacheSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    /**
     * Invalidates all cached entries.
     */
    public void invalidate() {
        cache.invalidateAll();
    }
}
