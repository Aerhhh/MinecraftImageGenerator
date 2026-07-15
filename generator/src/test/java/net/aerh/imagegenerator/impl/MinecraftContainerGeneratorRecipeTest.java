package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Strict parsing coverage for {@link MinecraftContainerGenerator#fromRecipe}: the menu recipe
 * document format Wynncraft/MCC menu captures are transcribed into. Malformed documents must
 * fail loudly at parse time, never render something silently wrong.
 */
class MinecraftContainerGeneratorRecipeTest {

    @TempDir
    Path packDir;

    // ------------------------------------------------------------- happy path

    @Test
    void happyPathParsesRowsTitleAndSlots() {
        FixturePacks.writeContainerPack(packDir);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:containerrecipe",
            PackSource.directory(packDir, PackLimits.fromSystemProperties()));

        BufferedImage image = MinecraftContainerGenerator.fromRecipe("""
                {"rows": 1,
                 "title": [{"text": "\\uE10A\\uE100", "font": "testpack:menu", "color": "#ffffff"}],
                 "slots": {"1": "testpack:item/marker", "9": "testpack:item/marker:32"}}""")
            .withPack(packId)
            .withPackRepository(repository)
            .build().render(null).getImage();

        assertEquals(352, image.getWidth());
        assertEquals(264, image.getHeight());
        assertEquals(0xFF336699, image.getRGB(0, 16), "title glyph art at GUI (0, 8)");
        assertEquals(0xFFAA5500, image.getRGB(16, 36), "slot 1 item");
        assertEquals(0xFFAA5500, image.getRGB(304, 36), "slot 9 item");
    }

    @Test
    void titleRunsSupportColorBoldAndItalic() {
        MinecraftContainerGenerator generator = MinecraftContainerGenerator.fromRecipe("""
                {"rows": 2,
                 "title": [{"text": "Bank", "color": "#3f3f3f", "bold": true, "italic": true}]}""")
            .build();
        BufferedImage image = generator.render(null).getImage();

        assertEquals((114 + 36) * 2, image.getHeight());
        boolean foundColoredText = false;
        for (int y = 8; y < 34 && !foundColoredText; y++) {
            for (int x = 12; x < 140; x++) {
                if (image.getRGB(x, y) == 0xFF3F3F3F) {
                    foundColoredText = true;
                    break;
                }
            }
        }
        assertTrue(foundColoredText, "title drawn in the run's color");
    }

    @Test
    void titleAndSlotsAreOptional() {
        BufferedImage image = MinecraftContainerGenerator.fromRecipe("""
                {"rows": 3}""")
            .build().render(null).getImage();
        assertEquals((114 + 54) * 2, image.getHeight());
    }

    // ------------------------------------------------------------ strictness

    private static IllegalArgumentException reject(String json) {
        return assertThrows(IllegalArgumentException.class,
            () -> MinecraftContainerGenerator.fromRecipe(json));
    }

    @Test
    void malformedJsonIsRejected() {
        reject("{nope");
        reject("");
        reject(null);
        reject("[1, 2]");
        reject("\"rows\"");
    }

    @Test
    void trailingContentAfterTheDocumentIsRejected() {
        // A concatenation of two menu captures must never silently render only the first.
        // (The strict reader flags the second value at peek time; wording is Gson's.)
        reject("""
            {"rows": 1} {"rows": 6}""");
        reject("""
            {"rows": 1} garbage""");
    }

    @Test
    void duplicateKeysAreRejectedEverywhere() {
        // Gson's tree model keeps the LAST duplicate key; strict parsing must fail loudly
        // instead of silently rendering rows=6 or only one of two transcribed slot items.
        IllegalArgumentException rowsTwice = reject("""
            {"rows": 1, "rows": 6}""");
        assertTrue(rowsTwice.getMessage().contains("rows"), rowsTwice.getMessage());

        IllegalArgumentException slotTwice = reject("""
            {"rows": 1, "slots": {"5": "stone", "5": "dirt"}}""");
        assertTrue(slotTwice.getMessage().contains("5"), slotTwice.getMessage());

        reject("""
            {"rows": 1, "title": [{"text": "x", "text": "y"}]}""");
    }

    @Test
    void nonCanonicalSlotKeysAreRejected() {
        // "01" and "+1" parse to slot 1: distinct keys addressing one slot would silently drop
        // an item, so only the canonical integer form is legal.
        IllegalArgumentException leadingZero = reject("""
            {"rows": 1, "slots": {"01": "stone"}}""");
        assertTrue(leadingZero.getMessage().contains("01"), leadingZero.getMessage());
        reject("""
            {"rows": 1, "slots": {"+1": "stone"}}""");
        reject("""
            {"rows": 1, "slots": {"1": "stone", "01": "diamond_sword"}}""");
    }

    @Test
    void unknownRootKeysAreRejected() {
        IllegalArgumentException exception = reject("""
            {"rows": 1, "bogus": 2}""");
        assertTrue(exception.getMessage().contains("bogus"), "names the offending key");
    }

    @Test
    void rowsAreRequiredAndRangeChecked() {
        reject("""
            {"title": []}""");
        reject("""
            {"rows": 0}""");
        reject("""
            {"rows": 7}""");
        reject("""
            {"rows": 1.5}""");
        reject("""
            {"rows": "one"}""");
    }

    @Test
    void malformedTitleRunsAreRejected() {
        reject("""
            {"rows": 1, "title": {"text": "x"}}""");
        reject("""
            {"rows": 1, "title": ["x"]}""");
        reject("""
            {"rows": 1, "title": [{}]}""");
        reject("""
            {"rows": 1, "title": [{"text": "x", "shadow": true}]}""");
        reject("""
            {"rows": 1, "title": [{"text": 5}]}""");
        reject("""
            {"rows": 1, "title": [{"text": "x", "bold": "yes"}]}""");
        reject("""
            {"rows": 1, "title": [{"text": "x", "font": "NOT A FONT"}]}""");
    }

    @Test
    void malformedColorsAreRejected() {
        reject("""
            {"rows": 1, "title": [{"text": "x", "color": "red"}]}""");
        reject("""
            {"rows": 1, "title": [{"text": "x", "color": "#12345"}]}""");
        reject("""
            {"rows": 1, "title": [{"text": "x", "color": "#1234567"}]}""");
        reject("""
            {"rows": 1, "title": [{"text": "x", "color": "3f3f3f"}]}""");
    }

    @Test
    void badSlotIndicesAreRejected() {
        reject("""
            {"rows": 1, "slots": {"0": "stone"}}""");
        reject("""
            {"rows": 1, "slots": {"10": "stone"}}""");
        reject("""
            {"rows": 6, "slots": {"55": "stone"}}""");
        reject("""
            {"rows": 1, "slots": {"first": "stone"}}""");
        reject("""
            {"rows": 1, "slots": {"-1": "stone"}}""");
    }

    @Test
    void malformedSlotValuesAreRejected() {
        reject("""
            {"rows": 1, "slots": {"1": 5}}""");
        reject("""
            {"rows": 1, "slots": {"1": " "}}""");
        reject("""
            {"rows": 1, "slots": {"1": "stone%%stone"}}""");
        reject("""
            {"rows": 1, "slots": {"1": "stone:5,64"}}""");
        reject("""
            {"rows": 1, "slots": {"1": "stone:0"}}""");
        reject("""
            {"rows": 1, "slots": {"1": "stone:65"}}""");
        reject("""
            {"rows": 1, "slots": ["stone"]}""");
    }
}
