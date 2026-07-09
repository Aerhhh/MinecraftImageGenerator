package net.aerh.imagegenerator.pack;

/**
 * The background and frame sprite pair for one tooltip style, matching the vanilla
 * {@code minecraft:tooltip_style} convention of {@code <style>_background} and
 * {@code <style>_frame} GUI sprites (or the pack's default {@code tooltip/background} and
 * {@code tooltip/frame} override).
 */
public record TooltipSprites(GuiSprite background, GuiSprite frame) {

    public TooltipSprites {
        if (background == null || frame == null) {
            throw new IllegalArgumentException("background and frame must both be provided");
        }
    }
}
