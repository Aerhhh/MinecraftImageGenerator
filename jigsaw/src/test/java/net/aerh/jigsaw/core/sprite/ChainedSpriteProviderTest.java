package net.aerh.jigsaw.core.sprite;

import net.aerh.jigsaw.api.sprite.SpriteProvider;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChainedSpriteProviderTest {

    // --- helpers ---

    private static BufferedImage solidImage(Color color) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        return img;
    }

    /**
     * Minimal stub that serves a fixed map of sprites.
     */
    private static SpriteProvider stubProvider(Map<String, BufferedImage> sprites) {
        return new SpriteProvider() {
            @Override
            public Optional<BufferedImage> getSprite(String textureId) {
                return Optional.ofNullable(sprites.get(textureId));
            }

            @Override
            public Collection<String> availableSprites() {
                return sprites.keySet();
            }

            @Override
            public Optional<BufferedImage> search(String query) {
                return sprites.keySet().stream()
                        .filter(k -> k.contains(query))
                        .findFirst()
                        .map(sprites::get);
            }

            @Override
            public List<Map.Entry<String, BufferedImage>> searchAll(String query) {
                return sprites.entrySet().stream()
                        .filter(e -> e.getKey().toLowerCase().contains(query.toLowerCase()))
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
            }

            @Override
            public Map<String, BufferedImage> getAllSprites() {
                return Collections.unmodifiableMap(sprites);
            }
        };
    }

    // --- constructor validation ---

    @Test
    void constructor_nullProviders_throws() {
        assertThatThrownBy(() -> new ChainedSpriteProvider(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_emptyProviders_throws() {
        assertThatThrownBy(() -> new ChainedSpriteProvider(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- getSprite ---

    @Test
    void getSprite_firstProviderWins() {
        BufferedImage firstImage = solidImage(Color.RED);
        BufferedImage secondImage = solidImage(Color.BLUE);

        SpriteProvider first = stubProvider(Map.of("sword", firstImage));
        SpriteProvider second = stubProvider(Map.of("sword", secondImage));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        Optional<BufferedImage> result = chained.getSprite("sword");
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(firstImage);
    }

    @Test
    void getSprite_fallsBackToSecondProvider() {
        BufferedImage pickaxeImage = solidImage(Color.GREEN);

        SpriteProvider first = stubProvider(Map.of());
        SpriteProvider second = stubProvider(Map.of("pickaxe", pickaxeImage));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        Optional<BufferedImage> result = chained.getSprite("pickaxe");
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(pickaxeImage);
    }

    @Test
    void getSprite_allEmpty_returnsEmpty() {
        SpriteProvider first = stubProvider(Map.of());
        SpriteProvider second = stubProvider(Map.of());

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        assertThat(chained.getSprite("unknown_item")).isEmpty();
    }

    // --- availableSprites ---

    @Test
    void availableSprites_mergesAll() {
        SpriteProvider first = stubProvider(Map.of("sword", solidImage(Color.RED)));
        SpriteProvider second = stubProvider(Map.of("pickaxe", solidImage(Color.BLUE)));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        assertThat(chained.availableSprites()).contains("sword", "pickaxe");
    }

    @Test
    void availableSprites_deduplicates() {
        SpriteProvider first = stubProvider(Map.of("sword", solidImage(Color.RED)));
        SpriteProvider second = stubProvider(Map.of("sword", solidImage(Color.BLUE)));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        assertThat(chained.availableSprites()).hasSize(1);
        assertThat(chained.availableSprites()).containsExactly("sword");
    }

    // --- search ---

    @Test
    void search_firstProviderWins() {
        BufferedImage firstImage = solidImage(Color.RED);
        BufferedImage secondImage = solidImage(Color.BLUE);

        SpriteProvider first = stubProvider(Map.of("diamond_sword", firstImage));
        SpriteProvider second = stubProvider(Map.of("diamond_sword", secondImage));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        Optional<BufferedImage> result = chained.search("diamond");
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(firstImage);
    }

    @Test
    void search_fallsBackToSecondProvider() {
        BufferedImage axeImage = solidImage(Color.GREEN);

        SpriteProvider first = stubProvider(Map.of());
        SpriteProvider second = stubProvider(Map.of("iron_axe", axeImage));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        Optional<BufferedImage> result = chained.search("axe");
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(axeImage);
    }

    @Test
    void search_noMatch_returnsEmpty() {
        SpriteProvider first = stubProvider(Map.of("sword", solidImage(Color.RED)));
        SpriteProvider second = stubProvider(Map.of("pickaxe", solidImage(Color.BLUE)));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        assertThat(chained.search("zzz_no_such_item_zzz")).isEmpty();
    }

    // --- searchAll ---

    @Test
    void searchAll_mergesAndDeduplicatesByKey_firstProviderWins() {
        BufferedImage firstSword = solidImage(Color.RED);
        BufferedImage secondSword = solidImage(Color.BLUE);
        BufferedImage axeImage = solidImage(Color.GREEN);

        SpriteProvider first = stubProvider(Map.of("diamond_sword", firstSword));
        SpriteProvider second = stubProvider(Map.of("diamond_sword", secondSword, "iron_axe", axeImage));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        List<Map.Entry<String, BufferedImage>> results = chained.searchAll("sword");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getKey()).isEqualTo("diamond_sword");
        assertThat(results.get(0).getValue()).isSameAs(firstSword);
    }

    @Test
    void searchAll_resultsSortedAlphabetically() {
        SpriteProvider first = stubProvider(Map.of(
                "z_sword", solidImage(Color.RED),
                "a_sword", solidImage(Color.BLUE)
        ));
        SpriteProvider second = stubProvider(Map.of("m_sword", solidImage(Color.GREEN)));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        List<Map.Entry<String, BufferedImage>> results = chained.searchAll("sword");
        List<String> keys = results.stream().map(Map.Entry::getKey).toList();
        assertThat(keys).isSortedAccordingTo(String::compareTo);
    }

    @Test
    void searchAll_noMatch_returnsEmptyList() {
        SpriteProvider first = stubProvider(Map.of("sword", solidImage(Color.RED)));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first));

        assertThat(chained.searchAll("zzz_no_such_item_zzz")).isEmpty();
    }

    // --- getAllSprites ---

    @Test
    void getAllSprites_firstProviderWinsOnConflict() {
        BufferedImage firstSword = solidImage(Color.RED);
        BufferedImage secondSword = solidImage(Color.BLUE);

        SpriteProvider first = stubProvider(Map.of("sword", firstSword));
        SpriteProvider second = stubProvider(Map.of("sword", secondSword));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        Map<String, BufferedImage> all = chained.getAllSprites();
        assertThat(all.get("sword")).isSameAs(firstSword);
    }

    @Test
    void getAllSprites_mergesUniqueKeys() {
        SpriteProvider first = stubProvider(Map.of("sword", solidImage(Color.RED)));
        SpriteProvider second = stubProvider(Map.of("pickaxe", solidImage(Color.BLUE)));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first, second));

        Map<String, BufferedImage> all = chained.getAllSprites();
        assertThat(all).containsKeys("sword", "pickaxe");
    }

    @Test
    void getAllSprites_mapIsUnmodifiable() {
        SpriteProvider first = stubProvider(Map.of("sword", solidImage(Color.RED)));

        ChainedSpriteProvider chained = new ChainedSpriteProvider(List.of(first));

        Map<String, BufferedImage> all = chained.getAllSprites();
        assertThatThrownBy(() -> all.put("foo", solidImage(Color.WHITE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
