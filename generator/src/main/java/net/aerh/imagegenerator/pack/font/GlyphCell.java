package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The shared raster-cell machinery behind every drawn {@link PackGlyph}: an owned cell image, the
 * nearest-neighbor scale to GUI pixels, multiplicative tint and the vanilla integer italic
 * row-shear, plus the bounded per-{@code pixelSize} scaled-raster cache. Extracted from
 * {@link BitmapGlyph} so {@link BitmapGlyph} (cell cut from a PNG sheet) and {@link OtfGlyph} (cell
 * rasterized from a TTF outline) stay pixel-consistent for scale, tint, italic, bold and shadow -
 * the callers differ only in where the cell and its vertical placement come from.
 *
 * <p>The scaled (untinted) cell is cached per {@code pixelSize}, bounded to the
 * {@value #MAX_CACHED_PIXEL_SIZES} most recently used sizes; tint is applied per draw call so one
 * cell serves every text color. A {@code null} cell (fully transparent glyph) draws nothing and
 * retains no raster.
 */
final class GlyphCell {

    private static final int WHITE_OPAQUE = 0xFFFFFFFF;

    /**
     * How many distinct {@code pixelSize} scaled rasters one cell retains, least recently used
     * evicted first. Renders use a single pixel size per generator and real workloads mix at
     * most a couple of scales concurrently (e.g. a scale-1 tooltip beside a scale-2 container
     * sharing one cached font), so two entries keep the hot path allocation-free while an
     * unbounded map - one raster PER DISTINCT pixel size, each up to
     * {@code renderedGuiHeight * cellWidth * pixelSize^2} pixels - could grow without limit on
     * fonts held in the pack font cache. Evicted sizes simply rescale from the owned cell on next
     * use.
     */
    private static final int MAX_CACHED_PIXEL_SIZES = 2;

    private final BufferedImage cell;
    private final int renderedGuiHeight;
    private final float scale;
    /**
     * Access-ordered LRU bounded to {@link #MAX_CACHED_PIXEL_SIZES}; the synchronized wrapper is
     * the mutex for every access, matching the previous concurrent-map thread safety.
     */
    private final Map<Integer, BufferedImage> scaledCellCache =
        Collections.synchronizedMap(new LinkedHashMap<>(4, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest) {
                return size() > MAX_CACHED_PIXEL_SIZES;
            }
        });

    /**
     * @param cell              the glyph's full cell (never a shared subimage); {@code null} for a
     *                          fully transparent cell, which draws nothing and retains no raster
     * @param renderedGuiHeight GUI pixels the cell renders tall (its vertical size in the 1x GUI
     *                          coordinate space); {@code cell.getHeight() * scale}
     * @param scale             GUI pixels per cell pixel horizontally ({@code renderedGuiHeight /
     *                          cell.getHeight()} for a bitmap cell, {@code 1 / oversample} for a
     *                          TTF cell)
     */
    GlyphCell(BufferedImage cell, int renderedGuiHeight, float scale) {
        this.cell = cell;
        this.renderedGuiHeight = renderedGuiHeight;
        this.scale = scale;
    }

    /** Whether the cell draws nothing (fully transparent, so no raster is retained). */
    boolean isEmpty() {
        return cell == null;
    }

    /** Bytes retained by the owned cell raster (ARGB, 4 bytes per pixel); 0 for an empty cell. */
    long retainedBytes() {
        return cell == null ? 0 : 4L * cell.getWidth() * cell.getHeight();
    }

    /** The pixel sizes currently holding a cached scaled raster; for tests pinning the LRU cap. */
    Set<Integer> cachedScaledPixelSizes() {
        synchronized (scaledCellCache) {
            // Iterating a synchronized map requires holding its mutex; copy inside the lock.
            return Set.copyOf(scaledCellCache.keySet());
        }
    }

    /**
     * Draws the cell nearest-neighbor scaled so it renders {@code renderedGuiHeight} GUI pixels
     * tall, top-left corner at {@code (leftGuiPx, cellTopGuiPx)} GUI pixels, tinted
     * multiplicatively. Italic applies the vanilla shear per destination GUI-pixel row: x offset
     * {@code 1 - 0.25 * guiPxBelowLineTop}, where {@code guiPxBelowLineTop} is the row's distance
     * below {@code lineTopGuiPx}, rounded to the nearest canvas pixel per row. A {@code null} or
     * zero-height cell draws nothing.
     *
     * @param graphics      target canvas graphics (default SrcOver compositing is used)
     * @param leftGuiPx     left edge of the cell in GUI pixels (fractional legal)
     * @param lineTopGuiPx  top of the text line in GUI pixels (the italic shear reference)
     * @param cellTopGuiPx  top edge of the cell in GUI pixels (fractional legal); the caller
     *                      derives this from the glyph's vertical placement model
     * @param pixelSize     canvas pixels per GUI pixel, at least 1
     * @param tint          multiplicative tint; white or null leaves the cell unchanged
     * @param italic        whether to apply the italic shear
     */
    void draw(Graphics2D graphics, double leftGuiPx, double lineTopGuiPx, double cellTopGuiPx,
              int pixelSize, Color tint, boolean italic) {
        if (cell == null || renderedGuiHeight <= 0) {
            return;
        }
        BufferedImage scaled = scaledCellCache.computeIfAbsent(pixelSize, this::scaleCell);
        BufferedImage tinted = tinted(scaled, tint);
        if (!italic) {
            graphics.drawImage(tinted,
                (int) Math.round(leftGuiPx * pixelSize),
                (int) Math.round(cellTopGuiPx * pixelSize), null);
            return;
        }
        // Integer row shear at GUI-pixel granularity: each destination GUI row gets the vanilla
        // shear offset for its distance below the line top, rounded to the nearest canvas pixel.
        for (int row = 0; row < renderedGuiHeight; row++) {
            double guiPxBelowLineTop = (cellTopGuiPx - lineTopGuiPx) + row;
            double shear = 1.0 - 0.25 * guiPxBelowLineTop;
            int destX = (int) Math.round((leftGuiPx + shear) * pixelSize);
            int destY = (int) Math.round((cellTopGuiPx + row) * pixelSize);
            BufferedImage band = tinted.getSubimage(0, row * pixelSize, tinted.getWidth(), pixelSize);
            graphics.drawImage(band, destX, destY, null);
        }
    }

    /**
     * Nearest-neighbor scales the full cell so it renders {@code renderedGuiHeight} GUI pixels
     * tall at {@code pixelSize} canvas pixels per GUI pixel. Explicit floor-mapping loop rather
     * than Graphics2D interpolation hints so the result is platform-deterministic.
     */
    private BufferedImage scaleCell(int pixelSize) {
        int destHeight = renderedGuiHeight * pixelSize;
        int destWidth = Math.max(1, Math.round(cell.getWidth() * scale * pixelSize));
        BufferedImage scaled = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_ARGB);
        for (int destY = 0; destY < destHeight; destY++) {
            int srcY = (int) ((long) destY * cell.getHeight() / destHeight);
            for (int destX = 0; destX < destWidth; destX++) {
                int srcX = (int) ((long) destX * cell.getWidth() / destWidth);
                scaled.setRGB(destX, destY, cell.getRGB(srcX, srcY));
            }
        }
        return scaled;
    }

    /**
     * Multiplies every pixel by the tint, all four channels, each product rounded to nearest
     * ({@code round(channel * tintChannel / 255)}). White opaque (or null) tint returns the
     * cached image unchanged.
     */
    private static BufferedImage tinted(BufferedImage image, Color tint) {
        if (tint == null || tint.getRGB() == WHITE_OPAQUE) {
            return image;
        }
        int tintAlpha = tint.getAlpha();
        int tintRed = tint.getRed();
        int tintGreen = tint.getGreen();
        int tintBlue = tint.getBlue();
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = Math.round(((argb >>> 24) & 0xFF) * tintAlpha / 255.0f);
                int red = Math.round(((argb >> 16) & 0xFF) * tintRed / 255.0f);
                int green = Math.round(((argb >> 8) & 0xFF) * tintGreen / 255.0f);
                int blue = Math.round((argb & 0xFF) * tintBlue / 255.0f);
                result.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return result;
    }
}
