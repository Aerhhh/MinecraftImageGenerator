package net.aerh.jigsaw.core.generator.body;

import net.aerh.jigsaw.core.resource.ResourcePack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads Minecraft armor textures from a {@link ResourcePack} and creates {@link ArmorPiece}
 * objects for use with the body renderer.
 *
 * <p>Supports both the modern (1.21.4+) equipment format and the legacy format:
 *
 * <h3>Modern format (1.21.4+)</h3>
 * <p>Equipment definitions at {@code assets/minecraft/equipment/{material}.json} reference
 * texture names per layer type. Textures are stored at:
 * <pre>
 * assets/minecraft/textures/entity/equipment/humanoid/{name}.png       (helmet, chestplate, boots)
 * assets/minecraft/textures/entity/equipment/humanoid_leggings/{name}.png  (leggings)
 * </pre>
 *
 * <h3>Legacy format (pre-1.21.4)</h3>
 * <pre>
 * assets/minecraft/textures/models/armor/{material}_layer_1.png  (helmet, chestplate, boots)
 * assets/minecraft/textures/models/armor/{material}_layer_2.png  (leggings)
 * </pre>
 *
 * <p>The provider tries the modern format first, then falls back to legacy. Loaded textures
 * are cached to avoid redundant I/O.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ArmorTextureProvider armorTextures = new ArmorTextureProvider(resourcePack);
 *
 * // Load armor pieces by material name
 * Optional<ArmorPiece> helmet = armorTextures.piece(ArmorSlot.HELMET, "iron");
 * Optional<ArmorPiece> leggings = armorTextures.piece(ArmorSlot.LEGGINGS, "iron");
 *
 * // Load a dyed leather armor piece
 * Optional<ArmorPiece> dyedChestplate = armorTextures.piece(ArmorSlot.CHESTPLATE, "leather", 0xFFA06540);
 * }</pre>
 */
public final class ArmorTextureProvider {

    // Modern (1.21.4+) paths
    private static final String EQUIPMENT_PREFIX = "assets/minecraft/equipment/";
    private static final String HUMANOID_TEXTURE_PREFIX = "assets/minecraft/textures/entity/equipment/humanoid/";
    private static final String HUMANOID_LEGGINGS_TEXTURE_PREFIX = "assets/minecraft/textures/entity/equipment/humanoid_leggings/";

    // Legacy (pre-1.21.4) paths
    private static final String LEGACY_ARMOR_TEXTURE_PREFIX = "assets/minecraft/textures/models/armor/";

    // Extracts texture names from equipment JSON without a full JSON parser.
    // Matches entries under "humanoid" and "humanoid_leggings" layer arrays.
    private static final Pattern HUMANOID_TEXTURE_PATTERN = Pattern.compile(
            "\"humanoid\"\\s*:\\s*\\[\\s*\\{[^}]*\"texture\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LEGGINGS_TEXTURE_PATTERN = Pattern.compile(
            "\"humanoid_leggings\"\\s*:\\s*\\[\\s*\\{[^}]*\"texture\"\\s*:\\s*\"([^\"]+)\"");

    private final ResourcePack resourcePack;
    private final ConcurrentHashMap<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a new armor texture provider backed by the given resource pack.
     *
     * @param resourcePack the resource pack to load armor textures from; must not be {@code null}
     */
    public ArmorTextureProvider(ResourcePack resourcePack) {
        this.resourcePack = Objects.requireNonNull(resourcePack, "resourcePack must not be null");
    }

    /**
     * Creates an {@link ArmorPiece} for the given slot and material, loading the appropriate
     * texture from the resource pack.
     *
     * @param slot     the armor slot
     * @param material the armor material name (e.g. "iron", "diamond", "leather")
     * @return the armor piece, or empty if the texture was not found in the resource pack
     */
    public Optional<ArmorPiece> piece(ArmorSlot slot, String material) {
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(material, "material must not be null");

        return loadTexture(material, layerFor(slot))
                .map(texture -> ArmorPiece.of(slot, texture));
    }

    /**
     * Creates a dyed {@link ArmorPiece} for the given slot and material.
     *
     * @param slot     the armor slot
     * @param material the armor material name (e.g. "leather")
     * @param dyeColor the dye color in ARGB format
     * @return the armor piece with the dye color applied, or empty if the texture was not found
     */
    public Optional<ArmorPiece> piece(ArmorSlot slot, String material, int dyeColor) {
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(material, "material must not be null");

        return loadTexture(material, layerFor(slot))
                .map(texture -> ArmorPiece.dyed(slot, texture, dyeColor));
    }

