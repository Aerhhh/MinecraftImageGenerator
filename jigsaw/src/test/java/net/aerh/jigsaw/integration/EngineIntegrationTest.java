package net.aerh.jigsaw.integration;

import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GenerationFeedback;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.generator.RenderRequest;
import net.aerh.jigsaw.core.generator.CompositeRequest;
import net.aerh.jigsaw.core.generator.ItemRequest;
import net.aerh.jigsaw.core.generator.TooltipRequest;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.exception.RenderException;
import net.aerh.jigsaw.exception.ValidationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    // render(RenderRequest) - Item
    // -------------------------------------------------------------------------

    @Test
    void render_itemRequestProducesResult() throws RenderException, ParseException {
        GeneratorResult result = engine.render(
                ItemRequest.builder().itemId("diamond_sword").build());

        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
        assertThat(result.firstFrame().getHeight()).isGreaterThan(0);
    }

    @Test
    void render_itemRequestIsStaticWhenNotEnchanted() throws RenderException, ParseException {
        GeneratorResult result = engine.render(
                ItemRequest.builder().itemId("diamond_sword").build());
        assertThat(result.isAnimated()).isFalse();
    }

    @Test
    void render_itemRequestUnknownItemThrowsRenderException() {
        assertThatThrownBy(() -> engine.render(
                ItemRequest.builder().itemId("this_item_does_not_exist_xyz").build()))
                .isInstanceOf(RenderException.class);
    }

    @Test
    void render_itemRequestWithContextUsesContext() throws RenderException, ParseException {
        GeneratorResult result = engine.render(
                ItemRequest.builder().itemId("diamond_sword").build(),
                GenerationContext.defaults());

        assertThat(result).isNotNull();
    }

    // -------------------------------------------------------------------------
    // render(RenderRequest) - Tooltip
    // -------------------------------------------------------------------------

    @Test
    void render_tooltipRequestProducesResult() throws RenderException, ParseException {
        GeneratorResult result = engine.render(
                TooltipRequest.builder()
                        .line("&6&lLEGENDARY SWORD")
                        .line("&7Some lore text")
                        .build());

        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // render(RenderRequest) - Composite with sub-requests
    // -------------------------------------------------------------------------

    @Test
    void render_compositeRequestWithItemSubRequests() throws RenderException, ParseException {
        CompositeRequest request = CompositeRequest.builder()
                .add(ItemRequest.builder().itemId("diamond_sword").build())
                .add(ItemRequest.builder().itemId("diamond_sword").build())
                .layout(CompositeRequest.Layout.HORIZONTAL)
                .build();

        GeneratorResult result = engine.render(request);

        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
        assertThat(result.firstFrame().getWidth()).isGreaterThan(0);
    }

    @Test
    void render_compositeRequestWithMixedSubRequests() throws RenderException, ParseException {
        CompositeRequest request = CompositeRequest.builder()
                .add(ItemRequest.builder().itemId("diamond_sword").build())
                .add(TooltipRequest.builder().line("&6&lTest").build())
                .build();

        GeneratorResult result = engine.render(request);

        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
    }

    @Test
    void render_nestedCompositeRequest() throws RenderException, ParseException {
        CompositeRequest inner = CompositeRequest.builder()
                .add(ItemRequest.builder().itemId("diamond_sword").build())
                .add(ItemRequest.builder().itemId("diamond_sword").build())
                .layout(CompositeRequest.Layout.HORIZONTAL)
                .build();

        CompositeRequest outer = CompositeRequest.builder()
                .add(inner)
                .add(TooltipRequest.builder().line("&6&lTest").build())
                .layout(CompositeRequest.Layout.VERTICAL)
                .build();

        GeneratorResult result = engine.render(outer);

        assertThat(result).isNotNull();
        assertThat(result.firstFrame()).isNotNull();
    }

    @Test
    void render_compositeRequestPassesContextToSubRequests() throws RenderException, ParseException {
        List<String> messages = new ArrayList<>();
        GenerationContext context = GenerationContext.builder()
                .feedback((msg, eph) -> messages.add(msg))
                .build();

        CompositeRequest request = CompositeRequest.builder()
                .add(ItemRequest.builder().itemId("diamond_sword").build())
                .build();

        GeneratorResult result = engine.render(request, context);

        assertThat(result).isNotNull();
    }

    @Test
    void render_emptyCompositeRequestReturnsMinimalImage() throws RenderException, ParseException {
        CompositeRequest request = CompositeRequest.builder().build();
        GeneratorResult result = engine.render(request);

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        assertThat(result.firstFrame()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Positional insertion in CompositeRequest
    // -------------------------------------------------------------------------

    @Test
    void compositeRequestBuilder_positionalInsertionWorks() throws RenderException, ParseException {
        ItemRequest item = ItemRequest.builder().itemId("diamond_sword").build();
        TooltipRequest tooltip = TooltipRequest.builder().line("&6&lTest").build();

        CompositeRequest.Builder builder = CompositeRequest.builder()
                .add(item);

        // Insert tooltip at position 0 (before the item)
        builder.add(0, tooltip);

        CompositeRequest request = builder.build();

        assertThat(request.requests()).hasSize(2);
        assertThat(request.requests().get(0)).isInstanceOf(TooltipRequest.class);
        assertThat(request.requests().get(1)).isInstanceOf(ItemRequest.class);

        // Verify it actually renders
        GeneratorResult result = engine.render(request);
        assertThat(result).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Unknown request type
    // -------------------------------------------------------------------------

    @Test
    void render_unknownRequestTypeThrowsValidationException() {
        RenderRequest unknown = new RenderRequest() {};

        assertThatThrownBy(() -> engine.render(unknown))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown request type");
    }

    // -------------------------------------------------------------------------
    // Convenience methods (backward compatibility)
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
        var ctx = GenerationContext.defaults();
        GeneratorResult result = engine.renderItem("diamond_sword", ctx);

        assertThat(result).isNotNull();
    }

    // -------------------------------------------------------------------------
    // GenerationFeedback
    // -------------------------------------------------------------------------

    @Test
    void generationFeedback_fromConsumerAdapter() {
        List<String> received = new ArrayList<>();
        GenerationFeedback feedback = GenerationFeedback.fromConsumer(received::add);
        feedback.send("hello", true);
        feedback.send("world", false);
        assertThat(received).containsExactly("hello", "world");
    }

    @Test
    void generationFeedback_noopDoesNotThrow() {
        GenerationFeedback noop = GenerationFeedback.noop();
        noop.send("test", true);
        // No exception expected
    }

    @Test
    void generationContext_builderAcceptsFeedback() {
        GenerationContext ctx = GenerationContext.builder()
                .feedback((msg, eph) -> {})
                .build();
        assertThat(ctx.feedback()).isNotNull();
    }

    @Test
    void generationContext_builderAcceptsConsumerFeedback() {
        GenerationContext ctx = GenerationContext.builder()
                .feedback(msg -> {})
                .build();
        assertThat(ctx.feedback()).isNotNull();
    }
}
