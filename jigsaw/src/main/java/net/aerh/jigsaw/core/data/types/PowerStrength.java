package net.aerh.jigsaw.core.data.types;

import java.util.Objects;

/**
 * Represents a power strength tier used by Accessory Bag power stones.
 *
 * <p>{@code stone} indicates whether this tier requires a physical stone item.
 *
 * @param name    the identifier used as the registry lookup key
 * @param display the human-readable display label for this tier
 * @param stone   whether this tier requires a physical stone item to activate
 */
public record PowerStrength(String name, String display, boolean stone) {

    public PowerStrength {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(display, "display must not be null");
    }
}