    /**
     * Loads the raw armor texture image for a given material and layer number.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Modern: parse {@code equipment/{material}.json} for the texture name, then load
     *       from {@code entity/equipment/humanoid/} or {@code humanoid_leggings/}</li>
     *   <li>Modern fallback: try {@code entity/equipment/humanoid/{material}.png} directly
     *       (handles packs without equipment JSON)</li>
     *   <li>Legacy: try {@code models/armor/{material}_layer_{layer}.png}</li>
     * </ol>
     *
     * @param material the armor material name
     * @param layer    the layer number (1 or 2)
     * @return the loaded texture, or empty if not found
     */
    public Optional<BufferedImage> loadTexture(String material, int layer) {
        Objects.requireNonNull(material, "material must not be null");
        if (layer != 1 && layer != 2) {
            throw new IllegalArgumentException("layer must be 1 or 2, got: " + layer);
        }

        String cacheKey = material + "/" + layer;
        return cache.computeIfAbsent(cacheKey, k -> resolveTexture(material, layer));
    }

    /**
     * Returns whether the resource pack contains an armor texture for the given material and slot.
     *
     * @param material the armor material name
     * @param slot     the armor slot
     * @return true if the texture exists in either modern or legacy format
     */
    public boolean hasTexture(String material, ArmorSlot slot) {
        int layer = layerFor(slot);
        String modernPrefix = layer == 2 ? HUMANOID_LEGGINGS_TEXTURE_PREFIX : HUMANOID_TEXTURE_PREFIX;

        // Check modern paths
        if (resourcePack.hasResource(EQUIPMENT_PREFIX + material + ".json")) {
            return true;
        }
        if (resourcePack.hasResource(modernPrefix + material + ".png")) {
            return true;
        }
        // Check legacy path
        return resourcePack.hasResource(LEGACY_ARMOR_TEXTURE_PREFIX + material + "_layer_" + layer + ".png");
    }

    // -------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------

    private Optional<BufferedImage> resolveTexture(String material, int layer) {
        // 1. Try modern: parse equipment JSON for the texture reference
        Optional<BufferedImage> result = resolveFromEquipmentJson(material, layer);
        if (result.isPresent()) return result;

        // 2. Try modern fallback: direct path using material name
        String modernPrefix = layer == 2 ? HUMANOID_LEGGINGS_TEXTURE_PREFIX : HUMANOID_TEXTURE_PREFIX;
        result = loadImage(modernPrefix + material + ".png");
        if (result.isPresent()) return result;

        // 3. Try legacy path
        return loadImage(LEGACY_ARMOR_TEXTURE_PREFIX + material + "_layer_" + layer + ".png");
    }

    private Optional<BufferedImage> resolveFromEquipmentJson(String material, int layer) {
        String jsonPath = EQUIPMENT_PREFIX + material + ".json";
        Optional<InputStream> resource = resourcePack.getResource(jsonPath);
        if (resource.isEmpty()) return Optional.empty();

        try (InputStream in = resource.get()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            Pattern pattern = layer == 2 ? LEGGINGS_TEXTURE_PATTERN : HUMANOID_TEXTURE_PATTERN;
            Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) return Optional.empty();

            String textureRef = matcher.group(1);
            String textureName = stripNamespace(textureRef);

            String texturePrefix = layer == 2 ? HUMANOID_LEGGINGS_TEXTURE_PREFIX : HUMANOID_TEXTURE_PREFIX;
            return loadImage(texturePrefix + textureName + ".png");
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> loadImage(String path) {
        Optional<InputStream> resource = resourcePack.getResource(path);
        if (resource.isEmpty()) return Optional.empty();

        try (InputStream in = resource.get()) {
            BufferedImage image = ImageIO.read(in);
            return Optional.ofNullable(image);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Strips the {@code "minecraft:"} namespace prefix from a texture reference.
     */
    private static String stripNamespace(String ref) {
        int colon = ref.indexOf(':');
        return colon >= 0 ? ref.substring(colon + 1) : ref;
    }

    private static int layerFor(ArmorSlot slot) {
        return slot == ArmorSlot.LEGGINGS ? 2 : 1;
    }
}
