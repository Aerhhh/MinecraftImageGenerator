package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.aerh.imagegenerator.tooling.ToolingPaths;
import net.aerh.imagegenerator.tooling.minecraft.ColorExtractor;
import net.aerh.imagegenerator.tooling.minecraft.DyeColorData;
import net.aerh.imagegenerator.tooling.minecraft.ExtractedColors;
import net.aerh.imagegenerator.tooling.minecraft.TrimPaletteExtractor;
import net.aerh.imagegenerator.tooling.minecraft.TrimPaletteExtractor.TrimPalettes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates overlay_colors.json by extracting colors and trim palette PNGs
 * from the Minecraft JAR. This config defines color options for dyeable items,
 * potions, effects, fireworks, and armor trims.
 */
public class OverlayColorConfigGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Default undyed leather armor color (brown)
    private static final int DEFAULT_LEATHER_COLOR = 0xA06540;

    // Water bottle and default potion color (blue)
    private static final int WATER_POTION_COLOR = 0xFF395FCA;

    // Default firework star color (white)
    private static final int DEFAULT_FIREWORK_COLOR = 0xFFFFFF;

    private final ToolingPaths paths;
    private final String version;

    /**
     * Reads colours from whichever client JAR is cached in the work directory.
     */
    public OverlayColorConfigGenerator(ToolingPaths paths) {
        this(paths, null);
    }

    /**
     * @param version the Minecraft version whose cached JAR to read, or null to use whatever JAR is cached
     */
    public OverlayColorConfigGenerator(ToolingPaths paths, String version) {
        this.paths = paths;
        this.version = version;
    }

    /**
     * Generates overlay_colors.json.
     */
    public void generate() throws IOException {
        System.out.println("\nGenerating overlay color config...");

        ExtractedColors extractedColors = extractColorsFromJar();
        TrimPalettes trimPalettes = extractTrimPalettes();

        if (extractedColors == null) {
            throw new IOException("Failed to extract colors from the client JAR; see the errors above.");
        }

        JsonArray colorConfig = buildColorConfig(extractedColors, trimPalettes);
        saveConfig(colorConfig);
    }

    private ExtractedColors extractColorsFromJar() throws IOException {
        String resolvedVersion = version != null ? version : paths.cachedClientVersion().orElse(null);

        if (resolvedVersion == null) {
            System.err.println("  ERROR: No cached JAR found. Run 'MinecraftAssetTool --download' first.");
            return null;
        }

        Path jarPath = paths.jarPath(resolvedVersion);
        Path mappingsPath = paths.mappingsPath(resolvedVersion);

        if (!Files.exists(jarPath)) {
            System.err.println("  ERROR: Missing JAR: " + jarPath);
            return null;
        }

        // Mojang stopped publishing mappings with 26.1; from then on the JAR carries the real class names.
        boolean hasMappings = Files.exists(mappingsPath);
        System.out.println("  Extracting colors from JAR (version " + resolvedVersion
            + (hasMappings ? ", using mappings" : ", unobfuscated") + ")...");
        try {
            ColorExtractor extractor = hasMappings
                ? new ColorExtractor(jarPath, mappingsPath)
                : new ColorExtractor(jarPath);
            ExtractedColors colors = extractor.extract();
            System.out.println("  Successfully extracted " + colors.dyeColors.size() + " dye colors and " +
                colors.potionColors.size() + " potion colors from JAR");
            return colors;
        } catch (Exception e) {
            System.err.println("  ERROR: Failed to extract colors from JAR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private TrimPalettes extractTrimPalettes() throws IOException {
        System.out.println("  Extracting trim palettes from PNG files...");
        TrimPalettes palettes = TrimPaletteExtractor.load(paths.minecraftAssets());
        System.out.println("  Found " + palettes.materials().size() + " trim palettes");
        return palettes;
    }

    private JsonArray buildColorConfig(ExtractedColors colors, TrimPalettes trimPalettes) {
        JsonArray config = new JsonArray();

        if (colors.dyeColors.isEmpty()) {
            System.err.println("  ERROR: No dye colors extracted from JAR!");
        } else {
            System.out.println("  Adding leather_armor colors (" + colors.dyeColors.size() + " colors)");
            config.add(createLeatherArmorConfig(colors.dyeColors));
        }

        if (trimPalettes.isEmpty()) {
            System.err.println("  ERROR: No trim palettes found in assets!");
        } else {
            System.out.println("  Adding armor_trim colors (" + trimPalettes.materials().size() + " palettes)");
            config.add(createArmorTrimConfig(trimPalettes));
        }

        if (colors.potionColors.isEmpty()) {
            System.err.println("  ERROR: No potion colors extracted from JAR!");
        } else {
            System.out.println("  Adding potion colors (" + colors.potionColors.size() + " effects with potion forms)");
            config.add(createPotionConfig(colors.potionColors));
        }

        if (colors.effectColors.isEmpty()) {
            System.err.println("  ERROR: No effect colors extracted from JAR!");
        } else {
            System.out.println("  Adding effect colors (" + colors.effectColors.size() + " total effects)");
            config.add(createEffectConfig(colors.effectColors));
        }

        if (colors.dyeColors.isEmpty()) {
            System.err.println("  ERROR: No firework colors (uses dye colors) extracted from JAR!");
        } else {
            System.out.println("  Adding firework colors (" + colors.dyeColors.size() + " colors)");
            config.add(createFireworkConfig(colors.dyeColors));
        }

        return config;
    }

    private void saveConfig(JsonArray config) throws IOException {
        File outputDir = paths.json().toFile();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + outputDir);
        }

        File outputFile = new File(outputDir, "overlay_colors.json");
        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(config, writer);
        }

        System.out.println("Color config saved to: " + outputFile.getAbsolutePath());
    }

    private JsonObject createLeatherArmorConfig(Map<String, DyeColorData> dyeColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "leather_armor");

        JsonObject options = new JsonObject();
        for (Map.Entry<String, DyeColorData> entry : dyeColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue().dyeColor()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(DEFAULT_LEATHER_COLOR));

        return config;
    }

    /**
     * Builds the armor_trim colour options. The map goes from each grey of the key palette to its index, which
     * is how the trim overlay textures are drawn, and every material palette with the same number of colours
     * becomes an option. A material whose palette length differs from the key cannot be indexed and is skipped.
     */
    static JsonObject createArmorTrimConfig(TrimPalettes palettes) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "armor_trim");

        int[] key = palettes.key();
        JsonObject map = new JsonObject();
        for (int i = 0; i < key.length; i++) {
            map.addProperty(String.valueOf(key[i]), i);
        }
        config.add("map", map);

        JsonObject options = new JsonObject();
        for (Map.Entry<String, int[]> entry : palettes.materials().entrySet()) {
            if (entry.getValue().length != key.length) {
                System.err.println("  WARNING: trim palette '" + entry.getKey() + "' has " + entry.getValue().length
                    + " colours but the key palette has " + key.length + "; skipping it");
                continue;
            }
            options.add(entry.getKey(), jsonArray(entry.getValue()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", false);
        config.addProperty("useDefaultIfMissing", false);

        return config;
    }

    private JsonObject createPotionConfig(Map<String, Integer> potionColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "potion");

        JsonObject options = new JsonObject();
        options.add("water", jsonArray(WATER_POTION_COLOR));

        for (Map.Entry<String, Integer> entry : potionColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(WATER_POTION_COLOR));

        return config;
    }

    private JsonObject createEffectConfig(Map<String, Integer> effectColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "effect");

        JsonObject options = new JsonObject();
        options.add("water", jsonArray(WATER_POTION_COLOR));

        for (Map.Entry<String, Integer> entry : effectColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(WATER_POTION_COLOR));

        return config;
    }

    private JsonObject createFireworkConfig(Map<String, DyeColorData> dyeColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "firework");

        JsonObject options = new JsonObject();
        for (Map.Entry<String, DyeColorData> entry : dyeColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue().fireworkColor()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(DEFAULT_FIREWORK_COLOR));

        return config;
    }

    private static JsonArray jsonArray(int... values) {
        JsonArray arr = new JsonArray();
        for (int v : values) {
            arr.add(v);
        }
        return arr;
    }
}
