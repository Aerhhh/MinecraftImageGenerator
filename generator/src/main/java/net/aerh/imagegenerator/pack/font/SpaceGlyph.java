package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A space-provider glyph: pure advance (negative and fractional legal), nothing drawn - no
 * glyph, no shadow, no bold copy - but the advance fully counts toward text width. Height and
 * ascent are 0 because there is no cell to place.
 */
final class SpaceGlyph implements PackGlyph {

    private final float advance;

    SpaceGlyph(float advance) {
        this.advance = advance;
    }

    float advance() {
        return advance;
    }

    @Override
    public float advance(boolean bold) {
        return advance + (bold ? 1.0f : 0.0f);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int height() {
        return 0;
    }

    @Override
    public int ascent() {
        return 0;
    }

    @Override
    public void draw(Graphics2D graphics, double xGuiPx, double lineTopGuiPx, int pixelSize, Color tint, boolean italic) {
        if (pixelSize <= 0) {
            throw new IllegalArgumentException("pixelSize must be positive, got: " + pixelSize);
        }
        // Intentionally a no-op: space glyphs never rasterize.
    }
}
