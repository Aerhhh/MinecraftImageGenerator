package net.aerh.jigsaw.core.resource;

import net.aerh.jigsaw.api.sprite.SpriteProvider;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A {@link SpriteProvider} that resolves item textures from a Minecraft {@link ResourcePack}
 * by following model JSON inheritance and loading the corresponding PNG textures.
 *
 * <p>Resolution order for a given item ID:
 * <ol>
 *   <li>Resolve the model via {@link ModelResolver} to obtain texture references.</li>
 *   <li>Collect layer textures: {@code layer0}, {@code layer1}, {@code layer2}, ... until
 *       no further key is found.</li>
 *   <li>If only one layer is present, return it directly. If multiple layers are found,
 *       composite them top-to-bottom using alpha blending.</li>
 *   <li>If no layer keys are found, try fallback keys: {@code all}, {@code front},
 *       {@code south}, {@code cross}, {@code particle}. Skip values that start with
 *       {@code #} (unresolved texture variables).</li>
 *   <li>Results are cached in a {@link ConcurrentHashMap} to avoid redundant I/O.</li>
 * </ol>
 */
public class ResourcePackSpriteProvider implements SpriteProvider {

    private static final String MODEL_PREFIX = "assets/minecraft/models/item/";
    private static final String TEXTURE_PREFIX = "assets/minecraft/textures/";
    private static final String MINECRAFT_NAMESPACE = "minecraft:";

    private static final List<String> FALLBACK_KEYS = List.of("all", "front", "south", "cross", "particle");

    private final ResourcePack pack;
    private final ModelResolver modelResolver;
    private final ConcurrentHashMap<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a {@link ResourcePackSpriteProvider} using the given pack and a default
     * {@link ModelResolver}.
     *
     * @param pack the resource pack to load models and textures from; must not be {@code null}
     */
    public ResourcePackSpriteProvider(ResourcePack pack) {
        this(pack, new ModelResolver());
    }

    /**
     * Creates a {@link ResourcePackSpriteProvider} with an explicit {@link ModelResolver}.
     *
     * @param pack          the resource pack to load models and textures from; must not be {@code null}
     * @param modelResolver the resolver to use for model JSON lookup; must not be {@code null}
     */
    public ResourcePackSpriteProvider(ResourcePack pack, ModelResolver modelResolver) {
        this.pack = Objects.requireNonNull(pack, "pack must not be null");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver must not be null");
    }

    /**
     * Returns the sprite for the given item ID (e.g. {@code "diamond_sword"} or
     * {@code "minecraft:diamond_sword"}), or empty if the item model or textures cannot be resolved.
     *
     * @param itemId the item identifier; must not be {@code null}
     * @return an {@link Optional} containing the resolved sprite image, or empty if not found
     */
    @Override
    public Optional<BufferedImage> getSprite(String itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        String normalizedId = stripNamespace(itemId);
        return cache.computeIfAbsent(normalizedId, this::resolve);
    }

    /**
     * Scans {@code assets/minecraft/models/item/*.json} in the pack and returns the item IDs
     * found there (filename without extension), excluding the {@code generated} meta-model.
     *
     * @return an unmodifiable collection of available item IDs
     */
    @Override
    public Collection<String> availableSprites() {
        Set<String> paths = pack.listResources(MODEL_PREFIX);
        return paths.stream()
                .filter(p -> p.endsWith(".json"))
                .map(p -> {
                    String filename = p.substring(p.lastIndexOf('/') + 1);
                    return filename.substring(0, filename.length() - ".json".length());
                })
                .filter(id -> !id.equals("generated"))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the first sprite whose item ID contains the given query string.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return an {@link Optional} containing the first matching sprite, or empty if none found
     */
    @Override
    public Optional<BufferedImage> search(String query) {
        Objects.requireNonNull(query, "query must not be null");
        return availableSprites().stream()
                .filter(id -> id.contains(query))
                .sorted()
                .findFirst()
                .flatMap(this::getSprite);
    }

    /**
     * Returns all sprites whose item ID contains the given query string (case-insensitive),
     * sorted alphabetically.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return an alphabetically ordered list of matching entries; never {@code null}
     */
    @Override
    public List<Map.Entry<String, BufferedImage>> searchAll(String query) {
        Objects.requireNonNull(query, "query must not be null");
        String lowerQuery = query.toLowerCase();
        return availableSprites().stream()
                .filter(id -> id.toLowerCase().contains(lowerQuery))
                .sorted()
                .map(id -> getSprite(id).map(img ->
                        (Map.Entry<String, BufferedImage>) new AbstractMap.SimpleImmutableEntry<>(id, img)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Returns a snapshot of every resolvable sprite, keyed by item ID, sorted alphabetically.
     *
     * @return an unmodifiable, alphabetically sorted map of all item IDs to their images
     */
    @Override
    public Map<String, BufferedImage> getAllSprites() {
        TreeMap<String, BufferedImage> result = new TreeMap<>();
        for (String id : availableSprites()) {
            getSprite(id).ifPresent(img -> result.put(id, img));
        }
        return Collections.unmodifiableMap(result);
    }

    // -------------------------------------------------------------------------
    // Internal resolution
    // -------------------------------------------------------------------------

    private Optional<BufferedImage> resolve(String itemId) {
        Optional<ItemModelData> modelDataOpt = modelResolver.resolve(pack, itemId);
        if (modelDataOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, String> textures = modelDataOpt.get().textures();

        List<BufferedImage> layers = collectLayers(textures);
        if (!layers.isEmpty()) {
            return Optional.of(compositeLayers(layers));
        }

        return resolveFromFallbackKeys(textures);
    }

    /**
     * Collects layer images by iterating {@code layer0}, {@code layer1}, {@code layer2}, ...
     * until no more keys are present.
     */
    private List<BufferedImage> collectLayers(Map<String, String> textures) {
        List<BufferedImage> layers = new ArrayList<>();
        int index = 0;
        while (true) {
            String key = "layer" + index;
            if (!textures.containsKey(key)) {
                break;
            }
            String textureRef = textures.get(key);
            if (!textureRef.startsWith("#")) {
                loadTexture(textureRef).ifPresent(layers::add);
            }
            index++;
        }
        return layers;
    }

    /**
     * Tries each fallback key in order, returning the first successfully loaded texture.
     */
    private Optional<BufferedImage> resolveFromFallbackKeys(Map<String, String> textures) {
        for (String key : FALLBACK_KEYS) {
            String textureRef = textures.get(key);
            if (textureRef == null || textureRef.startsWith("#")) {
                continue;
            }
            Optional<BufferedImage> img = loadTexture(textureRef);
            if (img.isPresent()) {
                return img;
            }
        }
        return Optional.empty();
    }

    /**
     * Loads a PNG texture from the pack given a texture reference such as
     * {@code "minecraft:item/diamond_sword"} or {@code "item/diamond_sword"}.
     */
    private Optional<BufferedImage> loadTexture(String textureRef) {
        String path = TEXTURE_PREFIX + stripNamespace(textureRef) + ".png";
        Optional<InputStream> streamOpt = pack.getResource(path);
        if (streamOpt.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = streamOpt.get()) {
            BufferedImage img = ImageIO.read(stream);
            return Optional.ofNullable(img);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Composites a list of layer images by drawing them in order (layer0 first, then each
     * subsequent layer on top using {@link AlphaComposite#SRC_OVER}).
     *
     * <p>The output canvas matches the dimensions of the first layer. If there is only one
     * layer, it is returned directly without allocating a new image.
     */
    private static BufferedImage compositeLayers(List<BufferedImage> layers) {
        if (layers.size() == 1) {
            return layers.get(0);
        }
        BufferedImage base = layers.get(0);
        BufferedImage result = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            for (BufferedImage layer : layers) {
                g.drawImage(layer, 0, 0, null);
            }
        } finally {
            g.dispose();
        }
        return result;
    }

    /**
     * Strips the {@code minecraft:} namespace prefix from a texture reference if present.
     */
    private static String stripNamespace(String reference) {
        if (reference.startsWith(MINECRAFT_NAMESPACE)) {
            return reference.substring(MINECRAFT_NAMESPACE.length());
        }
        return reference;
    }
}
