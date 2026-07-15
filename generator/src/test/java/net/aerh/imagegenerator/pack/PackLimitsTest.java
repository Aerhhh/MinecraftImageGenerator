package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackLimitsTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("generator.pack.maxEntries");
        System.clearProperty("generator.pack.maxEntryBytes");
        System.clearProperty("generator.pack.maxTextureDim");
        System.clearProperty("generator.pack.textureCache.maxBytes");
        System.clearProperty("generator.pack.fontTextureMaxDim");
    }

    @Test
    void defaultsAreSecure() {
        PackLimits limits = PackLimits.fromSystemProperties();
        assertEquals(20_000, limits.maxEntries());
        assertEquals(8L * 1024 * 1024, limits.maxEntryBytes());
        assertEquals(1_024, limits.maxTextureDim());
        assertEquals(64L * 1024 * 1024, limits.textureCacheMaxBytes());
        assertEquals(8_192, limits.fontTextureMaxDim());
    }

    @Test
    void systemPropertiesOverrideDefaults() {
        System.setProperty("generator.pack.maxEntries", "5");
        System.setProperty("generator.pack.maxEntryBytes", "1024");
        System.setProperty("generator.pack.maxTextureDim", "64");
        System.setProperty("generator.pack.textureCache.maxBytes", "2048");
        System.setProperty("generator.pack.fontTextureMaxDim", "256");
        PackLimits limits = PackLimits.fromSystemProperties();
        assertEquals(5, limits.maxEntries());
        assertEquals(1024L, limits.maxEntryBytes());
        assertEquals(64, limits.maxTextureDim());
        assertEquals(2048L, limits.textureCacheMaxBytes());
        assertEquals(256, limits.fontTextureMaxDim());
    }

    @Test
    void fourArgConstructorAppliesDefaultFontTextureCap() {
        assertEquals(8_192, new PackLimits(1, 32, 1, 1).fontTextureMaxDim());
    }

    @Test
    void boundedReadLimitIsCapPlusOne() {
        assertEquals(33, new PackLimits(1, 32, 1, 1).boundedReadLimit());
    }

    @Test
    void boundedReadLimitClampsHugeCaps() {
        assertEquals(Integer.MAX_VALUE - 8, new PackLimits(1, Long.MAX_VALUE, 1, 1).boundedReadLimit());
    }

    @Test
    void allPositiveValuesConstructSuccessfully() {
        PackLimits limits = new PackLimits(1, 1, 1, 1);
        assertEquals(1, limits.maxEntries());
        assertEquals(1L, limits.maxEntryBytes());
        assertEquals(1, limits.maxTextureDim());
        assertEquals(1L, limits.textureCacheMaxBytes());
    }

    @Test
    void maxEntriesZeroThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(0, 32, 1024, 1024));
        assertTrue(exception.getMessage().contains("generator.pack.maxEntries"));
    }

    @Test
    void maxEntriesNegativeThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(-5, 32, 1024, 1024));
        assertTrue(exception.getMessage().contains("generator.pack.maxEntries"));
        assertTrue(exception.getMessage().contains("-5"));
    }

    @Test
    void maxEntryBytesZeroThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, 0, 1024, 1024));
        assertTrue(exception.getMessage().contains("generator.pack.maxEntryBytes"));
    }

    @Test
    void maxEntryBytesNegativeThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, -5, 1024, 1024));
        assertTrue(exception.getMessage().contains("generator.pack.maxEntryBytes"));
        assertTrue(exception.getMessage().contains("-5"));
    }

    @Test
    void maxTextureDimZeroThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, 32, 0, 1024));
        assertTrue(exception.getMessage().contains("generator.pack.maxTextureDim"));
    }

    @Test
    void maxTextureDimNegativeThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, 32, -5, 1024));
        assertTrue(exception.getMessage().contains("generator.pack.maxTextureDim"));
        assertTrue(exception.getMessage().contains("-5"));
    }

    @Test
    void textureCacheMaxBytesZeroThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, 32, 1024, 0));
        assertTrue(exception.getMessage().contains("generator.pack.textureCache.maxBytes"));
    }

    @Test
    void textureCacheMaxBytesNegativeThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, 32, 1024, -5));
        assertTrue(exception.getMessage().contains("generator.pack.textureCache.maxBytes"));
        assertTrue(exception.getMessage().contains("-5"));
    }

    @Test
    void fontTextureMaxDimZeroThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, 32, 1024, 1024, 0));
        assertTrue(exception.getMessage().contains("generator.pack.fontTextureMaxDim"));
    }

    @Test
    void fontTextureMaxDimNegativeThrowsNamingTheKnob() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PackLimits(100, 32, 1024, 1024, -5));
        assertTrue(exception.getMessage().contains("generator.pack.fontTextureMaxDim"));
        assertTrue(exception.getMessage().contains("-5"));
    }
}
