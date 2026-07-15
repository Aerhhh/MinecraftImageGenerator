package net.aerh.imagegenerator.pack;

/**
 * Defensive limits applied while reading pack files. All values come from {@code generator.pack.*}
 * system properties and default to secure values (on-by-default posture).
 *
 * <p><b>Sizing for large server packs:</b> the defaults comfortably fit vanilla-scale packs, but
 * large server resource packs can exceed them by a wide margin - Wynncraft-class packs are on the
 * order of ~36,000 files, well past the default {@link #maxEntries()} of 20,000. What
 * {@code maxEntries} counts depends on the source type: for ZIP sources it bounds ALL
 * central-directory records (regular files AND directory entries, so a ZIP's effective count is
 * higher than its file count), while for directory sources it bounds only the regular files under
 * the listed prefix (in practice {@code assets/}). To load such a pack, construct one
 * {@link PackLimits} with {@code maxEntries} comfortably above the pack's record count and pass
 * the SAME instance to both the {@link PackSource} factory and
 * {@link PackRepository#register(String, PackSource, PackLimits)} so read-time and index-time
 * limits agree.
 *
 * <p><b>Font textures get their own cap:</b> {@link #maxTextureDim()} is the image-bomb guard
 * for item and GUI sprite textures, where vanilla-scale art stays far below the 1024 default.
 * Font glyph sheets are legitimately much larger - real server packs ship sheets in the
 * 2048-8192 pixel range - so font sheet decodes are bounded by the separate
 * {@link #fontTextureMaxDim()} (default 8192, {@code generator.pack.fontTextureMaxDim}). The
 * 4-argument constructor keeps the pre-font record shape working and applies the font default.
 */
public record PackLimits(int maxEntries, long maxEntryBytes, int maxTextureDim, long textureCacheMaxBytes,
                         int fontTextureMaxDim) {

    private static final int DEFAULT_FONT_TEXTURE_MAX_DIM = 8_192;

    /**
     * Compatibility constructor predating font support: applies the default
     * {@link #fontTextureMaxDim()} of 8192.
     */
    public PackLimits(int maxEntries, long maxEntryBytes, int maxTextureDim, long textureCacheMaxBytes) {
        this(maxEntries, maxEntryBytes, maxTextureDim, textureCacheMaxBytes, DEFAULT_FONT_TEXTURE_MAX_DIM);
    }

    public PackLimits {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException(
                "generator.pack.maxEntries must be positive, got: " + maxEntries);
        }
        if (maxEntryBytes <= 0) {
            throw new IllegalArgumentException(
                "generator.pack.maxEntryBytes must be positive, got: " + maxEntryBytes);
        }
        if (maxTextureDim <= 0) {
            throw new IllegalArgumentException(
                "generator.pack.maxTextureDim must be positive, got: " + maxTextureDim);
        }
        if (textureCacheMaxBytes <= 0) {
            throw new IllegalArgumentException(
                "generator.pack.textureCache.maxBytes must be positive, got: " + textureCacheMaxBytes);
        }
        if (fontTextureMaxDim <= 0) {
            throw new IllegalArgumentException(
                "generator.pack.fontTextureMaxDim must be positive, got: " + fontTextureMaxDim);
        }
    }

    public static PackLimits fromSystemProperties() {
        return new PackLimits(
            Integer.getInteger("generator.pack.maxEntries", 20_000),
            Long.getLong("generator.pack.maxEntryBytes", 8L * 1024 * 1024),
            Integer.getInteger("generator.pack.maxTextureDim", 1_024),
            Long.getLong("generator.pack.textureCache.maxBytes", 64L * 1024 * 1024),
            Integer.getInteger("generator.pack.fontTextureMaxDim", DEFAULT_FONT_TEXTURE_MAX_DIM)
        );
    }

    /**
     * The array-safe number of bytes to request when enforcing {@link #maxEntryBytes()} with a
     * single bounded read: cap + 1 so an over-limit entry is detectable, clamped to the maximum
     * JVM array size for huge caps.
     */
    public int boundedReadLimit() {
        return maxEntryBytes() >= Integer.MAX_VALUE - 8 ? Integer.MAX_VALUE - 8 : (int) maxEntryBytes() + 1;
    }
}
