package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackLimitsTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("generator.pack.maxEntries");
        System.clearProperty("generator.pack.maxEntryBytes");
        System.clearProperty("generator.pack.maxTextureDim");
        System.clearProperty("generator.pack.textureCache.maxBytes");
    }

    @Test
    void defaultsAreSecure() {
        PackLimits limits = PackLimits.fromSystemProperties();
        assertEquals(20_000, limits.maxEntries());
        assertEquals(8L * 1024 * 1024, limits.maxEntryBytes());
        assertEquals(1_024, limits.maxTextureDim());
        assertEquals(64L * 1024 * 1024, limits.textureCacheMaxBytes());
    }

    @Test
    void systemPropertiesOverrideDefaults() {
        System.setProperty("generator.pack.maxEntries", "5");
        System.setProperty("generator.pack.maxEntryBytes", "1024");
        System.setProperty("generator.pack.maxTextureDim", "64");
        System.setProperty("generator.pack.textureCache.maxBytes", "2048");
        PackLimits limits = PackLimits.fromSystemProperties();
        assertEquals(5, limits.maxEntries());
        assertEquals(1024L, limits.maxEntryBytes());
        assertEquals(64, limits.maxTextureDim());
        assertEquals(2048L, limits.textureCacheMaxBytes());
    }

    @Test
    void boundedReadLimitIsCapPlusOne() {
        assertEquals(33, new PackLimits(1, 32, 1, 1).boundedReadLimit());
    }

    @Test
    void boundedReadLimitClampsHugeCaps() {
        assertEquals(Integer.MAX_VALUE - 8, new PackLimits(1, Long.MAX_VALUE, 1, 1).boundedReadLimit());
    }
}
