package net.aerh.imagegenerator.pack;

import java.awt.image.BufferedImage;

/**
 * A GUI sprite resolved from a pack: the decoded texture (flipbook first frame when animated)
 * plus its declared {@link GuiScaling}. The texture is a defensive copy owned by the caller.
 */
public record GuiSprite(BufferedImage texture, GuiScaling scaling) {

    public GuiSprite {
        if (texture == null) {
            throw new IllegalArgumentException("texture must not be null");
        }
        if (scaling == null) {
            throw new IllegalArgumentException("scaling must not be null");
        }
    }
}
