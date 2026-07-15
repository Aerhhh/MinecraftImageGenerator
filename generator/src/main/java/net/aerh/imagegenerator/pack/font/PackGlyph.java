package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * One renderable glyph of a resolved {@link PackFont}: metrics plus deterministic rasterization
 * onto a canvas. Coordinates are in GUI pixels (the vanilla 1x GUI coordinate space); the canvas
 * renders at {@code pixelSize} canvas pixels per GUI pixel.
 *
 * <p>Vertical model (vanilla): the baseline sits 7 GUI pixels below the line top and the glyph
 * cell's top edge sits at {@code lineTop + 7 - ascent} GUI pixels; a line is 9 GUI pixels tall.
 */
public interface PackGlyph {

    /**
     * Horizontal advance in GUI pixels. Bold adds exactly {@code +1.0} at any scale. Space-glyph
     * advances may be negative or fractional.
     */
    float advance(boolean bold);

    /**
     * Whether this glyph draws nothing: space glyphs always, bitmap glyphs whose cell is fully
     * transparent (ink width 0). Empty glyphs still advance.
     */
    boolean isEmpty();

    /** Provider {@code height} field in GUI pixels; 0 for space glyphs (nothing is drawn). */
    int height();

    /** Provider {@code ascent} field in GUI pixels (negative legal); 0 for space glyphs. */
    int ascent();

    /**
     * Draws the FULL glyph cell (leading blank columns included) nearest-neighbor scaled so the
     * cell renders {@code height()} GUI pixels tall, top edge at
     * {@code lineTopGuiPx + 7 - ascent()} GUI pixels, left edge at {@code xGuiPx}. The glyph
     * texture is tinted multiplicatively by {@code tint} (white or null = unchanged; all four
     * channels multiply, each rounded to nearest). Italic applies the vanilla shear per
     * destination GUI-pixel row: x offset {@code 1 - 0.25 * guiPxBelowLineTop}, rounded to the
     * nearest canvas pixel per row.
     *
     * <p>No shadow is drawn here; callers draw the shadow pass themselves (a second call at
     * {@code +1,+1} GUI pixels with the quartered color) BEFORE the main pass. Rasterization is
     * deterministic: identical arguments always produce identical pixels.
     *
     * @param graphics     target canvas graphics (default SrcOver compositing is used)
     * @param xGuiPx       left edge in GUI pixels (fractional legal; rounded to the nearest
     *                     canvas pixel after scaling)
     * @param lineTopGuiPx top of the text line in GUI pixels
     * @param pixelSize    canvas pixels per GUI pixel, at least 1
     * @param tint         multiplicative tint; white or null leaves the texture unchanged
     * @param italic       whether to apply the italic shear
     * @throws IllegalArgumentException when {@code pixelSize} is not positive
     */
    void draw(Graphics2D graphics, double xGuiPx, double lineTopGuiPx, int pixelSize, Color tint, boolean italic);
}
