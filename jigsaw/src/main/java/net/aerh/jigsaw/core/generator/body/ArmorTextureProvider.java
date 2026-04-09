package net.aerh.jigsaw.core.generator.body;

import net.aerh.jigsaw.core.resource.ResourcePack;
import net.aerh.jigsaw.core.util.ColorUtil;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads Minecraft armor textures and creates {@link ArmorPiece} objects for use with the
 * body renderer.
 *
 * <p>Textures are resolved in this order:
 * <ol>
 *   <li>Custom {@link ResourcePack} (if configured)</li>
 *   <li>Bundled defaults on the classpath</li>
 * </ol>
 *
 * <p>Supports both the modern (1.21.4+) equipment format and the legacy format:
 *
 * <h3>Modern format (1.21.4+)</h3>
 * <p>Equipment definitions at {@code equipment/{material}.json} reference texture names
 * per layer type. Textures are stored at:
 * <pre>
 * textures/entity/equipment/humanoid/{name}.png       (helmet, chestplate, boots)
 * textures/entity/equipment/humanoid_leggings/{name}.png  (leggings)
 * </pre>
 *
 * <h3>Legacy format (pre-1.21.4)</h3>
 * <pre>
 * textures/models/armor/{material}_layer_1.png  (helmet, chestplate, boots)
 * textures/models/armor/{material}_layer_2.png  (leggings)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // With bundled defaults only
 * ArmorTextureProvider provider = ArmorTextureProvider.withDefaults();
 *
 * // With a custom resource pack (overrides bundled defaults)
 * ArmorTextureProvider provider = new ArmorTextureProvider(resourcePack);
 *
 * Optional<ArmorPiece> helmet = provider.piece(ArmorSlot.HELMET, "iron");
 * Optional<ArmorPiece> dyedChestplate = provider.piece(ArmorSlot.CHESTPLATE, "leather", 0xFFA06540);
 * }</pre>
 */
public final class ArmorTextureProvider {

    // Resource pack paths (with assets/minecraft/ prefix)
    private static final String RP_EQUIPMENT_PREFIX = "assets/minecraft/equipment/";
    private static final String RP_HUMANOID_PREFIX = "assets/minecraft/textures/entity/equipment/humanoid/";
    private static final String RP_HUMANOID_LEGGINGS_PREFIX = "assets/minecraft/textures/entity/equipment/humanoid_leggings/";
    private static final String RP_LEGACY_PREFIX = "assets/minecraft/textures/models/armor/";

    // Classpath paths (relative to classpath root, matching src/main/resources layout)
    private static final String CP_EQUIPMENT_PREFIX = "minecraft/assets/equipment/";
    private static final String CP_HUMANOID_PREFIX = "minecraft/assets/textures/entity/equipment/humanoid/";
    private static final String CP_HUMANOID_LEGGINGS_PREFIX = "minecraft/assets/textures/entity/equipment/humanoid_leggings/";

    private static final Pattern HUMANOID_TEXTURE_PATTERN = Pattern.compile(
            "\"humanoid\"\\s*:\\s*\\[\\s*\\{[^}]*\"texture\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LEGGINGS_TEXTURE_PATTERN = Pattern.compile(
            "\"humanoid_leggings\"\\s*:\\s*\\[\\s*\\{[^}]*\"texture\"\\s*:\\s*\"([^\"]+)\"");

    /** Default undyed leather color (from Minecraft's equipment/leather.json: color_when_undyed = -6265536). */
    private static final int DEFAULT_LEATHER_COLOR = 0xA06540;

    private final ResourcePack resourcePack;
    private final ConcurrentHashMap<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a new armor texture provider backed by the given resource pack.
     * The resource pack is checked first; bundled classpath defaults are used as fallback.
     *
     * @param resourcePack the resource pack to load armor textures from; must not be {@code null}
     */
    public ArmorTextureProvider(ResourcePack resourcePack) {
        this.resourcePack = java.util.Objects.requireNonNull(resourcePack, "resourcePack must not be null");
    }

    /**
     * Creates a new armor texture provider that only uses the bundled default textures
     * on the classpath.
     *
     * @return a provider backed by bundled defaults
     */
    public static ArmorTextureProvider withDefaults() {
        return new ArmorTextureProvider();
    }

    /** Internal constructor for classpath-only mode. */
    private ArmorTextureProvider() {
        this.resourcePack = null;
    }

