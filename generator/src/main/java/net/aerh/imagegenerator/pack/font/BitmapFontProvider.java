package net.aerh.imagegenerator.pack.font;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackResolveException;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A runtime bitmap provider: cuts the sheet into equal cells by the codepoint grid and computes
 * the vanilla-exact per-glyph metrics eagerly.
 *
 * <p>Vanilla math implemented here: {@code cellW = texW / charsPerRow},
 * {@code cellH = texH / rowCount} (leftover pixels beyond an exact multiple are unused, like
 * vanilla), {@code scale = height / cellH}, ink width = index of the rightmost column with any
 * alpha above zero plus one (leading blank columns count and are rendered),
 * {@code advance = (int) (0.5f + inkWidth * scale) + 1} (the +1 is added after rounding,
 * unscaled; a fully transparent cell has ink 0 and therefore advance 1). U+0000 grid entries are
 * skipped entirely; a duplicate codepoint within one provider keeps the LAST occurrence and
 * warns.
 *
 * <p>Each glyph owns a COPY of its cell so the (potentially very large) sheet is garbage after
 * construction instead of being pinned by subimage references. Fully transparent cells retain no
 * raster at all (they draw nothing), so sparse sheets cost only their inked cells.
 */
@Slf4j
final class BitmapFontProvider {

    private final Map<Integer, BitmapGlyph> glyphs;
    private final long retainedCellBytes;

    private BitmapFontProvider(Map<Integer, BitmapGlyph> glyphs) {
        this.glyphs = glyphs;
        this.retainedCellBytes = glyphs.values().stream().mapToLong(BitmapGlyph::retainedBytes).sum();
    }

    /**
     * @param fontId resolved font id, for log and error context only
     * @throws PackResolveException when the sheet is smaller than the grid (a zero-size cell)
     */
    static BitmapFontProvider create(FontProviderDefinition.Bitmap definition, BufferedImage sheet, String fontId) {
        List<String> rows = definition.charRows();
        int charsPerRow = rows.get(0).codePointCount(0, rows.get(0).length());
        int cellWidth = sheet.getWidth() / charsPerRow;
        int cellHeight = sheet.getHeight() / rows.size();
        if (cellWidth <= 0 || cellHeight <= 0) {
            throw new PackResolveException(
                "Font `%s`: bitmap texture `%s` (%sx%s) is too small for its %sx%s glyph grid",
                fontId, definition.file(), String.valueOf(sheet.getWidth()), String.valueOf(sheet.getHeight()),
                String.valueOf(charsPerRow), String.valueOf(rows.size()));
        }
        float scale = definition.height() / (float) cellHeight;
        Map<Integer, BitmapGlyph> glyphs = new HashMap<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String row = rows.get(rowIndex);
            int column = 0;
            for (int i = 0; i < row.length(); column++) {
                int codePoint = row.codePointAt(i);
                i += Character.charCount(codePoint);
                if (codePoint == 0) {
                    // U+0000 marks an unused cell; skipped entirely (no glyph, no duplicate check).
                    continue;
                }
                int inkWidth = inkWidth(sheet, column * cellWidth, rowIndex * cellHeight, cellWidth, cellHeight);
                // Empty cells (ink 0) keep no raster: they draw nothing, only their advance counts.
                BufferedImage cell = inkWidth == 0 ? null
                    : copyCell(sheet, column * cellWidth, rowIndex * cellHeight, cellWidth, cellHeight);
                int advance = (int) (0.5f + inkWidth * scale) + 1;
                BitmapGlyph previous = glyphs.put(codePoint,
                    new BitmapGlyph(cell, inkWidth, advance, definition.height(), definition.ascent(), scale));
                if (previous != null) {
                    log.warn("Font {}: duplicate codepoint U+{} in bitmap provider {}; last occurrence wins",
                        fontId, Integer.toHexString(codePoint).toUpperCase(), definition.file());
                }
            }
        }
        return new BitmapFontProvider(glyphs);
    }

    /** The glyph for a codepoint, or null when this provider's grid does not map it. */
    BitmapGlyph glyph(int codePoint) {
        return glyphs.get(codePoint);
    }

    /** An unmodifiable view of every codepoint this provider's grid maps. */
    Set<Integer> codePoints() {
        return Collections.unmodifiableSet(glyphs.keySet());
    }

    /** Total bytes retained by this provider's copied cell rasters (empty cells retain none). */
    long retainedCellBytes() {
        return retainedCellBytes;
    }

    private static BufferedImage copyCell(BufferedImage sheet, int x, int y, int width, int height) {
        BufferedImage cell = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = sheet.getRGB(x, y, width, height, null, 0, width);
        cell.setRGB(0, 0, width, height, pixels, 0, width);
        return cell;
    }

    /**
     * Scans the FULL cell region of the sheet (leading blank columns included) for the rightmost
     * column containing any pixel with alpha above zero.
     */
    private static int inkWidth(BufferedImage sheet, int cellX, int cellY, int width, int height) {
        for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                if ((sheet.getRGB(cellX + x, cellY + y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }
}
