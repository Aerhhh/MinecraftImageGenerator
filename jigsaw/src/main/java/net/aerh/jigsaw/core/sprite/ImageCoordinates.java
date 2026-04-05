package net.aerh.jigsaw.core.sprite;

import java.util.Objects;

/**
 * Holds the position and size of a single sprite within a texture atlas.
 *
 * @param name the texture identifier matching the item or block ID
 * @param x    the x pixel offset of the sprite within the atlas; must be {@code >= 0}
 * @param y    the y pixel offset of the sprite within the atlas; must be {@code >= 0}
 * @param size the width and height of the sprite in pixels; must be positive
 */
public record ImageCoordinates(String name, int x, int y, int size) {

    public ImageCoordinates {
        Objects.requireNonNull(name, "name must not be null");
        if (x < 0) throw new IllegalArgumentException("x must not be negative");
        if (y < 0) throw new IllegalArgumentException("y must not be negative");
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
    }
}
