package net.aerh.jigsaw.core.sprite;

import net.aerh.jigsaw.api.sprite.SpriteProvider;

import java.awt.image.BufferedImage;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link SpriteProvider} decorator that delegates to an ordered list of providers
 * with first-match-wins semantics.
 *
 * <p>Providers are queried in priority order (index 0 = highest priority). When multiple
 * providers contain the same texture ID the result from the earliest provider in the list
 * is used.
 */
public class ChainedSpriteProvider implements SpriteProvider {

    private final List<SpriteProvider> providers;

    /**
     * Creates a {@link ChainedSpriteProvider} from the given list of delegates.
     *
     * @param providers ordered list of providers, index 0 = highest priority; must not be null or empty
     * @throws IllegalArgumentException if {@code providers} is empty
     * @throws NullPointerException     if {@code providers} is null
     */
    public ChainedSpriteProvider(List<SpriteProvider> providers) {
        Objects.requireNonNull(providers, "providers must not be null");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        this.providers = List.copyOf(providers);
    }

    /**
     * Iterates providers in priority order and returns the first non-empty result.
     *
     * @param textureId the texture identifier to look up; must not be {@code null}
     * @return the first match found, or empty if no provider holds the sprite
     */
    @Override
    public Optional<BufferedImage> getSprite(String textureId) {
        Objects.requireNonNull(textureId, "textureId must not be null");
        for (SpriteProvider provider : providers) {
            Optional<BufferedImage> result = provider.getSprite(textureId);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the union of all texture IDs across every delegate, deduplicated.
     *
     * @return deduplicated set of all available texture IDs
     */
    @Override
    public Collection<String> availableSprites() {
        return providers.stream()
                .flatMap(p -> p.availableSprites().stream())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * Iterates providers in priority order and returns the first non-empty search result.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return the first match found across delegates, or empty if none found
     */
    @Override
    public Optional<BufferedImage> search(String query) {
        Objects.requireNonNull(query, "query must not be null");
        for (SpriteProvider provider : providers) {
            Optional<BufferedImage> result = provider.search(query);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /**
     * Merges {@code searchAll} results from all providers, deduplicating by key (first provider
     * wins) and sorting the final list alphabetically by texture ID.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return alphabetically sorted, deduplicated list of matching entries; never {@code null}
     */
    @Override
    public List<Map.Entry<String, BufferedImage>> searchAll(String query) {
        Objects.requireNonNull(query, "query must not be null");
        Map<String, BufferedImage> merged = new LinkedHashMap<>();
        for (SpriteProvider provider : providers) {
            for (Map.Entry<String, BufferedImage> entry : provider.searchAll(query)) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> (Map.Entry<String, BufferedImage>) new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Merges sprites from all providers, with higher-priority providers overwriting lower-priority
     * ones on key conflicts. The returned map is unmodifiable.
     *
     * @return unmodifiable map of all texture IDs to their images, higher-priority providers winning
     */
    @Override
    public Map<String, BufferedImage> getAllSprites() {
        Map<String, BufferedImage> merged = new LinkedHashMap<>();
        // Iterate in reverse so that higher-priority providers overwrite lower-priority ones.
        ListIterator<SpriteProvider> it = providers.listIterator(providers.size());
        while (it.hasPrevious()) {
            merged.putAll(it.previous().getAllSprites());
        }
        return Collections.unmodifiableMap(merged);
    }
}
