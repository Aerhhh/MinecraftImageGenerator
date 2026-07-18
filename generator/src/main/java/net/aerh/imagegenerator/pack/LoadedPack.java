package net.aerh.imagegenerator.pack;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.pack.font.BitmapProviderCache;
import net.aerh.imagegenerator.pack.font.FontProviderDefinition;
import net.aerh.imagegenerator.pack.font.FontResolver;
import net.aerh.imagegenerator.pack.font.MovementTintRule;
import net.aerh.imagegenerator.pack.font.PackFont;
import net.aerh.imagegenerator.pack.font.VanillaFontSheets;
import org.jetbrains.annotations.Nullable;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A registered resource pack: eagerly indexed item definitions and models (JSON parsed once at
 * construction, per-item errors tolerated), with textures decoded lazily through a bounded cache.
 * Font JSONs are indexed by path at construction and parsed lazily on
 * {@link #resolveFont(String)}, with resolved fonts held in a bounded, GC-friendly cache
 * mirroring the texture cache (resolved fonts retain their glyph cell rasters, which for large
 * glyph-art sheets can be substantial).
 */
@Slf4j
final class LoadedPack {

    private static final Pattern ITEM_PATH = Pattern.compile("assets/([^/]+)/items/(.+)\\.json");
    private static final Pattern MODEL_PATH = Pattern.compile("assets/([^/]+)/models/(.+)\\.json");
    private static final Pattern TOOLTIP_SPRITE_PATH =
        Pattern.compile("assets/([^/]+)/textures/gui/sprites/tooltip/(.+)\\.png");
    private static final Pattern FONT_PATH = Pattern.compile("assets/([^/]+)/font/(.+)\\.json");
    private static final String BACKGROUND_SUFFIX = "_background";
    private static final String FRAME_SUFFIX = "_frame";
    // The styleless default tooltip is the fixed minecraft:tooltip/background + frame sprite
    // pair, not a <style>_background naming-convention entry.
    private static final String DEFAULT_BACKGROUND_PATH = "assets/minecraft/textures/gui/sprites/tooltip/background.png";
    private static final String DEFAULT_FRAME_PATH = "assets/minecraft/textures/gui/sprites/tooltip/frame.png";
    private static final Pattern CONTAINER_TEXTURE_PATH =
        Pattern.compile("assets/([^/]+)/textures/gui/container/(.+)\\.png");
    // The vanilla texture behind every generic 9-by-N chest menu; packs restyle chest screens by
    // overriding it (fully transparent overrides are how glyph-art menu packs blank the chrome).
    private static final String GENERIC_CONTAINER_PATH = "assets/minecraft/textures/gui/container/generic_54.png";
    /**
     * Texture cache key prefix selecting the {@link PackLimits#sheetTextureMaxDim() sheet cap}
     * instead of the strict item cap. Call sites that legitimately read sheet-shaped textures
     * (tooltip sprites) prefix their cache keys; every other lookup - item layers included, even
     * ones whose models point at tooltip-sprite paths - decodes under the item cap. Texture
     * paths always start with {@code assets/}, so the prefix can never collide with a real path.
     */
    private static final String SHEET_CAPPED_KEY_PREFIX = "sheet:";
    /**
     * Texture cache key prefix requesting the FULL decoded texture with no animation crop - the
     * uncropped flipbook sheet the animated resolution paths crop frames from on demand. May
     * combine with {@link #SHEET_CAPPED_KEY_PREFIX} ({@code "anim:sheet:<path>"}); the decode
     * cap selection is unchanged. Like the sheet prefix, it can never collide with a real path
     * (paths always start with {@code assets/}). Storing the sheet once and cropping per frame
     * keeps the cache from double-storing sheets AND per-frame crops.
     */
    private static final String FULL_SHEET_KEY_PREFIX = "anim:";
    private static final int MAX_PARENT_DEPTH = 8;
    /**
     * How many {@code layerN} textures a generated flat model may stack: vanilla's
     * {@code ItemModelGenerator} bakes {@code layer0} through {@code layer4} and stops at the
     * first missing index.
     */
    private static final int MAX_GENERATED_LAYERS = 5;
    /** The {@code item/} path prefix every concrete vanilla item template shares. */
    private static final String VANILLA_ITEM_PATH_PREFIX = "item/";
    /**
     * Pack ids whose textures use alpha 252 as an "opaque full-bright" emissive marker (a
     * Hypixel SkyBlock shader convention). Only these packs get the alpha normalized to fully
     * opaque at decode time; other packs may ship legitimate alpha-252 pixels that must be
     * preserved. Add a pack id here if another pack is confirmed to use the same convention.
     */
    private static final Set<String> EMISSIVE_ALPHA_PACK_IDS = Set.of("hypixel:skyblock");

    private record ItemEntry(ItemModelNode node, boolean oversizedInGui, String error) {
    }

    private static final class TooltipStyleEntry {
        private String backgroundPath;
        private String framePath;
    }

    private final PackId id;
    private final PackSource source;
    private final PackLimits limits;
    private final boolean normalizeEmissiveAlpha;
    /**
     * The pack's movement text shader no-tint rule, or null when the pack ships no such shader.
     * Detected once at construction (see {@link MovementShaderDetector}) and threaded into every
     * resolved {@link PackFont} so movement marker run colors keep their glyphs' native texel color.
     * Null leaves the font path tinting vanilla-style, byte-identical to before.
     */
    private final MovementTintRule movementTintRule;
    private final Set<String> namespaces = new HashSet<>();
    private final Map<String, ItemEntry> items = new HashMap<>();
    private final Map<String, ModelInfo> models = new HashMap<>();
    private final Map<String, TooltipStyleEntry> tooltipStyles = new HashMap<>();
    private final Map<String, String> fontPaths = new HashMap<>();
    private final BitmapProviderCache fontProviderCache = new BitmapProviderCache();
    private boolean hasTooltipSprites;
    private boolean hasContainerTextures;
    private final LoadingCache<String, BufferedImage> textureCache;
    private final LoadingCache<String, PackFont> fontCache;
    /**
     * Loads bitmap provider sheets and probes their existence so {@link PackFont#create} can
     * skip providers whose sheet is absent from the pack (the documented
     * {@link #resolveFont(String)} deviation) while present-but-broken sheets keep failing
     * loudly through {@link #loadFontSheet}.
     */
    private final PackFont.TextureLoader fontSheetLoader = new PackFont.TextureLoader() {
        @Override
        public BufferedImage load(String textureFileRef) {
            return loadFontSheet(textureFileRef);
        }

        @Override
        public boolean exists(String textureFileRef) {
            return source.exists(fontSheetPath(textureFileRef));
        }

        @Override
        public Optional<BufferedImage> vanillaFallbackSheet(String textureFileRef) {
            ResourceRef ref = parseRefOrResolveError(textureFileRef, "minecraft");
            return VanillaFontSheets.sheet(ref.namespace(), ref.path());
        }

        @Override
        public Optional<byte[]> ttfFontData(String fontFileRef) {
            // Vanilla resolves a ttf provider `file` beneath assets/<ns>/font/, not textures/.
            ResourceRef ref = parseRefOrResolveError(fontFileRef, "minecraft");
            String path = "assets/" + ref.namespace() + "/font/" + ref.path();
            if (!source.exists(path)) {
                return Optional.empty();
            }
            try {
                return Optional.of(source.read(path));
            } catch (PackLoadException e) {
                // Oversized (over maxEntryBytes) or unreadable: degrade to the never-claim
                // unsupported entry rather than fail the whole font.
                log.warn("Pack {}: TTF font `{}` failed to read: {}", id, path, e.getMessage());
                return Optional.empty();
            }
        }
    };

    LoadedPack(PackId id, PackSource source, PackLimits limits) {
        this.id = id;
        this.source = source;
        this.limits = limits;
        this.normalizeEmissiveAlpha = EMISSIVE_ALPHA_PACK_IDS.contains(id.toString());
        this.movementTintRule = MovementShaderDetector.detect(source).orElse(null);
        this.textureCache = Caffeine.newBuilder()
            .maximumWeight(limits.textureCacheMaxBytes())
            // long arithmetic: large custom maxTextureDim configs can overflow int and silently
            // underweight the cache, mirroring TextureDecoder's overflow-safe bounds pattern.
            .weigher((String key, BufferedImage image) ->
                (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) image.getWidth() * image.getHeight() * 4)))
            .softValues()
            .build(this::loadTexture);
        // Resolved fonts retain their glyph cell rasters, which for glyph-art sheets (bounded by
        // sheetTextureMaxDim, not maxTextureDim) can dwarf item textures. Bound and soft-reference
        // them exactly like the texture cache, reusing the same byte budget as a separate
        // allowance, so a pack full of huge sheets cannot pin unbounded memory.
        this.fontCache = Caffeine.newBuilder()
            .maximumWeight(limits.textureCacheMaxBytes())
            .weigher((String key, PackFont font) ->
                (int) Math.min(Integer.MAX_VALUE, Math.max(1L, font.retainedCellBytes())))
            .softValues()
            .build(this::resolveFontUncached);
        buildIndex();
    }

    public PackId id() {
        return id;
    }

    /**
     * Releases the pack's retained resources on unregister: invalidates the texture and font
     * caches and closes the source. A resolve racing the release either completes against the
     * still-valid in-memory index or fails loudly reading the closed source - sources wrap
     * closed-state failures as {@link PackLoadException}, which the resolve paths surface as
     * the ordinary {@link PackResolveException}, so no raw closed-source error ever escapes.
     * The caches themselves stay structurally safe (Caffeine is thread-safe), so concurrent
     * callers never see corruption.
     */
    void release() {
        textureCache.invalidateAll();
        fontCache.invalidateAll();
        try {
            source.close();
        } catch (Exception e) {
            // Same rationale as PackRepository's failed-registration cleanup: a source whose
            // close() throws must not turn a successful unregister into a failure.
            log.warn("Pack {}: failed to close source on release", id, e);
        }
    }

    public Set<String> assetNamespaces() {
        return Set.copyOf(namespaces);
    }

    private void buildIndex() {
        int errors = 0;
        for (String path : source.list("assets/")) {
            Matcher itemMatcher = ITEM_PATH.matcher(path);
            if (itemMatcher.matches()) {
                namespaces.add(itemMatcher.group(1));
                String key = itemMatcher.group(1) + ":" + itemMatcher.group(2);
                try {
                    ItemDefinition definition = PackJsonParser.parseItemDefinitionInfo(source.read(path));
                    items.put(key, new ItemEntry(definition.model(), definition.oversizedInGui(), null));
                } catch (RuntimeException e) {
                    errors++;
                    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                    items.put(key, new ItemEntry(null, false, message));
                }
                continue;
            }
            Matcher modelMatcher = MODEL_PATH.matcher(path);
            if (modelMatcher.matches()) {
                namespaces.add(modelMatcher.group(1));
                try {
                    String key = modelMatcher.group(1) + ":" + modelMatcher.group(2);
                    models.put(key, PackJsonParser.parseModel(source.read(path)));
                } catch (RuntimeException e) {
                    errors++;
                    log.warn("Pack {}: skipping malformed model {}: {}", id, path, e.getMessage());
                }
                continue;
            }
            Matcher tooltipMatcher = TOOLTIP_SPRITE_PATH.matcher(path);
            if (tooltipMatcher.matches()) {
                hasTooltipSprites = true;
                indexTooltipSprite(tooltipMatcher.group(1), tooltipMatcher.group(2), path);
                continue;
            }
            if (CONTAINER_TEXTURE_PATH.matcher(path).matches()) {
                hasContainerTextures = true;
                continue;
            }
            Matcher fontMatcher = FONT_PATH.matcher(path);
            if (fontMatcher.matches()) {
                // Skip files that are not valid resource locations (mirroring vanilla): indexing
                // them would make fontIds() advertise ids that resolveFont() must reject as
                // malformed caller input, breaking the fontIds -> resolveFont round-trip.
                if (!isValidResourceLocation(fontMatcher.group(1), fontMatcher.group(2))) {
                    log.warn("Pack {}: skipping font with invalid resource location: {}", id, path);
                    continue;
                }
                namespaces.add(fontMatcher.group(1));
                // Index the path only; the JSON parses lazily at resolve time so a broken font
                // fails loudly when requested instead of degrading the whole pack at register.
                fontPaths.put(fontMatcher.group(1) + ":" + fontMatcher.group(2), path);
            }
        }
        if (items.isEmpty() && !hasTooltipSprites && fontPaths.isEmpty() && !hasContainerTextures) {
            throw new PackLoadException(
                "Pack %s contains no item definitions under assets/*/items/, no tooltip sprites under assets/*/textures/gui/sprites/tooltip/, no fonts under assets/*/font/, and no container textures under assets/*/textures/gui/container/",
                id.toString());
        }
        if (errors > 0) {
            log.warn("Pack {}: indexed {} items, {} models and {} fonts with {} malformed entries",
                id, items.size(), models.size(), fontPaths.size(), errors);
        } else {
            log.info("Pack {}: indexed {} items, {} models and {} fonts",
                id, items.size(), models.size(), fontPaths.size());
        }
    }

    private void indexTooltipSprite(String namespace, String spriteName, String path) {
        String styleName;
        boolean isBackground;
        if (spriteName.endsWith(BACKGROUND_SUFFIX)) {
            styleName = spriteName.substring(0, spriteName.length() - BACKGROUND_SUFFIX.length());
            isBackground = true;
        } else if (spriteName.endsWith(FRAME_SUFFIX)) {
            styleName = spriteName.substring(0, spriteName.length() - FRAME_SUFFIX.length());
            isBackground = false;
        } else {
            return;
        }
        if (styleName.isEmpty()) {
            log.warn("Pack {}: ignoring tooltip sprite with empty style name: {}", id, path);
            return;
        }
        TooltipStyleEntry entry = tooltipStyles.computeIfAbsent(namespace + ":" + styleName,
            key -> new TooltipStyleEntry());
        if (isBackground) {
            entry.backgroundPath = path;
        } else {
            entry.framePath = path;
        }
    }

    /**
     * Resolves a tooltip style ref (the {@code minecraft:tooltip_style} component value, e.g.
     * {@code hypixel_skyblock:epic}; a bare ref defaults to the {@code minecraft} namespace) to
     * its background and frame sprites.
     *
     * <p>Tooltip sprite decodes are bounded by {@link PackLimits#sheetTextureMaxDim()}, NOT
     * {@link PackLimits#maxTextureDim()}: animated tooltip sprites are vertical flipbook strips
     * of square frames that real packs ship far beyond the item texture cap. An animated sprite
     * is cropped to the first frame of its mcmeta frames list before use.
     *
     * @return empty when the pack defines no such style - callers decide the fallback policy
     * @throws IllegalArgumentException when the style ref itself is malformed (caller input)
     * @throws PackResolveException     when the style exists but is broken (a sprite is missing
     *                                  or its gui scaling mcmeta is malformed)
     */
    public Optional<TooltipSprites> resolveTooltipSprites(String styleRef) {
        ResourceRef ref = ResourceRef.parse(styleRef, "minecraft");
        TooltipStyleEntry entry = tooltipStyles.get(ref.toString());
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.backgroundPath == null || entry.framePath == null) {
            throw new PackResolveException("Tooltip style `%s` in pack `%s` is missing its %s sprite",
                ref.toString(), id.toString(), entry.backgroundPath == null ? "background" : "frame");
        }
        return Optional.of(new TooltipSprites(loadGuiSprite(entry.backgroundPath), loadGuiSprite(entry.framePath)));
    }

    /**
     * Resolves the pack's override of the styleless default tooltip
     * ({@code minecraft:tooltip/background} + {@code frame}).
     *
     * @return empty when the pack does not override the default tooltip
     * @throws PackResolveException when only one of the two sprites is present
     */
    public Optional<TooltipSprites> resolveDefaultTooltipSprites() {
        boolean hasBackground = source.exists(DEFAULT_BACKGROUND_PATH);
        boolean hasFrame = source.exists(DEFAULT_FRAME_PATH);
        if (!hasBackground && !hasFrame) {
            return Optional.empty();
        }
        if (!hasBackground || !hasFrame) {
            throw new PackResolveException("Pack `%s` overrides only the default tooltip %s sprite; both are required",
                id.toString(), hasBackground ? "background" : "frame");
        }
        return Optional.of(new TooltipSprites(loadGuiSprite(DEFAULT_BACKGROUND_PATH), loadGuiSprite(DEFAULT_FRAME_PATH)));
    }

    /**
     * Resolves the pack's override of the generic chest container background
     * ({@code minecraft:textures/gui/container/generic_54.png}), the texture behind every
     * 9-by-N chest menu. Decoded under the strict {@link PackLimits#maxTextureDim()} like other
     * GUI textures; an animation mcmeta crops to the first frame as usual.
     *
     * @return empty when the pack does not override the texture - callers fall back to the
     *     procedural vanilla-style chrome
     * @throws PackResolveException when the texture exists but fails to decode
     */
    public Optional<BufferedImage> resolveContainerBackground() {
        if (!source.exists(GENERIC_CONTAINER_PATH)) {
            return Optional.empty();
        }
        // Defensive copy, mirroring loadGuiSprite: the cached instance is shared and the image
        // is handed to callers.
        return Optional.of(copy(textureCache.get(GENERIC_CONTAINER_PATH)));
    }

    /**
     * Resolves a tooltip style's sprite ANIMATIONS: the style resolves exactly like
     * {@link #resolveTooltipSprites(String)}, and each sprite whose texture carries an animation
     * mcmeta contributes its {@link PackAnimation}.
     *
     * @return empty when the pack defines no such style, or when NEITHER sprite is animated -
     *     callers render the static sprites instead
     * @throws IllegalArgumentException when the style ref itself is malformed (caller input)
     * @throws PackResolveException     when the style exists but is broken
     */
    public Optional<AnimatedTooltipSprites> resolveTooltipSpritesAnimation(String styleRef) {
        ResourceRef ref = ResourceRef.parse(styleRef, "minecraft");
        TooltipStyleEntry entry = tooltipStyles.get(ref.toString());
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.backgroundPath == null || entry.framePath == null) {
            throw new PackResolveException("Tooltip style `%s` in pack `%s` is missing its %s sprite",
                ref.toString(), id.toString(), entry.backgroundPath == null ? "background" : "frame");
        }
        return animatedTooltipSprites(entry.backgroundPath, entry.framePath);
    }

    /**
     * Resolves the sprite animations of the pack's default tooltip override; the animated
     * counterpart of {@link #resolveDefaultTooltipSprites()}.
     *
     * @return empty when the pack does not override the default tooltip, or when neither sprite
     *     is animated
     * @throws PackResolveException when only one of the two sprites is present
     */
    public Optional<AnimatedTooltipSprites> resolveDefaultTooltipSpritesAnimation() {
        boolean hasBackground = source.exists(DEFAULT_BACKGROUND_PATH);
        boolean hasFrame = source.exists(DEFAULT_FRAME_PATH);
        if (!hasBackground && !hasFrame) {
            return Optional.empty();
        }
        if (!hasBackground || !hasFrame) {
            throw new PackResolveException("Pack `%s` overrides only the default tooltip %s sprite; both are required",
                id.toString(), hasBackground ? "background" : "frame");
        }
        return animatedTooltipSprites(DEFAULT_BACKGROUND_PATH, DEFAULT_FRAME_PATH);
    }

    /** Empty unless at least one of the two sprites is animated; sheet-capped like all sprites. */
    private Optional<AnimatedTooltipSprites> animatedTooltipSprites(String backgroundPath, String framePath) {
        PackAnimation background = textureAnimation(backgroundPath, true).orElse(null);
        PackAnimation frame = textureAnimation(framePath, true).orElse(null);
        if (background == null && frame == null) {
            return Optional.empty();
        }
        return Optional.of(new AnimatedTooltipSprites(
            new TooltipSprites(loadGuiSprite(backgroundPath), loadGuiSprite(framePath)), background, frame));
    }

    /**
     * Resolves the animation of the pack's generic chest container background; the animated
     * counterpart of {@link #resolveContainerBackground()}.
     *
     * @return empty when the pack does not override the texture, or when it is not animated
     * @throws PackResolveException when the texture exists but fails to decode
     */
    public Optional<PackAnimation> resolveContainerBackgroundAnimation() {
        if (!source.exists(GENERIC_CONTAINER_PATH)) {
            return Optional.empty();
        }
        return textureAnimation(GENERIC_CONTAINER_PATH, false);
    }

    /**
     * Resolves a texture's animation: present when the texture has a valid animation mcmeta
     * with at least two frame entries. The uncropped flipbook sheet is served by the texture
     * cache under the {@link #FULL_SHEET_KEY_PREFIX} (bounded by the existing cache weights);
     * frames crop from it on demand. A malformed animation follows the decode path's
     * warn-and-fallback policy and resolves empty; an undecodable TEXTURE stays loud, exactly
     * like the static path.
     */
    private Optional<PackAnimation> textureAnimation(String texturePath, boolean sheetCapped) {
        String mcmetaPath = texturePath + ".mcmeta";
        if (!source.exists(mcmetaPath)) {
            return Optional.empty();
        }
        try {
            McMeta meta = PackJsonParser.parseMcmeta(source.read(mcmetaPath));
            if (meta.animation() == null) {
                return Optional.empty();
            }
            BufferedImage sheet = textureCache.get(
                FULL_SHEET_KEY_PREFIX + (sheetCapped ? SHEET_CAPPED_KEY_PREFIX : "") + texturePath);
            return PackAnimation.resolve(sheet, meta.animation());
        } catch (PackLoadException e) {
            log.warn("Pack {}: ignoring malformed animation mcmeta for {}: {}", id, texturePath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Style refs with both sprites present, sorted, for discovery/autocomplete. A style listed
     * here can still fail at resolve time (e.g. malformed gui scaling mcmeta) - loudly.
     */
    public List<String> tooltipStyleRefs() {
        return tooltipStyles.entrySet().stream()
            .filter(entry -> entry.getValue().backgroundPath != null && entry.getValue().framePath != null)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }

    /**
     * Resolves a font id (e.g. {@code minecraft:default}, {@code mypack:chest_backgrounds}; a bare
     * id defaults to the {@code minecraft} namespace) to its resolved font: references expanded,
     * filters applied for Force Unicode OFF / jp OFF, bitmap sheets decoded and metrics computed.
     * Resolved fonts are held in a cache bounded by retained glyph-cell bytes and soft
     * references (mirroring the texture cache), so repeated callers get the cached instance
     * while huge glyph-art fonts stay reclaimable instead of pinning unbounded memory.
     *
     * <p>Font sheet decodes are bounded by {@link PackLimits#sheetTextureMaxDim()}, NOT
     * {@link PackLimits#maxTextureDim()}: real packs ship glyph sheets far beyond the item
     * texture cap. Sheets are decoded once per DISTINCT bitmap provider (fonts referencing the
     * same sheet share one built provider) and released after the per-glyph cells are copied
     * out, so they intentionally bypass the item texture cache (caching a sheet of up to
     * 8192x8192 pixels would blow the cache budget for no repeat-read benefit).
     *
     * <p><b>Deviation from vanilla, by design:</b> a bitmap provider whose sheet texture is
     * ABSENT from the pack is resolved in two steps. Real server packs override
     * {@code minecraft:default} (and {@code default_offset}) with providers referencing vanilla
     * client sheets (e.g. {@code minecraft:font/ascii.png}) they do not ship, assuming the vanilla
     * client already has them. Those three vanilla sheets are bundled ({@link VanillaFontSheets}),
     * so such a provider renders REAL bitmap glyphs from the bundled copy, keeping the pack's own
     * metrics (a {@code default_offset} font's re-declared ascent still shifts the text down). Only
     * when no bundled vanilla sheet matches - a non-vanilla sheet this library does not bundle - is
     * the provider skipped with a warning instead of failing the whole font, keeping every glyph
     * the pack actually ships renderable. A font whose providers ALL skip still resolves, as an
     * empty font claiming no codepoint. A pack that ships its OWN copy of a vanilla sheet wins: the
     * provider is present, so the bundled fallback is never consulted. Present-but-broken sheets
     * (undecodable, oversized) keep failing loudly.
     *
     * @return empty when the pack defines no such font - callers fall back to built-in fonts
     * @throws IllegalArgumentException when the font id itself is malformed (caller input)
     * @throws PackResolveException     when the font exists but is broken (malformed JSON,
     *                                  missing or cyclic reference, undecodable or oversized
     *                                  sheet)
     */
    public Optional<PackFont> resolveFont(String fontId) {
        ResourceRef ref = ResourceRef.parse(fontId, "minecraft");
        String key = ref.toString();
        if (!fontPaths.containsKey(key)) {
            return Optional.empty();
        }
        return Optional.of(fontCache.get(key));
    }

    /** Cache loader for {@link #resolveFont(String)}; {@code key} is the normalized font id. */
    private PackFont resolveFontUncached(String key) {
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders(key, this::fontDefinitions);
        return PackFont.create(key, resolved, fontSheetLoader, fontProviderCache, movementTintRule);
    }

    private static boolean isValidResourceLocation(String namespace, String path) {
        try {
            new ResourceRef(namespace, path);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Sorted font ids defined by this pack, for discovery/autocomplete. */
    public List<String> fontIds() {
        return fontPaths.keySet().stream().sorted().toList();
    }

    /**
     * {@link FontResolver.DefinitionLookup} backed by this pack's font index: empty for unknown
     * ids (the resolver decides whether that is fatal), loud for present-but-malformed JSON.
     */
    private Optional<List<FontProviderDefinition>> fontDefinitions(String fontId) {
        String path = fontPaths.get(fontId);
        if (path == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(PackFontParser.parse(source.read(path)));
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Font `%s` in pack `%s` is malformed: %s", fontId, id.toString(), e.getMessage()), e);
        }
    }

    /**
     * Loads a bitmap provider sheet. The {@code file} reference includes its extension (vanilla
     * resolves it directly beneath {@code textures/}), and the decode is bounded by the dedicated
     * sheet cap; see {@link #resolveFont(String)} for why this bypasses the item texture cache.
     */
    private BufferedImage loadFontSheet(String textureFileRef) {
        String path = fontSheetPath(textureFileRef);
        try {
            return TextureDecoder.decode(source.read(path), limits.sheetTextureMaxDim(), normalizeEmissiveAlpha);
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Font texture `%s` in pack `%s` failed to load: %s", path, id.toString(), e.getMessage()), e);
        }
    }

    /**
     * The pack path of a bitmap provider {@code file} reference (extension included), shared by
     * the sheet loader and its existence probe so the two can never disagree. A malformed
     * reference fails loudly here - malformed is not absent.
     */
    private String fontSheetPath(String textureFileRef) {
        ResourceRef ref = parseRefOrResolveError(textureFileRef, "minecraft");
        return "assets/" + ref.namespace() + "/textures/" + ref.path();
    }

    private GuiSprite loadGuiSprite(String texturePath) {
        // Defensive copy: the cache instance is shared and GuiSprite hands the image to callers.
        // Sheet-capped: tooltip sprites are the sheet-shaped textures the looser cap exists for.
        return new GuiSprite(copy(textureCache.get(SHEET_CAPPED_KEY_PREFIX + texturePath)), guiScalingFor(texturePath));
    }

    private GuiScaling guiScalingFor(String texturePath) {
        String mcmetaPath = texturePath + ".mcmeta";
        if (!source.exists(mcmetaPath)) {
            return new GuiScaling.Stretch();
        }
        McMeta meta;
        try {
            meta = PackJsonParser.parseMcmeta(source.read(mcmetaPath));
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Gui sprite mcmeta `%s` in pack `%s` is malformed: %s", mcmetaPath, id.toString(), e.getMessage()), e);
        }
        return meta.guiScaling() != null ? meta.guiScaling() : new GuiScaling.Stretch();
    }

    /**
     * Resolves an item reference (full namespaced items/ path, e.g.
     * {@code hypixel_skyblock:item/jacob/cactus_knife}) to a flat GUI sprite - the classic
     * generated-layer path only ({@code layer0} up to {@code layer4}, stacked like vanilla's
     * {@code ItemModelGenerator}), evaluated with no custom model data. Items whose models are
     * elements-based resolve through {@link #resolveItemVisual(String, CustomModelData, int)}
     * instead. Supported tint sources (constant, dye and custom_model_data defaults) color
     * layer {@code i} with tint {@code i} like the vanilla client; unsupported sources warn and
     * stay untinted.
     *
     * @return empty when the ref is bare, in a foreign namespace, or unknown - callers fall back
     *     to vanilla; a present sprite otherwise
     * @throws PackResolveException when the item exists but cannot be rendered
     */
    public Optional<BufferedImage> resolveSprite(String itemRef) {
        ItemEntry entry = lookupItem(itemRef);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(composeSpriteLayers(itemRef,
            resolveGuiModels(itemRef, entry, CustomModelData.EMPTY, null), CustomModelData.EMPTY,
            textureCache::get));
    }

    /**
     * Resolves an item reference to its GUI visual, evaluating {@code custom_model_data}
     * dispatch nodes and tint sources against {@code data}:
     *
     * <ul>
     * <li>Models whose resolved chains carry no elements compose exactly like
     *     {@link #resolveSprite(String)} and return a {@link PackItemVisual.Sprite} at native
     *     texture resolution (callers scale as before). Supported tint sources multiply layer
     *     {@code i} like vanilla's {@code item/generated} tintindex {@code i} quads;
     *     unsupported sources warn and stay untinted so vanilla-style dye/potion items keep
     *     rendering.</li>
     * <li>Models with elements rasterize through the GUI projection directly at
     *     {@code pixelsPerGuiPx} (no 16 px intermediate, so sub-GUI-px geometry survives) and
     *     return a {@link PackItemVisual.ElementsRaster}; the item's {@code oversized_in_gui}
     *     flag selects slot-box clipping versus full-extent output. Mixing elements models and
     *     flat layer0 models in one composite is unsupported and fails loudly.</li>
     * </ul>
     *
     * <p>Elements chains stop at parents outside the pack (vanilla model assets are
     * unavailable); their textures and display transforms are treated as absent. Chains ending
     * at any vanilla item template {@code minecraft:item/*} the pack does not ship - the builtin
     * {@code item/generated} and {@code item/handheld} or a concrete model like
     * {@code item/wooden_pickaxe} - terminate with flat generated-family semantics even when the
     * pack claims the {@code minecraft} namespace without shipping those models (the template's
     * own layer0 is contributed from the pack's copy of the identically named texture, or
     * warn-skipped when absent). A missing IN-pack parent of any other name (a
     * {@code minecraft:block/*} template included), a cyclic chain, or a chain deeper than the
     * layer0 path allows fails loudly instead of silently dropping inherited transforms.
     *
     * @return empty when the ref is bare, in a foreign namespace, or unknown - callers fall back
     *     to vanilla
     * @throws PackResolveException when the item exists but cannot be rendered (broken
     *                              references, unsupported node types or tint sources, gui
     *                              rotations beyond identity and the mirror without the
     *                              full-rotation opt-in)
     */
    public Optional<PackItemVisual> resolveItemVisual(String itemRef, CustomModelData data, int pixelsPerGuiPx) {
        return resolveItemVisual(itemRef, data, null, pixelsPerGuiPx, false);
    }

    /**
     * Like {@link #resolveItemVisual(String, CustomModelData, int)} with two extra evaluation
     * inputs: {@code damage} feeds {@code range_dispatch} nodes with
     * {@code property: minecraft:damage} (null evaluates the property at 0), and
     * {@code fullGuiRotations} renders {@code display.gui} rotations beyond identity and the
     * mirror through the true orthographic projection instead of failing (see
     * {@link ElementModelRenderer} for the projection semantics).
     */
    public Optional<PackItemVisual> resolveItemVisual(String itemRef, CustomModelData data,
                                                      @Nullable ItemDamage damage, int pixelsPerGuiPx,
                                                      boolean fullGuiRotations) {
        ItemEntry entry = lookupItem(itemRef);
        if (entry == null) {
            return Optional.empty();
        }
        ResolvedModels resolved = resolveModels(itemRef, entry, data, damage);
        if (!resolved.elements()) {
            return Optional.of(new PackItemVisual.Sprite(
                composeSpriteLayers(itemRef, resolved.models(), data, textureCache::get)));
        }
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(
            buildModelInstances(itemRef, resolved, data), pixelsPerGuiPx, entry.oversizedInGui(), fullGuiRotations,
            this::loadElementTexture, "item `" + itemRef + "` in pack `" + id + "`");
        return Optional.of(new PackItemVisual.ElementsRaster(
            raster.image(), raster.offsetX(), raster.offsetY(), entry.oversizedInGui()));
    }

    /**
     * Resolves an item ref across its own animation timeline: the item's model dispatch and
     * chains resolve exactly like {@link #resolveItemVisual(String, CustomModelData, ItemDamage,
     * int, boolean)}, every texture the resolved visual uses is probed for an animation mcmeta,
     * and each timeline step renders the same visual with each animated texture showing its
     * frame at that step. Detection covers every DECLARED element face - a never-visible
     * animated face still marks the item animated (its frames simply render identically).
     *
     * @return empty when the ref is bare, foreign or unknown, or when NO texture of the
     *     resolved visual is animated - callers render the static visual instead
     * @throws PackResolveException when the item exists but cannot be rendered (the exact
     *                              failure modes of the static resolution)
     */
    public Optional<PackAnimatedVisual> resolveItemVisualAnimation(String itemRef, CustomModelData data,
                                                                   @Nullable ItemDamage damage, int pixelsPerGuiPx,
                                                                   boolean fullGuiRotations) {
        ItemEntry entry = lookupItem(itemRef);
        if (entry == null) {
            return Optional.empty();
        }
        ResolvedModels resolved = resolveModels(itemRef, entry, data, damage);
        String context = "item `" + itemRef + "` in pack `" + id + "`";
        Map<String, PackAnimation> animated = collectAnimatedTextures(itemRef, resolved, context);
        if (animated.isEmpty()) {
            return Optional.empty();
        }
        AnimationTimeline timeline = AnimationTimeline.of(
            animated.values().stream().map(PackAnimation::frameTicks).toList());
        List<String> animatedPaths = List.copyOf(animated.keySet());
        List<ElementModelRenderer.ModelInstance> instances =
            resolved.elements() ? buildModelInstances(itemRef, resolved, data) : null;
        List<PackItemVisual> steps = new ArrayList<>(timeline.steps().size());
        for (AnimationTimeline.Step step : timeline.steps()) {
            Map<String, BufferedImage> framesByPath = new HashMap<>();
            for (int sourceIndex = 0; sourceIndex < animatedPaths.size(); sourceIndex++) {
                String path = animatedPaths.get(sourceIndex);
                framesByPath.put(path, animated.get(path).frameImage(step.framePositions().get(sourceIndex)));
            }
            Function<String, BufferedImage> lookup =
                path -> framesByPath.containsKey(path) ? framesByPath.get(path) : textureCache.get(path);
            if (!resolved.elements()) {
                steps.add(new PackItemVisual.Sprite(
                    composeSpriteLayers(itemRef, resolved.models(), data, lookup)));
            } else {
                ElementModelRenderer.Raster raster = ElementModelRenderer.render(
                    instances, pixelsPerGuiPx, entry.oversizedInGui(), fullGuiRotations,
                    textureRef -> lookup.apply(parseRefOrResolveError(textureRef, "minecraft").texturePath()),
                    context);
                steps.add(new PackItemVisual.ElementsRaster(
                    raster.image(), raster.offsetX(), raster.offsetY(), entry.oversizedInGui()));
            }
        }
        return Optional.of(new PackAnimatedVisual(steps, timeline.stepTicks()));
    }

    /**
     * The animated textures a resolved visual can draw, keyed by texture path in deterministic
     * first-use order: flat visuals enumerate their generated layer paths, elements visuals
     * every declared face's resolved reference (unresolvable references are skipped here - the
     * render itself fails loudly if such a face actually paints).
     */
    private Map<String, PackAnimation> collectAnimatedTextures(String itemRef, ResolvedModels resolved,
                                                               String context) {
        Map<String, PackAnimation> animated = new LinkedHashMap<>();
        Set<String> probed = new HashSet<>();
        if (!resolved.elements()) {
            for (GuiModelResolver.GuiModel model : resolved.models()) {
                for (String path : resolveGeneratedLayerPaths(model.modelRef())) {
                    probeAnimation(animated, probed, path);
                }
            }
            return animated;
        }
        for (ChainData chain : resolved.chains()) {
            for (ModelElement element : chain.elements()) {
                for (ModelElement.Face face : element.faces().values()) {
                    String textureRef;
                    try {
                        textureRef = ElementModelRenderer.resolveTextureRef(
                            face.textureRef(), chain.textures(), context);
                    } catch (PackResolveException e) {
                        continue;
                    }
                    probeAnimation(animated, probed, parseRefOrResolveError(textureRef, "minecraft").texturePath());
                }
            }
        }
        return animated;
    }

    /** Probes one texture path for an animation, at most once per path. */
    private void probeAnimation(Map<String, PackAnimation> animated, Set<String> probed, String texturePath) {
        if (!probed.add(texturePath)) {
            return;
        }
        textureAnimation(texturePath, false).ifPresent(animation -> animated.put(texturePath, animation));
    }

    /**
     * The dispatch-resolved models of an item with their chains walked, plus whether the item
     * renders through the elements pipeline. A composite mixing elements and flat layer0 models
     * fails loudly here - shared by the static and animated resolutions so the two can never
     * disagree.
     */
    private record ResolvedModels(List<GuiModelResolver.GuiModel> models, List<ChainData> chains,
                                  boolean elements) {
    }

    private ResolvedModels resolveModels(String itemRef, ItemEntry entry, CustomModelData data,
                                         @Nullable ItemDamage damage) {
        List<GuiModelResolver.GuiModel> models = resolveGuiModels(itemRef, entry, data, damage);
        List<ChainData> chains = new ArrayList<>(models.size());
        boolean anyElements = false;
        boolean allElements = !models.isEmpty();
        for (GuiModelResolver.GuiModel model : models) {
            ChainData chain = resolveModelChain(model.modelRef());
            chains.add(chain);
            if (chain.elements() != null) {
                anyElements = true;
            } else {
                allElements = false;
            }
        }
        if (anyElements && !allElements) {
            throw new PackResolveException(
                "Item `%s` in pack `%s` mixes elements models and flat layer0 models in one composite; unsupported",
                itemRef, id.toString());
        }
        return new ResolvedModels(List.copyOf(models), List.copyOf(chains), anyElements);
    }

    /** The renderer inputs of an elements-resolved item; tints evaluate against {@code data}. */
    private List<ElementModelRenderer.ModelInstance> buildModelInstances(String itemRef, ResolvedModels resolved,
                                                                         CustomModelData data) {
        List<ElementModelRenderer.ModelInstance> instances = new ArrayList<>(resolved.models().size());
        for (int i = 0; i < resolved.models().size(); i++) {
            ChainData chain = resolved.chains().get(i);
            instances.add(new ElementModelRenderer.ModelInstance(
                chain.elements(), chain.textures(),
                chain.guiTransform() != null ? chain.guiTransform() : GuiTransform.IDENTITY,
                evaluateElementTints(itemRef, resolved.models().get(i).tints(), data),
                chain.guiLight()));
        }
        return instances;
    }

    /**
     * Shared item lookup guards: null when the ref is bare, malformed, foreign or unknown
     * (callers return empty and fall back to vanilla); loud when the item exists but its
     * definition JSON failed to parse at register time.
     */
    private ItemEntry lookupItem(String itemRef) {
        if (itemRef == null || itemRef.indexOf(':') < 0) {
            return null;
        }
        ResourceRef ref;
        try {
            ref = ResourceRef.parse(itemRef, null);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!namespaces.contains(ref.namespace())) {
            return null;
        }
        ItemEntry entry = items.get(ref.toString());
        if (entry == null) {
            return null;
        }
        if (entry.error() != null) {
            throw new PackResolveException("Item `%s` in pack `%s` is malformed: %s",
                itemRef, id.toString(), entry.error());
        }
        return entry;
    }

    /**
     * Resolves the item's model node tree against the supplied data and damage state, prefixing
     * dispatch errors (unsupported node types, select/range_dispatch dead ends) with the item
     * and pack: {@link GuiModelResolver} is item-agnostic, and resolve-time failures must name
     * their offender like every other resolve error this class raises.
     */
    private List<GuiModelResolver.GuiModel> resolveGuiModels(String itemRef, ItemEntry entry, CustomModelData data,
                                                             @Nullable ItemDamage damage) {
        try {
            return GuiModelResolver.resolveGui(entry.node(), data, damage);
        } catch (PackResolveException e) {
            throw withItemContext(itemRef, e);
        }
    }

    /**
     * Evaluates an elements layer's tint sources strictly, prefixing unsupported-source errors
     * with the item and pack; see {@link #resolveGuiModels}.
     */
    private List<Integer> evaluateElementTints(String itemRef, List<ItemModelNode.TintSpec> tints,
                                               CustomModelData data) {
        try {
            return GuiModelResolver.evaluateTints(tints, data);
        } catch (PackResolveException e) {
            throw withItemContext(itemRef, e);
        }
    }

    /** Wraps an item-agnostic resolve error with the item ref and pack id it belongs to. */
    private PackResolveException withItemContext(String itemRef, PackResolveException cause) {
        return new PackResolveException(GeneratorException.formatMessage(
            "Item `%s` in pack `%s`: %s", itemRef, id.toString(), cause.getMessage()), cause);
    }

    /**
     * The classic generated-layer composition plus vanilla {@code item/generated} tinting: the
     * client bakes layer {@code i} with tintindex {@code i}, so each model's {@code layer0}
     * through {@code layer4} textures stack bottom-to-top with tint {@code i} multiplying layer
     * {@code i} (tints past the declared list are white, a no-op - real packs commonly tint
     * layer0 and leave an overlay layer1 untinted, the vanilla dyed-ball shape). Unsupported
     * tint sources warn and stay untinted (vanilla-style dye/potion items rendered fine before
     * tints were parsed; see {@link GuiModelResolver#evaluateTintsLenient}). Byte-identical to
     * the pre-elements sprite path for single-layer untinted models.
     *
     * <p>{@code textures} maps a layer's texture path to its image: the texture cache for
     * static renders, a per-step frame lookup for animated ones. Layer images are never
     * mutated (the first layer is copied, tints copy too), so shared frame instances are safe.
     */
    private BufferedImage composeSpriteLayers(String itemRef, List<GuiModelResolver.GuiModel> models,
                                              CustomModelData data, Function<String, BufferedImage> textures) {
        BufferedImage sprite = null;
        for (GuiModelResolver.GuiModel model : models) {
            List<Integer> tints = GuiModelResolver.evaluateTintsLenient(model.tints(), data);
            List<String> layerPaths = resolveGeneratedLayerPaths(model.modelRef());
            for (int index = 0; index < layerPaths.size(); index++) {
                BufferedImage layer = textures.apply(layerPaths.get(index));
                int tint = index < tints.size() ? tints.get(index) : GuiModelResolver.WHITE;
                if (tint != GuiModelResolver.WHITE) {
                    layer = tintedCopy(layer, tint);
                }
                sprite = sprite == null ? copy(layer) : stack(sprite, layer);
            }
        }
        if (models.isEmpty() || sprite == null) {
            // An empty composite (or any resolution path that yields no layers) must fail loudly
            // rather than silently falling back to vanilla: the item DOES exist in the pack, it is
            // just broken, and that distinction matters to callers.
            throw new PackResolveException("Item `%s` in pack `%s` resolved to zero renderable layers",
                itemRef, id.toString());
        }
        return sprite;
    }

    /**
     * The texture paths of a model chain's generated flat layers: {@code layer0} up to
     * {@code layer4} (the {@link #MAX_GENERATED_LAYERS vanilla ItemModelGenerator cap}) from the
     * merged chain texture map (child entries win per key, exactly like
     * {@link #resolveModelChain}), stopping at the first missing index like vanilla. A
     * {@code layerN} value of the form {@code #key} follows the merged map exactly like element
     * face references (vanilla resolves references for generated layers too); an indirection to
     * an undefined key fails loudly. A chain with no layer0 at all raises the same per-shape
     * errors as {@link #resolveLayer0} - a vanilla-item-template end, an outside-pack end, a
     * parentless model and a broken parent ref each keep their dedicated message.
     */
    private List<String> resolveGeneratedLayerPaths(String modelRefValue) {
        Map<String, String> textures = resolveModelChain(modelRefValue).textures();
        List<String> paths = new ArrayList<>(1);
        String context = "model `" + modelRefValue + "` in pack `" + id + "`";
        for (int index = 0; index < MAX_GENERATED_LAYERS; index++) {
            String layerRef = textures.get("layer" + index);
            if (layerRef == null) {
                break;
            }
            // The renderer's #-chain resolver, shared so the generated-layer and element-face
            // texture paths can never disagree on reference semantics.
            String resolved = ElementModelRenderer.resolveTextureRef(layerRef, textures, context);
            paths.add(parseRefOrResolveError(resolved, "minecraft").texturePath());
        }
        if (paths.isEmpty()) {
            // resolveLayer0 walks the same chain and throws the precise no-layer0 error for
            // every termination shape; the merged map lacking layer0 guarantees it cannot
            // return a path (both read the models' own texture maps, child-most first).
            resolveLayer0(modelRefValue);
            throw new PackResolveException("Model `%s` in pack `%s` has no layer0 texture",
                modelRefValue, id.toString());
        }
        return paths;
    }

    /**
     * A copy of {@code source} with every pixel's color channels multiplied by the tint, alpha
     * kept - the same multiplicative convention as element face tinting (the cached source
     * instance is shared and must never be mutated).
     */
    private static BufferedImage tintedCopy(BufferedImage source, int tint) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                target.setRGB(x, y, ElementModelRenderer.tinted(source.getRGB(x, y), tint));
            }
        }
        return target;
    }

    /**
     * Everything an elements render needs from one model's parent chain, merged with child-most
     * precedence: the first elements list, the first {@code display.gui} entry, the first
     * {@code gui_light} (resolved to its effective mode at the chain end) and the texture map
     * with child entries winning per key.
     *
     * <p>{@code elements} is null when no model in the reachable chain declares any - the item
     * then composes through the classic layer0 path.
     */
    private record ChainData(@Nullable List<ModelElement> elements, Map<String, String> textures,
                             @Nullable GuiTransform guiTransform, ElementModelRenderer.GuiLight guiLight) {
    }

    /**
     * The effective {@code gui_light} of a resolved chain: the first declared value wins;
     * absent values default to {@code side} like vanilla, except chains ending at the builtin
     * flat templates, which declare {@code front} in the vanilla assets.
     */
    private static ElementModelRenderer.GuiLight guiLightOf(@Nullable String declared, boolean builtinFlatEnd) {
        if (declared != null) {
            return "front".equals(declared) ? ElementModelRenderer.GuiLight.FRONT : ElementModelRenderer.GuiLight.SIDE;
        }
        return builtinFlatEnd ? ElementModelRenderer.GuiLight.FRONT : ElementModelRenderer.GuiLight.SIDE;
    }

    /**
     * Whether an unresolved model reference is a concrete vanilla item template - a
     * {@code minecraft:item/*} model the pack does not ship. Every vanilla item model is itself a
     * flat generated/handheld-family model whose own layer0 is the identically named texture, so a
     * chain reaching one terminates with flat-sprite semantics rather than failing: a child that
     * parents {@code minecraft:item/wooden_pickaxe} and supplies its own layer0 renders exactly
     * like a child of {@code item/generated}. Block-model parents ({@code minecraft:block/*}) and
     * every non-{@code minecraft} namespace still fail loud - they were meant to supply real
     * geometry or live in an unavailable namespace.
     */
    private static boolean isVanillaItemTerminal(ResourceRef ref) {
        return "minecraft".equals(ref.namespace()) && ref.path().startsWith(VANILLA_ITEM_PATH_PREFIX);
    }

    /**
     * Contributes a vanilla item template's own {@code layer0} to the merged texture map when the
     * chain reaches the template without a child-supplied layer0. A vanilla item model declares
     * {@code layer0} as the identically named texture ({@code minecraft:item/<name>}); it is added
     * only when the pack actually ships that texture (real packs override the vanilla textures they
     * reskin). An absent texture is warn-skipped so the layer simply drops rather than exploding
     * the later decode - the bundled-vanilla-atlas fallback the client would consult is not shipped
     * here. A child that already declared {@code layer0} keeps it (child-most wins), so this never
     * overrides pack art.
     */
    private void contributeVanillaTerminalLayer0(ResourceRef terminal, Map<String, String> textures) {
        if (textures.containsKey("layer0")) {
            return;
        }
        if (source.exists(terminal.texturePath())) {
            textures.put("layer0", terminal.toString());
        } else {
            log.warn("Pack {}: vanilla item template `{}` supplies layer0 `{}`, but the pack ships no such "
                + "texture; skipping the layer", id, terminal, terminal.texturePath());
        }
    }

    /**
     * Walks a model's parent chain. Chains stop silently at the first parent OUTSIDE the pack
     * (vanilla model assets are unavailable; its textures and transforms are treated as absent,
     * documented behavior) and at any {@link #isVanillaItemTerminal vanilla item template} the
     * pack does not ship even when it claims the {@code minecraft} namespace - real packs end
     * chains at {@code item/generated}, {@code item/handheld} or a concrete vanilla item model
     * (e.g. {@code item/wooden_pickaxe}) without shipping those templates. A missing IN-pack
     * model of any other name (a {@code minecraft:block/*} template included), or a chain
     * exceeding {@link #MAX_PARENT_DEPTH} (cycles included), fails loudly with the same errors
     * {@link #resolveLayer0} raises for the identical breakage - a broken parent ref must never
     * silently drop the transforms or textures it was meant to supply.
     */
    private ChainData resolveModelChain(String modelRefValue) {
        ResourceRef modelRef = parseRefOrResolveError(modelRefValue, "minecraft");
        List<ModelElement> elements = null;
        GuiTransform guiTransform = null;
        String guiLight = null;
        Map<String, String> textures = new HashMap<>();
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            if (!namespaces.contains(modelRef.namespace())) {
                // Vanilla model assets are unavailable; stop and use what the pack declares.
                return new ChainData(elements, Map.copyOf(textures), guiTransform,
                    guiLightOf(guiLight, false));
            }
            ModelInfo model = models.get(modelRef.toString());
            if (model == null) {
                if (isVanillaItemTerminal(modelRef)) {
                    // A concrete vanilla item template the pack does not ship (item/generated,
                    // item/handheld, or a named item like item/wooden_pickaxe): every vanilla
                    // item model is a flat generated-family model, so ending here is the ordinary
                    // flat-item shape, not a broken pack. The template's own layer0 is the
                    // identically named texture; contribute it when no child overrode it (see
                    // the helper). The vanilla templates declare gui_light front, so an undeclared
                    // chain inherits it here.
                    contributeVanillaTerminalLayer0(modelRef, textures);
                    return new ChainData(elements, Map.copyOf(textures), guiTransform,
                        guiLightOf(guiLight, true));
                }
                throw new PackResolveException("Model `%s` not found in pack `%s`",
                    modelRef.toString(), id.toString());
            }
            if (elements == null && model.elements() != null) {
                elements = model.elements();
            }
            model.textures().forEach(textures::putIfAbsent);
            if (guiTransform == null && model.guiTransform() != null) {
                guiTransform = model.guiTransform();
            }
            if (guiLight == null && model.guiLight() != null) {
                guiLight = model.guiLight();
            }
            if (model.parentRef() == null) {
                return new ChainData(elements, Map.copyOf(textures), guiTransform,
                    guiLightOf(guiLight, false));
            }
            modelRef = parseRefOrResolveError(model.parentRef(), "minecraft");
        }
        throw new PackResolveException("Model parent chain exceeds depth %s in pack `%s`",
            String.valueOf(MAX_PARENT_DEPTH), id.toString());
    }

    /**
     * Loads an element face texture (already resolved to a concrete reference) under the strict
     * item cap. The renderer only reads the returned image, so the cached instance is shared.
     * A bare reference defaults to the {@code minecraft} namespace - the vanilla resource
     * location rule, shared with {@link #resolveLayer0} so both texture paths agree.
     */
    private BufferedImage loadElementTexture(String textureRef) {
        return textureCache.get(parseRefOrResolveError(textureRef, "minecraft").texturePath());
    }

    /**
     * Walks a model's parent chain to the first {@code layer0} texture path. A bare (namespace
     * free) {@code layer0} reference defaults to the {@code minecraft} namespace - the vanilla
     * resource location rule - exactly like element face texture references resolved through
     * {@link #loadElementTexture}, so the two texture paths can never disagree on where a bare
     * reference points.
     *
     * <p>Reaching a {@link #isVanillaItemTerminal vanilla item template} that is not in the pack
     * ends the chain like {@link #resolveModelChain}: those templates declare no layer0 of their
     * own beyond the identically named texture, so arriving here without one found earlier (and
     * without the pack shipping that texture, which {@link #resolveModelChain} would have
     * contributed) means the chain has no texture at all - a loud failure with a dedicated message
     * rather than a missing-model error.
     */
    private String resolveLayer0(String modelRefValue) {
        ResourceRef modelRef = parseRefOrResolveError(modelRefValue, "minecraft");
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            ModelInfo model = namespaces.contains(modelRef.namespace()) ? models.get(modelRef.toString()) : null;
            if (model == null) {
                if (!namespaces.contains(modelRef.namespace())) {
                    throw new PackResolveException(
                        "Model `%s` in pack `%s` resolves outside the pack; cannot render without vanilla model assets",
                        modelRef.toString(), id.toString());
                }
                if (isVanillaItemTerminal(modelRef)) {
                    throw new PackResolveException(
                        "Model chain ends at vanilla item template `%s` without any layer0 texture in pack `%s`",
                        modelRef.toString(), id.toString());
                }
                throw new PackResolveException("Model `%s` not found in pack `%s`", modelRef.toString(), id.toString());
            }
            if (model.layer0Ref() != null) {
                return parseRefOrResolveError(model.layer0Ref(), "minecraft").texturePath();
            }
            if (model.parentRef() == null) {
                throw new PackResolveException("Model `%s` in pack `%s` has neither layer0 nor parent",
                    modelRef.toString(), id.toString());
            }
            modelRef = parseRefOrResolveError(model.parentRef(), "minecraft");
        }
        throw new PackResolveException("Model parent chain exceeds depth %s in pack `%s`",
            String.valueOf(MAX_PARENT_DEPTH), id.toString());
    }

    /**
     * Parses a model/texture reference coming from untrusted pack JSON, converting parse failures
     * into the {@link PackResolveException} contract of {@link #resolveSprite(String)}.
     */
    private ResourceRef parseRefOrResolveError(String value, String defaultNamespace) {
        try {
            return ResourceRef.parse(value, defaultNamespace);
        } catch (IllegalArgumentException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Malformed resource reference `%s` in pack `%s`", value, id.toString()), e);
        }
    }

    /**
     * Cache loader; {@code cacheKey} is the texture path, optionally prefixed with
     * {@link #FULL_SHEET_KEY_PREFIX} (skip the animation first-frame crop) and/or
     * {@link #SHEET_CAPPED_KEY_PREFIX}. The decode cap is selected by the PREFIX - i.e. by how
     * the call site USES the texture - never by the path itself: tooltip sprite sheets
     * legitimately exceed the item cap (real packs ship animated frame strips like 146x2482),
     * but an item model referencing a texture stored under the tooltip sprite path must still
     * fail at the strict item cap, or the image-bomb guard would be bypassable by path choice.
     */
    private BufferedImage loadTexture(String cacheKey) {
        boolean fullSheet = cacheKey.startsWith(FULL_SHEET_KEY_PREFIX);
        String cappedKey = fullSheet ? cacheKey.substring(FULL_SHEET_KEY_PREFIX.length()) : cacheKey;
        boolean sheetCapped = cappedKey.startsWith(SHEET_CAPPED_KEY_PREFIX);
        String texturePath = sheetCapped ? cappedKey.substring(SHEET_CAPPED_KEY_PREFIX.length()) : cappedKey;
        int maxDim = sheetCapped ? limits.sheetTextureMaxDim() : limits.maxTextureDim();
        BufferedImage image;
        try {
            image = TextureDecoder.decode(source.read(texturePath), maxDim, normalizeEmissiveAlpha);
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Texture `%s` in pack `%s` failed to load: %s", texturePath, id.toString(), e.getMessage()), e);
        }
        if (fullSheet) {
            // The animated paths crop frames themselves; hand back the whole flipbook.
            return image;
        }
        String mcmetaPath = texturePath + ".mcmeta";
        if (source.exists(mcmetaPath)) {
            try {
                // The static crop uses the LENIENT first-frame parse, not the strict full-animation
                // parse: a malformed animation section (e.g. frametime 0, a non-array frames value)
                // still yields the first-frame crop it did before full-model parsing, keeping the
                // flag-off output byte-identical. The animated resolution path parses the same
                // mcmeta strictly and warns-and-falls-back on the same malformations.
                AnimationMeta firstFrameMeta = PackJsonParser.parseFirstFrameMeta(source.read(mcmetaPath));
                if (firstFrameMeta != null) {
                    return TextureDecoder.firstFrame(image, firstFrameMeta);
                }
            } catch (PackLoadException e) {
                log.warn("Pack {}: ignoring malformed mcmeta for {}: {}", id, texturePath, e.getMessage());
            }
        }
        return image;
    }

    /**
     * An exact per-pixel copy: {@link AlphaComposite#Src}, aligned with the decode path in
     * {@link TextureDecoder}, so defensively copied cache instances keep their exact translucent
     * channel values instead of drifting by one through SrcOver premultiplication.
     */
    private static BufferedImage copy(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Alpha-blends {@code layer} over {@code base} (scaled to the base's size), mutating and
     * returning {@code base}. Deliberately the DEFAULT SrcOver compositing - unlike
     * {@link #copy}, stacking composite layers is real alpha blending, exactly how the vanilla
     * client bakes {@code item/generated} layers over one another.
     */
    private static BufferedImage stack(BufferedImage base, BufferedImage layer) {
        Graphics2D graphics = base.createGraphics();
        try {
            graphics.drawImage(layer, 0, 0, base.getWidth(), base.getHeight(), null);
        } finally {
            graphics.dispose();
        }
        return base;
    }
}
