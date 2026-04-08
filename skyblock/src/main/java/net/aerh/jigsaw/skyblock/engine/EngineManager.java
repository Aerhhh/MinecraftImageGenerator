package net.aerh.jigsaw.skyblock.engine;

import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.resource.PackMetadata;
import net.aerh.jigsaw.core.resource.ResourcePackSpriteProvider;
import net.aerh.jigsaw.core.resource.ZipResourcePack;
import net.aerh.jigsaw.core.sprite.AtlasSpriteProvider;
import net.aerh.jigsaw.core.sprite.ChainedSpriteProvider;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages named {@link Engine} instances backed by resource pack zip files loaded from a directory.
 *
 * <p>On construction, the directory is scanned for {@code *.zip} files. Each valid zip is opened
 * as a {@link ZipResourcePack}, a {@link SpriteProvider} is built (optionally chained with the
 * vanilla atlas), and an {@link Engine} is created for it. The pack name is the zip filename
 * minus the {@code .zip} extension, lowercased.
 *
 * <p>State is held in an {@link AtomicReference} to allow thread-safe hot-reloads via
 * {@link #reload()}: a new state is built first, then atomically swapped in, and the old
 * packs are closed.
 */
public class EngineManager implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineManager.class);

    private final Path packDirectory;
    private final @Nullable String defaultPackName;
    private final boolean vanillaFallback;
    private final Engine vanillaEngine;

    private final AtomicReference<PackState> stateRef = new AtomicReference<>();

    /**
     * Creates an {@link EngineManager} and immediately loads all packs from the given directory.
     *
     * @param packDirectory  the directory to scan for {@code *.zip} resource packs
     * @param defaultPackName the name of the pack to use as default; may be {@code null} to use
     *                        vanilla-only as default
     * @param vanillaFallback if {@code true}, the vanilla atlas is chained after each pack's
     *                        sprite provider so that items missing from the pack fall back to vanilla
     */
    public EngineManager(Path packDirectory, @Nullable String defaultPackName, boolean vanillaFallback) {
        this.packDirectory = packDirectory;
        this.defaultPackName = defaultPackName != null ? defaultPackName.toLowerCase() : null;
        this.vanillaFallback = vanillaFallback;
        this.vanillaEngine = Engine.builder().build();
        loadPacks();
    }

    /**
     * Scans {@link #packDirectory} for {@code *.zip} files, builds an {@link Engine} per pack,
     * and atomically replaces the current state. Old packs are closed after the swap.
     *
     * <p>If the directory does not exist it is created. Corrupt or unreadable zips are skipped
     * with a warning.
     */
    public void loadPacks() {
        ensureDirectoryExists();

        Map<String, Engine> engines = new TreeMap<>();
        Map<String, PackMetadata> metadata = new TreeMap<>();
        List<ZipResourcePack> packs = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packDirectory, "*.zip")) {
            for (Path zipPath : stream) {
                String packName = derivePackName(zipPath);
                ZipResourcePack pack = null;
                try {
                    pack = new ZipResourcePack(zipPath);
                    SpriteProvider spriteProvider = buildSpriteProvider(pack);
                    // Extract slot texture from pack's GUI texture if available
                    java.awt.image.BufferedImage slotTexture =
                            net.aerh.jigsaw.core.generator.InventoryGenerator.extractSlotTextureFromPack(pack);

                    var engineBuilder = Engine.builder().spriteProvider(spriteProvider);
                    if (slotTexture != null) {
                        engineBuilder.slotTexture(slotTexture);
                    }
                    Engine engine = engineBuilder.build();
                    engines.put(packName, engine);
                    metadata.put(packName, pack.metadata());
                    packs.add(pack);
                } catch (Exception e) {
                    LOGGER.warn("Skipping corrupt or unreadable pack '{}': {}", zipPath.getFileName(), e.getMessage());
                    if (pack != null) {
                        try {
                            pack.close();
                        } catch (IOException closeEx) {
                            LOGGER.warn("Failed to close pack '{}' after load error", zipPath.getFileName(), closeEx);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan pack directory '{}': {}", packDirectory, e.getMessage());
        }

        if (defaultPackName != null && !engines.containsKey(defaultPackName)) {
            LOGGER.warn(
                "Configured default pack '{}' was not found; falling back to vanilla",
                defaultPackName
            );
        }

        PackState newState = new PackState(
            Collections.unmodifiableMap(engines),
            Collections.unmodifiableMap(metadata),
            List.copyOf(packs)
        );

        PackState oldState = stateRef.getAndSet(newState);
        if (oldState != null) {
            closePacksSilently(oldState.packs());
        }
    }

    /**
     * Reloads all packs by delegating to {@link #loadPacks()}.
     */
    public void reload() {
        loadPacks();
    }

    /**
     * Returns the {@link Engine} for the given pack name, or the default engine if the name is
     * {@code null} or not found.
     *
     * <p>Pack name lookup is case-insensitive.
     *
     * @param packName the name of the pack (filename without {@code .zip}); may be {@code null}
     * @return the engine for the named pack, or the default engine
     */
    public Engine getEngine(@Nullable String packName) {
        if (packName == null) {
            return getDefaultEngine();
        }
        String normalized = packName.toLowerCase();
        Engine engine = stateRef.get().engines().get(normalized);
        if (engine == null) {
            return getDefaultEngine();
        }
        return engine;
    }

    /**
     * Returns the default {@link Engine}.
     *
     * <p>If a default pack name was configured and the pack was loaded, that engine is returned.
     * Otherwise a vanilla-only engine is returned.
     *
     * @return the default engine
     */
    public Engine getDefaultEngine() {
        if (defaultPackName != null) {
            Engine engine = stateRef.get().engines().get(defaultPackName);
            if (engine != null) {
                return engine;
            }
        }
        return vanillaEngine;
    }

    /**
     * Returns a sorted, unmodifiable collection of all currently loaded pack names.
     *
     * @return available pack names in alphabetical order
     */
    public Collection<String> availablePackNames() {
        return Collections.unmodifiableCollection(stateRef.get().engines().keySet());
    }

    /**
     * Returns the {@link PackMetadata} for the named pack, or empty if not found.
     *
     * @param packName the pack name (case-insensitive)
     * @return an {@link Optional} containing the metadata, or empty if the pack is not loaded
     */
    public Optional<PackMetadata> getPackMetadata(String packName) {
        String normalized = packName.toLowerCase();
        return Optional.ofNullable(stateRef.get().metadata().get(normalized));
    }

    /**
     * Closes all open {@link ZipResourcePack} instances in the current state.
     */
    @Override
    public void close() {
        PackState state = stateRef.get();
        if (state != null) {
            closePacksSilently(state.packs());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private SpriteProvider buildSpriteProvider(ZipResourcePack pack) {
        SpriteProvider packProvider = new ResourcePackSpriteProvider(pack);
        if (vanillaFallback) {
            AtlasSpriteProvider vanilla = AtlasSpriteProvider.fromDefaults();
            return new ChainedSpriteProvider(List.of(packProvider, vanilla));
        }
        return packProvider;
    }

    private static String derivePackName(Path zipPath) {
        String filename = zipPath.getFileName().toString();
        String withoutExtension = filename.endsWith(".zip")
            ? filename.substring(0, filename.length() - ".zip".length())
            : filename;
        return withoutExtension.toLowerCase();
    }

    private void ensureDirectoryExists() {
        if (!Files.exists(packDirectory)) {
            try {
                Files.createDirectories(packDirectory);
                LOGGER.info("Created pack directory: {}", packDirectory);
            } catch (IOException e) {
                LOGGER.warn("Failed to create pack directory '{}': {}", packDirectory, e.getMessage());
            }
        }
    }

    private static void closePacksSilently(List<ZipResourcePack> packs) {
        for (ZipResourcePack pack : packs) {
            try {
                pack.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close pack during cleanup", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal state record
    // -------------------------------------------------------------------------

    private record PackState(
        Map<String, Engine> engines,
        Map<String, PackMetadata> metadata,
        List<ZipResourcePack> packs
    ) {}
}
