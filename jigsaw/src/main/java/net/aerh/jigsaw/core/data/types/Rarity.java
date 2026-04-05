package net.aerh.jigsaw.core.data.types;

import java.util.Objects;

/**
 * Represents an item rarity tier (e.g. COMMON, RARE, LEGENDARY).
 */
public record Rarity(String name, String display, String color) {

    public Rarity {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(display, "display must not be null");
        Objects.requireNonNull(color, "color must not be null");
    }
}
