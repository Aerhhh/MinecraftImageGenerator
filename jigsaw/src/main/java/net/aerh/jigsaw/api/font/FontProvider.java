package net.aerh.jigsaw.api.font;

import java.awt.Font;
import java.util.Optional;

/**
 * Provides a font and character-level metrics for a specific font resource.
 *
 * @see FontRegistry
 */
public interface FontProvider {

    /**
     * Unique identifier for this font (e.g. {@code "minecraft:default"}).
     */
    String id();

    /**
     * Returns the AWT {@link Font} for rendering, or empty if not yet loaded.
     */
    Optional<Font> getFont();

    /**
     * Returns the AWT {@link Font} derived at the given size, or {@code null} if not loaded.
     *
     * @param size the desired point size
     * @return the derived font, or {@code null} if this provider has no font loaded
     */
    default Font getFont(float size) {
        return getFont().map(f -> f.deriveFont(size)).orElse(null);
    }

    /**
     * Returns the pixel width of the given character in this font at its default size.
     */
    int getCharWidth(char c);

    /**
     * Returns {@code true} if this font has a glyph for the given character.
     */
    boolean supportsChar(char c);
}
