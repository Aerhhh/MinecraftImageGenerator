package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A TTF-provider glyph (Tier 1): a vector outline rasterized once at the provider's
 * {@code ppem = round(size * oversample)} device resolution into an owned {@link GlyphCell}, then
 * nearest-neighbor scaled down by {@code 1 / oversample} to GUI pixels at draw time - the same
 * cell machinery {@link BitmapGlyph} uses, so scale, tint, italic shear, bold and shadow behave
 * identically. Only the advance and the vertical placement differ: a TTF glyph sits on the vanilla
 * baseline (7 GUI px below the line top) and honors the provider {@code shift[x,y]} as a placement
 * offset, never touching the advance.
 *
 * <p>Rasterization is antialiasing-OFF and fractional-metrics-OFF, matching the aliased text path
 * everywhere else; a pixel font authored for integral ppem (MCC's {@code hud.ttf} at
 * {@code 7 * 4 = 28}) reproduces exactly. Non-pixel outlines render blocky at this tier (no
 * coverage downsampling) - the documented Tier-1 limitation.
 */
final class OtfGlyph implements PackGlyph {

    private final GlyphCell cell;
    private final float advance;
    private final int height;
    private final int ascent;
    /** Cell left edge relative to the pen origin, in GUI px (side bearing plus {@code shiftX}). */
    private final double leftOffsetGuiPx;
    /** GUI pixels the cell renders wide (the downscaled device cell); 0 for an inkless glyph. */
    private final int renderedGuiWidth;
    /** Cell top edge relative to the line top, in GUI px (exact placement, shift included). */
    private final double cellTopRelLineTopGuiPx;

    /**
     * @param cell                  the rasterized cell scaled by {@code 1 / oversample}; null for
     *                              a glyph with no ink (e.g. the space), which draws nothing but
     *                              still advances
     * @param advance               unbold advance in GUI pixels (the device advance rounded at
     *                              ppem, divided by oversample); fractional legal
     * @param leftOffsetGuiPx       cell left edge relative to the pen origin, GUI px
     * @param cellTopRelLineTopGuiPx cell top edge relative to the line top, GUI px (baseline at
     *                              {@code 7 + shiftY}, cell top the cell's ascent above it)
     * @param renderedGuiWidth      GUI pixels the cell renders wide (0 for an inkless glyph)
     * @param renderedGuiHeight     GUI pixels the cell renders tall (0 for an inkless glyph)
     */
    OtfGlyph(GlyphCell cell, float advance, double leftOffsetGuiPx, double cellTopRelLineTopGuiPx,
             int renderedGuiWidth, int renderedGuiHeight) {
        this.cell = cell;
        this.advance = advance;
        this.leftOffsetGuiPx = leftOffsetGuiPx;
        this.renderedGuiWidth = renderedGuiWidth;
        this.cellTopRelLineTopGuiPx = cellTopRelLineTopGuiPx;
        // Integer measurement envelope that CONTAINS the exact (possibly fractional-shift) drawn
        // cell, expressed in the vanilla `top = 7 - ascent`, `bottom = top + height` convention so
        // MinecraftTooltip's extent folding sizes canvases that never clip the glyph. For integral
        // shiftY (the real-pack case) this collapses to ascent = cell-ascent, height =
        // renderedGuiHeight, matching the bitmap model exactly.
        int boxTop = (int) Math.floor(cellTopRelLineTopGuiPx);
        this.ascent = 7 - boxTop;
        this.height = (int) Math.ceil(cellTopRelLineTopGuiPx + renderedGuiHeight) - boxTop;
    }

    /** Bytes retained by the owned cell raster; 0 for an inkless glyph. */
    long retainedBytes() {
        return cell.retainedBytes();
    }

    @Override
    public float advance(boolean bold) {
        return advance + (bold ? 1.0f : 0.0f);
    }

    @Override
    public boolean isEmpty() {
        return cell.isEmpty();
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int ascent() {
        return ascent;
    }

    @Override
    public double inkLeftGuiPx() {
        // Inkless glyphs draw nothing: report the degenerate box so extent folding skips them.
        return cell.isEmpty() ? 0.0 : leftOffsetGuiPx;
    }

    @Override
    public double inkRightGuiPx() {
        // The drawn cell spans [leftOffset, leftOffset + width]; a shiftX or a glyph wider than
        // its advance makes this exceed the advance, so measurement must fold it.
        return cell.isEmpty() ? 0.0 : leftOffsetGuiPx + renderedGuiWidth;
    }

    @Override
    public void draw(Graphics2D graphics, double xGuiPx, double lineTopGuiPx, int pixelSize, Color tint, boolean italic) {
        if (pixelSize <= 0) {
            throw new IllegalArgumentException("pixelSize must be positive, got: " + pixelSize);
        }
        cell.draw(graphics, xGuiPx + leftOffsetGuiPx, lineTopGuiPx,
            lineTopGuiPx + cellTopRelLineTopGuiPx, pixelSize, tint, italic);
    }
}
