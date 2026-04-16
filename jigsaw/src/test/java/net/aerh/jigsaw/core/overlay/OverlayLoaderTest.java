package net.aerh.jigsaw.core.overlay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayLoaderTest {

    private static OverlayLoader loader;

    @BeforeAll
    static void loadDefaults() {
        loader = OverlayLoader.fromDefaults();
    }

    // --- Loading from defaults ---

    @Test
    void fromDefaults_loadsSuccessfully() {
        assertThat(loader).isNotNull();
    }

    // --- Finding overlays ---

    @Test
    void getOverlay_findsLeatherHelmet() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("leather_helmet");
        assertThat(overlay).isPresent();
        assertThat(overlay.get().overlayTexture()).isNotNull();
        assertThat(overlay.get().rendererType()).isEqualTo("normal");
        assertThat(overlay.get().colorOptionsCategory()).isEqualTo("leather_armor");
    }

    @Test
    void getOverlay_findsLeatherChestplate() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("leather_chestplate");
        assertThat(overlay).isPresent();
        assertThat(overlay.get().colorMode()).isEqualTo(net.aerh.jigsaw.api.overlay.ColorMode.BASE);
    }

    @Test
    void getOverlay_findsPotion() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("potion");
        assertThat(overlay).isPresent();
        assertThat(overlay.get().colorMode()).isEqualTo(net.aerh.jigsaw.api.overlay.ColorMode.OVERLAY);
        assertThat(overlay.get().colorOptionsCategory()).isEqualTo("potion");
    }

    @Test
    void getOverlay_findsTippedArrow() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("tipped_arrow_base");
        assertThat(overlay).isPresent();
        assertThat(overlay.get().colorMode()).isEqualTo(net.aerh.jigsaw.api.overlay.ColorMode.OVERLAY);
    }

    @Test
    void getOverlay_findsFireworkStar() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("firework_star");
        assertThat(overlay).isPresent();
        assertThat(overlay.get().colorMode()).isEqualTo(net.aerh.jigsaw.api.overlay.ColorMode.OVERLAY);
    }

    @Test
    void getOverlay_returnsEmptyForUnknownItem() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("diamond_sword");
        assertThat(overlay).isEmpty();
    }

    @Test
    void getOverlay_isCaseInsensitive() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("LEATHER_HELMET");
        assertThat(overlay).isPresent();
    }

    // --- hasOverlay ---

    @Test
    void hasOverlay_returnsTrueForKnownItem() {
        assertThat(loader.hasOverlay("leather_boots")).isTrue();
    }

    @Test
    void hasOverlay_returnsFalseForUnknownItem() {
        assertThat(loader.hasOverlay("diamond_sword")).isFalse();
    }

    // --- Color option names ---

    @Test
    void getAllColorOptionNames_includesLeatherArmorColors() {
        Set<String> names = loader.getAllColorOptionNames();
        assertThat(names).contains("red", "blue", "green", "white", "black");
    }

    @Test
    void getAllColorOptionNames_includesPotionColors() {
        Set<String> names = loader.getAllColorOptionNames();
        assertThat(names).contains("speed", "strength", "poison", "water");
    }

    @Test
    void getAllColorOptionNames_isNotEmpty() {
        assertThat(loader.getAllColorOptionNames()).isNotEmpty();
    }

    // --- Default colors for leather armor ---

    @Test
    void leatherArmor_hasDefaultColors() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("leather_helmet");
        assertThat(overlay).isPresent();
        assertThat(overlay.get().defaultColors()).isNotNull();
        assertThat(overlay.get().defaultColors()).hasSizeGreaterThan(0);
    }

    @Test
    void leatherArmor_allowsHexColors() {
        Optional<ItemOverlayData> overlay = loader.getOverlay("leather_helmet");
        assertThat(overlay).isPresent();
        assertThat(overlay.get().allowHexColors()).isTrue();
    }

    // --- Trim variants share overlays ---

    @Test
    void trimVariants_shareOverlayWithBase() {
        Optional<ItemOverlayData> base = loader.getOverlay("leather_helmet");
        Optional<ItemOverlayData> goldTrim = loader.getOverlay("leather_helmet_gold_trim");
        assertThat(base).isPresent();
        assertThat(goldTrim).isPresent();
        // Same overlay texture (they reference the same overlay definition)
        assertThat(goldTrim.get().overlayTexture()).isSameAs(base.get().overlayTexture());
    }
}
