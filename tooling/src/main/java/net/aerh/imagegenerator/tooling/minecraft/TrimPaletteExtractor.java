package net.aerh.imagegenerator.tooling.minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads armor trim colour palettes out of extracted Minecraft assets.
 * <p>
 * Every trim material has an 8x1 PNG whose pixels are that material's gradient, and a key palette holds the
 * greys the greyscale trim overlay textures are drawn in. The game recolours an overlay by swapping each key
 * grey for the material colour at the same index, so a consumer needs both halves. Two layouts are known:
 * current versions keep the materials in {@code textures/palettes/trim/} with the key at
 * {@code textures/palettes/trim_base.png}; versions before the palette move kept everything in
 * {@code textures/trims/color_palettes/} with the key named {@code trim_palette.png}.
 */
public final class TrimPaletteExtractor {

    static final Path CURRENT_MATERIALS_DIR = Path.of("textures/palettes/trim");
    static final Path CURRENT_KEY_FILE = Path.of("textures/palettes/trim_base.png");
    static final Path LEGACY_MATERIALS_DIR = Path.of("textures/trims/color_palettes");
    static final String LEGACY_KEY_NAME = "trim_palette.png";

    private TrimPaletteExtractor() {
    }

    /**
     * The key greys and every material palette, materials in file name order.
     */
    public record TrimPalettes(int[] key, Map<String, int[]> materials) {

        public static final TrimPalettes NONE = new TrimPalettes(new int[0], Map.of());

        public boolean isEmpty() {
            return materials.isEmpty();
        }
    }

    /**
     * Loads the trim palettes from extracted assets, trying the current layout first and then the legacy one.
     *
     * @return the palettes, or {@link TrimPalettes#NONE} after a message on stderr when neither layout exists
     *
     * @throws IOException if a layout is present but its key palette is missing or unreadable
     */
    public static TrimPalettes load(Path minecraftAssets) throws IOException {
        Path currentDir = minecraftAssets.resolve(CURRENT_MATERIALS_DIR);
        if (Files.isDirectory(currentDir)) {
            return read(currentDir, minecraftAssets.resolve(CURRENT_KEY_FILE), null);
        }

        Path legacyDir = minecraftAssets.resolve(LEGACY_MATERIALS_DIR);
        if (Files.isDirectory(legacyDir)) {
            return read(legacyDir, legacyDir.resolve(LEGACY_KEY_NAME), LEGACY_KEY_NAME);
        }

        System.err.println("Trim palettes not found under " + minecraftAssets
            + " (looked in " + CURRENT_MATERIALS_DIR + " and " + LEGACY_MATERIALS_DIR + ")");
        return TrimPalettes.NONE;
    }

    private static TrimPalettes read(Path materialsDir, Path keyFile, String excludedName) throws IOException {
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("Trim key palette not found: " + keyFile);
        }

        int[] key = extractPalette(keyFile.toFile());
        if (key == null) {
            throw new IOException("Trim key palette is not a readable image: " + keyFile);
        }

        return new TrimPalettes(key, extractAllPalettes(materialsDir, excludedName));
    }

    /**
     * Extracts every material palette in a directory, keyed by file name without extension, skipping the file
     * named {@code excludedName} (the legacy key palette lives among the materials).
     */
    private static Map<String, int[]> extractAllPalettes(Path palettesPath, String excludedName) {
        Map<String, int[]> palettes = new LinkedHashMap<>();

        if (!Files.exists(palettesPath)) {
            System.err.println("Palettes directory not found: " + palettesPath);
            return palettes;
        }

        File[] paletteFiles = palettesPath.toFile().listFiles((dir, name) ->
            name.endsWith(".png") && !name.equals(excludedName)
        );

        if (paletteFiles == null) {
            return palettes;
        }

        Arrays.sort(paletteFiles, Comparator.comparing(File::getName));
        for (File file : paletteFiles) {
            String name = file.getName().replace(".png", "");
            int[] colors = extractPalette(file);
            if (colors != null) {
                palettes.put(name, colors);
            }
        }

        return palettes;
    }

    /**
     * Extracts colors from a single palette PNG file.
     *
     * @param paletteFile the 8x1 PNG file
     *
     * @return array of RGB colors as signed integers, or null if extraction failed
     */
    public static int[] extractPalette(File paletteFile) {
        BufferedImage image;
        try {
            image = ImageIO.read(paletteFile);
        } catch (IOException e) {
            System.err.println("Failed to read palette '" + paletteFile.getName() + "': " + e.getMessage());
            return null;
        }

        if (image == null) {
            System.err.println("Failed to read palette '" + paletteFile.getName() + "': unsupported format");
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (height != 1) {
            System.err.println("Palette '" + paletteFile.getName() + "' has unexpected height: " + height);
        }

        int[] colors = new int[width];
        for (int x = 0; x < width; x++) {
            colors[x] = image.getRGB(x, 0);
        }

        return colors;
    }
}
