package net.aerh.jigsaw.api;

import net.aerh.jigsaw.api.data.DataRegistry;
import net.aerh.jigsaw.api.data.RegistryKey;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.engine.DefaultEngine;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.exception.RenderException;

/**
 * Entry point for the Jigsaw rendering engine.
 *
 * <p>Obtain an instance via {@link #builder()}.
 *
 * <pre>{@code
 * Engine engine = Engine.builder().build();
 * GeneratorResult result = engine.renderItem("diamond_sword");
 * }</pre>
 */
public interface Engine {

    /**
     * Renders the item with the given ID using the default {@link GenerationContext}.
     *
     * @param itemId the Minecraft item ID (e.g. {@code "diamond_sword"})
     * @return the rendered result
     * @throws RenderException if the item cannot be rendered
     */
    GeneratorResult renderItem(String itemId) throws RenderException;

    /**
     * Renders the item with the given ID using the supplied {@link GenerationContext}.
     *
     * @param itemId  the Minecraft item ID
     * @param context the generation context
     * @return the rendered result
     * @throws RenderException if the item cannot be rendered
     */
    GeneratorResult renderItem(String itemId, GenerationContext context) throws RenderException;

    /**
     * Parses the given raw NBT string and returns the structured item data.
     *
     * @param nbt the raw NBT string
     * @return the parsed item
     * @throws ParseException if the string cannot be parsed
     */
    ParsedItem parseNbt(String nbt) throws ParseException;

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
     * Returns the data registry associated with the given key.
     *
     * @param key the registry key
     * @param <T> the type of objects stored in the registry
     * @return the data registry
     */
    <T> DataRegistry<T> registry(RegistryKey<T> key);

    /**
     * Returns the sprite provider used by this engine.
     */
    SpriteProvider sprites();

    /**
     * Returns a new {@link EngineBuilder} configured with all defaults enabled.
     */
    static EngineBuilder builder() {
        return new DefaultEngine.Builder();
    }
}
