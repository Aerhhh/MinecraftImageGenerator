package net.aerh.jigsaw.api.sprite;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Optional;

/**
 * Source of item and block textures identified by texture IDs.
 *
 * @see net.aerh.jigsaw.api.Engine#sprites()
 */
public interface SpriteProvider {

    /**
     * Returns the sprite for the given texture ID (e.g. {@code "minecraft:item/diamond_sword"}),
     * or empty if not found.
     */
    Optional<BufferedImage> getSprite(String textureId);

    /**
     * Returns all texture IDs available from this provider.
     */
    Collection<String> availableSprites();

    /**
     * Searches for a sprite whose ID contains the given query string.
     * Returns the first match, or empty if none found.
     */
    Optional<BufferedImage> search(String query);
}
