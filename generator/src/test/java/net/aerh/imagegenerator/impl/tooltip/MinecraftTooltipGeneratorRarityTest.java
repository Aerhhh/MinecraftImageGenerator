package net.aerh.imagegenerator.impl.tooltip;

import net.aerh.imagegenerator.data.Rarity;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The NONE rarity must behave exactly like no rarity at all: no color prefix on the name and
 * no rarity footer line. parseLore guards on {@code rarity != Rarity.byName("NONE")}, which
 * only works once byName resolves "none" to the registry instance instead of null.
 */
class MinecraftTooltipGeneratorRarityTest {

    private static BufferedImage render(Rarity rarity) {
        return new MinecraftTooltipGenerator.Builder()
            .withName("Plain Item")
            .withRarity(rarity)
            .withItemLore("&7Just a line")
            .build().generate().getImage();
    }

    @Test
    void noneRarityRendersIdenticallyToNullRarity() {
        BufferedImage withNull = render(null);
        BufferedImage withNone = render(Rarity.byName("none"));
        assertEquals(withNull.getWidth(), withNone.getWidth(), "no footer line may be added for NONE");
        assertEquals(withNull.getHeight(), withNone.getHeight(), "no footer line may be added for NONE");
        for (int y = 0; y < withNull.getHeight(); y++) {
            for (int x = 0; x < withNull.getWidth(); x++) {
                assertEquals(withNull.getRGB(x, y), withNone.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }
}
