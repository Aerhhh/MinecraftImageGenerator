package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Wraps a {@link PackGlyph} so its draw applies a pack's {@link MovementTintRule} to the run color
 * before the glyph's own tint multiply. Every metric method delegates unchanged, so measurement,
 * obfuscation and provider priority are untouched; only {@link #draw} substitutes the effective
 * tint. {@link PackFont#glyph(int)} installs this wrapper only when the pack ships the movement
 * shader, so packs without it hand back the raw glyph and render byte-identically to before.
 */
final class TintRuleGlyph implements PackGlyph {

    private final PackGlyph delegate;
    private final MovementTintRule rule;

    TintRuleGlyph(PackGlyph delegate, MovementTintRule rule) {
        this.delegate = delegate;
        this.rule = rule;
    }

    @Override
    public float advance(boolean bold) {
        return delegate.advance(bold);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public int height() {
        return delegate.height();
    }

    @Override
    public int ascent() {
        return delegate.ascent();
    }

    @Override
    public double inkLeftGuiPx() {
        return delegate.inkLeftGuiPx();
    }

    @Override
    public double inkRightGuiPx() {
        return delegate.inkRightGuiPx();
    }

    @Override
    public void draw(Graphics2D graphics, double xGuiPx, double lineTopGuiPx, int pixelSize, Color tint, boolean italic) {
        delegate.draw(graphics, xGuiPx, lineTopGuiPx, pixelSize, rule.effectiveTint(tint), italic);
    }
}
