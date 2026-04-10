package net.aerh.jigsaw.core.sprite;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AtlasSpriteProvider#searchAll(String)} and
 * {@link AtlasSpriteProvider#getAllSprites()}.
 */
class AtlasSpriteProviderSearchAllTest {

    private static AtlasSpriteProvider provider;

    @BeforeAll
    static void setUp() {
        provider = AtlasSpriteProvider.fromDefaults();
    }

    // --- searchAll ---

    @Test
    void searchAll_nullQueryThrows() {
        assertThatThrownBy(() -> provider.searchAll(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void searchAll_noMatchReturnsEmptyList() {
        List<Map.Entry<String, BufferedImage>> results = provider.searchAll("zzz_no_such_item_zzz");
        assertThat(results).isEmpty();
    }

    @Test
    void searchAll_knownPartialMatchReturnsResults() {
        // "sword" matches diamond_sword, iron_sword, etc.
        List<Map.Entry<String, BufferedImage>> results = provider.searchAll("sword");
        assertThat(results).isNotEmpty();
    }

    @Test
    void searchAll_resultsContainOnlyMatchingEntries() {
        String query = "sword";
        List<Map.Entry<String, BufferedImage>> results = provider.searchAll(query);
        for (Map.Entry<String, BufferedImage> entry : results) {
            assertThat(entry.getKey().toLowerCase()).contains(query.toLowerCase());
        }
    }

    @Test
    void searchAll_resultsSortedAlphabetically() {
        List<Map.Entry<String, BufferedImage>> results = provider.searchAll("sword");
        assertThat(results).isNotEmpty();
        List<String> names = results.stream().map(Map.Entry::getKey).toList();
        List<String> sorted = names.stream().sorted().toList();
        assertThat(names).isEqualTo(sorted);
    }

    @Test
    void searchAll_allEntriesHaveNonNullImages() {
        List<Map.Entry<String, BufferedImage>> results = provider.searchAll("stone");
        assertThat(results).isNotEmpty();
        for (Map.Entry<String, BufferedImage> entry : results) {
            assertThat(entry.getValue()).isNotNull();
            assertThat(entry.getValue().getWidth()).isGreaterThan(0);
        }
    }

    @Test
    void searchAll_isCaseInsensitive() {
        List<Map.Entry<String, BufferedImage>> lower = provider.searchAll("sword");
        List<Map.Entry<String, BufferedImage>> upper = provider.searchAll("SWORD");
        List<String> lowerNames = lower.stream().map(Map.Entry::getKey).toList();
        List<String> upperNames = upper.stream().map(Map.Entry::getKey).toList();
        assertThat(lowerNames).containsExactlyElementsOf(upperNames);
    }

    @Test
    void searchAll_exactNameMatchReturnsSingleEntry() {
        // diamond_sword is a known full name; using it as query should find at least itself
        List<Map.Entry<String, BufferedImage>> results = provider.searchAll("diamond_sword");
        assertThat(results).isNotEmpty();
        assertThat(results.stream().map(Map.Entry::getKey)).contains("diamond_sword");
    }

    // --- getAllSprites ---

    @Test
    void getAllSprites_returnsNonEmptyMap() {
        Map<String, BufferedImage> sprites = provider.getAllSprites();
        assertThat(sprites).isNotEmpty();
    }

    @Test
    void getAllSprites_containsKnownSprite() {
        Map<String, BufferedImage> sprites = provider.getAllSprites();
        assertThat(sprites).containsKey("diamond_sword");
    }

    @Test
    void getAllSprites_mapIsUnmodifiable() {
        Map<String, BufferedImage> sprites = provider.getAllSprites();
        assertThatThrownBy(() -> sprites.put("foo", new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAllSprites_keysMatchAvailableSprites() {
        Map<String, BufferedImage> sprites = provider.getAllSprites();
        assertThat(sprites.keySet()).containsExactlyInAnyOrderElementsOf(provider.availableSprites());
    }

    @Test
    void getAllSprites_allValuesAreNonNull() {
        Map<String, BufferedImage> sprites = provider.getAllSprites();
        for (Map.Entry<String, BufferedImage> entry : sprites.entrySet()) {
            assertThat(entry.getValue())
                    .as("sprite for '%s' should not be null", entry.getKey())
                    .isNotNull();
        }
    }
}
