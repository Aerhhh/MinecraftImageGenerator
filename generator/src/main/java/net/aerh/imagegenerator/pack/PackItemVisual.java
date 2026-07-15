package net.aerh.imagegenerator.pack;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * A pack item resolved for GUI rendering: either a flat layer0 sprite (the classic path, at
 * native texture resolution for the caller to scale exactly as before) or an elements raster
 * computed directly at the requested target resolution.
 */
public sealed interface PackItemVisual {

    /**
     * A flat layer0-composed sprite at its native texture resolution; callers scale it onto
     * their canvas exactly as the pre-elements pipeline did.
     */
    record Sprite(BufferedImage sprite) implements PackItemVisual {

        public Sprite {
            Objects.requireNonNull(sprite, "sprite");
        }
    }

    /**
     * An elements-model raster at the requested target resolution.
     *
     * <p>{@code offsetX}/{@code offsetY} position the image's top-left corner relative to the
     * top-left corner of the 16-GUI-px slot box, in canvas pixels at the requested scale. A
     * clipped (non-oversized) render is exactly the slot box: offsets (0, 0) and a square image
     * of {@code 16 * pixelsPerGuiPx}. An oversized render covers the union of the slot box and
     * the art's extent, so its offsets are never positive.
     *
     * @param oversized whether the item declared {@code oversized_in_gui} (slot clipping was
     *                  skipped and the raster may span neighboring slots)
     */
    record ElementsRaster(BufferedImage image, int offsetX, int offsetY, boolean oversized) implements PackItemVisual {

        public ElementsRaster {
            Objects.requireNonNull(image, "image");
        }
    }
}
