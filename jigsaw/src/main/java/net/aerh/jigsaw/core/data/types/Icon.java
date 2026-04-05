package net.aerh.jigsaw.core.data.types;

import java.util.Objects;

/**
 * Represents a named icon character used in item tooltip rendering.
 */
public record Icon(String name, String icon) {

    public Icon {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(icon, "icon must not be null");
    }
}
