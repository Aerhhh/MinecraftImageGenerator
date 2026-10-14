package net.aerh.imagegenerator.spritesheet;

import net.hypixel.nerdbot.marmalade.functional.Pair;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the static facade against the bundled vanilla atlas.
 */
class SpritesheetTest {

    @Test
    void getTextureReturnsBundledTexture() {
        BufferedImage stone = Spritesheet.getTexture("stone");

        assertNotNull(stone, "the vanilla spritesheet has stone");
        assertTrue(stone.getWidth() > 0, "width");
        assertTrue(stone.getHeight() > 0, "height");
    }

    @Test
    void getTextureReturnsNullForUnknownName() {
        assertNull(Spritesheet.getTexture("definitely_not_a_texture"));
    }

    @Test
    void repeatedGetTextureReturnsSameInstance() {
        assertSame(Spritesheet.getTexture("stone"), Spritesheet.getTexture("stone"));
    }

    @Test
    void searchForTextureReturnsSubstringMatchesOnly() {
        List<String> names = Spritesheet.searchForTexture("stone").stream().map(Pair::first).toList();

        assertTrue(names.contains("stone"), names.toString());
        assertTrue(names.stream().allMatch(name -> name.contains("stone")), names.toString());
    }

    @Test
    void getImageMapContainsEveryBundledTexture() {
        Map<String, BufferedImage> imageMap = Spritesheet.getImageMap();

        assertFalse(imageMap.isEmpty());
        assertSame(Spritesheet.getTexture("stone"), imageMap.get("stone"));
    }
}
