package net.aerh.imagegenerator.pack;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.exception.PackResolveException;

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
 */
@Slf4j
final class LoadedPack {

    private static final Pattern ITEM_PATH = Pattern.compile("assets/([^/]+)/items/(.+)\\.json");
    private static final Pattern MODEL_PATH = Pattern.compile("assets/([^/]+)/models/(.+)\\.json");
    private static final int MAX_PARENT_DEPTH = 8;

    private record ItemEntry(ItemModelNode node, String error) {
    }

    private final PackId id;
    private final PackSource source;
    private final PackLimits limits;
    private final Set<String> namespaces = new HashSet<>();
    private final Map<String, ItemEntry> items = new HashMap<>();
    private final Map<String, ModelInfo> models = new HashMap<>();
    private final LoadingCache<String, BufferedImage> textureCache;

    LoadedPack(PackId id, PackSource source, PackLimits limits) {
        this.id = id;
        this.source = source;
        this.limits = limits;
        this.textureCache = Caffeine.newBuilder()
            .maximumWeight(limits.textureCacheMaxBytes())
            // long arithmetic: large custom maxTextureDim configs can overflow int and silently
            // underweight the cache, mirroring TextureDecoder's overflow-safe bounds pattern.
            .weigher((String key, BufferedImage image) ->
                (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) image.getWidth() * image.getHeight() * 4)))
            .softValues()
            .build(this::loadTexture);
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
            }
        }
        if (items.isEmpty()) {
            throw new PackLoadException("Pack %s contains no item definitions under assets/*/items/", id.toString());
        }
        if (errors > 0) {
            log.warn("Pack {}: indexed {} items and {} models with {} malformed entries",
                id, items.size(), models.size(), errors);
        } else {
            log.info("Pack {}: indexed {} items and {} models", id, items.size(), models.size());
        }
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
            image = TextureDecoder.decode(source.read(texturePath), limits.maxTextureDim());
        } catch (PackLoadException e) {
            throw new PackResolveException(GeneratorException.formatMessage(
                "Texture `%s` in pack `%s` failed to load: %s", texturePath, id.toString(), e.getMessage()), e);
        }
        String mcmetaPath = texturePath + ".mcmeta";
        if (source.exists(mcmetaPath)) {
            try {
                return TextureDecoder.firstFrame(image, PackJsonParser.parseAnimationMeta(source.read(mcmetaPath)));
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
