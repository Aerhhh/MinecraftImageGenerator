package net.aerh.imagegenerator.impl.tooltip;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for pack fonts through {@link MinecraftTooltipGenerator}: plain lore picks
 * up a pack's {@code minecraft:default} font override without any segment font id, and one
 * golden image - built ENTIRELY from the synthetic {@link FixturePacks#writeTextFontPack text
 * font pack} - pins the full pipeline (glyph raster, quartered shadows, fractional and negative
 * advances, canvas sizing) pixel-exactly.
 */
class TooltipPackFontGeneratorTest {

    private static final String GLYPH_RED = Character.toString(0xE000);
    private static final String GLYPH_BLUE = Character.toString(0xE001);
    private static final String GLYPH_GREEN = Character.toString(0xE002);
    private static final String SPACE_MINUS_FOUR = Character.toString(0xE00A);
    private static final String SPACE_HALF = Character.toString(0xE00B);

    private static final String GOLDEN_LORE =
        "&fHello " + GLYPH_RED + GLYPH_BLUE + " world\\n"
            + "&a" + GLYPH_GREEN + SPACE_HALF + GLYPH_GREEN + " fractional\\n"
            + "&b" + GLYPH_RED + SPACE_MINUS_FOUR + GLYPH_BLUE + " overlap";

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void setUp() {
        repository = new PackRepository();
        FixturePacks.writeTextFontPack(packDir);
        packId = repository.register("test:fonts", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private static boolean containsColor(BufferedImage image, int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == argb) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void loreRendersPackGlyphsThroughTheDefaultFontOverride() {
        BufferedImage withPack = new MinecraftTooltipGenerator.Builder()
            .withItemLore("&f" + GLYPH_RED)
            .withPack(packId)
            .withPackRepository(repository)
            .build().generate().getImage();
        assertTrue(containsColor(withPack, 0xFFFF0000),
            "the pack's minecraft:default override serves the PUA glyph");

        BufferedImage withoutPack = new MinecraftTooltipGenerator.Builder()
            .withItemLore("&f" + GLYPH_RED)
            .build().generate().getImage();
        assertFalse(containsColor(withoutPack, 0xFFFF0000),
            "without a pack the PUA codepoint stays on the built-in font path");
    }

    private MinecraftTooltipGenerator goldenScenario() {
        return new MinecraftTooltipGenerator.Builder()
            .withName("Pack Font Golden")
            .withItemLore(GOLDEN_LORE)
            .withMaxLineLength(36)
            .withPack(packId)
            .withPackRepository(repository)
            .build();
    }

    @Test
    void packFontTooltipMatchesGolden() throws IOException {
        BufferedImage actual = goldenScenario().generate().getImage();
        BufferedImage golden = ImageIO.read(new ByteArrayInputStream(
            TestResources.readBytes("golden/tooltip/pack_font_synthetic.png")));
        assertEquals(golden.getWidth(), actual.getWidth(), "width");
        assertEquals(golden.getHeight(), actual.getHeight(), "height");
        for (int y = 0; y < golden.getHeight(); y++) {
            for (int x = 0; x < golden.getWidth(); x++) {
                assertEquals(golden.getRGB(x, y), actual.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    /** Run manually once (remove @Disabled) to (re)capture the golden; never in CI. */
    @Disabled("golden regeneration only - run manually, review the diff, commit")
    @Test
    void regenerateGolden() throws IOException {
        Path dir = Path.of("src/test/resources/golden/tooltip");
        Files.createDirectories(dir);
        ImageIO.write(goldenScenario().generate().getImage(), "png",
            dir.resolve("pack_font_synthetic.png").toFile());
    }
}
