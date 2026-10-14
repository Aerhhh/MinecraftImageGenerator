package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.aerh.imagegenerator.tooling.minecraft.TrimPaletteExtractor.TrimPalettes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OverlayColorConfigGeneratorTest {

    private static final int[] GREYS = {
        0xfff1f1f1, 0xffe1e1e1, 0xffd0d0d0, 0xffbcbcbc, 0xffa5a5a5, 0xff898989, 0xff636363, 0xff000000
    };
    private static final int[] GOLD = {
        0xfffffd90, 0xffecd93f, 0xffdeb12d, 0xffb16712, 0xffa0450a, 0xff803503, 0xff712d00, 0xff572300
    };

    @Test
    void armorTrimMapUsesTheKeyPaletteGreysInOrder() {
        JsonObject config = OverlayColorConfigGenerator.createArmorTrimConfig(new TrimPalettes(GREYS, Map.of("gold", GOLD)));

        JsonObject map = config.getAsJsonObject("map");
        assertEquals(GREYS.length, map.size());
        for (int index = 0; index < GREYS.length; index++) {
            assertEquals(index, map.get(String.valueOf(GREYS[index])).getAsInt(), "index of grey " + index);
        }
    }

    @Test
    void armorTrimOptionsListEveryMaterialPalette() {
        Map<String, int[]> materials = new LinkedHashMap<>();
        materials.put("gold", GOLD);
        materials.put("gold_darker", GREYS);

        JsonObject config = OverlayColorConfigGenerator.createArmorTrimConfig(new TrimPalettes(GREYS, materials));

        assertEquals("armor_trim", config.get("name").getAsString());
        assertEquals(Set.of("gold", "gold_darker"), config.getAsJsonObject("options").keySet());
        JsonArray gold = config.getAsJsonObject("options").getAsJsonArray("gold");
        assertEquals(GOLD.length, gold.size());
        assertEquals(GOLD[0], gold.get(0).getAsInt());
        assertFalse(config.get("allowHexColors").getAsBoolean());
        assertFalse(config.get("useDefaultIfMissing").getAsBoolean());
    }

    @Test
    void materialsWhoseLengthDiffersFromTheKeyAreLeftOut() {
        Map<String, int[]> materials = new LinkedHashMap<>();
        materials.put("gold", GOLD);
        materials.put("broken", new int[]{0xff000000, 0xffffffff});

        JsonObject config = OverlayColorConfigGenerator.createArmorTrimConfig(new TrimPalettes(GREYS, materials));

        assertEquals(Set.of("gold"), config.getAsJsonObject("options").keySet());
    }
}
