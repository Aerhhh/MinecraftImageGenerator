package net.aerh.jigsaw.core.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.exception.RenderException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * A {@link Generator} decorator that caches results in a Caffeine async cache.
 *
 * <p>When {@link GenerationContext#skipCache()} is {@code true}, the cache is bypassed entirely:
 * the delegate is called directly and the result is not stored.
 *
 * <p>The async cache deduplicates concurrent requests for the same key: if multiple threads
 * request the same uncached item simultaneously, only one delegate invocation occurs and all
 * callers share the resulting future. This prevents thundering herd / cache stampede scenarios.
 *
 * @param <I> input type
 * @param <O> output type
 */
public final class CachingGenerator<I, O> implements Generator<I, O> {

    private final Generator<I, O> delegate;
    private final Function<I, CacheKey> keyFunction;
    private final AsyncCache<String, O> cache;

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
                .buildAsync();
    }

    /**
     * Renders the given input, returning a cached result when available and caching is not skipped.
     *
     * <p>Concurrent requests for the same cache key are coalesced: only one delegate invocation
     * occurs, and all callers receive the same result.
     *
     * @param input   the render input; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     *
     * @return the rendered output, either from cache or freshly generated
     *
     * @throws RenderException if the delegate throws during rendering
     */
    @Override
    public O render(I input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (context.skipCache()) {
            return delegate.render(input, context);
        }

        String key = keyFunction.apply(input).value();

        try {
            CompletableFuture<O> future = cache.get(key, (k, executor) ->
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return delegate.render(input, context);
                        } catch (RenderException e) {
                            throw new CompletionException(e);
                        }
                    }, executor)
            );
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RenderException re) {
                throw re;
            }
            throw new RenderException("Cached render failed",
                    java.util.Map.of("key", key), cause != null ? cause : e);
        }
    }

    /**
     * Returns the input type supported by the delegate generator.
     *
     * @return the input class
     */
    @Override
    public Class<I> inputType() {
        return delegate.inputType();
    }

    /**
     * Returns the output type produced by the delegate generator.
     *
     * @return the output class
     */
    @Override
    public Class<O> outputType() {
        return delegate.outputType();
    }

    /**
     * Returns the current number of entries in the cache.
     */
    public long cacheSize() {
        cache.synchronous().cleanUp();
        return cache.synchronous().estimatedSize();
    }

    /**
     * Invalidates all cached entries.
     */
    public void invalidate() {
        cache.synchronous().invalidateAll();
    }
}
