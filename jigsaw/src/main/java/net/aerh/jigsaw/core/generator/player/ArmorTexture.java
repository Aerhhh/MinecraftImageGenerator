package net.aerh.jigsaw.core.generator.player;

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
 * Loads and caches Minecraft armor textures from the classpath.
 *
 * <p>Supports the modern (1.21.4+) equipment format where equipment definition files
 * at {@code equipment/{material}.json} reference texture names:
 * <pre>
 * textures/entity/equipment/humanoid/{name}.png         (layer 1 - helmet, chestplate, boots)
 * textures/entity/equipment/humanoid_leggings/{name}.png (layer 2 - leggings)
 * </pre>
 *
 * <p>For leather armor, also loads overlay textures ({@code {material}_overlay}) and
 * provides compositing: the base texture is tinted with the dye color via per-pixel
 * RGB multiplication, and the overlay (buckles, trim) is drawn on top untinted.
 */
public final class ArmorTexture {

    private static final String EQUIPMENT_PREFIX = "minecraft/assets/equipment/";
    private static final String HUMANOID_PREFIX = "minecraft/assets/textures/entity/equipment/humanoid/";
    private static final String HUMANOID_LEGGINGS_PREFIX = "minecraft/assets/textures/entity/equipment/humanoid_leggings/";

    private static final Pattern HUMANOID_TEXTURE_PATTERN = Pattern.compile(
            "\"humanoid\"\\s*:\\s*\\[\\s*\\{[^}]*\"texture\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LEGGINGS_TEXTURE_PATTERN = Pattern.compile(
            "\"humanoid_leggings\"\\s*:\\s*\\[\\s*\\{[^}]*\"texture\"\\s*:\\s*\"([^\"]+)\"");

    /** Default undyed leather color (from Minecraft's equipment/leather.json). */
    private static final int DEFAULT_LEATHER_COLOR = 0xA06540;

    private final ConcurrentHashMap<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();

    public ArmorTexture() {}

    /**
     * Loads the armor texture for a material and layer number.
     *
     * @param material the material name (e.g. "iron", "diamond", "leather")
     * @param layer    the layer number (1 or 2)
     * @return the texture image, or empty if not found
     */
    public Optional<BufferedImage> loadTexture(String material, int layer) {
        String key = material + "/" + layer;
        return cache.computeIfAbsent(key, k -> resolveTexture(material, layer));
    }

    /**
     * Loads the overlay texture for a dyeable material (e.g. leather).
     * Returns empty if the material has no overlay (not dyeable).
     */
    public Optional<BufferedImage> loadOverlayTexture(String material, int layer) {
        String key = material + "_overlay/" + layer;
        return cache.computeIfAbsent(key, k -> resolveTexture(material + "_overlay", layer));
    }

    /**
     * Loads the fully composited armor texture, handling leather dye tinting
     * and overlay compositing automatically.
     *
     * <p>For dyeable materials (leather): tints the base texture with the dye color
     * (or default leather color if no dye specified), then composites the overlay on top.
     *
     * <p>For non-dyeable materials: returns the texture as-is.
     *
     * @param material the material name
     * @param layer    the texture layer (1 or 2)
     * @param dyeColor optional dye color for leather armor
     * @return the composited texture, or empty if not found
     */
    public Optional<BufferedImage> loadComposited(String material, int layer,
                                                   Optional<Integer> dyeColor) {
        Optional<BufferedImage> overlay = loadOverlayTexture(material, layer);
        if (overlay.isPresent()) {
            int color = dyeColor.orElse(DEFAULT_LEATHER_COLOR);
            return loadTexture(material, layer)
                    .map(base -> tintWithOverlay(base, overlay.get(), color));
        }
        return loadTexture(material, layer);
    }

    /**
     * Tints the base texture with the given color and composites the overlay on top.
     *
     * <p>Tinting is per-pixel RGB multiplication: {@code outR = (pixelR * dyeR) / 255}.
     * The overlay is drawn without tinting, preserving non-colorable details.
     *
     * @param base     the base armor texture to tint
     * @param overlay  the overlay texture (drawn untinted on top)
     * @param dyeColor the RGB dye color
     * @return the composited result
     */
    public static BufferedImage tintWithOverlay(BufferedImage base, BufferedImage overlay,
                                                int dyeColor) {
        int w = base.getWidth();
        int h = base.getHeight();
        float[] tint = ColorUtil.extractTintRgb(dyeColor);

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

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

        Graphics2D g2d = result.createGraphics();
        g2d.setComposite(AlphaComposite.SrcOver);
        g2d.drawImage(overlay, 0, 0, null);
        g2d.dispose();

        return result;
    }

    // -- Resolution --

    private Optional<BufferedImage> resolveTexture(String material, int layer) {
        Optional<BufferedImage> result = resolveFromJson(material, layer);
        if (result.isPresent()) return result;

        String prefix = layer == 2 ? HUMANOID_LEGGINGS_PREFIX : HUMANOID_PREFIX;
        return loadFromClasspath(prefix + material + ".png");
    }

    private Optional<BufferedImage> resolveFromJson(String material, int layer) {
        String jsonPath = EQUIPMENT_PREFIX + material + ".json";
        InputStream stream = getClasspathStream(jsonPath);
        if (stream == null) return Optional.empty();

        try (InputStream in = stream) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            Pattern pattern = layer == 2 ? LEGGINGS_TEXTURE_PATTERN : HUMANOID_TEXTURE_PATTERN;
            Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) return Optional.empty();

            String textureName = stripNamespace(matcher.group(1));
            String prefix = layer == 2 ? HUMANOID_LEGGINGS_PREFIX : HUMANOID_PREFIX;
            return loadFromClasspath(prefix + textureName + ".png");
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> loadFromClasspath(String path) {
        InputStream stream = getClasspathStream(path);
        if (stream == null) return Optional.empty();

        try (InputStream in = stream) {
            return Optional.ofNullable(ImageIO.read(in));
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
}
