package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonObject;
import net.aerh.imagegenerator.tooling.CliArgs;
import net.aerh.imagegenerator.tooling.ToolingPaths;
import net.aerh.imagegenerator.tooling.minecraft.TrimPaletteExtractor;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Generates the item texture atlas from a directory of individual PNG renders.
 */
public class SpritesheetGenerator extends AtlasGenerator {

    public static final String INPUT_FLAG = "--input";

    private static final String[] ARMOR_TYPES = {"helmet", "chestplate", "leggings", "boots"};

    private final Path inputDir;
    private final Path trimPalettes;
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
        this.trimPalettes = paths.trimPalettes();
    }

    public static void main(String[] args) {
        try {
            ToolingPaths paths = ToolingPaths.fromArgs(args);
            AtlasLayout layout = AtlasLayout.fromArgs(args, AtlasLayout.DEFAULT);
            Path inputDir = CliArgs.pathValue(args, INPUT_FLAG).orElse(paths.renderedItems());

            new SpritesheetGenerator(inputDir, paths, layout).generate();
            System.out.println("Texture atlas generation complete!");
        } catch (IllegalArgumentException | UncheckedIOException e) {
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

    private void loadTrimMaterials() {
        try {
            trimMaterials = TrimPaletteExtractor.extractAllPalettes(trimPalettes).keySet();
            System.out.println("Loaded " + trimMaterials.size() + " trim materials from assets.");
        } catch (Exception e) {
            System.err.println("Failed to load trim materials: " + e.getMessage());
            trimMaterials = Set.of();
        }
    }

    @Override
    protected List<AtlasEntry> loadTextures() {
        loadTrimMaterials();

        List<AtlasEntry> textures = new ArrayList<>();
        File folder = inputDir.toFile();
        File[] files = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));

        if (files == null) {
            System.err.println("No textures found in: " + folder.getAbsolutePath());
            return textures;
        }

        System.out.println("Loading textures from: " + folder.getAbsolutePath());
        Arrays.sort(files, Comparator.comparing(File::getName));

        int skipped = 0;
        int trimFiltered = 0;

        for (File file : files) {
            String name = file.getName().replace(".png", "");

            if (isArmorTrimVariant(name)) {
                trimFiltered++;
                continue;
            }

            BufferedImage image = loadImage(file);
            if (image == null) {
                System.err.println("Failed to load: " + file.getName());
                continue;
            }

            try {
                BufferedImage processed = resizeToLayoutSize(image);

                if (ImageUtil.isFullyTransparent(processed)) {
                    skipped++;
                    continue;
                }

                textures.add(new AtlasEntry(name, processed));
            } catch (Exception e) {
                System.err.println("Failed to process: " + file.getName());
                e.printStackTrace();
            }
        }

        if (skipped > 0) {
            System.out.println("Skipped " + skipped + " fully transparent textures.");
        }

        if (trimFiltered > 0) {
            System.out.println("Filtered out " + trimFiltered + " armor trim variants (rendered dynamically).");
        }

        return textures;
    }

    private BufferedImage loadImage(File file) {
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            return null;
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
