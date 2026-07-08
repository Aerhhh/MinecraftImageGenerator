package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.testsupport.TestResources;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins vanilla rendering pixel-exactly. These goldens were captured BEFORE the pack system was
 * integrated; if a pack-system change breaks them, the change is wrong, not the golden.
 */
class VanillaGoldenImageTest {

    private static final String[] GOLDEN_ITEMS = {"stone", "diamond_sword", "oak_planks"};

    private static BufferedImage render(String itemId) {
        return new MinecraftItemGenerator.Builder().withItem(itemId).build().generate().getImage();
    }

    static Stream<String> goldenItems() {
        return Arrays.stream(GOLDEN_ITEMS);
    }

    @ParameterizedTest
    @MethodSource("goldenItems")
    void vanillaRenderMatchesGolden(String itemId) throws IOException {
        BufferedImage actual = render(itemId);
        BufferedImage golden = ImageIO.read(new ByteArrayInputStream(
            TestResources.readBytes("golden/vanilla/" + itemId + ".png")));
        assertEquals(golden.getWidth(), actual.getWidth(), "Width mismatch for '" + itemId + "'");
        assertEquals(golden.getHeight(), actual.getHeight(), "Height mismatch for '" + itemId + "'");
        for (int y = 0; y < golden.getHeight(); y++) {
            for (int x = 0; x < golden.getWidth(); x++) {
                assertEquals(golden.getRGB(x, y), actual.getRGB(x, y),
                    "Pixel mismatch for '" + itemId + "' at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void enchantedItemStillProducesAnimation() {
        GeneratedObject enchanted = new MinecraftItemGenerator.Builder()
            .withItem("diamond_sword").isEnchanted(true).build().generate();
        assertTrue(enchanted.isAnimated(), "enchanted items render as animations");
        assertTrue(enchanted.getAnimationFrames().size() > 1);
    }

    /** Run manually once (remove @Disabled) to (re)capture goldens; never in CI. */
    @Disabled("golden regeneration only - run manually, review the diff, commit")
    @Test
    void regenerateGoldens() throws IOException {
        Path dir = Path.of("src/test/resources/golden/vanilla");
        Files.createDirectories(dir);
        for (String itemId : GOLDEN_ITEMS) {
            ImageIO.write(render(itemId), "png", dir.resolve(itemId + ".png").toFile());
        }
    }
}
