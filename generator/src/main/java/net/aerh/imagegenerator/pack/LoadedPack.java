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
import net.aerh.imagegenerator.pack.font.PackFont;
import org.jetbrains.annotations.Nullable;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final int MAX_PARENT_DEPTH = 8;
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

    LoadedPack(PackId id, PackSource source, PackLimits limits) {
        this.id = id;
        this.source = source;
        this.limits = limits;
        this.normalizeEmissiveAlpha = EMISSIVE_ALPHA_PACK_IDS.contains(id.toString());
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
     * Resolves a font id (e.g. {@code minecraft:default}, {@code mcc:chest_backgrounds}; a bare
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
     * @return empty when the pack defines no such font - callers fall back to built-in fonts
     * @throws IllegalArgumentException when the font id itself is malformed (caller input)
     * @throws PackResolveException     when the font exists but is broken (malformed JSON,
     *                                  missing or cyclic reference, missing or oversized sheet)
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
        return PackFont.create(key, resolved, this::loadFontSheet, fontProviderCache);
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
        ResourceRef ref = parseRefOrResolveError(textureFileRef, "minecraft");
        String path = "assets/" + ref.namespace() + "/textures/" + ref.path();
        try {
            return TextureDecoder.decode(source.read(path), limits.sheetTextureMaxDim(), normalizeEmissiveAlpha);
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Font texture `%s` in pack `%s` failed to load: %s", path, id.toString(), e.getMessage()), e);
        }
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
     * layer0 path only, evaluated with no custom model data. Items whose models are
     * elements-based resolve through {@link #resolveItemVisual(String, CustomModelData, int)}
     * instead. Supported tint sources (constant, custom_model_data defaults) color the sprite
     * like the vanilla client; unsupported sources warn and stay untinted.
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
            resolveGuiModels(itemRef, entry, CustomModelData.EMPTY), CustomModelData.EMPTY));
    }

    /**
     * Resolves an item reference to its GUI visual, evaluating {@code custom_model_data}
     * dispatch nodes and tint sources against {@code data}:
     *
     * <ul>
     * <li>Models whose resolved chains carry no elements compose exactly like
     *     {@link #resolveSprite(String)} and return a {@link PackItemVisual.Sprite} at native
     *     texture resolution (callers scale as before). Supported tint sources multiply the
     *     layer like vanilla's {@code item/generated} tintindex 0 quad; unsupported sources
     *     warn and stay untinted so vanilla-style dye/potion items keep rendering.</li>
     * <li>Models with elements rasterize through the flat front projection directly at
     *     {@code pixelsPerGuiPx} (no 16 px intermediate, so sub-GUI-px geometry survives) and
     *     return a {@link PackItemVisual.ElementsRaster}; the item's {@code oversized_in_gui}
     *     flag selects slot-box clipping versus full-extent output. Mixing elements models and
     *     flat layer0 models in one composite is unsupported and fails loudly.</li>
     * </ul>
     *
     * <p>Elements chains stop at parents outside the pack (vanilla model assets are
     * unavailable); their textures and display transforms are treated as absent. A missing
     * IN-pack parent, a cyclic chain, or a chain deeper than the layer0 path allows fails
     * loudly instead of silently dropping inherited transforms.
     *
     * @return empty when the ref is bare, in a foreign namespace, or unknown - callers fall back
     *     to vanilla
     * @throws PackResolveException when the item exists but cannot be rendered (broken
     *                              references, unsupported node types or tint sources, non-zero
     *                              element rotations, unsupported gui rotations)
     */
    public Optional<PackItemVisual> resolveItemVisual(String itemRef, CustomModelData data, int pixelsPerGuiPx) {
        ItemEntry entry = lookupItem(itemRef);
        if (entry == null) {
            return Optional.empty();
        }
        List<GuiModelResolver.GuiModel> models = resolveGuiModels(itemRef, entry, data);
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
        if (!anyElements) {
            return Optional.of(new PackItemVisual.Sprite(composeSpriteLayers(itemRef, models, data)));
        }
        if (!allElements) {
            throw new PackResolveException(
                "Item `%s` in pack `%s` mixes elements models and flat layer0 models in one composite; unsupported",
                itemRef, id.toString());
        }
        List<ElementModelRenderer.ModelInstance> instances = new ArrayList<>(models.size());
        for (int i = 0; i < models.size(); i++) {
            ChainData chain = chains.get(i);
            instances.add(new ElementModelRenderer.ModelInstance(
                chain.elements(), chain.textures(),
                chain.guiTransform() != null ? chain.guiTransform() : GuiTransform.IDENTITY,
                evaluateElementTints(itemRef, models.get(i).tints(), data)));
        }
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(
            instances, pixelsPerGuiPx, entry.oversizedInGui(), this::loadElementTexture,
            "item `" + itemRef + "` in pack `" + id + "`");
        return Optional.of(new PackItemVisual.ElementsRaster(
            raster.image(), raster.offsetX(), raster.offsetY(), entry.oversizedInGui()));
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
     * Resolves the item's model node tree against the supplied data, prefixing dispatch errors
     * (unsupported node types, select/range_dispatch dead ends) with the item and pack:
     * {@link GuiModelResolver} is item-agnostic, and resolve-time failures must name their
     * offender like every other resolve error this class raises.
     */
    private List<GuiModelResolver.GuiModel> resolveGuiModels(String itemRef, ItemEntry entry, CustomModelData data) {
        try {
            return GuiModelResolver.resolveGui(entry.node(), data);
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
     * The classic layer0 composition plus vanilla {@code item/generated} tinting: the client
     * bakes layer {@code i} with tintindex {@code i}, and only layer0 is supported here, so each
     * leaf's tint 0 multiplies its layer. Unsupported tint sources warn and stay untinted
     * (vanilla-style dye/potion items rendered fine before tints were parsed; see
     * {@link GuiModelResolver#evaluateTintsLenient}). Byte-identical to the pre-elements sprite
     * path when no tints are declared.
     */
    private BufferedImage composeSpriteLayers(String itemRef, List<GuiModelResolver.GuiModel> models,
                                              CustomModelData data) {
        BufferedImage sprite = null;
        for (GuiModelResolver.GuiModel model : models) {
            BufferedImage layer = textureCache.get(resolveLayer0(model.modelRef()));
            List<Integer> tints = GuiModelResolver.evaluateTintsLenient(model.tints(), data);
            int tint = tints.isEmpty() ? GuiModelResolver.WHITE : tints.get(0);
            if (tint != GuiModelResolver.WHITE) {
                layer = tintedCopy(layer, tint);
            }
            sprite = sprite == null ? copy(layer) : stack(sprite, layer);
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
     * precedence: the first elements list, the first {@code display.gui} entry, and the texture
     * map with child entries winning per key. The parsed root {@code gui_light} is deliberately
     * not threaded: the flat GUI projection never shades (see {@link ElementModelRenderer}).
     *
     * <p>{@code elements} is null when no model in the reachable chain declares any - the item
     * then composes through the classic layer0 path.
     */
    private record ChainData(@Nullable List<ModelElement> elements, Map<String, String> textures,
                             @Nullable GuiTransform guiTransform) {
    }

    /**
     * Walks a model's parent chain. Chains stop silently at the first parent OUTSIDE the pack
     * (vanilla model assets are unavailable; its textures and transforms are treated as absent,
     * documented behavior), but a missing IN-pack model or a chain exceeding
     * {@link #MAX_PARENT_DEPTH} (cycles included) fails loudly with the same errors
     * {@link #resolveLayer0} raises for the identical breakage - a broken parent ref must never
     * silently drop the transforms or textures it was meant to supply.
     */
    private ChainData resolveModelChain(String modelRefValue) {
        ResourceRef modelRef = parseRefOrResolveError(modelRefValue, "minecraft");
        List<ModelElement> elements = null;
        GuiTransform guiTransform = null;
        Map<String, String> textures = new HashMap<>();
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            if (!namespaces.contains(modelRef.namespace())) {
                // Vanilla model assets are unavailable; stop and use what the pack declares.
                return new ChainData(elements, Map.copyOf(textures), guiTransform);
            }
            ModelInfo model = models.get(modelRef.toString());
            if (model == null) {
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
            if (model.parentRef() == null) {
                return new ChainData(elements, Map.copyOf(textures), guiTransform);
            }
            modelRef = parseRefOrResolveError(model.parentRef(), "minecraft");
        }
        throw new PackResolveException("Model parent chain exceeds depth %s in pack `%s`",
            String.valueOf(MAX_PARENT_DEPTH), id.toString());
    }

    /**
     * Loads an element face texture (already resolved to a concrete reference) under the strict
     * item cap. The renderer only reads the returned image, so the cached instance is shared.
     */
    private BufferedImage loadElementTexture(String textureRef) {
        return textureCache.get(parseRefOrResolveError(textureRef, "minecraft").texturePath());
    }

    private String resolveLayer0(String modelRefValue) {
        ResourceRef modelRef = parseRefOrResolveError(modelRefValue, "minecraft");
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            if (!namespaces.contains(modelRef.namespace())) {
                throw new PackResolveException(
                    "Model `%s` in pack `%s` resolves outside the pack; cannot render without vanilla model assets",
                    modelRef.toString(), id.toString());
            }
            ModelInfo model = models.get(modelRef.toString());
            if (model == null) {
                throw new PackResolveException("Model `%s` not found in pack `%s`", modelRef.toString(), id.toString());
            }
            if (model.layer0Ref() != null) {
                return parseRefOrResolveError(model.layer0Ref(), modelRef.namespace()).texturePath();
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
     * {@link #SHEET_CAPPED_KEY_PREFIX}. The decode cap is selected by the PREFIX - i.e. by how
     * the call site USES the texture - never by the path itself: tooltip sprite sheets
     * legitimately exceed the item cap (real packs ship animated frame strips like 146x2482),
     * but an item model referencing a texture stored under the tooltip sprite path must still
     * fail at the strict item cap, or the image-bomb guard would be bypassable by path choice.
     */
    private BufferedImage loadTexture(String cacheKey) {
        boolean sheetCapped = cacheKey.startsWith(SHEET_CAPPED_KEY_PREFIX);
        String texturePath = sheetCapped ? cacheKey.substring(SHEET_CAPPED_KEY_PREFIX.length()) : cacheKey;
        int maxDim = sheetCapped ? limits.sheetTextureMaxDim() : limits.maxTextureDim();
        BufferedImage image;
        try {
            image = TextureDecoder.decode(source.read(texturePath), maxDim, normalizeEmissiveAlpha);
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Texture `%s` in pack `%s` failed to load: %s", texturePath, id.toString(), e.getMessage()), e);
        }
        String mcmetaPath = texturePath + ".mcmeta";
        if (source.exists(mcmetaPath)) {
            try {
                McMeta meta = PackJsonParser.parseMcmeta(source.read(mcmetaPath));
                if (meta.animation() != null) {
                    return TextureDecoder.firstFrame(image, meta.animation());
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
