package net.aerh.imagegenerator.pack.font;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaFontSheetsTest {

    @Test
    void everyBundledVanillaSheetResolves() {
        assertTrue(VanillaFontSheets.sheet("minecraft", "font/ascii.png").isPresent());
        assertTrue(VanillaFontSheets.sheet("minecraft", "font/accented.png").isPresent());
        assertTrue(VanillaFontSheets.sheet("minecraft", "font/nonlatin_european.png").isPresent());
    }

    @Test
    void bundledSheetsHaveVanillaDimensions() {
        assertEquals(128, VanillaFontSheets.sheet("minecraft", "font/ascii.png").orElseThrow().getWidth());
        assertEquals(128, VanillaFontSheets.sheet("minecraft", "font/ascii.png").orElseThrow().getHeight());
        assertEquals(144, VanillaFontSheets.sheet("minecraft", "font/accented.png").orElseThrow().getWidth());
        assertEquals(900, VanillaFontSheets.sheet("minecraft", "font/accented.png").orElseThrow().getHeight());
        assertEquals(128, VanillaFontSheets.sheet("minecraft", "font/nonlatin_european.png").orElseThrow().getWidth());
        assertEquals(536, VanillaFontSheets.sheet("minecraft", "font/nonlatin_european.png").orElseThrow().getHeight());
    }

    @Test
    void nonMinecraftNamespaceNeverMatches() {
        assertTrue(VanillaFontSheets.sheet("testpack", "font/ascii.png").isEmpty());
        assertTrue(VanillaFontSheets.sheet("mcc", "font/ascii.png").isEmpty());
    }

    @Test
    void minecraftPathOutsideTheBundledSetNeverMatches() {
        assertTrue(VanillaFontSheets.sheet("minecraft", "font/unifont.png").isEmpty());
        assertTrue(VanillaFontSheets.sheet("minecraft", "font/ascii_sga.png").isEmpty());
        assertTrue(VanillaFontSheets.sheet("minecraft", "gui/sprites/tooltip/background.png").isEmpty());
    }

    @Test
    void decodedSheetIsMemoizedAndSharedWithinTheRun() {
        BufferedImage first = VanillaFontSheets.sheet("minecraft", "font/ascii.png").orElseThrow();
        BufferedImage second = VanillaFontSheets.sheet("minecraft", "font/ascii.png").orElseThrow();
        assertSame(first, second, "the tiny bundled sheet decodes at most once and is shared read-only");
    }
}
