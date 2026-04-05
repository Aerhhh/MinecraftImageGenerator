package net.aerh.jigsaw.core.data.types;

import java.util.Objects;

/**
 * Represents an item rarity tier (e.g. COMMON, RARE, LEGENDARY).
 *
 * @param name    the identifier used as the registry lookup key (e.g. {@code "common"})
 * @param display the human-readable display label shown in tooltips
 * @param color   the Minecraft color code character for this rarity
 */
public record Rarity(String name, String display, String color) {

    public Rarity {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(display, "display must not be null");
        Objects.requireNonNull(color, "color must not be null");
    }
}
