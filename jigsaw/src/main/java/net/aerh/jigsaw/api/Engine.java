package net.aerh.jigsaw.api;

import net.aerh.jigsaw.api.data.DataRegistry;
import net.aerh.jigsaw.api.data.RegistryKey;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.generator.RenderRequest;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.engine.DefaultEngine;
import net.aerh.jigsaw.core.generator.ItemRequest;
import net.aerh.jigsaw.core.overlay.OverlayColorProvider;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.exception.RenderException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Entry point for the Jigsaw rendering engine.
 *
 * <p>Obtain an instance via {@link #builder()}.
 *
 * <pre>{@code
 * Engine engine = Engine.builder().build();
 * GeneratorResult result = engine.render(
 *     ItemRequest.builder().itemId("diamond_sword").build()
 * );
 * }</pre>
 *
 * @see EngineBuilder
 */
public interface Engine {

    /**
     * Renders any request type using the default {@link GenerationContext}.
     *
     * <p>The engine routes the request to the appropriate generator internally based on
     * the concrete type of the request.
     *
     * @param request the render request; must not be {@code null}
     * @return the rendered result
     * @throws RenderException if rendering fails
     * @throws ParseException  if the request involves NBT parsing that fails
     */
    GeneratorResult render(RenderRequest request) throws RenderException, ParseException;

    /**
     * Renders any request type using the supplied {@link GenerationContext}.
     *
     * <p>The engine routes the request to the appropriate generator internally based on
     * the concrete type of the request.
     *
     * @param request the render request; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     * @return the rendered result
     * @throws RenderException if rendering fails
     * @throws ParseException  if the request involves NBT parsing that fails
     */
    GeneratorResult render(RenderRequest request, GenerationContext context) throws RenderException, ParseException;

    /**
     * Parses the given raw NBT string and returns the structured item data.
     *
     * @param nbt the raw NBT string
     * @return the parsed item
     * @throws ParseException if the string cannot be parsed
     */
    ParsedItem parseNbt(String nbt) throws ParseException;

    /**
     * Returns the data registry associated with the given key.
     *
     * @param key the registry key
     * @param <T> the type of objects stored in the registry
     * @return the data registry
     */
    <T> DataRegistry<T> registry(RegistryKey<T> key);

    /**
     * Returns the sprite provider used by this engine.
     *
     * @return the sprite provider
     */
    SpriteProvider sprites();

    /**
     * Returns the overlay color provider, which exposes named color options from all overlay
     * categories for use in autocomplete and color resolution.
     *
     * @return the {@link OverlayColorProvider}
     */
    OverlayColorProvider overlayColors();

    /**
     * Convenience method that renders an item by ID using the default context.
     *
     * <p>Equivalent to {@code render(ItemRequest.builder().itemId(itemId).build())}.
     *
     * @param itemId the Minecraft item ID (e.g. {@code "diamond_sword"})
     * @return the rendered result
     * @throws RenderException if the item cannot be rendered
     */
    default GeneratorResult renderItem(String itemId) throws RenderException {
        try {
            return render(ItemRequest.builder().itemId(itemId).build());
        } catch (ParseException e) {
            // ItemRequest never triggers ParseException
            throw new AssertionError("Unexpected ParseException from ItemRequest", e);
        }
    }

    /**
     * Convenience method that renders an item by ID using the supplied context.
     *
     * <p>Equivalent to {@code render(ItemRequest.builder().itemId(itemId).build(), context)}.
     *
     * @param itemId  the Minecraft item ID
     * @param context the generation context
     * @return the rendered result
     * @throws RenderException if the item cannot be rendered
     */
    default GeneratorResult renderItem(String itemId, GenerationContext context) throws RenderException {
        try {
            return render(ItemRequest.builder().itemId(itemId).build(), context);
        } catch (ParseException e) {
            // ItemRequest never triggers ParseException
            throw new AssertionError("Unexpected ParseException from ItemRequest", e);
        }
    }

    /**
     * Parses the given NBT string and renders the resulting item using the default context.
     *
     * @param nbt the raw NBT string
     * @return the rendered result
     * @throws ParseException  if the string cannot be parsed
     * @throws RenderException if the item cannot be rendered
     */
    GeneratorResult renderFromNbt(String nbt) throws ParseException, RenderException;

    /**
     * Parses the given NBT string and renders the resulting item using the supplied context.
     *
     * @param nbt     the raw NBT string
     * @param context the generation context
     * @return the rendered result
     * @throws ParseException  if the string cannot be parsed
     * @throws RenderException if the item cannot be rendered
     */
    GeneratorResult renderFromNbt(String nbt, GenerationContext context) throws ParseException, RenderException;

    /**
     * Shared virtual thread executor used by {@link #renderAsync} methods.
     * Virtual thread executors are lightweight and designed for long-lived sharing.
     */
    Executor ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Asynchronously renders any request type using the default {@link GenerationContext}.
     *
     * <p>The render is executed on a virtual thread. The returned future completes with the
     * rendered result, or completes exceptionally with a {@link CompletionException} wrapping
     * the original {@link RenderException} or {@link ParseException}.
     *
     * @param request the render request; must not be {@code null}
     * @return a future that completes with the rendered result
     */
    default CompletableFuture<GeneratorResult> renderAsync(RenderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return render(request);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    /**
     * Asynchronously renders any request type using the supplied {@link GenerationContext}.
     *
     * <p>The render is executed on a virtual thread. The returned future completes with the
     * rendered result, or completes exceptionally with a {@link CompletionException} wrapping
     * the original {@link RenderException} or {@link ParseException}.
     *
     * @param request the render request; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     * @return a future that completes with the rendered result
     */
    default CompletableFuture<GeneratorResult> renderAsync(RenderRequest request, GenerationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return render(request, context);
            } catch (RenderException | ParseException e) {
                throw new CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    /**
     * Returns a new {@link EngineBuilder} configured with all defaults enabled.
     *
     * @return a new builder
     */
    static EngineBuilder builder() {
        return new DefaultEngine.Builder();
    }
}
