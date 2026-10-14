package net.aerh.imagegenerator.spritesheet;

import lombok.experimental.UtilityClass;
import net.hypixel.nerdbot.marmalade.functional.Pair;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Static facade over a single {@link SpritesheetLoader} for the bundled vanilla texture atlas under
 * {@code /minecraft/assets}.
 * <p>
 * The loader is created when this class is first used and decodes atlas pages lazily, so the first call that touches
 * a page pays for decoding it. Use {@link SpritesheetLoader} directly to load an atlas from another resource path.
 */
@UtilityClass
public class Spritesheet {

    private static final SpritesheetLoader LOADER = new SpritesheetLoader();

    /**
     * Get a texture by name.
     *
     * @param textureId Texture name as listed in the coordinates file
     *
     * @return The texture, or null if the name is null or not listed
     *
     * @see SpritesheetLoader#getTexture(String)
     */
    public static BufferedImage getTexture(String textureId) {
        return LOADER.getTexture(textureId);
    }

    /**
     * Find every texture whose name contains {@code textureId}. The match is case-sensitive.
     *
     * @param textureId Substring to look for in texture names
     *
     * @return Matching textures paired with their names; empty if nothing matches
     *
     * @see SpritesheetLoader#searchForTexture(String)
     */
    public static List<Pair<String, BufferedImage>> searchForTexture(String textureId) {
        return LOADER.searchForTexture(textureId);
    }

    /**
     * Get every texture keyed by name. This decodes every atlas page.
     *
     * @return An unmodifiable map of texture name to texture
     *
     * @see SpritesheetLoader#getImageMap()
     */
    public static Map<String, BufferedImage> getImageMap() {
        return LOADER.getImageMap();
    }
}
