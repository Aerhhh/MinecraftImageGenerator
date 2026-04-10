package net.aerh.jigsaw.core.cache;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachingGeneratorTest {

    /**
     * A simple identity generator that counts how many times it has been called.
     */
    private static class CountingGenerator implements Generator<String, String> {

        final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public String render(String input, GenerationContext context) {
            callCount.incrementAndGet();
            return "result:" + input;
        }

        @Override
        public Class<String> inputType() {
            return String.class;
        }

        @Override
        public Class<String> outputType() {
            return String.class;
        }
    }

    private CountingGenerator delegate;
    private CachingGenerator<String, String> cachingGenerator;

    @BeforeEach
    void setUp() {
        delegate = new CountingGenerator();
        cachingGenerator = new CachingGenerator<>(delegate, CacheKey::of, 100);
    }

    // --- Caching behavior ---

    @Test
    void render_firstCallInvokesDelegateAndCachesResult() throws RenderException {
        String result = cachingGenerator.render("item1", GenerationContext.defaults());

        assertThat(result).isEqualTo("result:item1");
        assertThat(delegate.callCount.get()).isEqualTo(1);
    }

    @Test
    void render_secondCallWithSameInputReturnsCachedResult() throws RenderException {
        String first = cachingGenerator.render("item1", GenerationContext.defaults());
        String second = cachingGenerator.render("item1", GenerationContext.defaults());

        assertThat(first).isEqualTo(second);
        // Delegate should only have been called once - cache served the second
        assertThat(delegate.callCount.get()).isEqualTo(1);
    }

    @Test
    void render_differentInputsGetSeparateCacheEntries() throws RenderException {
        String r1 = cachingGenerator.render("item1", GenerationContext.defaults());
        String r2 = cachingGenerator.render("item2", GenerationContext.defaults());

        assertThat(r1).isEqualTo("result:item1");
        assertThat(r2).isEqualTo("result:item2");
        assertThat(delegate.callCount.get()).isEqualTo(2);
    }

    @Test
    void render_subsequentCallsForDifferentInputsOnlyCallDelegateOnceEach() throws RenderException {
        // Cache item1 and item2
        cachingGenerator.render("item1", GenerationContext.defaults());
        cachingGenerator.render("item2", GenerationContext.defaults());

        // Fetch both again
        cachingGenerator.render("item1", GenerationContext.defaults());
        cachingGenerator.render("item2", GenerationContext.defaults());

        assertThat(delegate.callCount.get()).isEqualTo(2);
    }

    // --- skipCache behavior ---

    @Test
    void render_skipCacheBypassesCacheAndAlwaysCallsDelegate() throws RenderException {
        GenerationContext skipCtx = GenerationContext.builder().skipCache(true).build();

        cachingGenerator.render("item1", skipCtx);
        cachingGenerator.render("item1", skipCtx);

        assertThat(delegate.callCount.get()).isEqualTo(2);
    }

    @Test
    void render_skipCacheDoesNotStoreResultInCache() throws RenderException {
        GenerationContext skipCtx = GenerationContext.builder().skipCache(true).build();

        cachingGenerator.render("item1", skipCtx);

        // Now fetch normally - delegate must be called again because skip-cache path never stored it
        cachingGenerator.render("item1", GenerationContext.defaults());

        assertThat(delegate.callCount.get()).isEqualTo(2);
    }

    @Test
    void render_normalFetchAfterSkipCacheFetchPopulatesCache() throws RenderException {
        // First call with defaults - populates cache
        cachingGenerator.render("item1", GenerationContext.defaults());

        // skipCache call - bypasses cache but does NOT evict the cached entry
        GenerationContext skipCtx = GenerationContext.builder().skipCache(true).build();
        cachingGenerator.render("item1", skipCtx);

        // Normal call again - should still come from cache (call count stays at 2, not 3)
        cachingGenerator.render("item1", GenerationContext.defaults());

        assertThat(delegate.callCount.get()).isEqualTo(2);
    }

    // --- invalidate ---

    @Test
    void invalidate_clearsAllCachedEntries() throws RenderException {
        cachingGenerator.render("item1", GenerationContext.defaults());
        cachingGenerator.render("item2", GenerationContext.defaults());

        cachingGenerator.invalidate();

        cachingGenerator.render("item1", GenerationContext.defaults());
        cachingGenerator.render("item2", GenerationContext.defaults());

        // All 4 calls should have hit the delegate
        assertThat(delegate.callCount.get()).isEqualTo(4);
    }

    // --- cacheSize ---

    @Test
    void cacheSize_returnsZeroOnFreshInstance() {
        assertThat(cachingGenerator.cacheSize()).isEqualTo(0);
    }

    @Test
    void cacheSize_incrementsAfterSuccessfulRender() throws RenderException {
        cachingGenerator.render("item1", GenerationContext.defaults());
        assertThat(cachingGenerator.cacheSize()).isEqualTo(1);
    }

    @Test
    void cacheSize_returnsZeroAfterInvalidate() throws RenderException {
        cachingGenerator.render("item1", GenerationContext.defaults());
        cachingGenerator.invalidate();
        assertThat(cachingGenerator.cacheSize()).isEqualTo(0);
    }

    // --- inputType / outputType delegation ---

    @Test
    void inputType_delegatesToUnderlyingGenerator() {
        assertThat(cachingGenerator.inputType()).isEqualTo(String.class);
    }

    @Test
    void outputType_delegatesToUnderlyingGenerator() {
        assertThat(cachingGenerator.outputType()).isEqualTo(String.class);
    }

    // --- null argument guards ---

    @Test
    void render_nullInputThrowsNullPointerException() {
        assertThatThrownBy(() -> cachingGenerator.render(null, GenerationContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void render_nullContextThrowsNullPointerException() {
        assertThatThrownBy(() -> cachingGenerator.render("item1", null))
                .isInstanceOf(NullPointerException.class);
    }
}
