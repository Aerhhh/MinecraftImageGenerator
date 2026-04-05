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
     * Returns the pixel width of the given character in this font at its default size.
     */
    int getCharWidth(char c);

    /**
     * Returns {@code true} if this font has a glyph for the given character.
     */
    boolean supportsChar(char c);
}
