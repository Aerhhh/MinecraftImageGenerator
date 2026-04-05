package net.aerh.jigsaw.integration;

import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.exception.RenderException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link Engine} built with default configuration.
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
