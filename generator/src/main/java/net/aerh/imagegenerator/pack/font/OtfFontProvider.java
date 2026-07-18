package net.aerh.imagegenerator.pack.font;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * A runtime TTF provider (Tier 1): owns ONE {@link java.awt.Font} for the pack, derived once to the
 * provider's {@code ppem = round(size * oversample)} device pixel size, and rasterizes glyphs from
 * it on demand. Glyphs are cut lazily (a real font covers thousands of codepoints; a HUD text run
 * touches a few dozen) and cached in a bounded per-codepoint cache; rasterization is deterministic,
 * so an evicted glyph re-rasterizes to identical pixels within a run.
 *
 * <p><b>Isolation:</b> the {@link java.awt.Font} is created with {@link Font#createFont} and kept
 * private to this provider - it is NEVER {@link java.awt.GraphicsEnvironment#registerFont
 * registered} globally. Global registration leaks a pack's fonts into every other pack's rendering
 * and is nondeterministic across JVMs; keeping the instance private means two packs shipping a
 * same-named font never collide.
 *
 * <p>Coverage is {@code font.canDisplay(cp)} AND {@code cp} not in the provider {@code skip} set.
 * A codepoint the font cannot display, or one it skips, is not claimed - a later provider serves it,
 * exactly like any first-wins provider.
 */
@Slf4j
final class OtfFontProvider {

    /**
     * How many distinct rasterized glyphs one provider retains. A text run touches a bounded set
     * of codepoints (Latin plus a few symbols for HUD/menu lettering); the cap keeps a pathological
     * font from pinning unbounded rasters while eviction stays harmless (deterministic re-raster).
     */
    private static final int MAX_CACHED_GLYPHS = 512;

    /**
     * Antialiasing OFF, fractional metrics OFF - the aliased integer-metric context the rest of
     * the text path uses. Shared by measurement ({@link GlyphVector#getPixelBounds},
     * {@link java.awt.font.GlyphMetrics#getAdvanceX}) and the raster graphics so the two never
     * disagree on a glyph's bounds or advance.
     */
    private static final FontRenderContext ALIASED = new FontRenderContext(new AffineTransform(), false, false);

    private final Font ppemFont;
    private final int oversample;
    private final int ppem;
    private final float shiftX;
    private final float shiftY;
    private final Set<Integer> skip;
    private final Cache<Integer, OtfGlyph> glyphs = Caffeine.newBuilder()
        .maximumSize(MAX_CACHED_GLYPHS)
        .build();

    private OtfFontProvider(Font ppemFont, int oversample, int ppem, float shiftX, float shiftY, Set<Integer> skip) {
        this.ppemFont = ppemFont;
        this.oversample = oversample;
        this.ppem = ppem;
        this.shiftX = shiftX;
        this.shiftY = shiftY;
        this.skip = skip;
    }

    /**
     * Builds the provider for a TTF definition when its font file resolves from the pack AND
     * loads. A missing file (loader returns empty) or an unparseable font degrades to empty, so
     * the caller falls back to the never-claim unsupported entry and a later provider serves the
     * codepoints - a broken TTF never throws.
     *
     * <p>{@code oversample} is used as its rounded-to-nearest positive integer for the raster grid
     * (real packs author integral oversample; a clean {@code 1 / oversample} GUI-pixel mapping
     * requires it), and {@code ppem = round(size * oversample)} at that integer.
     *
     * @param fontId resolved font id, for log context only
     */
    static Optional<OtfFontProvider> tryCreate(FontProviderDefinition.Ttf definition,
                                               PackFont.TextureLoader loader, String fontId) {
        Optional<byte[]> bytes = loader.ttfFontData(definition.file());
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        int oversample = Math.max(1, Math.round(definition.oversample()));
        int ppem = Math.max(1, Math.round(definition.size() * oversample));
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes.get()));
            Font ppemFont = base.deriveFont((float) ppem);
            return Optional.of(new OtfFontProvider(ppemFont, oversample, ppem,
                definition.shiftX(), definition.shiftY(), definition.skip()));
        } catch (FontFormatException | IOException e) {
            log.warn("Font `{}`: TTF `{}` failed to load ({}); a later provider will serve its codepoints",
                fontId, definition.file(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The glyph for a codepoint, or null when this provider does not claim it (the font cannot
     * display it, or it is in the {@code skip} set). Rasterized on first request and cached.
     */
    OtfGlyph glyph(int codePoint) {
        if (skip.contains(codePoint) || !ppemFont.canDisplay(codePoint)) {
            return null;
        }
        return glyphs.get(codePoint, this::rasterize);
    }

    /** Total bytes retained by this provider's rasterized cell rasters so far (an estimate). */
    long retainedCellBytes() {
        return glyphs.asMap().values().stream().mapToLong(OtfGlyph::retainedBytes).sum();
    }

    /**
     * The bounded worst-case bytes this provider's glyph cache can retain, used for font-cache
     * weighing. Glyphs rasterize LAZILY, so at font-insertion time {@link #retainedCellBytes()}
     * is zero and the font cache (which weighs a font once, at insertion, and never re-weighs)
     * would account a TTF font at nothing while it lazily fills up to {@link #MAX_CACHED_GLYPHS}
     * cells. Reserving the cap up front - each cell estimated at an em-box-sized device raster
     * ({@code 4 ARGB bytes * ppem * ppem}) - accounts a TTF font at the memory it can hold, so it
     * obeys the same budget bitmap fonts do (whose cells are cut eagerly and weighed exactly). An
     * estimate: glyph cells vary around the em box, but their COUNT is hard-capped.
     */
    long maxRetainedCellBytes() {
        return (long) MAX_CACHED_GLYPHS * 4L * ppem * ppem;
    }

    /**
     * Rasterizes one glyph at {@code ppem} device pixels into a tight cell snapped to
     * oversample-multiple boundaries (so it downscales to whole GUI pixels), computing the advance
     * and the vanilla-baseline placement.
     */
    private OtfGlyph rasterize(int codePoint) {
        String text = new String(Character.toChars(codePoint));
        GlyphVector vector = ppemFont.createGlyphVector(ALIASED, text);
        // Advance rounded to integer device px at ppem, then divided by oversample to GUI px; a
        // pixel font authored at integral ppem yields whole-GUI-px advances (28 / 4 = 7).
        float advanceGuiPx = Math.round(vector.getGlyphMetrics(0).getAdvanceX()) / (float) oversample;
        Rectangle ink = vector.getPixelBounds(ALIASED, 0.0f, 0.0f);
        if (ink.width <= 0 || ink.height <= 0) {
            // No ink (e.g. the space): nothing to draw, the advance still counts.
            return new OtfGlyph(new GlyphCell(null, 0, 1.0f / oversample), advanceGuiPx, 0.0, 0.0, 0, 0);
        }
        int cellLeft = floorToMultiple(Math.min(0, ink.x), oversample);
        int cellRight = ceilToMultiple(ink.x + ink.width, oversample);
        // ink.y is negative for the (usual) part of the glyph above the baseline.
        int cellTop = floorToMultiple(ink.y, oversample);
        int cellBottom = ceilToMultiple(ink.y + ink.height, oversample);
        BufferedImage cellImage = new BufferedImage(cellRight - cellLeft, cellBottom - cellTop,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cellImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            graphics.setColor(Color.WHITE);
            // Pen origin placed so the baseline (y = 0) and the left side bearing land in-cell.
            graphics.drawGlyphVector(vector, (float) -cellLeft, (float) -cellTop);
        } finally {
            graphics.dispose();
        }
        if (!hasInk(cellImage)) {
            // A glyph the predicted bounds flagged as inked but that rasterizes to nothing (e.g. a
            // blank outline, which reports a degenerate 1x1 bounds): draw nothing, still advance.
            return new OtfGlyph(new GlyphCell(null, 0, 1.0f / oversample), advanceGuiPx, 0.0, 0.0, 0, 0);
        }
        int renderedGuiWidth = (cellRight - cellLeft) / oversample;
        int renderedGuiHeight = (cellBottom - cellTop) / oversample;
        int cellAscentGuiPx = -cellTop / oversample;
        double leftOffsetGuiPx = cellLeft / (double) oversample + shiftX;
        // Baseline sits 7 GUI px below the line top, shifted down by shiftY; the cell top is the
        // cell's ascent above that baseline.
        double cellTopRelLineTop = 7.0 + shiftY - cellAscentGuiPx;
        GlyphCell cell = new GlyphCell(cellImage, renderedGuiHeight, 1.0f / oversample);
        return new OtfGlyph(cell, advanceGuiPx, leftOffsetGuiPx, cellTopRelLineTop, renderedGuiWidth,
            renderedGuiHeight);
    }

    /** The device pixel size the font rasterizes at ({@code round(size * oversample)}); for tests. */
    int ppem() {
        return ppem;
    }

    private static boolean hasInk(BufferedImage cell) {
        for (int y = 0; y < cell.getHeight(); y++) {
            for (int x = 0; x < cell.getWidth(); x++) {
                if ((cell.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int floorToMultiple(int value, int multiple) {
        return Math.floorDiv(value, multiple) * multiple;
    }

    private static int ceilToMultiple(int value, int multiple) {
        return Math.floorDiv(value + multiple - 1, multiple) * multiple;
    }
}