    /**
     * Creates an {@link ArmorPiece} for the given slot and material.
     *
     * <p>For dyeable materials (e.g. leather), the default undyed color is applied and the
     * overlay texture is composited automatically.
     *
     * @param slot     the armor slot
     * @param material the armor material name (e.g. "iron", "diamond", "leather")
     * @return the armor piece, or empty if the texture was not found
     */
    public Optional<ArmorPiece> piece(ArmorSlot slot, String material) {
        java.util.Objects.requireNonNull(slot, "slot must not be null");
        java.util.Objects.requireNonNull(material, "material must not be null");

        int layer = layerFor(slot);
        Optional<BufferedImage> overlay = loadOverlayTexture(material, layer);
        if (overlay.isPresent()) {
            // Dyeable material - tint base with default color, composite overlay
            return loadTexture(material, layer)
                    .map(base -> compositeWithOverlay(base, overlay.get(), DEFAULT_LEATHER_COLOR))
                    .map(texture -> ArmorPiece.of(slot, texture));
        }

        return loadTexture(material, layer)
                .map(texture -> ArmorPiece.of(slot, texture));
    }

    /**
     * Creates a dyed {@link ArmorPiece} for the given slot and material.
     *
     * <p>For dyeable materials (e.g. leather), the dye color is applied to the base texture
     * and the overlay is composited on top. The returned {@link ArmorPiece} has the color
     * pre-baked into the texture, so no further tinting is needed at render time.
     *
     * @param slot     the armor slot
     * @param material the armor material name (e.g. "leather")
     * @param dyeColor the dye color in RGB format
     * @return the armor piece, or empty if the texture was not found
     */
    public Optional<ArmorPiece> piece(ArmorSlot slot, String material, int dyeColor) {
        java.util.Objects.requireNonNull(slot, "slot must not be null");
        java.util.Objects.requireNonNull(material, "material must not be null");

        int layer = layerFor(slot);
        Optional<BufferedImage> overlay = loadOverlayTexture(material, layer);
        if (overlay.isPresent()) {
            // Dyeable material - tint base with dye color, composite overlay, return without color
            return loadTexture(material, layer)
                    .map(base -> compositeWithOverlay(base, overlay.get(), dyeColor))
                    .map(texture -> ArmorPiece.of(slot, texture));
        }

        return loadTexture(material, layer)
                .map(texture -> ArmorPiece.dyed(slot, texture, dyeColor));
    }

    /**
     * Loads the raw armor texture image for a given material and layer number.
     *
     * @param material the armor material name
     * @param layer    the layer number (1 or 2)
     * @return the loaded texture, or empty if not found
     */
    public Optional<BufferedImage> loadTexture(String material, int layer) {
        java.util.Objects.requireNonNull(material, "material must not be null");
        if (layer != 1 && layer != 2) {
            throw new IllegalArgumentException("layer must be 1 or 2, got: " + layer);
        }

        String cacheKey = material + "/" + layer;
        return cache.computeIfAbsent(cacheKey, k -> resolveTexture(material, layer));
    }

    /**
     * Loads the overlay texture for a dyeable material (e.g. {@code leather_overlay}).
     * Returns empty if no overlay exists (meaning the material is not dyeable).
     */
    private Optional<BufferedImage> loadOverlayTexture(String material, int layer) {
        String overlayKey = material + "_overlay/" + layer;
        return cache.computeIfAbsent(overlayKey, k -> resolveTexture(material + "_overlay", layer));
    }

    /**
     * Tints the base texture with the given color and composites the overlay on top.
     * The overlay is drawn without tinting, preserving stitching/buckle details.
     */
    private static BufferedImage compositeWithOverlay(BufferedImage base, BufferedImage overlay, int tintColor) {
        int w = base.getWidth();
        int h = base.getHeight();
        float[] tint = ColorUtil.extractTintRgb(tintColor);

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        // Tint and draw the base layer
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = base.getRGB(x, y);
                int a = (pixel >> 24) & 0xFF;
                if (a == 0) continue;

                int r = ColorUtil.clamp(Math.round(((pixel >> 16) & 0xFF) * tint[0]));
                int g = ColorUtil.clamp(Math.round(((pixel >> 8) & 0xFF) * tint[1]));
                int b = ColorUtil.clamp(Math.round((pixel & 0xFF) * tint[2]));
                result.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        // Composite the overlay on top (no tinting)
        Graphics2D g2d = result.createGraphics();
        g2d.setComposite(AlphaComposite.SrcOver);
        g2d.drawImage(overlay, 0, 0, null);
        g2d.dispose();

        return result;
    }

