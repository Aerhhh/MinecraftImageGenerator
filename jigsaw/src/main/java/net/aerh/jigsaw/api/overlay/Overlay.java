package net.aerh.jigsaw.api.overlay;

import java.awt.image.BufferedImage;

/**
 * Describes a single overlay layer to be composited over an item's base texture.
 *
 * @param itemId       The item this overlay is bound to (e.g. {@code "minecraft:leather_chestplate"}).
 * @param texture      The overlay sprite to render.
 * @param colorMode    Whether the overlay is tinted by the item's color.
 * @param rendererType The renderer type key used to look up the {@link OverlayRenderer} for this overlay.
 */
public record Overlay(
        String itemId,
        BufferedImage texture,
        ColorMode colorMode,
        String rendererType
) {}
