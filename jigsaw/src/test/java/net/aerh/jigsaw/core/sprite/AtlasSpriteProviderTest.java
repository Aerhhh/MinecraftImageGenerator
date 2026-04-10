package net.aerh.jigsaw.core.sprite;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasSpriteProviderTest {

    private static AtlasSpriteProvider provider;

    @BeforeAll
    static void setUp() {
        provider = AtlasSpriteProvider.fromDefaults();
    }

    @Test
    void loadsFromDefaults() {
        assertThat(provider.availableSprites()).isNotEmpty();
    }

    @Test
    void getKnownSprite_diamondSword() {
        Optional<BufferedImage> sprite = provider.getSprite("diamond_sword");
        assertThat(sprite).isPresent();
        assertThat(sprite.get().getWidth()).isGreaterThan(0);
        assertThat(sprite.get().getHeight()).isGreaterThan(0);
    }

    @Test
    void unknownSpriteReturnsEmpty() {
        assertThat(provider.getSprite("this_does_not_exist_xyz")).isEmpty();
    }

    @Test
    void searchFindsPartialMatch() {
        // "sword" should match "diamond_sword", "iron_sword", etc.
        Optional<BufferedImage> result = provider.search("sword");
        assertThat(result).isPresent();
    }

    @Test
    void searchWithNoMatchReturnsEmpty() {
        assertThat(provider.search("zzz_no_such_item_zzz")).isEmpty();
    }

    @Test
    void spriteIsCached_returnsSameInstance() {
        Optional<BufferedImage> first = provider.getSprite("diamond_sword");
        Optional<BufferedImage> second = provider.getSprite("diamond_sword");
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get()).isSameAs(second.get());
    }

    @Test
    void availableSpritesContainsDiamondSword() {
        assertThat(provider.availableSprites()).contains("diamond_sword");
    }
}
