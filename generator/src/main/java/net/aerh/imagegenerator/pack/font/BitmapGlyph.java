package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;

/**
 * A bitmap-provider glyph: an owned copy of its full sheet cell plus the vanilla-exact metrics.
 * The raster work (scale, tint, italic shear and the bounded scaled-cell cache) lives in the
 * shared {@link GlyphCell}, which {@link OtfGlyph} reuses so the two glyph kinds stay
 * pixel-consistent; this class only supplies the bitmap metrics and the vanilla vertical
 * placement.
 */
final class BitmapGlyph implements PackGlyph {

    private final GlyphCell cell;
    private final int advance;
    private final int height;
    private final int ascent;

    /**
     * @param cell    the glyph's full cell, copied out of the sheet (never a shared subimage);
     *                null for a fully transparent cell, which draws nothing and retains no raster
     *                memory
     * @param advance unbold advance in GUI pixels: {@code (int) (0.5f + inkWidth * scale) + 1}
     * @param height  provider height field (GUI pixels the cell renders tall)
     * @param ascent  provider ascent field
     * @param scale   {@code height / cellHeight}
     */
    BitmapGlyph(BufferedImage cell, int advance, int height, int ascent, float scale) {
        this.cell = new GlyphCell(cell, height, scale);
        this.advance = advance;
        this.height = height;
        this.ascent = ascent;
    }

    /** Bytes retained by the owned cell raster (ARGB, 4 bytes per pixel); 0 for an empty cell. */
    long retainedBytes() {
        return cell.retainedBytes();
    }

    /** The pixel sizes currently holding a cached scaled raster; for tests pinning the LRU cap. */
    Set<Integer> cachedScaledPixelSizes() {
        return cell.cachedScaledPixelSizes();
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
    public void draw(Graphics2D graphics, double xGuiPx, double lineTopGuiPx, int pixelSize, Color tint, boolean italic) {
        if (pixelSize <= 0) {
            throw new IllegalArgumentException("pixelSize must be positive, got: " + pixelSize);
        }
        if (height <= 0) {
            // A zero (or negative) height cell occupies no vertical space; nothing to draw, the
            // advance still counts. Vanilla renders a degenerate quad here.
            return;
        }
        // Vanilla vertical model: the cell's top edge sits `ascent` GUI px above the baseline,
        // which is 7 GUI px below the line top.
        cell.draw(graphics, xGuiPx, lineTopGuiPx, lineTopGuiPx + 7 - ascent, pixelSize, tint, italic);
    }
}
