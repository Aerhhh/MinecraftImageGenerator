package net.aerh.imagegenerator.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.pack.font.PackFont;
import org.jetbrains.annotations.Nullable;

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
 * <p>The repository takes ownership of registered sources; they remain open until the pack is
 * {@link #unregister(String) unregistered} (or the process ends). Sources passed to failed
 * registrations are closed before the exception propagates.
 *
 * <p>All operations are safe for concurrent use: registration, resolution and unregistration
 * may interleave freely. A resolve racing an unregister of the same pack either completes or
 * throws a {@link PackResolveException} - the ordinary unregistered-pack error, or a resolve
 * error naming the closed source when the resolve was already reading a zip-backed pack as the
 * unregister closed it. Never a {@link java.util.ConcurrentModificationException} or a raw
 * closed-source {@link IllegalStateException}.
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
     * success the repository owns the source until {@link #unregister(String)} releases it.
     * Registering an id that is already registered throws; to replace a pack under the same id,
     * unregister it first (see {@link #unregister(String)} for the replace semantics).
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
     * Unregisters a pack and releases it: the pack's texture and font caches are invalidated
     * and its source is closed (the repository owns registered sources). The id is immediately
     * free for a new {@link #register} call. Resolves racing the unregister either complete or
     * throw a {@link PackResolveException} (see the class javadoc for the exact guarantee); the
     * repository map is never structurally corrupted.
     *
     * <p><b>Register-with-replace:</b> there is no atomic replace - to swap a pack's content
     * under the same id, call {@code unregister(id)} followed by {@code register(id, ...)}. A
     * resolve arriving between the two calls sees the pack as unregistered and throws, exactly
     * like any other unknown pack id.
     *
     * @param packId the {@code "namespace:name"} id the pack was registered under
     * @return true when a pack with this id was registered and has been released, false when no
     *     such pack was registered
     * @throws IllegalArgumentException when the id is not a well-formed pack id
     */
    public boolean unregister(String packId) {
        LoadedPack removed = packs.remove(PackId.parse(packId));
        if (removed == null) {
            return false;
        }
        removed.release();
        log.info("Unregistered resource pack {}", removed.id());
        return true;
    }

    /**
     * Resolves an item ref against a registered pack - the classic flat layer0 sprite path.
     * Items with elements-based models resolve through
     * {@link #resolveItemVisual(PackId, String, CustomModelData, int)} instead.
     *
     * @return empty when the pack does not contain the item (callers fall back to vanilla)
     * @throws PackResolveException when the pack is not registered, or the item exists but is broken
     */
    public Optional<BufferedImage> resolve(PackId packId, String itemRef) {
        return requireRegistered(packId).resolveSprite(itemRef);
    }

    /**
     * Resolves an item ref against a registered pack to its GUI visual, evaluating
     * {@code custom_model_data} dispatch nodes and tint sources against {@code data}. Flat
     * layer0 models return a {@link PackItemVisual.Sprite} at native texture resolution
     * (identical to {@link #resolve(PackId, String)}); elements models rasterize through the
     * GUI projection directly at {@code pixelsPerGuiPx} canvas px per GUI px and return a
     * {@link PackItemVisual.ElementsRaster}, clipped to the 16-GUI-px slot box unless the
     * item declares {@code oversized_in_gui}.
     *
     * @return empty when the pack does not contain the item (callers fall back to vanilla)
     * @throws PackResolveException when the pack is not registered, or the item exists but
     *                              cannot be rendered (broken references, unsupported node
     *                              types or tint sources, gui rotations beyond identity and
     *                              the mirror without the full-rotation opt-in)
     */
    public Optional<PackItemVisual> resolveItemVisual(PackId packId, String itemRef,
                                                      CustomModelData data, int pixelsPerGuiPx) {
        return resolveItemVisual(packId, itemRef, data, null, pixelsPerGuiPx, false);
    }

    /**
     * Like {@link #resolveItemVisual(PackId, String, CustomModelData, int)} with two extra
     * evaluation inputs:
     *
     * <ul>
     * <li>{@code damage}: the item's damage state, read by {@code range_dispatch} nodes with
     *     {@code property: minecraft:damage} ({@code normalize: true}, the vanilla default,
     *     evaluates the 0..1 damage fraction; {@code normalize: false} the raw damage). Null
     *     evaluates the property at 0.</li>
     * <li>{@code fullGuiRotations}: when true, {@code display.gui} rotations beyond identity
     *     and the (0, 180, 0) mirror render through the true orthographic projection of the
     *     rotated model (vanilla GUI semantics: no perspective) instead of failing. Default
     *     behavior (false) keeps the loud failure.</li>
     * </ul>
     *
     * @return empty when the pack does not contain the item (callers fall back to vanilla)
     * @throws PackResolveException when the pack is not registered, or the item exists but
     *                              cannot be rendered
     */
    public Optional<PackItemVisual> resolveItemVisual(PackId packId, String itemRef,
                                                      CustomModelData data, @Nullable ItemDamage damage,
                                                      int pixelsPerGuiPx, boolean fullGuiRotations) {
        Objects.requireNonNull(data, "data");
        return requireRegistered(packId)
            .resolveItemVisual(itemRef, data, damage, pixelsPerGuiPx, fullGuiRotations);
    }

    /**
     * Resolves an item ref across its own animation timeline: the visual resolves exactly like
     * {@link #resolveItemVisual(PackId, String, CustomModelData, ItemDamage, int, boolean)},
     * and when at least one texture it uses carries an animation mcmeta the result is one
     * visual per timeline step with per-step tick durations (the least common multiple of the
     * animated texture cycles, capped per {@link AnimationTimeline}).
     *
     * @return empty when the pack does not contain the item, or when no texture of the resolved
     *     visual is animated - callers render the static visual instead
     * @throws PackResolveException when the pack is not registered, or the item exists but
     *                              cannot be rendered
     */
    public Optional<PackAnimatedVisual> resolveItemVisualAnimation(PackId packId, String itemRef,
                                                                   CustomModelData data, @Nullable ItemDamage damage,
                                                                   int pixelsPerGuiPx, boolean fullGuiRotations) {
        Objects.requireNonNull(data, "data");
        return requireRegistered(packId)
            .resolveItemVisualAnimation(itemRef, data, damage, pixelsPerGuiPx, fullGuiRotations);
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
     * Resolves a tooltip style's sprite animations against a registered pack: the animated
     * counterpart of {@link #resolveTooltipSprites(PackId, String)}, present when at least one
     * of the style's two sprites carries an animation mcmeta (the "shiny" frame strips real
     * packs ship).
     *
     * @return empty when the pack defines no such style, or when neither sprite is animated
     * @throws IllegalArgumentException when the style ref itself is malformed
     * @throws PackResolveException     when the pack is not registered, or the style exists but
     *                                  is broken
     */
    public Optional<AnimatedTooltipSprites> resolveTooltipSpritesAnimation(PackId packId, String styleRef) {
        return requireRegistered(packId).resolveTooltipSpritesAnimation(styleRef);
    }

    /**
     * Resolves the sprite animations of a registered pack's default tooltip override: the
     * animated counterpart of {@link #resolveDefaultTooltipSprites(PackId)}.
     *
     * @return empty when the pack does not override the default tooltip, or when neither sprite
     *     is animated
     * @throws PackResolveException when the pack is not registered, or only one sprite is present
     */
    public Optional<AnimatedTooltipSprites> resolveDefaultTooltipSpritesAnimation(PackId packId) {
        return requireRegistered(packId).resolveDefaultTooltipSpritesAnimation();
    }

    /**
     * Resolves a registered pack's override of the generic chest container background
     * ({@code minecraft:textures/gui/container/generic_54.png}).
     *
     * @return empty when the pack does not override the texture - callers fall back to the
     *     procedural vanilla-style chrome
     * @throws PackResolveException when the pack is not registered, or the texture exists but
     *                              fails to decode
     */
    public Optional<BufferedImage> resolveContainerBackground(PackId packId) {
        return requireRegistered(packId).resolveContainerBackground();
    }

    /**
     * Resolves the animation of a registered pack's generic chest container background: the
     * animated counterpart of {@link #resolveContainerBackground(PackId)}.
     *
     * @return empty when the pack does not override the texture, or when it is not animated
     * @throws PackResolveException when the pack is not registered, or the texture exists but
     *                              fails to decode
     */
    public Optional<PackAnimation> resolveContainerBackgroundAnimation(PackId packId) {
        return requireRegistered(packId).resolveContainerBackgroundAnimation();
    }

    /**
     * Resolves a font id (e.g. {@code minecraft:default}; a bare id defaults to the
     * {@code minecraft} namespace) against a registered pack. Bitmap providers whose sheet
     * texture is absent from the pack are skipped with a warning instead of failing the font -
     * a documented deviation from vanilla for real packs that reference unbundled vanilla
     * client sheets.
     *
     * @return empty when the pack defines no such font - callers fall back to built-in fonts
     * @throws IllegalArgumentException when the font id itself is malformed
     * @throws PackResolveException     when the pack is not registered, or the font exists but
     *                                  is broken (malformed JSON, missing or cyclic reference,
     *                                  undecodable or oversized glyph sheet)
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
