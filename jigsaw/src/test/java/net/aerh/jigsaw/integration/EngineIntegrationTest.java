package net.aerh.jigsaw.integration;

import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.core.data.DataRegistryKeys;
import net.aerh.jigsaw.core.data.types.*;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link Engine} built with default configuration.
 *
 * <p>NBT-related tests are omitted because the NBT implementation classes
 * ({@code DefaultNbtParser} and format handlers) are being built in parallel
 * and may not yet be available on the classpath.
 */
class EngineIntegrationTest {

    private static Engine engine;

    @BeforeAll
    static void buildEngine() {
        engine = Engine.builder().build();
    }

    // -------------------------------------------------------------------------
    // Engine construction
    // -------------------------------------------------------------------------

    @Test
    void build_withDefaultsSucceeds() {
        assertThat(engine).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Sprite provider
    // -------------------------------------------------------------------------

    @Test
    void sprites_isAvailable() {
        assertThat(engine.sprites()).isNotNull();
    }

    @Test
    void sprites_containsKnownItems() {
        assertThat(engine.sprites().availableSprites()).contains("diamond_sword");
    }

    // -------------------------------------------------------------------------
    // Data registries
    // -------------------------------------------------------------------------

    @Test
    void registry_raritiesIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.RARITIES);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_raritiesContainsLegendary() {
        var reg = engine.registry(DataRegistryKeys.RARITIES);
        assertThat(reg.get("legendary")).isPresent();
    }

    @Test
    void registry_statsIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.STATS);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_flavorsIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.FLAVORS);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_gemstonesIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.GEMSTONES);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_iconsIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.ICONS);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_parseTypesIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.PARSE_TYPES);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_powerStrengthsIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.POWER_STRENGTHS);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_armorTypesIsLoaded() {
        var reg = engine.registry(DataRegistryKeys.ARMOR_TYPES);
        assertThat(reg).isNotNull();
        assertThat(reg.isEmpty()).isFalse();
    }

    @Test
    void registry_unknownKeyThrowsException() {
        var unknownKey = net.aerh.jigsaw.api.data.RegistryKey.of("no_such_registry", String.class);
        assertThatThrownBy(() -> engine.registry(unknownKey))
                .isInstanceOf(net.aerh.jigsaw.exception.RegistryException.class);
    }

    // -------------------------------------------------------------------------
    // Item rendering
    // -------------------------------------------------------------------------

    @Test
    void renderItem_diamondSwordProducesResult() throws RenderException {
        GeneratorResult result = engine.renderItem("diamond_sword");

        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
        assertThat(result.firstFrame().getHeight()).isGreaterThan(0);
    }

    @Test
    void renderItem_isStaticWhenNotEnchanted() throws RenderException {
        GeneratorResult result = engine.renderItem("diamond_sword");
        assertThat(result.isAnimated()).isFalse();
    }

    @Test
    void renderItem_unknownItemThrowsRenderException() {
        assertThatThrownBy(() -> engine.renderItem("this_item_does_not_exist_xyz"))
                .isInstanceOf(RenderException.class);
    }

    @Test
    void renderItem_withContextUsesContext() throws RenderException {
        var ctx = net.aerh.jigsaw.api.generator.GenerationContext.defaults();
        GeneratorResult result = engine.renderItem("diamond_sword", ctx);

        assertThat(result).isNotNull();
    }
}
