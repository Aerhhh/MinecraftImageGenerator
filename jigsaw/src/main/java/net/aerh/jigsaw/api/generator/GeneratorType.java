package net.aerh.jigsaw.api.generator;

/**
 * Categorises the kind of output a {@link Generator} produces.
 *
 * <p>The type is declared by each {@link net.aerh.jigsaw.spi.GeneratorFactory} so the engine can
 * route requests to the appropriate generator without inspecting generic type parameters at runtime.
 *
 * @see net.aerh.jigsaw.spi.GeneratorFactory
 */
public enum GeneratorType {

    /**
     * Generates a single item sprite image.
     */
    ITEM,

    /**
     * Generates an inventory grid image containing multiple item slots.
     */
    INVENTORY,

    /**
     * Generates a player head (skull) image from a Base64 skin texture.
     */
    PLAYER_HEAD,

    /**
     * Generates a tooltip image (item name and lore text).
     */
    TOOLTIP,

    /**
     * Combines multiple generator outputs into a single composited image.
     */
    COMPOSITE
}
