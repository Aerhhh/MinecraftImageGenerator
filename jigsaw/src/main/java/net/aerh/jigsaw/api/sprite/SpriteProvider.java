package net.aerh.jigsaw.api.sprite;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Returns all sprites whose texture ID contains the given query string (case-insensitive),
     * sorted alphabetically by texture ID.
     *
     * <p>Use this method when you need every matching sprite, for example to populate an
     * autocomplete list. If you only need a single result, prefer {@link #search(String)}.
     *
     * @param query the substring to search for; must not be {@code null}
     * @return an alphabetically ordered list of matching name-to-image entries; never {@code null}
     */
    List<Map.Entry<String, BufferedImage>> searchAll(String query);

    /**
     * Returns a snapshot of every loaded sprite, keyed by texture ID.
     *
     * <p>This is intended for use cases such as autocomplete that need access to the full
     * sprite map. The returned map is a read-only view and its contents may be cached.
     *
     * @return an unmodifiable map of all texture IDs to their {@link BufferedImage}s
     */
    Map<String, BufferedImage> getAllSprites();
}
