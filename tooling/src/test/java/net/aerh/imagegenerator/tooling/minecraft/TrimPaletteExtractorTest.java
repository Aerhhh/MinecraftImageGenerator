package net.aerh.imagegenerator.tooling.minecraft;

import net.aerh.imagegenerator.tooling.minecraft.TrimPaletteExtractor.TrimPalettes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrimPaletteExtractorTest {

    /** The greys of the 26.3 {@code textures/palettes/trim_base.png}. */
    private static final int[] GREYS = {
        0xfff1f1f1, 0xffe1e1e1, 0xffd0d0d0, 0xffbcbcbc, 0xffa5a5a5, 0xff898989, 0xff636363, 0xff000000
    };
    private static final int[] GOLD = {
        0xfffffd90, 0xffecd93f, 0xffdeb12d, 0xffb16712, 0xffa0450a, 0xff803503, 0xff712d00, 0xff572300
    };
    private static final int[] AMETHYST = {
        0xffe4c2ff, 0xffd49cff, 0xffc478ff, 0xffb45cf5, 0xff9b43d8, 0xff8233be, 0xff6b26a2, 0xff4f1a7a
    };

    @TempDir
    Path assets;

    @Test
    void currentLayoutProvidesTheKeyAndEveryMaterial() throws IOException {
        writePalette(assets.resolve("textures/palettes/trim_base.png"), GREYS);
        writePalette(assets.resolve("textures/palettes/trim/gold.png"), GOLD);
        writePalette(assets.resolve("textures/palettes/trim/gold_darker.png"), AMETHYST);

        TrimPalettes palettes = TrimPaletteExtractor.load(assets);

        assertArrayEquals(GREYS, palettes.key());
        assertEquals(Set.of("gold", "gold_darker"), palettes.materials().keySet());
        assertArrayEquals(GOLD, palettes.materials().get("gold"));
    }

    @Test
    void legacyLayoutUsesTrimPaletteAsTheKeyAndNotAsAMaterial() throws IOException {
        writePalette(assets.resolve("textures/trims/color_palettes/trim_palette.png"), GREYS);
        writePalette(assets.resolve("textures/trims/color_palettes/amethyst.png"), AMETHYST);

        TrimPalettes palettes = TrimPaletteExtractor.load(assets);

        assertArrayEquals(GREYS, palettes.key());
        assertEquals(Set.of("amethyst"), palettes.materials().keySet());
        assertArrayEquals(AMETHYST, palettes.materials().get("amethyst"));
    }

    @Test
    void currentLayoutWinsWhenBothLayoutsExist() throws IOException {
        writePalette(assets.resolve("textures/palettes/trim_base.png"), GREYS);
        writePalette(assets.resolve("textures/palettes/trim/gold.png"), GOLD);
        writePalette(assets.resolve("textures/trims/color_palettes/trim_palette.png"), GREYS);
        writePalette(assets.resolve("textures/trims/color_palettes/amethyst.png"), AMETHYST);

        assertEquals(Set.of("gold"), TrimPaletteExtractor.load(assets).materials().keySet());
    }

    @Test
    void noLayoutGivesEmptyPalettes() throws IOException {
        TrimPalettes palettes = TrimPaletteExtractor.load(assets);

        assertTrue(palettes.isEmpty());
        assertEquals(0, palettes.key().length);
    }

    @Test
    void currentLayoutWithoutTheBasePaletteIsAnError() throws IOException {
        writePalette(assets.resolve("textures/palettes/trim/gold.png"), GOLD);

        IOException error = assertThrows(IOException.class, () -> TrimPaletteExtractor.load(assets));
        assertTrue(error.getMessage().contains("trim_base.png"), error.getMessage());
    }

    @Test
    void legacyLayoutWithoutTheKeyPaletteIsAnError() throws IOException {
        writePalette(assets.resolve("textures/trims/color_palettes/amethyst.png"), AMETHYST);

        IOException error = assertThrows(IOException.class, () -> TrimPaletteExtractor.load(assets));
        assertTrue(error.getMessage().contains("trim_palette.png"), error.getMessage());
    }

    @Test
    void filesThatAreNotPngsAreNotMaterials() throws IOException {
        writePalette(assets.resolve("textures/palettes/trim_base.png"), GREYS);
        writePalette(assets.resolve("textures/palettes/trim/gold.png"), GOLD);
        Files.writeString(assets.resolve("textures/palettes/trim/gold.png.mcmeta"), "{}");

        assertEquals(Set.of("gold"), TrimPaletteExtractor.load(assets).materials().keySet());
    }

    @Test
    void extractPaletteReadsPixelsLeftToRight() throws IOException {
        Path file = assets.resolve("gold.png");
        writePalette(file, GOLD);

        assertArrayEquals(GOLD, TrimPaletteExtractor.extractPalette(file.toFile()));
    }

    private static void writePalette(Path file, int[] colors) throws IOException {
        BufferedImage image = new BufferedImage(colors.length, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < colors.length; x++) {
            image.setRGB(x, 0, colors[x]);
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }
}
