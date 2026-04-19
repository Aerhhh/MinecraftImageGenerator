package net.aerh.tessera.core.generator;

import net.aerh.tessera.api.Engine;
import net.aerh.tessera.api.EngineBuilder;
import net.aerh.tessera.api.assets.AssetEntry;
import net.aerh.tessera.api.assets.AssetManifest;
import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.assets.CacheLocator;
import net.aerh.tessera.api.assets.Capabilities;
import net.aerh.tessera.api.assets.ManifestLoader;
import net.aerh.tessera.api.cache.CacheKey;
import net.aerh.tessera.api.data.DataRegistry;
import net.aerh.tessera.api.effect.ImageEffect;
import net.aerh.tessera.api.exception.TesseraAssetsMissingException;
import net.aerh.tessera.api.font.FontProvider;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.api.image.OutputSizeGate;
import net.aerh.tessera.api.nbt.NbtParser;
import net.aerh.tessera.api.overlay.OverlayColorProvider;
import net.aerh.tessera.api.overlay.OverlayRenderer;
import net.aerh.tessera.api.resource.ResourcePack;
import net.aerh.tessera.api.sprite.SpriteProvider;
import net.aerh.tessera.core.cache.CachingGenerator;
import net.aerh.tessera.core.engine.AssetProviderResolver;
import net.aerh.tessera.core.engine.BoundedVirtualExecutor;
import net.aerh.tessera.core.engine.StartupCheck;
import net.aerh.tessera.core.engine.assets.TesseraAtlasBuilder;
import net.aerh.tessera.core.resource.LayeredResourcePack;
import net.aerh.tessera.core.effect.DurabilityBarEffect;
import net.aerh.tessera.core.effect.EffectPipeline;
import net.aerh.tessera.core.effect.GlintEffect;
import net.aerh.tessera.core.effect.HoverEffect;
import net.aerh.tessera.core.effect.OverlayEffect;
import net.aerh.tessera.core.font.DefaultFontRegistry;
import net.aerh.tessera.core.generator.player.ArmorTexture;
import net.aerh.tessera.core.nbt.DefaultNbtParser;
import net.aerh.tessera.core.nbt.handler.ComponentsNbtFormatHandler;
import net.aerh.tessera.core.nbt.handler.DefaultNbtFormatHandler;
import net.aerh.tessera.core.nbt.handler.PostFlatteningNbtFormatHandler;
import net.aerh.tessera.core.nbt.handler.PreFlatteningNbtFormatHandler;
import net.aerh.tessera.core.overlay.OverlayLoader;
import net.aerh.tessera.core.overlay.OverlayRegistry;
import net.aerh.tessera.core.sprite.AtlasSpriteProvider;
import net.aerh.tessera.core.text.MinecraftTextRenderer;
import net.aerh.tessera.spi.NbtFormatHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Concrete {@link EngineBuilder} implementation; resolved by {@link Engine#builder()} via
 * {@link ServiceLoader} using the {@code META-INF/services/net.aerh.tessera.api.EngineBuilder}
 * descriptor.
 *
 * <p>Must ship a public no-arg constructor for {@link ServiceLoader#load(Class)} to
 * instantiate it reflectively.
 *
 * <p>Orchestration in {@link #build()}:
 * <ol>
 *   <li>Resolve {@link AssetProvider} via {@link AssetProviderResolver}.</li>
 *   <li>Verify the pinned manifest's files are present in the resolved cache dir.</li>
 *   <li>Choose executor: consumer-supplied {@link #executor(Executor)} wins, else a
 *       {@link BoundedVirtualExecutor} sized from bound / env / default.</li>
 *   <li>Wire default generators (CachingGenerator-wrapped) + any user registrations.</li>
 *   <li>Run {@link StartupCheck} over the generator map.</li>
 *   <li>Return a {@link DefaultEngine} instance wired to the resolved state.</li>
 * </ol>
 *
 * @see Engine#builder()
 */
public final class DefaultEngineBuilder implements EngineBuilder {

    private static final Logger log = LoggerFactory.getLogger(DefaultEngineBuilder.class);
    private static final long DEFAULT_CACHE_MAX_SIZE = 1024;
    /** Running Tessera version, embedded in {@code UnsupportedMinecraftVersionException} messages. */
    private static final String TESSERA_VERSION = "1.0.0-SNAPSHOT";

    private boolean useDefaults = true;

    private final List<ImageEffect> extraEffects = new ArrayList<>();
    private final List<NbtFormatHandler> nbtHandlers = new ArrayList<>();
    private final List<OverlayRenderer> overlayRenderers = new ArrayList<>();
    private final List<FontProvider> fontProviders = new ArrayList<>();
    private final Map<String, DataRegistry<?>> customRegistries = new HashMap<>();
    private final List<AssetProvider> programmaticAssetProviders = new ArrayList<>();
    private SpriteProvider customSpriteProvider;
    private java.awt.image.BufferedImage customSlotTexture;
    private ResourcePack customResourcePack;
    private boolean acceptEula = false;
    private String mcVer = null;
    private Path assetDir = null;
    private Integer explicitExecutorBound = null;
    private Executor sharedExecutor = null;
    private Duration shutdownTimeout = null;
    /** Null when unset; resolved at engine-build time via OutputSizeGate.resolveStaticCap. */
    private Long staticOutputCapBytes = null;
    /** Null when unset; resolved at engine-build time via OutputSizeGate.resolveAnimatedCap. */
    private Long animatedOutputCapBytes = null;
    /**
     * Test-only generator overrides per {@link #testingWithGenerator}. Keyed by
     * {@link CoreRenderRequest} subtype; consulted inside {@link #build()} when wiring
     * the per-type Generator, so the replacement is used instead of the built-in.
     */
    private final Map<Class<? extends CoreRenderRequest>, Generator<?, ?>> testGenerators = new HashMap<>();

    /**
     * Public no-arg constructor. Required by {@link ServiceLoader} for reflective
     * instantiation via {@code META-INF/services/net.aerh.tessera.api.EngineBuilder}.
     */
    public DefaultEngineBuilder() {
    }

    @Override
    public DefaultEngineBuilder noDefaults() {
        this.useDefaults = false;
        return this;
    }

    @Override
    public DefaultEngineBuilder effect(ImageEffect effect) {
        extraEffects.add(Objects.requireNonNull(effect, "effect must not be null"));
        return this;
    }

    @Override
    public DefaultEngineBuilder overlayRenderer(OverlayRenderer renderer) {
        overlayRenderers.add(Objects.requireNonNull(renderer, "renderer must not be null"));
        return this;
    }

    @Override
    public DefaultEngineBuilder fontProvider(FontProvider provider) {
        fontProviders.add(Objects.requireNonNull(provider, "provider must not be null"));
        return this;
    }

    @Override
    public <T> DefaultEngineBuilder registry(DataRegistry<T> registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        customRegistries.put(registry.key().name(), registry);
        return this;
    }

    @Override
    public DefaultEngineBuilder spriteProvider(SpriteProvider provider) {
        this.customSpriteProvider = Objects.requireNonNull(provider, "provider must not be null");
        return this;
    }

    @Override
    public DefaultEngineBuilder slotTexture(java.awt.image.BufferedImage slotTexture) {
        this.customSlotTexture = Objects.requireNonNull(slotTexture, "slotTexture must not be null");
        return this;
    }

    @Override
    public DefaultEngineBuilder resourcePack(ResourcePack resourcePack) {
        this.customResourcePack = Objects.requireNonNull(resourcePack, "resourcePack must not be null");
        return this;
    }

    @Override
    public DefaultEngineBuilder acceptMojangEula(boolean accept) {
        this.acceptEula = accept;
        return this;
    }

    @Override
    public DefaultEngineBuilder minecraftVersion(String mcVer) {
        this.mcVer = Objects.requireNonNull(mcVer, "mcVer must not be null");
        return this;
    }

    @Override
    public DefaultEngineBuilder assetDir(Path dir) {
        this.assetDir = Objects.requireNonNull(dir, "dir must not be null");
        return this;
    }

    /**
     * Implements {@link EngineBuilder#assetProvider(AssetProvider)}. Because
     * {@code AssetProvider} lives in {@code tessera-api.assets} the api interface
     * declares this method directly, with no concrete-only deferral required.
     */
    @Override
    public DefaultEngineBuilder assetProvider(AssetProvider provider) {
        programmaticAssetProviders.add(Objects.requireNonNull(provider, "provider must not be null"));
        return this;
    }

    @Override
    public DefaultEngineBuilder executorBound(int bound) {
        if (bound < 1) {
            throw new IllegalArgumentException("bound must be >= 1, got: " + bound);
        }
        this.explicitExecutorBound = bound;
        if (this.sharedExecutor != null) {
            log.warn("executor(Executor) was already called; executorBound({}) is ignored",
                    bound);
        }
        return this;
    }

    @Override
    public DefaultEngineBuilder executor(Executor executor) {
        this.sharedExecutor = Objects.requireNonNull(executor, "executor must not be null");
        if (this.explicitExecutorBound != null) {
            log.warn("Both executor() and executorBound() called; executor() wins");
        }
        return this;
    }

    @Override
    public DefaultEngineBuilder shutdownTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative: " + timeout);
        }
        this.shutdownTimeout = timeout;
        return this;
    }

    @Override
    public DefaultEngineBuilder staticOutputCapBytes(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("staticOutputCapBytes must be > 0, got: " + bytes);
        }
        this.staticOutputCapBytes = bytes;
        return this;
    }

    @Override
    public DefaultEngineBuilder animatedOutputCapBytes(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("animatedOutputCapBytes must be > 0, got: " + bytes);
        }
        this.animatedOutputCapBytes = bytes;
        return this;
    }

    /**
     * Registers a custom {@link NbtFormatHandler}. Concrete-only extension method; not
     * declared on the api {@link EngineBuilder} interface because the
     * {@link NbtFormatHandler} type lives in {@code tessera-spi} and exposing it on the
     * api would break the rule that the api must not depend on the spi module.
     *
     * @param handler the NBT handler; must not be {@code null}
     * @return this builder
     */
    public DefaultEngineBuilder nbtHandler(NbtFormatHandler handler) {
        nbtHandlers.add(Objects.requireNonNull(handler, "handler must not be null"));
        return this;
    }

    /**
     * Test-only seam: replaces the built-in generator for a given
     * {@link CoreRenderRequest} subtype with a test-supplied {@link Generator}.
     *
     * <p>Enables {@code FluentRenderExceptionWrappingTest} to prove the wrap invariant
     * (checked {@link net.aerh.tessera.api.exception.RenderException} /
     * {@link net.aerh.tessera.api.exception.ParseException} inside a {@link Generator}
     * is wrapped as {@link net.aerh.tessera.api.exception.RenderFailedException} at the
     * fluent {@code.render()} boundary) without requiring a full asset cache.
     *
     * <p>Declared on this concrete builder - NOT on the
     * {@link EngineBuilder} interface - so the test-only surface never leaks onto the
     * public api. Callers must downcast {@code (DefaultEngineBuilder) Engine.builder()}
     * to access it.
     *
     * @param requestType the sealed {@link CoreRenderRequest} subtype whose generator is replaced
     * @param generator the replacement generator; stored for use at {@link #build()} time
     * @return this builder
     * @throws NullPointerException if either argument is {@code null}
     */
    public DefaultEngineBuilder testingWithGenerator(
            Class<? extends CoreRenderRequest> requestType, Generator<?, ?> generator) {
        Objects.requireNonNull(requestType, "requestType must not be null");
        Objects.requireNonNull(generator, "generator must not be null");
        testGenerators.put(requestType, generator);
        return this;
    }

    @Override
    public Engine build() throws TesseraAssetsMissingException {
        // ----- Step 1: AssetProvider resolution. -----
        // minecraftVersion(...) is required as of 1.0.0. The previous fallback branch
        // called three @Deprecated(forRemoval=true) factories (AtlasSpriteProvider
        // .fromDefaults / OverlayLoader.fromDefaults / DefaultFontRegistry.withBuiltins)
        // that throw IllegalArgumentException at runtime on a fresh checkout once all
        // bundled Mojang bytes are stripped from the repo. Fail up-front with a clear
        // diagnostic instead of a cryptic downstream NullPointerException or
        // IllegalArgumentException.
        if (mcVer == null) {
            throw new IllegalStateException(
                    "Engine.builder().minecraftVersion(String) is required as of 1.0.0 - "
                            + "call .minecraftVersion(\"26.1.2\") before .build(). "
                            + "See net.aerh.tessera.api.assets.TesseraAssets#fetch for asset "
                            + "bootstrap; downstream tests that need a no-asset engine can use "
                            + "the package-private testingWithGenerator(...) + noDefaults() seam.");
        }
        AssetProvider resolvedProvider = AssetProviderResolver.resolve(
                mcVer, programmaticAssetProviders, TESSERA_VERSION);
        Capabilities resolvedCapabilities = resolvedProvider.capabilities();

        // ----- Step 2: asset-presence check (throws TesseraAssetsMissingException). -----
        verifyAssetsPresent();

        // ----- Step 2.5: ensure the stitched item atlas exists. -----
        // TesseraAssets.fetch invoked AssetProvider#hydrate to extract client.jar into the
        // cache; the atlas is derived from those per-item PNGs on first build (idempotent:
        // re-running with the atlas already on disk is a no-op).
        ensureAtlasBuilt(resolvedProvider);

        // ----- Step 3: Executor selection. -----
        Executor engineExecutor;
        boolean ownExecutor;
        if (sharedExecutor != null) {
            engineExecutor = sharedExecutor;
            ownExecutor = false;  // Consumer owns the lifecycle of externally-supplied executors.
        } else {
            int defaultBound = Math.max(16, Runtime.getRuntime().availableProcessors() * 4);
            int envBound = Optional.ofNullable(System.getenv("TESSERA_EXECUTOR_BOUND"))
                    .map(v -> {
                        try {
                            return Integer.parseInt(v);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid TESSERA_EXECUTOR_BOUND '{}'; using default {}",
                                    v, defaultBound);
                            return defaultBound;
                        }
                    })
                    .orElse(defaultBound);
            int resolvedBound = explicitExecutorBound != null ? explicitExecutorBound : envBound;
            engineExecutor = new BoundedVirtualExecutor(resolvedBound);
            ownExecutor = true;
        }

        // ----- Step 4: Sprite provider. -----
        // mcVer is guaranteed non-null by the guard at the top of build(), so we route
        // default sprite resolution through AtlasSpriteProvider.fromAssetProvider instead
        // of the deprecated fromDefaults() classpath fallback.
        SpriteProvider spriteProvider = customSpriteProvider != null
                ? customSpriteProvider
                : (useDefaults ? AtlasSpriteProvider.fromAssetProvider(resolvedProvider, mcVer) : null);
        Objects.requireNonNull(spriteProvider, "A SpriteProvider is required. Use defaults or provide one.");

        // ----- Step 5: Effect pipeline. -----
        // mcVer is guaranteed non-null by the guard at the top of build(), so the glint
        // texture is routed through GlintEffect.fromAssetProvider(resolvedProvider, mcVer)
        // instead of the no-arg constructor (which tries to load a now-stripped classpath
        // PNG and throws IllegalStateException at runtime on a fresh checkout).
        EffectPipeline.Builder pipelineBuilder = EffectPipeline.builder();
        if (useDefaults) {
            pipelineBuilder
                    .add(GlintEffect.fromAssetProvider(resolvedProvider, mcVer))
                    .add(new HoverEffect())
                    .add(new DurabilityBarEffect());
        }
        extraEffects.forEach(pipelineBuilder::add);

        // ----- Step 6: Font + overlay registries. -----
        // Route defaults through the asset-provider variants now that mcVer is guaranteed.
        // The deprecated no-arg / classpath fallbacks remain on the public surface for
        // compatibility but are no longer reached from engine builds.
        DefaultFontRegistry fontRegistry = DefaultFontRegistry.withBuiltins(resolvedProvider, mcVer);
        fontProviders.forEach(fontRegistry::register);

        OverlayRegistry overlayRegistry = OverlayRegistry.withDefaults();
        overlayRenderers.forEach(overlayRegistry::register);

        OverlayLoader overlayLoader = useDefaults
                ? OverlayLoader.fromAssetProvider(resolvedProvider, mcVer)
                : null;

        if (useDefaults) {
            pipelineBuilder.add(new OverlayEffect(overlayRegistry));
        }

        EffectPipeline effectPipeline = pipelineBuilder.build();

        // ----- Step 7: Data registries. -----
        Map<String, DataRegistry<?>> registries = new HashMap<>(customRegistries);

        // ----- Step 8: NBT parser (ServiceLoader + builder-registered + defaults). -----
        List<NbtFormatHandler> handlers = new ArrayList<>(nbtHandlers);
        if (useDefaults) {
            addDefaultNbtHandlers(handlers);
        }
        NbtParser nbtParser = buildNbtParser(handlers);

        // ----- Step 9: Overlay color provider + text renderer. -----
        OverlayColorProvider overlayColorProvider = OverlayColorProvider.fromDefaults();
        MinecraftTextRenderer textRenderer = new MinecraftTextRenderer(fontRegistry);

        // ----- Step 10: Wire + cache-wrap the 5 built-in generators. -----
        // Each keyFunction produces a CacheKey stamped with CACHE_KEY_VERSION.
        // testGenerators overrides take precedence via the testingWithGenerator seam.
        Generator<ItemRequest, GeneratorResult> itemGenerator = resolveGenerator(
                ItemRequest.class,
                () -> new CachingGenerator<>(
                        new ItemGenerator(spriteProvider, effectPipeline, overlayLoader),
                        keyFromRecord(), DEFAULT_CACHE_MAX_SIZE));
        Generator<TooltipRequest, GeneratorResult> tooltipGenerator = resolveGenerator(
                TooltipRequest.class,
                () -> new CachingGenerator<>(
                        new TooltipGenerator(textRenderer),
                        keyFromRecord(), DEFAULT_CACHE_MAX_SIZE));
        Generator<InventoryRequest, GeneratorResult> inventoryGenerator = resolveGenerator(
                InventoryRequest.class,
                () -> new CachingGenerator<>(
                        new InventoryGenerator(spriteProvider, effectPipeline, customSlotTexture,
                                fontRegistry, engineExecutor),
                        keyFromRecord(), DEFAULT_CACHE_MAX_SIZE));
        Generator<PlayerHeadRequest, GeneratorResult> playerHeadGenerator = resolveGenerator(
                PlayerHeadRequest.class,
                () -> new CachingGenerator<>(
                        PlayerHeadGenerator.withDefaults(),
                        keyFromRecord(), DEFAULT_CACHE_MAX_SIZE));

        ArmorTexture armorTexture = new ArmorTexture();
        Generator<PlayerModelRequest, GeneratorResult> playerModelGenerator = resolveGenerator(
                PlayerModelRequest.class,
                () -> new CachingGenerator<>(
                        PlayerModelGenerator.withDefaults(armorTexture),
                        keyFromRecord(), DEFAULT_CACHE_MAX_SIZE));

        // ----- Step 11: Generator registry (Class-keyed map for StartupCheck). -----
        Map<Class<? extends CoreRenderRequest>, Generator<?, ?>> generators = new HashMap<>();
        generators.put(ItemRequest.class, itemGenerator);
        generators.put(TooltipRequest.class, tooltipGenerator);
        generators.put(InventoryRequest.class, inventoryGenerator);
        generators.put(PlayerHeadRequest.class, playerHeadGenerator);
        generators.put(PlayerModelRequest.class, playerModelGenerator);
        // CompositeRequest has no single generator (fan-out is done inside DefaultEngine);
        // register a marker so StartupCheck passes. The marker's render() is never called.
        generators.put(CompositeRequest.class, CompositeMarkerGenerator.INSTANCE);

        // ----- Step 12: Startup check. -----
        StartupCheck.verifyAllCoreRequestsHaveGenerators(generators);

        // ----- Step 13: Shutdown-timeout default. -----
        Duration resolvedShutdownTimeout = shutdownTimeout != null
                ? shutdownTimeout
                : Duration.ofSeconds(5);

        // ----- Step 14: ResourcePack threading. -----
        // When customResourcePack is non-null we wrap it in a LayeredResourcePack with no
        // vanilla fallback pack in v1: a single admin-configured override is all we allow.
        // Vanilla asset lookup already flows through AssetProvider; ResourcePack is a
        // secondary lookup surface for admins who want to override specific textures.
        // Future work will stack a vanilla-derived ResourcePack beneath the override once
        // the asset pipeline exposes one.
        List<ResourcePack> resourcePacks = new ArrayList<>();
        if (this.customResourcePack != null) {
            resourcePacks.add(new LayeredResourcePack(List.of(this.customResourcePack)));
        }

        // ----- Step 15: engine-owned HttpClient. -----
        // Owned by DefaultEngine; closed in close() step 5.
        HttpClient engineHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // ----- Step 16: resolve output-size caps via OutputSizeGate precedence. -----
        long staticCap = OutputSizeGate.resolveStaticCap(this.staticOutputCapBytes);
        long animatedCap = OutputSizeGate.resolveAnimatedCap(this.animatedOutputCapBytes);

        return new DefaultEngine(
                spriteProvider,
                itemGenerator,
                tooltipGenerator,
                inventoryGenerator,
                playerHeadGenerator,
                playerModelGenerator,
                nbtParser,
                registries,
                overlayColorProvider,
                resolvedCapabilities,
                engineExecutor,
                ownExecutor,
                resolvedShutdownTimeout,
                resourcePacks,
                engineHttpClient,
                staticCap,
                animatedCap
        );
    }

    /**
     * Returns the test-installed {@link Generator} for {@code requestType} when present,
     * otherwise invokes {@code defaultSupplier}. Cast is safe because
     * {@link #testingWithGenerator} constrains the value type to
     * {@code Generator<? extends CoreRenderRequest, ?>}.
     */
    @SuppressWarnings("unchecked")
    private <I extends CoreRenderRequest, O> Generator<I, O> resolveGenerator(
            Class<I> requestType, java.util.function.Supplier<Generator<I, O>> defaultSupplier) {
        Generator<?, ?> override = testGenerators.get(requestType);
        if (override != null) {
            return (Generator<I, O>) override;
        }
        return defaultSupplier.get();
    }

    /**
     * Returns a {@link Function} that derives a {@link CacheKey} from any
     * {@link CoreRenderRequest}-implementing record by calling its own {@code cacheKey()}
     * method. Cast is safe because the 5 CachingGenerator constructions above all feed
     * {@link CoreRenderRequest}-implementing records.
     */
    private static <I> Function<I, CacheKey> keyFromRecord() {
        return input -> ((CoreRenderRequest) input).cacheKey();
    }

    /**
     * Verifies every entry in the pinned manifest for the configured Minecraft version is
     * present in the resolved cache directory. No-op when {@link #mcVer} is unset.
     */
    private void verifyAssetsPresent() throws TesseraAssetsMissingException {
        if (mcVer == null) {
            return;
        }
        AssetManifest manifest = ManifestLoader.load(mcVer);
        Path cacheDir = CacheLocator.resolve(assetDir, mcVer);
        int missing = 0;
        for (AssetEntry entry : manifest.files()) {
            if (!Files.exists(cacheDir.resolve(entry.path()))) {
                missing++;
            }
        }
        if (missing > 0) {
            throw new TesseraAssetsMissingException(
                    "Tessera assets for MC " + mcVer + " are not cached at " + cacheDir + "; "
                            + missing + " file(s) missing. "
                            + "Call TesseraAssets.fetch(\"" + mcVer + "\") before "
                            + "Engine.builder()...build(). "
                            + "See net.aerh.tessera.api.assets.TesseraAssets for details.",
                    Map.of(
                            "mcVer", mcVer,
                            "missingCount", missing,
                            "cacheDir", cacheDir.toString()
                    )
            );
        }
    }

    /**
     * Stitches the item atlas on first build when the client.jar texture dir has been
     * hydrated. Idempotent:
     * <ul>
     *   <li>No-op if {@code resolvedProvider} is {@code null} (no mcVer set).</li>
     *   <li>No-op if {@code <cacheRoot>/tessera/atlas/item_atlas.png} already exists.</li>
     *   <li>No-op if {@code <cacheRoot>/assets/minecraft/textures/item/} has not been
     *       hydrated yet (the atlas cannot be stitched without input PNGs; {@link StartupCheck}
     *       already guards on TesseraAssetsMissingException).</li>
     * </ul>
     */
    private static void ensureAtlasBuilt(AssetProvider resolvedProvider) {
        if (resolvedProvider == null) {
            return;
        }
        Path cacheRoot;
        try {
            cacheRoot = resolvedProvider.resolveAssetRoot(
                    resolvedProvider.supportedVersions().iterator().next());
        } catch (RuntimeException e) {
            // Defensive: if resolveAssetRoot throws, skip atlas building - the broken
            // asset provider will surface later via verifyAssetsPresent / StartupCheck.
            log.warn("Skipping atlas build: resolveAssetRoot failed", e);
            return;
        }
        Path atlasPng = cacheRoot.resolve("tessera/atlas/item_atlas.png");
        if (Files.exists(atlasPng)) {
            return;
        }
        Path itemTextureDir = cacheRoot.resolve("assets/minecraft/textures/item");
        if (!Files.isDirectory(itemTextureDir)) {
            // Hydration has not run yet (e.g. test env without TesseraAssets.fetch). Leaving
            // the atlas unbuilt is the correct behaviour - Render paths will fail at
            // construction of AtlasSpriteProvider.fromAssetProvider, which is diagnostic enough.
            log.debug("Skipping atlas build: {} missing (hydrate() not yet run)", itemTextureDir);
            return;
        }
        try {
            TesseraAtlasBuilder.buildAtlas(cacheRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to build atlas from " + cacheRoot
                            + ". Ensure TesseraAssets.fetch ran successfully before Engine.builder().build().",
                    e);
        }
    }

    private static void addDefaultNbtHandlers(List<NbtFormatHandler> handlers) {
        handlers.add(new ComponentsNbtFormatHandler());
        handlers.add(new PostFlatteningNbtFormatHandler());
        handlers.add(new PreFlatteningNbtFormatHandler());
        handlers.add(new DefaultNbtFormatHandler());
    }

    private static NbtParser buildNbtParser(List<NbtFormatHandler> handlers) {
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

    /**
     * Sentinel {@link Generator} registered for {@link CompositeRequest} in the startup-check
     * map. Composite fan-out is handled inside {@link DefaultEngine#renderInternal}; this
     * marker exists solely so {@link StartupCheck} sees an entry for every permitted subtype
     * of {@link CoreRenderRequest}. Its {@code render()} is never called.
     */
    private static final class CompositeMarkerGenerator implements Generator<CompositeRequest, GeneratorResult> {
        static final CompositeMarkerGenerator INSTANCE = new CompositeMarkerGenerator();

        private CompositeMarkerGenerator() {
        }

        @Override
        public GeneratorResult render(CompositeRequest input, net.aerh.tessera.api.generator.GenerationContext context) {
            throw new IllegalStateException(
                    "CompositeMarkerGenerator should never be invoked; composite fan-out is owned by DefaultEngine.");
        }

        @Override
        public Class<CompositeRequest> inputType() {
            return CompositeRequest.class;
        }

        @Override
        public Class<GeneratorResult> outputType() {
            return GeneratorResult.class;
        }
    }
}
