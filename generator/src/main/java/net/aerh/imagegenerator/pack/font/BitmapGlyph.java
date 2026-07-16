package net.aerh.imagegenerator.pack.font;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A bitmap-provider glyph: an owned copy of its full sheet cell plus the vanilla-exact metrics.
 * The scaled (untinted) cell is cached per {@code pixelSize}, bounded to the
 * {@value #MAX_CACHED_PIXEL_SIZES} most recently used sizes; tint is applied per draw call so
 * one glyph instance serves every text color.
 */
final class BitmapGlyph implements PackGlyph {

    private static final int WHITE_OPAQUE = 0xFFFFFFFF;

    /**
     * How many distinct {@code pixelSize} scaled rasters one glyph retains, least recently used
     * evicted first. Renders use a single pixel size per generator and real workloads mix at
     * most a couple of scales concurrently (e.g. a scale-1 tooltip beside a scale-2 container
     * sharing one cached font), so two entries keep the hot path allocation-free while an
     * unbounded map - one raster PER DISTINCT pixel size, each up to
     * {@code height * cellWidth * pixelSize^2} pixels - could grow without limit on fonts held
     * in the pack font cache. Evicted sizes simply rescale from the owned cell on next use.
     */
    private static final int MAX_CACHED_PIXEL_SIZES = 2;

    private final BufferedImage cell;
    private final int inkWidth;
    private final int advance;
    private final int height;
    private final int ascent;
    private final float scale;
    /**
     * Access-ordered LRU bounded to {@link #MAX_CACHED_PIXEL_SIZES}; the synchronized wrapper
     * is the mutex for every access, matching the previous concurrent-map thread safety.
     */
    private final Map<Integer, BufferedImage> scaledCellCache =
        Collections.synchronizedMap(new LinkedHashMap<>(4, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest) {
                return size() > MAX_CACHED_PIXEL_SIZES;
            }
        });

    /**
     * @param cell     the glyph's full cell, copied out of the sheet (never a shared subimage);
     *                 null for a fully transparent cell, which draws nothing and retains no
     *                 raster memory
     * @param inkWidth index of the rightmost column with any alpha above zero, plus one; 0 for a
     *                 fully transparent cell
     * @param advance  unbold advance in GUI pixels: {@code (int) (0.5f + inkWidth * scale) + 1}
     * @param height   provider height field (GUI pixels the cell renders tall)
     * @param ascent   provider ascent field
     * @param scale    {@code height / cellHeight}
     */
    BitmapGlyph(BufferedImage cell, int inkWidth, int advance, int height, int ascent, float scale) {
        this.cell = cell;
        this.inkWidth = inkWidth;
        this.advance = advance;
        this.height = height;
        this.ascent = ascent;
        this.scale = scale;
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

    @Override
    public float advance(boolean bold) {
        return advance + (bold ? 1.0f : 0.0f);
    }

    @Override
    public boolean isEmpty() {
        return inkWidth == 0;
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
        if (cell == null) {
            // Fully transparent cell: drawing it would change no pixels, so no raster is kept.
            return;
        }
        BufferedImage scaled = scaledCellCache.computeIfAbsent(pixelSize, this::scaleCell);
        BufferedImage tinted = tinted(scaled, tint);
        double topGuiPx = lineTopGuiPx + 7 - ascent;
        if (!italic) {
            graphics.drawImage(tinted,
                (int) Math.round(xGuiPx * pixelSize),
                (int) Math.round(topGuiPx * pixelSize), null);
            return;
        }
        // Integer row shear at GUI-pixel granularity: each destination GUI row gets the vanilla
        // shear offset for its distance below the line top, rounded to the nearest canvas pixel.
        for (int row = 0; row < height; row++) {
            double guiPxBelowLineTop = (7.0 - ascent) + row;
            double shear = 1.0 - 0.25 * guiPxBelowLineTop;
            int destX = (int) Math.round((xGuiPx + shear) * pixelSize);
            int destY = (int) Math.round((topGuiPx + row) * pixelSize);
            BufferedImage band = tinted.getSubimage(0, row * pixelSize, tinted.getWidth(), pixelSize);
            graphics.drawImage(band, destX, destY, null);
        }
    }

    /**
     * Nearest-neighbor scales the full cell so it renders {@code height} GUI pixels tall at
     * {@code pixelSize} canvas pixels per GUI pixel. Explicit floor-mapping loop rather than
     * Graphics2D interpolation hints so the result is platform-deterministic.
     */
    private BufferedImage scaleCell(int pixelSize) {
        int destHeight = height * pixelSize;
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
