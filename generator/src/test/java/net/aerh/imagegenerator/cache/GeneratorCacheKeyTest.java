package net.aerh.imagegenerator.cache;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Two different configurations must never share a cache key, or one request is served another's image. */
class GeneratorCacheKeyTest {

    private record TwoFields(String lore, String name) {
    }

    private record OneField(String lore) {
    }

    @Test
    void equalConfigurationsShareAKey() {
        assertEquals(GeneratorCacheKey.fromGenerator(new TwoFields("a", "b")),
            GeneratorCacheKey.fromGenerator(new TwoFields("a", "b")));
    }

    @Test
    void valueContainingAFieldSeparatorDoesNotCollide() {
        // Without length prefixes both flatten to "lore=x;name=y;name=z;"
        String first = GeneratorCacheKey.fromGenerator(new TwoFields("x;name=y", "z"));
        String second = GeneratorCacheKey.fromGenerator(new TwoFields("x", "y;name=z"));
        assertNotEquals(first, second);
    }

    @Test
    void shortValueSpellingAHashDoesNotCollideWithTheLongValue() throws Exception {
        String longLore = "l".repeat(200);
        String hashOfLongLore = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(longLore.getBytes(StandardCharsets.UTF_8)));
        assertEquals(64, hashOfLongLore.length());

        assertNotEquals(GeneratorCacheKey.fromGenerator(new OneField(longLore)),
            GeneratorCacheKey.fromGenerator(new OneField(hashOfLongLore)));
    }
}
