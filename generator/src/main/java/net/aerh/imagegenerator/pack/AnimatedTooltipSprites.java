package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

/**
 * A tooltip style's sprites with their texture animations resolved: the static first-frame
 * pair (for measurement and any static fallback) plus a {@link PackAnimation} per sprite that
 * actually animates - the 17-frame "shiny" strips real packs ship. At least one of the two
 * animations is present; a style with neither resolves through the static path instead.
 *
 * @param sprites    the first-frame sprite pair, exactly what the static resolution returns
 * @param background the background sprite's animation, or null when it is static
 * @param frame      the frame sprite's animation, or null when it is static
 */
public record AnimatedTooltipSprites(TooltipSprites sprites, @Nullable PackAnimation background,
                                     @Nullable PackAnimation frame) {

    public AnimatedTooltipSprites {
        if (sprites == null) {
            throw new IllegalArgumentException("sprites must be provided");
        }
        if (background == null && frame == null) {
            throw new IllegalArgumentException("at least one sprite animation must be present");
        }
    }

    /**
     * The sprite pair shown at the given playback positions: each animated sprite contributes
     * its frame at its position (static sprites ignore theirs and keep the first-frame texture).
     * Scaling metadata carries over from the static pair - vanilla reads {@code gui.scaling}
     * once per sprite, not per frame.
     *
     * <p><b>Ownership:</b> unlike the static resolution paths ({@code resolveTooltipSprites},
     * {@code resolveContainerBackground}), which hand callers defensive copies, an animated
     * sprite's texture here is the shared, memoized {@link PackAnimation#frameImage} crop -
     * reused across every position and every {@code spritesAt} call showing that flipbook index.
     * Treat the returned textures as immutable (draw them, never paint into them); mutating one
     * would corrupt every frame showing that index for the life of this animation.
     */
    public TooltipSprites spritesAt(int backgroundPosition, int framePosition) {
        GuiSprite backgroundSprite = background == null ? sprites.background()
            : new GuiSprite(background.frameImage(backgroundPosition), sprites.background().scaling());
        GuiSprite frameSprite = frame == null ? sprites.frame()
            : new GuiSprite(frame.frameImage(framePosition), sprites.frame().scaling());
        return new TooltipSprites(backgroundSprite, frameSprite);
    }
}
