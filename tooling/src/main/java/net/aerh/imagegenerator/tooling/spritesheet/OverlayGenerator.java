package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.aerh.imagegenerator.tooling.CliArgs;
import net.aerh.imagegenerator.tooling.ToolingPaths;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates the overlay spritesheet from Minecraft texture assets.
 * <p>
 * Which overlays exist comes from a definitions file: a list of special overlays with their colour handling,
 * and the file name prefixes that mark dyeable armour overlays. The tooling bundles that file as a classpath
 * resource; callers can point at their own with {@value #DEFINITIONS_FLAG}. A supplied file replaces the
 * bundled one entirely, so a key it leaves out means "none".
 */
public class OverlayGenerator extends AtlasGenerator {

    public static final String DEFINITIONS_FLAG = "--overlay-definitions";

    private static final String BUNDLED_DEFINITIONS = "/overlay_definitions.json";
    private static final int ATLAS_WIDTH = 1_024;

    private final Path itemTextures;
    private final Path trimItems;
    private final List<OverlayDefinition> specialOverlayDefinitions;
    private final List<String> armorOverlayPatterns;

    /**
     * Uses the definitions bundled with the tooling.
     *
     * @throws IOException if the bundled definitions are missing or malformed
     */
    public OverlayGenerator(ToolingPaths paths) throws IOException {
        this(paths, readBundledDefinitions());
    }

    /**
     * Uses the caller's own definitions file instead of the bundled one.
     *
     * @throws IOException if the file cannot be read or is not a valid definitions document
     */
    public OverlayGenerator(ToolingPaths paths, Path definitionsFile) throws IOException {
        this(paths, readDefinitions(definitionsFile));
    }

    private OverlayGenerator(ToolingPaths paths, OverlayDefinitions definitions) {
        super(
            new AtlasLayout(AtlasLayout.DEFAULT_TILE_SIZE, ATLAS_WIDTH, AtlasLayout.UNLIMITED, 0),
            paths.spritesheets().resolve("overlays.png"),
            paths.json().resolve("overlay_coordinates.json")
        );
        this.itemTextures = paths.minecraftAssets().resolve("textures/item");
        this.trimItems = paths.minecraftAssets().resolve("textures/trims/items");
        this.specialOverlayDefinitions = definitions.specialOverlays();
        this.armorOverlayPatterns = definitions.armorOverlayPatterns();
    }

    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(50));
            System.out.println("Overlay Generator");
            System.out.println("=".repeat(50));

            ToolingPaths paths = ToolingPaths.fromArgs(args);
            Optional<Path> definitionsFile = CliArgs.pathValue(args, DEFINITIONS_FLAG);

            if (!Files.exists(paths.minecraftAssets())) {
                throw new IOException("Minecraft assets not found at: " + paths.minecraftAssets()
                    + "\nRun 'MinecraftAssetTool --download' first.");
            }

            OverlayGenerator generator = definitionsFile.isPresent()
                ? new OverlayGenerator(paths, definitionsFile.get())
                : new OverlayGenerator(paths);
            generator.generate();
            new OverlayColorConfigGenerator(paths).generate();

            System.out.println("\n" + "=".repeat(50));
            System.out.println("Overlay generation complete!");
            System.out.println("=".repeat(50));
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            System.err.println("Failed to generate overlays: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static OverlayDefinitions readBundledDefinitions() throws IOException {
        try (InputStream stream = OverlayGenerator.class.getResourceAsStream(BUNDLED_DEFINITIONS)) {
            if (stream == null) {
                throw new IOException("Bundled overlay definitions " + BUNDLED_DEFINITIONS + " are missing from the tooling classpath");
            }

            return parseDefinitions(new InputStreamReader(stream, StandardCharsets.UTF_8), "bundled " + BUNDLED_DEFINITIONS);
        }
    }

    private static OverlayDefinitions readDefinitions(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Overlay definitions file not found: " + file);
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            return parseDefinitions(reader, file.toString());
        }
    }

    private static OverlayDefinitions parseDefinitions(Reader reader, String source) throws IOException {
        JsonObject config;
        try {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Overlay definitions " + source + " are not a JSON object: " + e.getMessage(), e);
        }

        List<OverlayDefinition> specials = new ArrayList<>();
        if (config.has("specialOverlays")) {
            for (JsonElement element : asArray(config, "specialOverlays", source)) {
                if (!element.isJsonObject()) {
                    throw new IOException("Overlay definitions " + source + ": every specialOverlays entry must be an object");
                }

                JsonObject obj = element.getAsJsonObject();
                specials.add(new OverlayDefinition(
                    requiredString(obj, "fileName", source),
                    requiredString(obj, "colorOptions", source),
                    requiredString(obj, "type", source),
                    obj.has("colorMode") ? obj.get("colorMode").getAsString() : null
                ));
            }
        }

        List<String> patterns = new ArrayList<>();
        if (config.has("armorOverlayPatterns")) {
            for (JsonElement element : asArray(config, "armorOverlayPatterns", source)) {
                patterns.add(element.getAsString());
            }
        }

        System.out.println("  Loaded " + specials.size() + " special overlay definitions and "
            + patterns.size() + " armor overlay patterns from " + source);
        return new OverlayDefinitions(List.copyOf(specials), List.copyOf(patterns));
    }

    private static JsonArray asArray(JsonObject config, String key, String source) throws IOException {
        if (!config.get(key).isJsonArray()) {
            throw new IOException("Overlay definitions " + source + ": " + key + " must be an array");
        }

        return config.getAsJsonArray(key);
    }

    private static String requiredString(JsonObject obj, String key, String source) throws IOException {
        JsonElement value = obj.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IOException("Overlay definitions " + source + ": a specialOverlays entry is missing '" + key + "'");
        }

        return value.getAsString();
    }

    @Override
    protected List<AtlasEntry> loadTextures() {
        List<AtlasEntry> overlays = new ArrayList<>();

        System.out.println("\nDiscovering dyeable armor overlays from: " + itemTextures);
        List<OverlayDefinition> armorOverlays = discoverArmorOverlays();
        System.out.println("  Found " + armorOverlays.size() + " armor overlays");
        loadOverlays(overlays, itemTextures, armorOverlays);

        System.out.println("\nDiscovering special overlays from definitions");
        List<OverlayDefinition> specialOverlays = discoverSpecialOverlays();
        System.out.println("  Found " + specialOverlays.size() + " special overlays");
        loadOverlays(overlays, itemTextures, specialOverlays);

        System.out.println("\nDiscovering trim overlays from: " + trimItems);
        List<OverlayDefinition> trimOverlays = discoverTrimOverlays();
        System.out.println("  Found " + trimOverlays.size() + " trim types");
        loadOverlays(overlays, trimItems, trimOverlays);

        System.out.println("\nTotal overlays loaded: " + overlays.size());
        return overlays;
    }

    /**
     * Keeps the special overlay definitions whose texture exists in the assets.
     * These have unique color handling so mappings are kept explicit, but we verify existence.
     */
    private List<OverlayDefinition> discoverSpecialOverlays() {
        if (!Files.exists(itemTextures)) {
            return List.of();
        }

        return specialOverlayDefinitions.stream()
            .filter(def -> Files.exists(itemTextures.resolve(def.fileName + ".png")))
            .collect(Collectors.toList());
    }

    /**
     * Discovers dyeable armor overlays from the textures directory using the configured name prefixes.
     */
    private List<OverlayDefinition> discoverArmorOverlays() {
        List<OverlayDefinition> definitions = new ArrayList<>();

        if (!Files.exists(itemTextures)) {
            return definitions;
        }

        File[] files = itemTextures.toFile().listFiles((dir, name) -> {
            if (!name.endsWith("_overlay.png")) {
                return false;
            }

            return armorOverlayPatterns.stream().anyMatch(name::startsWith);
        });

        if (files == null) {
            return definitions;
        }

        for (File file : files) {
            String name = file.getName().replace(".png", "");
            definitions.add(new OverlayDefinition(name, "leather_armor", "NORMAL", null));
        }

        return definitions.stream()
            .sorted((a, b) -> a.fileName.compareTo(b.fileName))
            .collect(Collectors.toList());
    }

    /**
     * Discovers armor trim overlays from the trims/items directory. The game ships one greyscale overlay per
     * armour piece there, named {@code <armor>_trim.png}, and recolours it per material at runtime; each such
     * file becomes a MAPPED definition named after the file.
     */
    private List<OverlayDefinition> discoverTrimOverlays() {
        if (!Files.exists(trimItems)) {
            return List.of();
        }

        File[] files = trimItems.toFile().listFiles((dir, name) -> name.endsWith("_trim.png"));

        if (files == null) {
            return List.of();
        }

        return Arrays.stream(files)
            .map(f -> f.getName().replace(".png", ""))
            .sorted()
            .map(name -> new OverlayDefinition(name, "armor_trim", "MAPPED", null))
            .collect(Collectors.toList());
    }

    private void loadOverlays(List<AtlasEntry> overlays, Path sourceDir, List<OverlayDefinition> definitions) {
        if (!Files.exists(sourceDir)) {
            System.err.println("  Directory not found: " + sourceDir);
            return;
        }

        for (OverlayDefinition def : definitions) {
            File file = sourceDir.resolve(def.fileName + ".png").toFile();
            if (!file.exists()) {
                System.err.println("  Not found: " + def.fileName);
                continue;
            }

            try {
                BufferedImage image = ImageIO.read(file);
                if (image == null) {
                    System.err.println("  Failed to read: " + def.fileName);
                    continue;
                }

                BufferedImage resized = resizeToLayoutSize(ImageUtil.convertToArgbRaw(image));

                AtlasEntry entry = new AtlasEntry(def.fileName, resized)
                    .withMeta("type", def.type)
                    .withMeta("colorOptions", def.colorOptions);

                if (def.colorMode != null) {
                    entry.withMeta("colorMode", def.colorMode);
                }

                overlays.add(entry);
                System.out.println("  Loaded: " + def.fileName + " (" + image.getWidth() + "x" + image.getHeight()
                    + " -> " + layout.tileSize() + "x" + layout.tileSize() + ")");
            } catch (IOException e) {
                System.err.println("  Error loading " + def.fileName + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected JsonObject entryToJson(AtlasEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", entry.name);
        obj.addProperty("x", entry.x);
        obj.addProperty("y", entry.y);
        obj.addProperty("size", layout.tileSize());
        obj.addProperty("type", entry.getMeta("type"));
        obj.addProperty("colorOptions", entry.getMeta("colorOptions"));

        String colorMode = entry.getMeta("colorMode");
        if (colorMode != null) {
            obj.addProperty("colorMode", colorMode);
        }
        return obj;
    }

    private record OverlayDefinition(String fileName, String colorOptions, String type, String colorMode) {
    }

    private record OverlayDefinitions(List<OverlayDefinition> specialOverlays, List<String> armorOverlayPatterns) {
    }
}
