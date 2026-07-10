package net.aerh.imagegenerator.impl.tooltip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aerh.imagegenerator.data.Rarity;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void getRarityReturnsRarityAssignedThroughWithRarity() {
        MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder()
            .withRarity(Rarity.byName("epic"));
        assertEquals(Rarity.byName("epic"), builder.getRarity());

        assertNull(new MinecraftTooltipGenerator.Builder().getRarity());
    }

    @Test
    void getRarityExposesRarityParsedFromNbtFooter() {
        JsonObject nbt = JsonParser.parseString("""
            {"tag":{"display":{"Name":"§6Fancy Sword","Lore":["§7Just a line","","§6LEGENDARY SWORD"]}}}
            """).getAsJsonObject();

        MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder().parseNbtJson(nbt);

        assertEquals(Rarity.byName("legendary"), builder.getRarity());
    }

    @Test
    void getRarityIsNoneWhenNbtLoreHasNoRarityFooter() {
        JsonObject nbt = JsonParser.parseString("""
            {"tag":{"display":{"Name":"§7Plain Item","Lore":["§7Just a line"]}}}
            """).getAsJsonObject();

        MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder().parseNbtJson(nbt);

        assertEquals(Rarity.byName("none"), builder.getRarity());
    }
}
