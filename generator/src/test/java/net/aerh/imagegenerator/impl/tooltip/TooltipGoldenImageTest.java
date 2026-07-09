package net.aerh.imagegenerator.impl.tooltip;

import net.aerh.imagegenerator.data.Rarity;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins default tooltip rendering pixel-exactly. These goldens were captured BEFORE the pack
 * tooltip-theme system was integrated; if a theming change breaks them, the change is wrong,
 * not the golden.
 */
class TooltipGoldenImageTest {

    private static final Map<String, Supplier<MinecraftTooltipGenerator>> SCENARIOS = new LinkedHashMap<>();

    static {
        SCENARIOS.put("bordered_legendary", () -> new MinecraftTooltipGenerator.Builder()
            .withName("Golden Test Sword")
            .withRarity(Rarity.byName("LEGENDARY"))
            .withType("SWORD")
            .withItemLore("&7Damage: &c+120\n&7Strength: &a+50\n\n"
                + "&4dark red &6gold &1dark blue &9blue\n"
                + "&5dark purple &7gray &8dark gray &eyellow\n"
                + "&f&lBold &7&mStrike&r &b&nUnderline")
            .withMaxLineLength(36)
            .hasFirstLinePadding(true)
            .withRenderBorder(true)
            .build());

        SCENARIOS.put("borderless_text", () -> new MinecraftTooltipGenerator.Builder()
            .withItemLore("&aBorderless &btext box\n&7Second line with &dcolors")
            .withAlpha(100)
            .hasFirstLinePadding(false)
            .withRenderBorder(false)
            .withMaxLineLength(48)
            .build());

        SCENARIOS.put("centered_epic_scaled", () -> new MinecraftTooltipGenerator.Builder()
            .withName("Centered Epic")
            .withRarity(Rarity.byName("EPIC"))
            .withItemLore("&7Line one\n&5Line two")
            .withPadding(8)
            .isTextCentered(true)
            .withScaleFactor(2)
            .withRenderBorder(true)
            .build());
    }

    static Stream<String> goldenScenarios() {
        return SCENARIOS.keySet().stream();
    }

    private static BufferedImage render(String scenario) {
        GeneratedObject generated = SCENARIOS.get(scenario).get().generate();
        assertFalse(generated.isAnimated(), "golden scenarios must render statically");
        return generated.getImage();
    }

    @ParameterizedTest
    @MethodSource("goldenScenarios")
    void defaultTooltipRenderMatchesGolden(String scenario) throws IOException {
        BufferedImage actual = render(scenario);
        BufferedImage golden = ImageIO.read(new ByteArrayInputStream(
            TestResources.readBytes("golden/tooltip/" + scenario + ".png")));
        assertEquals(golden.getWidth(), actual.getWidth(), "Width mismatch for '" + scenario + "'");
        assertEquals(golden.getHeight(), actual.getHeight(), "Height mismatch for '" + scenario + "'");
        for (int y = 0; y < golden.getHeight(); y++) {
            for (int x = 0; x < golden.getWidth(); x++) {
                assertEquals(golden.getRGB(x, y), actual.getRGB(x, y),
                    "Pixel mismatch for '" + scenario + "' at (" + x + "," + y + ")");
            }
        }
    }

    /** Run manually once (remove @Disabled) to (re)capture goldens; never in CI. */
    @Disabled("golden regeneration only - run manually, review the diff, commit")
    @Test
    void regenerateGoldens() throws IOException {
        Path dir = Path.of("src/test/resources/golden/tooltip");
        Files.createDirectories(dir);
        for (String scenario : SCENARIOS.keySet()) {
            ImageIO.write(render(scenario), "png", dir.resolve(scenario + ".png").toFile());
        }
    }
}
