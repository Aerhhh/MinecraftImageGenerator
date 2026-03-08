package net.aerh.imagegenerator;

import net.aerh.imagegenerator.cache.Cacheable;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for all generators
 */
public interface Generator extends Cacheable {

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
        return cachedOrCompute(() -> render(generationContext));
    }

    /**
     * Performs the actual rendering logic for the generator
     *
     * @param generationContext the generation context, or null if unavailable
     * @return The generated object
     */
    @NotNull GeneratedObject render(@Nullable GenerationContext generationContext);
}
