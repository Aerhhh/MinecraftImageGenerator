package net.aerh.jigsaw.core.engine;

import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.api.EngineBuilder;
import net.aerh.jigsaw.api.data.DataRegistry;
import net.aerh.jigsaw.api.data.RegistryKey;
import net.aerh.jigsaw.api.effect.ImageEffect;
import net.aerh.jigsaw.api.font.FontProvider;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.nbt.NbtParser;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.api.overlay.OverlayRenderer;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.effect.DurabilityBarEffect;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.core.effect.GlintEffect;
import net.aerh.jigsaw.core.effect.HoverEffect;
import net.aerh.jigsaw.core.font.DefaultFontRegistry;
import net.aerh.jigsaw.core.generator.ItemGenerator;
import net.aerh.jigsaw.core.generator.ItemRequest;
import net.aerh.jigsaw.core.nbt.DefaultNbtParser;
import net.aerh.jigsaw.core.nbt.handler.ComponentsNbtFormatHandler;
import net.aerh.jigsaw.core.nbt.handler.DefaultNbtFormatHandler;
import net.aerh.jigsaw.core.nbt.handler.PostFlatteningNbtFormatHandler;
import net.aerh.jigsaw.core.nbt.handler.PreFlatteningNbtFormatHandler;
import net.aerh.jigsaw.core.overlay.OverlayRegistry;
import net.aerh.jigsaw.core.sprite.AtlasSpriteProvider;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.exception.RenderException;
import net.aerh.jigsaw.exception.RegistryException;
import net.aerh.jigsaw.spi.NbtFormatHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Default implementation of {@link Engine} that wires together all built-in components.
 *
 * <p>Use {@link Engine#builder()} (which delegates to {@link Builder}) to construct an instance.
 *
 * @see Engine
 */
public final class DefaultEngine implements Engine {

    private final SpriteProvider spriteProvider;
    private final ItemGenerator itemGenerator;
    private final NbtParser nbtParser;
    private final Map<String, DataRegistry<?>> registries;

    private DefaultEngine(
            SpriteProvider spriteProvider,
            ItemGenerator itemGenerator,
            NbtParser nbtParser,
            Map<String, DataRegistry<?>> registries
    ) {
        this.spriteProvider = spriteProvider;
        this.itemGenerator = itemGenerator;
        this.nbtParser = nbtParser;
        this.registries = Map.copyOf(registries);
    }

    /**
     * Renders the item with the given ID using default generation context options.
     *
     * @param itemId the Minecraft item ID to render; must not be {@code null}
     *
     * @return the rendered result
     *
     * @throws RenderException if the item is unknown or rendering fails
     */
    @Override
    public GeneratorResult renderItem(String itemId) throws RenderException {
        return renderItem(itemId, GenerationContext.defaults());
    }

    /**
     * Renders the item with the given ID using the supplied generation context.
     *
     * @param itemId  the Minecraft item ID to render; must not be {@code null}
     * @param context the generation context controlling rendering behavior; must not be {@code null}
     * @return the rendered result
     * @throws RenderException if the item is unknown or rendering fails
     */
    @Override
    public GeneratorResult renderItem(String itemId, GenerationContext context) throws RenderException {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        ItemRequest request = ItemRequest.builder().itemId(itemId).build();
        return itemGenerator.render(request, context);
    }

    /**
     * Parses an NBT string and returns the extracted item data.
     *
     * @param nbt the raw NBT string; must not be {@code null}
     * @return the parsed item data
     * @throws ParseException if the NBT string is invalid or no handler accepts it
     */
    @Override
    public ParsedItem parseNbt(String nbt) throws ParseException {
        Objects.requireNonNull(nbt, "nbt must not be null");
        return nbtParser.parse(nbt);
    }

    /**
     * Parses the NBT string and renders the item it describes using default generation context options.
     *
     * @param nbt the raw NBT string; must not be {@code null}
     * @return the rendered result
     * @throws ParseException  if the NBT is invalid or no handler accepts it
     * @throws RenderException if the item sprite is not found or rendering fails
     */
    @Override
    public GeneratorResult renderFromNbt(String nbt) throws ParseException, RenderException {
        return renderFromNbt(nbt, GenerationContext.defaults());
    }

    /**
     * Parses the NBT string and renders the item it describes using the supplied generation context.
     *
     * @param nbt     the raw NBT string; must not be {@code null}
     * @param context the generation context controlling rendering behavior; must not be {@code null}
     * @return the rendered result
     * @throws ParseException  if the NBT is invalid or no handler accepts it
     * @throws RenderException if the item sprite is not found or rendering fails
     */
    @Override
    public GeneratorResult renderFromNbt(String nbt, GenerationContext context) throws ParseException, RenderException {
        Objects.requireNonNull(nbt, "nbt must not be null");
        Objects.requireNonNull(context, "context must not be null");

        ParsedItem item = nbtParser.parse(nbt);

        ItemRequest.Builder requestBuilder = ItemRequest.builder()
            .itemId(item.itemId())
            .enchanted(item.enchanted());

        item.dyeColor().ifPresent(requestBuilder::dyeColor);

        return itemGenerator.render(requestBuilder.build(), context);
    }

    /**
     * Returns the data registry associated with the given key.
     *
     * @param <T> the type of objects in the registry
     * @param key the registry key to look up; must not be {@code null}
     * @return the registry for the given key
     * @throws net.aerh.jigsaw.exception.RegistryException if no registry is registered for the key
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> DataRegistry<T> registry(RegistryKey<T> key) {
        Objects.requireNonNull(key, "key must not be null");
        DataRegistry<?> registry = registries.get(key.name());
        if (registry == null) {
            throw new RegistryException("No registry registered for key: " + key.name(),
                    Map.of("registryKey", key.name()));
        }
        return (DataRegistry<T>) registry;
    }

    /**
     * Returns the sprite provider used by this engine.
     *
     * @return the {@link SpriteProvider}
     */
    @Override
    public SpriteProvider sprites() {
        return spriteProvider;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Builder implementation for {@link DefaultEngine}.
     *
     * <p>By default the builder wires up:
     * <ul>
     *   <li>The bundled {@link AtlasSpriteProvider} atlas.</li>
     *   <li>The {@link GlintEffect}, {@link HoverEffect}, and {@link DurabilityBarEffect} in the effect pipeline.</li>
     *   <li>The four built-in {@link NbtFormatHandler}s (components, post-flattening, pre-flattening, default).</li>
     *   <li>Any additional handlers discovered at runtime via {@link ServiceLoader}.</li>
     * </ul>
     *
     * <p>Call {@link #noDefaults()} before any other setter to suppress all of the above.
     *
     * @see Engine#builder()
     */
    public static final class Builder implements EngineBuilder {

        private boolean useDefaults = true;

        private final List<ImageEffect> extraEffects = new ArrayList<>();
        private final List<NbtFormatHandler> nbtHandlers = new ArrayList<>();
        private final List<OverlayRenderer> overlayRenderers = new ArrayList<>();
        private final List<FontProvider> fontProviders = new ArrayList<>();

        /** Creates a new builder with all defaults enabled. */
        public Builder() {
        }

        /**
         * Disables all built-in defaults so that only caller-supplied components are used.
         *
         * @return this builder for chaining
         */
        @Override
        public Builder noDefaults() {
            this.useDefaults = false;
            return this;
        }

        /**
         * Adds an additional {@link ImageEffect} to the effect pipeline.
         *
         * @param effect the effect to add; must not be {@code null}
         * @return this builder for chaining
         */
        @Override
        public Builder effect(ImageEffect effect) {
            extraEffects.add(Objects.requireNonNull(effect, "effect must not be null"));
            return this;
        }

        /**
         * Registers a custom {@link NbtFormatHandler}.
         * Handlers supplied here take precedence over any {@link ServiceLoader}-discovered handlers
         * with the same class, since duplicates are de-duplicated by class identity.
         *
         * @param handler the handler to add; must not be {@code null}
         * @return this builder for chaining
         */
        @Override
        public Builder nbtHandler(NbtFormatHandler handler) {
            nbtHandlers.add(Objects.requireNonNull(handler, "handler must not be null"));
            return this;
        }

        /**
         * Registers a custom {@link OverlayRenderer}.
         * Replaces any existing renderer registered under the same {@link OverlayRenderer#type()}.
         *
         * @param renderer the renderer to register; must not be {@code null}
         * @return this builder for chaining
         */
        @Override
        public Builder overlayRenderer(OverlayRenderer renderer) {
            overlayRenderers.add(Objects.requireNonNull(renderer, "renderer must not be null"));
            return this;
        }

        /**
         * Registers a custom {@link FontProvider}.
         *
         * @param provider the font provider to register; must not be {@code null}
         * @return this builder for chaining
         */
        @Override
        public Builder fontProvider(FontProvider provider) {
            fontProviders.add(Objects.requireNonNull(provider, "provider must not be null"));
            return this;
        }

        /**
         * Builds and returns the configured {@link Engine}.
         *
         * @return a fully initialized engine
         * @throws NullPointerException if defaults are disabled but no sprite provider was supplied
         */
        @Override
        public Engine build() {
            // Sprite provider
            SpriteProvider spriteProvider = useDefaults ? AtlasSpriteProvider.fromDefaults() : null;
            Objects.requireNonNull(spriteProvider, "A SpriteProvider is required. Use defaults or provide one.");

            // Effect pipeline
            EffectPipeline.Builder pipelineBuilder = EffectPipeline.builder();
            if (useDefaults) {
                pipelineBuilder
                        .add(new GlintEffect())
                        .add(new HoverEffect())
                        .add(new DurabilityBarEffect());
            }
            extraEffects.forEach(pipelineBuilder::add);
            EffectPipeline effectPipeline = pipelineBuilder.build();

            // Font registry
            DefaultFontRegistry fontRegistry = DefaultFontRegistry.withBuiltins();
            fontProviders.forEach(fontRegistry::register);

            // Overlay registry
            // Note: OverlayRegistry only exposes withDefaults(); both paths start from there.
            // When noDefaults() is used, caller-supplied renderers still work (they override or extend).
            OverlayRegistry overlayRegistry = OverlayRegistry.withDefaults();
            overlayRenderers.forEach(overlayRegistry::register);

            // Data registries - empty by default; consumers register their own via the registry API
            Map<String, DataRegistry<?>> registries = new HashMap<>();

            // NBT parser
            List<NbtFormatHandler> handlers = new ArrayList<>(nbtHandlers);
            if (useDefaults) {
                addDefaultNbtHandlers(handlers);
            }
            NbtParser nbtParser = buildNbtParser(handlers);

            ItemGenerator itemGenerator = new ItemGenerator(spriteProvider, effectPipeline);

            return new DefaultEngine(spriteProvider, itemGenerator, nbtParser, registries);
        }

        /**
         * Registers the default NBT format handlers discovered via {@link ServiceLoader} and
         * the built-in programmatic registrations.
         *
         * <p>ServiceLoader-discovered handlers are added first; the explicit programmatic
         * defaults are appended afterwards so that callers can override via {@link #nbtHandler}.
         * Duplicate implementations (same class) are deduplicated by class identity.
         */
        private static void addDefaultNbtHandlers(List<NbtFormatHandler> handlers) {
            // Programmatic defaults - all classes are now available at compile time
            handlers.add(new ComponentsNbtFormatHandler());
            handlers.add(new PostFlatteningNbtFormatHandler());
            handlers.add(new PreFlatteningNbtFormatHandler());
            handlers.add(new DefaultNbtFormatHandler());
        }

        /**
         * Builds an {@link NbtParser} from the given handlers, merging with any handlers
         * discovered via {@link ServiceLoader}.
         */
        private static NbtParser buildNbtParser(List<NbtFormatHandler> handlers) {
            // Merge ServiceLoader-discovered handlers, avoiding duplicates by class
            List<NbtFormatHandler> merged = new ArrayList<>(handlers);
            for (NbtFormatHandler discovered : ServiceLoader.load(NbtFormatHandler.class)) {
                boolean alreadyPresent = merged.stream()
                        .anyMatch(h -> h.getClass() == discovered.getClass());
                if (!alreadyPresent) {
                    merged.add(discovered);
                }
            }
            return new DefaultNbtParser(merged);
        }
    }
}
