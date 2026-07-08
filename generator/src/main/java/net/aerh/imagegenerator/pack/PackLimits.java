package net.aerh.imagegenerator.pack;

/**
 * Defensive limits applied while reading pack files. All values come from {@code generator.pack.*}
 * system properties and default to secure values (on-by-default posture).
 */
public record PackLimits(int maxEntries, long maxEntryBytes, int maxTextureDim, long textureCacheMaxBytes) {

    public static PackLimits fromSystemProperties() {
        return new PackLimits(
            Integer.getInteger("generator.pack.maxEntries", 20_000),
            Long.getLong("generator.pack.maxEntryBytes", 8L * 1024 * 1024),
            Integer.getInteger("generator.pack.maxTextureDim", 1_024),
            Long.getLong("generator.pack.textureCache.maxBytes", 64L * 1024 * 1024)
        );
    }
}
