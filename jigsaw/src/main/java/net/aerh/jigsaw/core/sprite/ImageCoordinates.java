package net.aerh.jigsaw.core.sprite;

import java.util.Objects;

/**
 * Holds the position and size of a single sprite within a texture atlas.
 */
public record ImageCoordinates(String name, int x, int y, int size) {

    public ImageCoordinates {
        Objects.requireNonNull(name, "name must not be null");
        if (x < 0) throw new IllegalArgumentException("x must not be negative");
        if (y < 0) throw new IllegalArgumentException("y must not be negative");
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
    }
}
