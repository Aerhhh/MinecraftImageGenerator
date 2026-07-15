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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
    private static final int MAX_PARENT_DEPTH = 8;
    /**
     * Pack ids whose textures use alpha 252 as an "opaque full-bright" emissive marker (a
     * Hypixel SkyBlock shader convention). Only these packs get the alpha normalized to fully
     * opaque at decode time; other packs may ship legitimate alpha-252 pixels that must be
     * preserved. Add a pack id here if another pack is confirmed to use the same convention.
     */
    private static final Set<String> EMISSIVE_ALPHA_PACK_IDS = Set.of("hypixel:skyblock");

    private record ItemEntry(ItemModelNode node, String error) {
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
        // fontTextureMaxDim, not maxTextureDim) can dwarf item textures. Bound and soft-reference
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
                    items.put(key, new ItemEntry(PackJsonParser.parseItemDefinition(source.read(path)), null));
                } catch (RuntimeException e) {
                    errors++;
                    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                    items.put(key, new ItemEntry(null, message));
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
        if (items.isEmpty() && !hasTooltipSprites && fontPaths.isEmpty()) {
            throw new PackLoadException(
                "Pack %s contains no item definitions under assets/*/items/, no tooltip sprites under assets/*/textures/gui/sprites/tooltip/, and no fonts under assets/*/font/",
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
     * <p>Font sheet decodes are bounded by {@link PackLimits#fontTextureMaxDim()}, NOT
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
     * font cap; see {@link #resolveFont(String)} for why this bypasses the item texture cache.
     */
    private BufferedImage loadFontSheet(String textureFileRef) {
        ResourceRef ref = parseRefOrResolveError(textureFileRef, "minecraft");
        String path = "assets/" + ref.namespace() + "/textures/" + ref.path();
        try {
            return TextureDecoder.decode(source.read(path), limits.fontTextureMaxDim(), normalizeEmissiveAlpha);
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Font texture `%s` in pack `%s` failed to load: %s", path, id.toString(), e.getMessage()), e);
        }
    }

    private GuiSprite loadGuiSprite(String texturePath) {
        // Defensive copy: the cache instance is shared and GuiSprite hands the image to callers.
        return new GuiSprite(copy(textureCache.get(texturePath)), guiScalingFor(texturePath));
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
     * {@code hypixel_skyblock:item/jacob/cactus_knife}) to a flat GUI sprite.
     *
     * @return empty when the ref is bare, in a foreign namespace, or unknown - callers fall back
     *     to vanilla; a present sprite otherwise
     * @throws PackResolveException when the item exists but cannot be rendered
     */
    public Optional<BufferedImage> resolveSprite(String itemRef) {
        if (itemRef == null || itemRef.indexOf(':') < 0) {
            return Optional.empty();
        }
        ResourceRef ref;
        try {
            ref = ResourceRef.parse(itemRef, null);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!namespaces.contains(ref.namespace())) {
            return Optional.empty();
        }
        ItemEntry entry = items.get(ref.toString());
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.error() != null) {
            throw new PackResolveException("Item `%s` in pack `%s` is malformed: %s",
                itemRef, id.toString(), entry.error());
        }
        List<String> modelRefs = GuiModelResolver.resolveGui(entry.node());
        BufferedImage sprite = null;
        for (String modelRef : modelRefs) {
            BufferedImage layer = textureCache.get(resolveLayer0(modelRef));
            sprite = sprite == null ? copy(layer) : stack(sprite, layer);
        }
        if (modelRefs.isEmpty() || sprite == null) {
            // An empty composite (or any resolution path that yields no layers) must fail loudly
            // rather than silently falling back to vanilla: the item DOES exist in the pack, it is
            // just broken, and that distinction matters to callers.
            throw new PackResolveException("Item `%s` in pack `%s` resolved to zero renderable layers",
                itemRef, id.toString());
        }
        return Optional.of(sprite);
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

    private BufferedImage loadTexture(String texturePath) {
        BufferedImage image;
        try {
            image = TextureDecoder.decode(source.read(texturePath), limits.maxTextureDim(), normalizeEmissiveAlpha);
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

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        drawOnto(target, source);
        return target;
    }

    private static BufferedImage stack(BufferedImage base, BufferedImage layer) {
        drawOnto(base, layer);
        return base;
    }

    private static void drawOnto(BufferedImage target, BufferedImage layer) {
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(layer, 0, 0, target.getWidth(), target.getHeight(), null);
        } finally {
            graphics.dispose();
        }
    }
}
