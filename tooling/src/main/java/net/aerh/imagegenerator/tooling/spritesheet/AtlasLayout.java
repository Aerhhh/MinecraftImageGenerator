package net.aerh.imagegenerator.tooling.spritesheet;

import net.aerh.imagegenerator.tooling.CliArgs;

/**
 * Fixed-grid atlas dimensions: square tiles of {@code tileSize} pixels, each wrapped in a transparent gutter of
 * {@code padding} pixels on every side, packed row by row into pages no larger than {@code maxWidth} by
 * {@code maxHeight}.
 */
public record AtlasLayout(int tileSize, int maxWidth, int maxHeight, int padding) {

    /** Height value meaning "one page, however tall it needs to be". */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    public static final int DEFAULT_TILE_SIZE = 256;

    /** The layout of the atlas bundled with the generator module: 256px tiles, 16 per row, single page, no gutter. */
    public static final AtlasLayout DEFAULT = new AtlasLayout(DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE * 16, UNLIMITED, 0);

    public static final String TILE_SIZE_FLAG = "--tile-size";
    public static final String ATLAS_WIDTH_FLAG = "--atlas-width";
    public static final String ATLAS_HEIGHT_FLAG = "--atlas-height";
    public static final String PADDING_FLAG = "--padding";

    public AtlasLayout {
        if (tileSize <= 0) {
            throw new IllegalArgumentException("Tile size must be positive, got " + tileSize);
        }

        if (padding < 0) {
            throw new IllegalArgumentException("Padding must be non-negative, got " + padding);
        }

        if (padding > (Integer.MAX_VALUE - tileSize) / 2) {
            throw new IllegalArgumentException("Padding " + padding + " is too large for tile size " + tileSize);
        }

        int pitch = tileSize + 2 * padding;
        if (maxWidth < pitch || maxHeight < pitch) {
            throw new IllegalArgumentException("Atlas bounds " + maxWidth + "x" + maxHeight
                + " must fit at least one padded tile of " + pitch + "px");
        }
    }

    /**
     * Reads the four layout flags, keeping {@code defaults} for any that are absent, and validates the result.
     *
     * @throws IllegalArgumentException when a value is not an integer or the combination is not a usable layout
     */
    public static AtlasLayout fromArgs(String[] args, AtlasLayout defaults) {
        return new AtlasLayout(
            CliArgs.intValue(args, TILE_SIZE_FLAG, defaults.tileSize()),
            CliArgs.intValue(args, ATLAS_WIDTH_FLAG, defaults.maxWidth()),
            CliArgs.intValue(args, ATLAS_HEIGHT_FLAG, defaults.maxHeight()),
            CliArgs.intValue(args, PADDING_FLAG, defaults.padding())
        );
    }

    /** Distance between the top-left corners of neighbouring tiles. */
    public int pitch() {
        return tileSize + 2 * padding;
    }

    public int columns() {
        return maxWidth / pitch();
    }

    public int rowsPerPage() {
        return maxHeight / pitch();
    }

    /** Tiles per page, saturating at {@link Integer#MAX_VALUE} for unlimited heights. */
    public int capacity() {
        return (int) Math.min(Integer.MAX_VALUE, (long) columns() * rowsPerPage());
    }
}
