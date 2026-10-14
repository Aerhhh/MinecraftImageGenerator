package net.aerh.imagegenerator.tooling.spritesheet;

/** Fixed-grid atlas dimensions, including an optional transparent gutter. */
public record AtlasLayout(int tileSize, int maxWidth, int maxHeight, int padding) {
    public AtlasLayout {
        if (tileSize <= 0 || padding < 0 || padding > (Integer.MAX_VALUE - tileSize) / 2) {
            throw new IllegalArgumentException("Tile size must be positive and padding non-negative");
        }
        int pitch = tileSize + 2 * padding;
        if (maxWidth < pitch || maxHeight < pitch) {
            throw new IllegalArgumentException("Atlas bounds must fit at least one padded tile");
        }
    }

    public int pitch() { return tileSize + 2 * padding; }
    public int columns() { return maxWidth / pitch(); }
    public int capacity() {
        return (int) Math.min(Integer.MAX_VALUE, (long) columns() * (maxHeight / pitch()));
    }
}
