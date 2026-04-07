package net.aerh.jigsaw.core.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link ResourcePack} that layers multiple packs with first-match-wins semantics.
 *
 * <p>When looking up a resource, each pack is tried in order and the first one that
 * contains the resource wins. This mirrors Minecraft's own resource pack layering
 * behavior, allowing texture-only packs to override textures while models are
 * resolved from a lower-priority pack (e.g. vanilla).
 *
 * <p>The metadata is taken from the first (highest priority) pack.
 */
public class LayeredResourcePack implements ResourcePack {

    private final List<ResourcePack> packs;

    /**
     * Creates a layered resource pack from the given list.
     *
     * @param packs ordered list of packs, index 0 = highest priority; must not be null or empty
     * @throws IllegalArgumentException if {@code packs} is empty
     */
    public LayeredResourcePack(List<ResourcePack> packs) {
        Objects.requireNonNull(packs, "packs must not be null");
        if (packs.isEmpty()) {
            throw new IllegalArgumentException("packs must not be empty");
        }
        this.packs = List.copyOf(packs);
    }

    @Override
    public Optional<InputStream> getResource(String path) {
        for (ResourcePack pack : packs) {
            Optional<InputStream> result = pack.getResource(path);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean hasResource(String path) {
        for (ResourcePack pack : packs) {
            if (pack.hasResource(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> listResources(String prefix) {
        Set<String> result = new HashSet<>();
        for (ResourcePack pack : packs) {
            result.addAll(pack.listResources(prefix));
        }
        return result;
    }

    /**
     * Returns the metadata from the highest-priority pack.
     */
    @Override
    public PackMetadata metadata() {
        return packs.get(0).metadata();
    }

    /**
     * Closes all underlying packs. Suppresses exceptions from later packs if an
     * earlier pack's close throws.
     */
    @Override
    public void close() throws IOException {
        IOException firstException = null;
        for (ResourcePack pack : packs) {
            try {
                pack.close();
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                } else {
                    firstException.addSuppressed(e);
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }
}
