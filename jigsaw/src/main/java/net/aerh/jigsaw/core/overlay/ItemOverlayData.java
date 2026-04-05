package net.aerh.jigsaw.core.overlay;

import net.aerh.jigsaw.api.overlay.ColorMode;

import java.awt.image.BufferedImage;

/**
 * Holds the loaded overlay info for a single item, including the extracted overlay texture,
 * the color mode (BASE vs OVERLAY), the renderer type key, and the color options category
 * used to look up named colors.
 *
 * @param overlayTexture       the extracted overlay sprite from the spritesheet
 * @param colorMode            whether the tint applies to the base texture or the overlay texture
 * @param rendererType         the renderer type key (e.g. {@code "normal"})
 * @param colorOptionsCategory the color category name for resolving named colors (e.g. {@code "leather_armor"})
 * @param defaultColors        the default color values for de-tinting, or {@code null} if not applicable
 * @param allowHexColors       whether hex color strings are accepted for this overlay
 */
public record ItemOverlayData(
        BufferedImage overlayTexture,
        ColorMode colorMode,
        String rendererType,
        String colorOptionsCategory,
        int[] defaultColors,
        boolean allowHexColors
) {}
