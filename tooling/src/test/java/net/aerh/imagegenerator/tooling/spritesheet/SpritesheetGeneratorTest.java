package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.aerh.imagegenerator.tooling.ToolingPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpritesheetGeneratorTest {

    private static final int RED = 0xffff0000;
    private static final int GREEN = 0xff00ff00;
    private static final AtlasLayout LAYOUT = new AtlasLayout(2, 8, 8, 0);

    @TempDir
    Path root;

    @Test
    void packsEveryPngInTheInputDirectoryInNameOrder() throws IOException {
        Path input = root.resolve("input");
        writePng(input.resolve("b.png"), GREEN);
        writePng(input.resolve("a.png"), RED);
        Files.createDirectories(input);
        Files.writeString(input.resolve("notes.txt"), "not an image");

        new SpritesheetGenerator(input, paths(), LAYOUT).generate();

        BufferedImage atlas = ImageIO.read(paths().spritesheets().resolve("minecraft_texture_atlas.png").toFile());
        assertEquals(RED, atlas.getRGB(0, 0));
        assertEquals(GREEN, atlas.getRGB(2, 0));
        assertEquals(List.of("a", "b"), names());
        assertEquals(2, readCoordinates().get(0).getAsJsonObject().get("size").getAsInt());
    }

    @Test
    void fullyTransparentImagesAreSkipped() throws IOException {
        Path input = root.resolve("input");
        writePng(input.resolve("solid.png"), RED);
        writePng(input.resolve("empty.png"), 0);

        new SpritesheetGenerator(input, paths(), LAYOUT).generate();

        assertEquals(List.of("solid"), names());
    }

    @Test
    void armorTrimVariantsAreLeftOutWhenTheAssetsHaveTrimPalettes() throws IOException {
        Path input = root.resolve("input");
        writePng(input.resolve("iron_helmet.png"), RED);
        writePng(input.resolve("iron_helmet_amethyst_trim.png"), GREEN);
        Path palettes = paths().minecraftAssets().resolve("textures/palettes");
        writePalette(palettes.resolve("trim_base.png"));
        writePalette(palettes.resolve("trim/amethyst.png"));

        new SpritesheetGenerator(input, paths(), LAYOUT).generate();

        assertEquals(List.of("iron_helmet"), names());
    }

    @Test
    void armorTrimVariantsAreLeftOutWithTheLegacyPaletteLayout() throws IOException {
        Path input = root.resolve("input");
        writePng(input.resolve("iron_helmet.png"), RED);
        writePng(input.resolve("iron_helmet_amethyst_trim.png"), GREEN);
        Path palettes = paths().minecraftAssets().resolve("textures/trims/color_palettes");
        writePalette(palettes.resolve("trim_palette.png"));
        writePalette(palettes.resolve("amethyst.png"));

        new SpritesheetGenerator(input, paths(), LAYOUT).generate();

        assertEquals(List.of("iron_helmet"), names());
    }

    @Test
    void trimVariantsStayWhenNoPalettesAreAvailable() throws IOException {
        Path input = root.resolve("input");
        writePng(input.resolve("iron_helmet.png"), RED);
        writePng(input.resolve("iron_helmet_amethyst_trim.png"), GREEN);

        new SpritesheetGenerator(input, paths(), LAYOUT).generate();

        assertEquals(List.of("iron_helmet", "iron_helmet_amethyst_trim"), names());
    }

    @Test
    void inputDefaultsToTheRenderedItemsDirectory() throws IOException {
        writePng(paths().renderedItems().resolve("stone.png"), RED);

        new SpritesheetGenerator(paths(), LAYOUT).generate();

        assertEquals(List.of("stone"), names());
    }

    @Test
    void missingInputDirectoryWritesNothing() {
        new SpritesheetGenerator(root.resolve("missing"), paths(), LAYOUT).generate();

        assertFalse(Files.exists(paths().spritesheets()));
        assertFalse(Files.exists(paths().json()));
    }

    private ToolingPaths paths() {
        return new ToolingPaths(root.resolve("work"), root.resolve("out"), root.resolve("renderer"));
    }

    private JsonArray readCoordinates() throws IOException {
        Path json = paths().json().resolve("atlas_coordinates.json");
        assertTrue(Files.isRegularFile(json), "coordinates file should exist");
        return JsonParser.parseString(Files.readString(json)).getAsJsonArray();
    }

    private List<String> names() throws IOException {
        List<String> names = new ArrayList<>();
        for (JsonElement element : readCoordinates()) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static void writePng(Path file, int argb) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, argb);
            }
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static void writePalette(Path file) throws IOException {
        BufferedImage image = new BufferedImage(8, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) {
            image.setRGB(x, 0, 0xff000000 | (x * 0x202020));
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }
}
