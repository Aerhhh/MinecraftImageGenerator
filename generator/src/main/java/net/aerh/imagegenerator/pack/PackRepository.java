package net.aerh.imagegenerator.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackResolveException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of runtime-loaded resource packs, keyed by {@link PackId}. Vanilla is NOT registered
 * here: {@code minecraft:minecraft} is reserved and always served by the built-in spritesheet
 * path in the generators.
 *
 * <p>The repository takes ownership of registered sources; they remain open for the lifetime of
 * the process. Sources passed to failed registrations are closed before the exception propagates.
 */
@Slf4j
public final class PackRepository {

    private static final PackRepository GLOBAL = new PackRepository();

    private final Map<PackId, LoadedPack> packs = new ConcurrentHashMap<>();

    /** The process-wide repository used by generator builders unless one is injected. */
    public static PackRepository global() {
        return GLOBAL;
    }

    /**
     * Loads and registers a pack. Eager and fail-fast: indexing errors that make the whole pack
     * unusable throw {@link net.aerh.imagegenerator.exception.PackLoadException} here, not at render.
     * On any failure the source is closed before the exception propagates; on success the
     * repository owns the source for the lifetime of the process.
     *
     * @throws IllegalArgumentException on duplicate registration or the reserved vanilla ID
     */
    public PackId register(String packId, PackSource source) {
        Objects.requireNonNull(source, "source");
        PackId id;
        LoadedPack loaded;
        try {
            id = PackId.parse(packId);
            if (PackId.VANILLA.equals(id)) {
                throw new IllegalArgumentException(
                    "minecraft:minecraft is the built-in vanilla pack and cannot be registered");
            }
            loaded = new LoadedPack(id, source, PackLimits.fromSystemProperties());
            if (packs.putIfAbsent(id, loaded) != null) {
                throw new IllegalArgumentException("Pack already registered: " + id);
            }
        } catch (RuntimeException e) {
            closeQuietly(source);
            throw e;
        }
        log.info("Registered resource pack {} (asset namespaces: {})", id, loaded.assetNamespaces());
        return id;
    }

    /**
     * Resolves an item ref against a registered pack.
     *
     * @return empty when the pack does not contain the item (callers fall back to vanilla)
     * @throws PackResolveException when the pack is not registered, or the item exists but is broken
     */
    public Optional<BufferedImage> resolve(PackId packId, String itemRef) {
        LoadedPack pack = packs.get(packId);
        if (pack == null) {
            throw new PackResolveException("Pack `%s` is not registered (registered: %s)",
                packId.toString(), registeredPacks().toString());
        }
        return pack.resolveSprite(itemRef);
    }

    public Set<PackId> registeredPacks() {
        return Set.copyOf(packs.keySet());
    }

    private static void closeQuietly(PackSource source) {
        try {
            source.close();
        } catch (IOException e) {
            log.warn("Failed to close pack source after failed registration", e);
        }
    }
}