    /**
     * Returns whether an armor texture exists for the given material and slot.
     *
     * @param material the armor material name
     * @param slot     the armor slot
     * @return true if the texture exists
     */
    public boolean hasTexture(String material, ArmorSlot slot) {
        return loadTexture(material, layerFor(slot)).isPresent();
    }

    // -------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------

    private Optional<BufferedImage> resolveTexture(String material, int layer) {
        // 1. Try resource pack (if configured)
        if (resourcePack != null) {
            Optional<BufferedImage> result = resolveFromResourcePack(material, layer);
            if (result.isPresent()) return result;
        }

        // 2. Fall back to bundled classpath defaults
        return resolveFromClasspath(material, layer);
    }

    // -- Resource pack resolution --

    private Optional<BufferedImage> resolveFromResourcePack(String material, int layer) {
        // Modern: parse equipment JSON
        Optional<BufferedImage> result = resolveJsonFromPack(material, layer);
        if (result.isPresent()) return result;

        // Modern direct path
        String prefix = layer == 2 ? RP_HUMANOID_LEGGINGS_PREFIX : RP_HUMANOID_PREFIX;
        result = loadImageFromPack(prefix + material + ".png");
        if (result.isPresent()) return result;

        // Legacy path
        return loadImageFromPack(RP_LEGACY_PREFIX + material + "_layer_" + layer + ".png");
    }

    private Optional<BufferedImage> resolveJsonFromPack(String material, int layer) {
        String jsonPath = RP_EQUIPMENT_PREFIX + material + ".json";
        Optional<InputStream> resource = resourcePack.getResource(jsonPath);
        if (resource.isEmpty()) return Optional.empty();

        return resolveFromJson(resource.get(), layer,
                layer == 2 ? RP_HUMANOID_LEGGINGS_PREFIX : RP_HUMANOID_PREFIX, true);
    }

    private Optional<BufferedImage> loadImageFromPack(String path) {
        Optional<InputStream> resource = resourcePack.getResource(path);
        if (resource.isEmpty()) return Optional.empty();

        try (InputStream in = resource.get()) {
            return Optional.ofNullable(ImageIO.read(in));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // -- Classpath resolution --

    private Optional<BufferedImage> resolveFromClasspath(String material, int layer) {
        // Modern: parse equipment JSON from classpath
        Optional<BufferedImage> result = resolveJsonFromClasspath(material, layer);
        if (result.isPresent()) return result;

        // Modern direct path
        String prefix = layer == 2 ? CP_HUMANOID_LEGGINGS_PREFIX : CP_HUMANOID_PREFIX;
        return loadImageFromClasspath(prefix + material + ".png");
    }

    private Optional<BufferedImage> resolveJsonFromClasspath(String material, int layer) {
        String jsonPath = CP_EQUIPMENT_PREFIX + material + ".json";
        InputStream stream = getClasspathStream(jsonPath);
        if (stream == null) return Optional.empty();

        return resolveFromJson(stream, layer,
                layer == 2 ? CP_HUMANOID_LEGGINGS_PREFIX : CP_HUMANOID_PREFIX, false);
    }

    private Optional<BufferedImage> loadImageFromClasspath(String path) {
        InputStream stream = getClasspathStream(path);
        if (stream == null) return Optional.empty();

        try (InputStream in = stream) {
            return Optional.ofNullable(ImageIO.read(in));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // -- Shared --

    private Optional<BufferedImage> resolveFromJson(InputStream jsonStream, int layer,
                                                     String texturePrefix, boolean fromPack) {
        try (InputStream in = jsonStream) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            Pattern pattern = layer == 2 ? LEGGINGS_TEXTURE_PATTERN : HUMANOID_TEXTURE_PATTERN;
            Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) return Optional.empty();

            String textureName = stripNamespace(matcher.group(1));
            String texturePath = texturePrefix + textureName + ".png";

            return fromPack ? loadImageFromPack(texturePath) : loadImageFromClasspath(texturePath);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static InputStream getClasspathStream(String path) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

    private static String stripNamespace(String ref) {
        int colon = ref.indexOf(':');
        return colon >= 0 ? ref.substring(colon + 1) : ref;
    }

    private static int layerFor(ArmorSlot slot) {
        return slot == ArmorSlot.LEGGINGS ? 2 : 1;
    }
}
