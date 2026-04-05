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
import net.aerh.jigsaw.core.data.DataRegistryKeys;
import net.aerh.jigsaw.core.data.JsonDataRegistry;
import net.aerh.jigsaw.core.data.types.*;
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

    @Override
    public GeneratorResult renderItem(String itemId) throws RenderException {
        return renderItem(itemId, GenerationContext.defaults());
    }

    @Override
    public GeneratorResult renderItem(String itemId, GenerationContext context) throws RenderException {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        ItemRequest request = ItemRequest.builder(itemId).build();
        return itemGenerator.render(request, context);
    }

    @Override
    public ParsedItem parseNbt(String nbt) throws ParseException {
        Objects.requireNonNull(nbt, "nbt must not be null");
        return nbtParser.parse(nbt);
    }

    @Override
    public GeneratorResult renderFromNbt(String nbt) throws ParseException, RenderException {
        return renderFromNbt(nbt, GenerationContext.defaults());
    }

    @Override
    public GeneratorResult renderFromNbt(String nbt, GenerationContext context) throws ParseException, RenderException {
        Objects.requireNonNull(nbt, "nbt must not be null");
        Objects.requireNonNull(context, "context must not be null");

        ParsedItem item = nbtParser.parse(nbt);

        ItemRequest.Builder requestBuilder = ItemRequest.builder(item.itemId())
                .enchanted(item.enchanted());

        item.dyeColor().ifPresent(requestBuilder::dyeColor);

        return itemGenerator.render(requestBuilder.build(), context);
    }

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

    @Override
    public SpriteProvider sprites() {
        return spriteProvider;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Builder implementation for {@link DefaultEngine}.
     */
    public static final class Builder implements EngineBuilder {

        private boolean useDefaults = true;

        private final List<ImageEffect> extraEffects = new ArrayList<>();
        private final List<NbtFormatHandler> nbtHandlers = new ArrayList<>();
        private final List<OverlayRenderer> overlayRenderers = new ArrayList<>();
        private final List<FontProvider> fontProviders = new ArrayList<>();

        public Builder() {}

        @Override
        public Builder noDefaults() {
            this.useDefaults = false;
            return this;
        }

        @Override
        public Builder effect(ImageEffect effect) {
            extraEffects.add(Objects.requireNonNull(effect, "effect must not be null"));
            return this;
        }

        @Override
        public Builder nbtHandler(NbtFormatHandler handler) {
            nbtHandlers.add(Objects.requireNonNull(handler, "handler must not be null"));
            return this;
        }

        @Override
        public Builder overlayRenderer(OverlayRenderer renderer) {
            overlayRenderers.add(Objects.requireNonNull(renderer, "renderer must not be null"));
            return this;
        }

        @Override
        public Builder fontProvider(FontProvider provider) {
            fontProviders.add(Objects.requireNonNull(provider, "provider must not be null"));
            return this;
        }

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

            // Data registries
            Map<String, DataRegistry<?>> registries = new HashMap<>();
            if (useDefaults) {
                registerDefaultRegistries(registries);
            }

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
         * Populates the registries map with all 8 built-in data types.
         */
        private static void registerDefaultRegistries(Map<String, DataRegistry<?>> registries) {
            registries.put(DataRegistryKeys.RARITIES.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.RARITIES, Rarity[].class,
                            "data/rarities.json", Rarity::name));

            registries.put(DataRegistryKeys.STATS.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.STATS, Stat[].class,
                            "data/stats.json", Stat::name));

            registries.put(DataRegistryKeys.FLAVORS.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.FLAVORS, Flavor[].class,
                            "data/flavor.json", Flavor::name));

            registries.put(DataRegistryKeys.GEMSTONES.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.GEMSTONES, Gemstone[].class,
                            "data/gemstones.json", Gemstone::name));

            registries.put(DataRegistryKeys.ICONS.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.ICONS, Icon[].class,
                            "data/icons.json", Icon::name));

            registries.put(DataRegistryKeys.PARSE_TYPES.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.PARSE_TYPES, ParseType[].class,
                            "data/parse_types.json", ParseType::name));

            registries.put(DataRegistryKeys.POWER_STRENGTHS.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.POWER_STRENGTHS, PowerStrength[].class,
                            "data/power_strengths.json", PowerStrength::name));

            registries.put(DataRegistryKeys.ARMOR_TYPES.name(),
                    new JsonDataRegistry<>(DataRegistryKeys.ARMOR_TYPES, ArmorType[].class,
                            "data/armor_types.json", ArmorType::materialName));
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
