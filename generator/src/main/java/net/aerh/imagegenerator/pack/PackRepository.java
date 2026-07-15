package net.aerh.imagegenerator.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.pack.font.PackFont;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
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
     * Loads and registers a pack using {@link PackLimits#fromSystemProperties()} for texture
     * decode and cache limits. See {@link #register(String, PackSource, PackLimits)}.
     *
     * @throws IllegalArgumentException on duplicate registration or the reserved vanilla ID
     */
    public PackId register(String packId, PackSource source) {
        return register(packId, source, PackLimits.fromSystemProperties());
    }

    /**
     * Loads and registers a pack with explicit limits. Eager and fail-fast: indexing errors that
     * make the whole pack unusable throw {@link net.aerh.imagegenerator.exception.PackLoadException}
     * here, not at render. On any failure the source is closed before the exception propagates; on
     * success the repository owns the source for the lifetime of the process.
     *
     * <p>{@code limits} governs texture decode and the per-pack texture cache for this pack only;
     * pass the same instance you gave the {@link PackSource} factory ({@link PackSource#directory}
     * / {@link PackSource#zip}) so read-time and decode-time limits agree.
     *
     * <p>Large server packs (Wynncraft-class packs run to ~36,000 files) exceed the default
     * {@link PackLimits#maxEntries()} and need explicitly raised limits on BOTH the source factory
     * and this call. Note the count semantics differ by source: ZIP sources count every
     * central-directory record including directory entries, directory sources count only regular
     * files under {@code assets/}. See the {@link PackLimits} javadoc for sizing guidance.
     *
     * @throws IllegalArgumentException on duplicate registration or the reserved vanilla ID
     */
    public PackId register(String packId, PackSource source, PackLimits limits) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(limits, "limits");
        PackId id;
        LoadedPack loaded;
        try {
            id = PackId.parse(packId);
            if (PackId.VANILLA.equals(id)) {
                throw new IllegalArgumentException(
                    "minecraft:minecraft is the built-in vanilla pack and cannot be registered");
            }
            loaded = new LoadedPack(id, source, limits);
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
        return requireRegistered(packId).resolveSprite(itemRef);
    }

    /**
     * Resolves a tooltip style ref (the {@code minecraft:tooltip_style} component value) against
     * a registered pack.
     *
     * @return empty when the pack defines no such style - callers decide the fallback policy
     * @throws IllegalArgumentException when the style ref itself is malformed
     * @throws PackResolveException     when the pack is not registered, or the style exists but
     *                                  is broken (missing sprite, malformed gui scaling mcmeta)
     */
    public Optional<TooltipSprites> resolveTooltipSprites(PackId packId, String styleRef) {
        return requireRegistered(packId).resolveTooltipSprites(styleRef);
    }

    /**
     * Resolves a registered pack's override of the styleless default tooltip
     * ({@code minecraft:tooltip/background} + {@code frame}).
     *
     * @return empty when the pack does not override the default tooltip
     * @throws PackResolveException when the pack is not registered, or only one sprite is present
     */
    public Optional<TooltipSprites> resolveDefaultTooltipSprites(PackId packId) {
        return requireRegistered(packId).resolveDefaultTooltipSprites();
    }

    /**
     * Sorted tooltip style refs defined by a registered pack, for discovery/autocomplete.
     *
     * @throws PackResolveException when the pack is not registered
     */
    public List<String> tooltipStyles(PackId packId) {
        return requireRegistered(packId).tooltipStyleRefs();
    }

    /**
     * Resolves a font id (e.g. {@code minecraft:default}; a bare id defaults to the
     * {@code minecraft} namespace) against a registered pack.
     *
     * @return empty when the pack defines no such font - callers fall back to built-in fonts
     * @throws IllegalArgumentException when the font id itself is malformed
     * @throws PackResolveException     when the pack is not registered, or the font exists but
     *                                  is broken (malformed JSON, missing or cyclic reference,
     *                                  missing or oversized glyph sheet)
     */
    public Optional<PackFont> resolveFont(PackId packId, String fontId) {
        return requireRegistered(packId).resolveFont(fontId);
    }

    /**
     * Sorted font ids defined by a registered pack, for discovery/autocomplete.
     *
     * @throws PackResolveException when the pack is not registered
     */
    public List<String> fontIds(PackId packId) {
        return requireRegistered(packId).fontIds();
    }

    private LoadedPack requireRegistered(PackId packId) {
        LoadedPack pack = packs.get(packId);
        if (pack == null) {
            throw new PackResolveException("Pack `%s` is not registered (registered: %s)",
                packId.toString(), registeredPacks().toString());
        }
        return pack;
    }

    public Set<PackId> registeredPacks() {
        return Set.copyOf(packs.keySet());
    }

    /**
     * Closes a pack source that failed registration, swallowing any failure from {@code close()}
     * itself. Catches {@link Exception} rather than the declared {@link IOException}: a
     * caller-supplied {@link PackSource} implementation whose {@code close()} throws an unchecked
     * exception must not mask (or replace) the original registration failure that triggered this
     * cleanup.
     */
    private static void closeQuietly(PackSource source) {
        try {
            source.close();
        } catch (Exception e) {
            log.warn("Failed to close pack source after failed registration", e);
        }
    }
}
