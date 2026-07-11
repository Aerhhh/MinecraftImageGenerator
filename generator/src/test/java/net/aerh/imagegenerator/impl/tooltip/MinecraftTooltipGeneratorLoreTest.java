package net.aerh.imagegenerator.impl.tooltip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lore parsing from NBT into the builder's {@code itemLore} string (the source for both the
 * {@code /gen} command and the rendered image). The components format writes blank lines as bare
 * empty-string elements; those must survive as double {@code \n} markers, matching the legacy
 * {@code tag.display.Lore} format. Regression guard for "Gen Parse doesn't apply double line breaks".
 */
class MinecraftTooltipGeneratorLoreTest {

    private static String loreFrom(String nbt) {
        JsonObject json = JsonParser.parseString(nbt).getAsJsonObject();
        return new MinecraftTooltipGenerator.Builder().parseNbtJson(json).getItemLore();
    }

    @Test
    void componentsFormatPreservesBlankLineBetweenParagraphs() {
        String lore = loreFrom("""
            {"components":{"minecraft:lore":[
              {"color":"gray","text":"Line A","italic":false},
              "",
              {"color":"gray","text":"Line B","italic":false}
            ]}}
            """);

        assertEquals("&7Line A\\n\\n&7Line B", lore,
            "bare \"\" element must render as a blank line (double \\n), not be skipped");
    }

    @Test
    void componentsFormatPreservesConsecutiveBlankLines() {
        String lore = loreFrom("""
            {"components":{"minecraft:lore":[
              {"color":"gray","text":"A","italic":false},
              "",
              "",
              {"color":"gray","text":"B","italic":false}
            ]}}
            """);

        assertEquals("&7A\\n\\n\\n&7B", lore, "two blank elements must yield two blank lines");
    }

    @Test
    void componentsAndLegacyFormatsProduceIdenticalLore() {
        String components = loreFrom("""
            {"components":{"minecraft:lore":[
              {"color":"gray","text":"First","italic":false},
              "",
              {"color":"gray","text":"Second","italic":false}
            ]}}
            """);
        String legacy = loreFrom("""
            {"tag":{"display":{"Lore":["§7First","","§7Second"]}}}
            """);

        assertEquals(legacy, components, "both NBT formats must yield the same lore string");
    }

    @Test
    void componentsFormatKeepsNonEmptyPlainStringLine() {
        String lore = loreFrom("""
            {"components":{"minecraft:lore":[
              {"color":"gray","text":"Object line","italic":false},
              "§7Plain string line"
            ]}}
            """);

        assertEquals("&7Object line\\n&7Plain string line", lore,
            "plain-string lore elements must not be dropped");
    }
}
