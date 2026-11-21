package net.aerh.imagegenerator;

import net.aerh.imagegenerator.image.RenderCancellation;
import net.aerh.imagegenerator.cache.GeneratorCache;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for all generators
 */
public interface Generator {

    /**
     * Entry point for generating an object with caching automatically applied
     *
     * @return the generated object (possibly retrieved from cache)
     */
    default GeneratedObject generate() {
        return generate(null);
    }

    /**
     * Entry point for generating an object with caching automatically applied
     *
     * @param generationContext the generation context, or null if unavailable
     *
     * @return the generated object (possibly retrieved from cache)
     */
    default GeneratedObject generate(@Nullable GenerationContext generationContext) {
        boolean skipCache = generationContext != null && generationContext.aprilFools();

        if (!skipCache) {
            String cacheKey = GeneratorCacheKey.fromGenerator(this);
            GeneratedObject cachedObject = GeneratorCache.getGeneratedObject(cacheKey);
            if (cachedObject != null) {
                return cachedObject;
            }

            GeneratedObject generatedObject = render(generationContext);
            // A render whose caller already timed out must not be cached: nobody is waiting for it,
            // and it may have been cut short.
            RenderCancellation.check();
            GeneratorCache.putGeneratedObject(cacheKey, generatedObject);
            return generatedObject;
        }

        return render(generationContext);
    }

    /**
     * Performs the actual rendering logic for the generator
     *
     * @param generationContext the generation context, or null if unavailable
     * @return The generated object
     */
    @NotNull GeneratedObject render(@Nullable GenerationContext generationContext);
}
