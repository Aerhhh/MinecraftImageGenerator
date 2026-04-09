package net.aerh.jigsaw.core.generator.body;

import net.aerh.jigsaw.core.resource.ResourcePack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads Minecraft armor textures from a {@link ResourcePack} and creates {@link ArmorPiece}
 * objects for use with the body renderer.
 *
 * <p>Armor textures in Minecraft resource packs follow this path convention:
 * <pre>
 * assets/minecraft/textures/models/armor/{material}_layer_1.png  (helmet, chestplate, boots)
 * assets/minecraft/textures/models/armor/{material}_layer_2.png  (leggings)
 * </pre>
 *
 * <p>For example, iron armor textures are at:
 * <ul>
 *   <li>{@code assets/minecraft/textures/models/armor/iron_layer_1.png}</li>
 *   <li>{@code assets/minecraft/textures/models/armor/iron_layer_2.png}</li>
 * </ul>
 *
 * <p>This provider caches loaded textures to avoid redundant I/O.
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

    private static final String ARMOR_TEXTURE_PREFIX = "assets/minecraft/textures/models/armor/";

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
     * <p>The texture layer is determined by the slot:
     * <ul>
     *   <li>{@link ArmorSlot#HELMET}, {@link ArmorSlot#CHESTPLATE}, {@link ArmorSlot#BOOTS}
     *       use {@code {material}_layer_1.png}</li>
     *   <li>{@link ArmorSlot#LEGGINGS} uses {@code {material}_layer_2.png}</li>
     * </ul>
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
     * @param material the armor material name
     * @param layer    the layer number (1 or 2)
     * @return the loaded texture, or empty if not found
     */
    public Optional<BufferedImage> loadTexture(String material, int layer) {
        Objects.requireNonNull(material, "material must not be null");
        if (layer != 1 && layer != 2) {
            throw new IllegalArgumentException("layer must be 1 or 2, got: " + layer);
        }

        String path = ARMOR_TEXTURE_PREFIX + material + "_layer_" + layer + ".png";
        return cache.computeIfAbsent(path, this::loadFromPack);
    }

    /**
     * Returns whether the resource pack contains an armor texture for the given material and slot.
     *
     * @param material the armor material name
     * @param slot     the armor slot
     * @return true if the texture exists
     */
    public boolean hasTexture(String material, ArmorSlot slot) {
        String path = ARMOR_TEXTURE_PREFIX + material + "_layer_" + layerFor(slot) + ".png";
        return resourcePack.hasResource(path);
    }

    private Optional<BufferedImage> loadFromPack(String path) {
        Optional<InputStream> resource = resourcePack.getResource(path);
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        try (InputStream in = resource.get()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return Optional.empty();
            }
            return Optional.of(image);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static int layerFor(ArmorSlot slot) {
        return slot == ArmorSlot.LEGGINGS ? 2 : 1;
    }
}
