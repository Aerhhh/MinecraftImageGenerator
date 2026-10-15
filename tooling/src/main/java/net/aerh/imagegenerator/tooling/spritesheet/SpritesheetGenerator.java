package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonObject;
import net.aerh.imagegenerator.tooling.CliArgs;
import net.aerh.imagegenerator.tooling.ToolingPaths;
import net.aerh.imagegenerator.tooling.minecraft.TrimPaletteExtractor;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates the item texture atlas from a directory of individual PNG renders.
 */
public class SpritesheetGenerator extends AtlasGenerator {

    public static final String INPUT_FLAG = "--input";

    private static final String[] ARMOR_TYPES = {"helmet", "chestplate", "leggings", "boots"};

    private final Path inputDir;
    private final Path minecraftAssets;
    private Set<String> trimMaterials;

    /**
     * Packs the renders found in {@link ToolingPaths#renderedItems()}.
     */
    public SpritesheetGenerator(ToolingPaths paths, AtlasLayout layout) {
        this(paths.renderedItems(), paths, layout);
    }

    public SpritesheetGenerator(Path inputDir, ToolingPaths paths, AtlasLayout layout) {
        super(
            layout,
            paths.spritesheets().resolve("minecraft_texture_atlas.png"),
            paths.json().resolve("atlas_coordinates.json")
        );
        this.inputDir = inputDir;
        this.minecraftAssets = paths.minecraftAssets();
    }

    public static void main(String[] args) {
        try {
            CliArgs.checkKnown(args, Set.of(), Set.of(
                INPUT_FLAG, AtlasLayout.TILE_SIZE_FLAG, AtlasLayout.ATLAS_WIDTH_FLAG, AtlasLayout.ATLAS_HEIGHT_FLAG,
                AtlasLayout.PADDING_FLAG, ToolingPaths.WORK_DIR_FLAG, ToolingPaths.OUTPUT_DIR_FLAG
            ));
            ToolingPaths paths = ToolingPaths.fromArgs(args);
            AtlasLayout layout = AtlasLayout.fromArgs(args, AtlasLayout.DEFAULT);
            Path inputDir = CliArgs.pathValue(args, INPUT_FLAG).orElse(paths.renderedItems());

            new SpritesheetGenerator(inputDir, paths, layout).generate();
            System.out.println("Texture atlas generation complete!");
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private boolean isArmorTrimVariant(String name) {
        if (!name.endsWith("_trim")) {
            return false;
        }

        if (trimMaterials == null || trimMaterials.isEmpty()) {
            return false;
        }

        return Arrays.stream(ARMOR_TYPES)
            .anyMatch(armor -> trimMaterials.stream()
                .anyMatch(material -> name.endsWith(armor + "_" + material + "_trim"))
            );
    }

    /**
     * Reads the trim material names so renders of trimmed armour can be left out of the atlas. Assets without
     * any palettes give an empty set; a palette layout that is present but broken ends the run, because packing
     * every trim variant by mistake is exactly what the filter exists to prevent.
     */
    private void loadTrimMaterials() {
        try {
            trimMaterials = TrimPaletteExtractor.load(minecraftAssets).materials().keySet();
            System.out.println("Loaded " + trimMaterials.size() + " trim materials from assets.");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load trim materials: " + e.getMessage(), e);
        }
    }

    /**
     * Reads every PNG in the input directory in name order. A directory that cannot be listed and a file that
     * is not a readable image both end the run: skipping them would pack an atlas with holes and report success,
     * which is worse for a build than a clear failure.
     */
    @Override
    protected List<AtlasEntry> loadTextures() {
        loadTrimMaterials();

        List<Path> files = listPngs();
        System.out.println("Loading textures from: " + inputDir.toAbsolutePath());

        List<AtlasEntry> textures = new ArrayList<>();
        int skipped = 0;
        int trimFiltered = 0;

        for (Path file : files) {
            String name = file.getFileName().toString().replace(".png", "");

            if (isArmorTrimVariant(name)) {
                trimFiltered++;
                continue;
            }

            BufferedImage processed = resizeToLayoutSize(readImage(file));

            if (ImageUtil.isFullyTransparent(processed)) {
                skipped++;
                continue;
            }

            textures.add(new AtlasEntry(name, processed));
        }

        if (skipped > 0) {
            System.out.println("Skipped " + skipped + " fully transparent textures.");
        }

        if (trimFiltered > 0) {
            System.out.println("Filtered out " + trimFiltered + " armor trim variants (rendered dynamically).");
        }

        return textures;
    }

    private List<Path> listPngs() {
        try (Stream<Path> listing = Files.list(inputDir)) {
            return listing
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().toLowerCase().endsWith(".png"))
                .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read texture input directory " + inputDir.toAbsolutePath(), e);
        }
    }

    /**
     * @throws UncheckedIOException when the file cannot be read, or is not in a format ImageIO understands
     */
    private static BufferedImage readImage(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                throw new IOException("not a readable image");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load texture " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    protected JsonObject entryToJson(AtlasEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", entry.name);
        obj.addProperty("x", entry.x);
        obj.addProperty("y", entry.y);
        obj.addProperty("size", layout.tileSize());
        return obj;
    }
}
