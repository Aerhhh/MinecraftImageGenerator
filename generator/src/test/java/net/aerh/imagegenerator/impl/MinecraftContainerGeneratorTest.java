package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.item.InventoryItem;
import net.aerh.imagegenerator.pack.CustomModelData;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import net.aerh.imagegenerator.text.RgbColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Container compositor coverage against the synthetic
 * {@link FixturePacks#writeContainerPack container pack}.
 *
 * <p>Fixed geometry at scale factor 1 (2 canvas px per GUI px): the GUI rect is 352 canvas px
 * wide and {@code (114 + 18 * rows) * 2} tall; slot 1's interior origin lands at canvas
 * (16, 36); the title line top sits at GUI y 6 so a height-64/ascent-5 glyph cell tops out at
 * GUI {@code 6 + 7 - 5 = 8} (canvas y 16).
 *
 * <p>Fixture glyphs (font {@code testpack:menu}): U+E100 64x64 GUI 0xFF336699 (ascent 5,
 * advance 65), U+E101 40x40 GUI 0xFF993366 (ascent 30, advance 41), U+E102 160x160 GUI
 * 0xFF663399 (ascent 5, advance 161); space advances U+E10A = -8.0 and U+E10B = 120.0.
 */
class MinecraftContainerGeneratorTest {

    private static final String MENU_FONT = "testpack:menu";
    private static final String PAD_MINUS_EIGHT = Character.toString(0xE10A);
    private static final String PAD_PLUS_120 = Character.toString(0xE10B);
    private static final String GLYPH_BACKGROUND = Character.toString(0xE100);
    private static final String GLYPH_TALL = Character.toString(0xE101);
    private static final String GLYPH_DEEP = Character.toString(0xE102);

    private static final int BACKGROUND_ART = 0xFF336699;
    private static final int TALL_ART = 0xFF993366;
    private static final int DEEP_ART = 0xFF663399;
    private static final int MARKER = 0xFFAA5500;

    @TempDir
    Path packDir;

    private PackRepository registerContainerPack() {
        FixturePacks.writeContainerPack(packDir);
        PackRepository repository = new PackRepository();
        repository.register("test:container", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
        return repository;
    }

    private MinecraftContainerGenerator.Builder packBuilder(PackRepository repository) {
        return new MinecraftContainerGenerator.Builder()
            .withPack(PackId.parse("test:container"))
            .withPackRepository(repository);
    }

    /**
     * A glyph-art title run in white, the way real menu packs author them: glyph textures are
     * tinted multiplicatively by the run color, so white preserves the art exactly (the default
     * #404040 would darken it; see {@link #defaultTitleColorTintsGlyphArt}).
     */
    private static MinecraftContainerGenerator.TitleRun whiteRun(String text) {
        return new MinecraftContainerGenerator.TitleRun(text, MENU_FONT, new RgbColor(0xFFFFFF), false, false);
    }

    // ---------------------------------------------------------------- geometry

    @Test
    void canvasMatchesVanillaGeometryForEveryRowCount() {
        for (int rows = 1; rows <= 6; rows++) {
            BufferedImage image = new MinecraftContainerGenerator.Builder()
                .withRows(rows).build().render(null).getImage();
            assertEquals(176 * 2, image.getWidth(), "width for rows " + rows);
            assertEquals((114 + 18 * rows) * 2, image.getHeight(), "height for rows " + rows);
        }
    }

    @Test
    void scaleFactorScalesTheWholeCanvas() {
        BufferedImage image = new MinecraftContainerGenerator.Builder()
            .withRows(1).withScaleFactor(2).build().render(null).getImage();
        assertEquals(176 * 4, image.getWidth());
        assertEquals(132 * 4, image.getHeight());
    }

    /**
     * The overdraw rounding math ({@code ceilToWholeGuiPx}) and the origin placement both
     * divide/multiply by the pixel size; at scale factor 1 (pixel size 2) a mutant hardcoding
     * 2 cancels out invisibly, so the glyph-title expansion is pinned at scale factor 2 too:
     * the SAME GUI geometry as {@link #tallAscentAndLeadingNegativeAdvancesExpandTheCanvasLeftAndTop},
     * at 4 canvas px per GUI px.
     */
    @Test
    void titleGlyphOverdrawAndSlotAnchoringScaleWithTheScaleFactor() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withScaleFactor(2)
            .withTitle(whiteRun(PAD_MINUS_EIGHT + PAD_MINUS_EIGHT + GLYPH_TALL))
            .withSlot(1, "testpack:item/marker")
            .build().render(null).getImage();

        assertEquals((8 + 176) * 4, image.getWidth(), "8 GUI px left overdraw at pixel size 4");
        assertEquals((17 + 132) * 4, image.getHeight(), "17 GUI px top overdraw at pixel size 4");

        // The 40x40 GUI cell spans canvas (0,0)..(159,159).
        assertEquals(TALL_ART, image.getRGB(0, 0), "art reaches the expanded top-left corner");
        assertEquals(TALL_ART, image.getRGB(159, 159), "art bottom-right");
        assertEquals(0, image.getRGB(160, 0), "right of the art");

        // Slot 1 interior anchored to the GUI rect origin at canvas (32, 68): items above art.
        assertEquals(MARKER, image.getRGB(64, 140), "slot 1 top-left");
        assertEquals(MARKER, image.getRGB(127, 203), "slot 1 bottom-right");
        assertEquals(TALL_ART, image.getRGB(63, 140), "art visible right next to the item");
    }

    @Test
    void slotItemsLandAtVanillaSlotPositions() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(6)
            .withSlot(1, "testpack:item/marker")
            .withSlot(54, "testpack:item/marker")
            .build().render(null).getImage();

        // Slot 1 interior: GUI (8, 18) -> canvas (16, 36), 32x32.
        assertEquals(MARKER, image.getRGB(16, 36), "slot 1 top-left");
        assertEquals(MARKER, image.getRGB(47, 67), "slot 1 bottom-right");
        assertEquals(0, image.getRGB(15, 36), "left of slot 1");
        assertEquals(0, image.getRGB(16, 35), "above slot 1");
        // Slot 54 interior: GUI (8 + 18*8, 18 + 18*5) = (152, 108) -> canvas (304, 216).
        assertEquals(MARKER, image.getRGB(304, 216), "slot 54 top-left");
        assertEquals(MARKER, image.getRGB(335, 247), "slot 54 bottom-right");
        assertEquals(0, image.getRGB(336, 216), "right of slot 54");
    }

    // ------------------------------------------------- title glyph background

    /**
     * The core technique from the spec: a transparent {@code generic_54}, a title of
     * {@code [pad -8][background glyph]} in a glyph-art font, and an item in slot 1. The art's
     * top-left must land at GUI-relative {@code (8 - 8, 6 + 7 - 5)} = (0, 8) and the item must
     * draw ABOVE the art.
     */
    @Test
    void titleGlyphArtLandsAtAscentDerivedPositionAndItemsDrawAboveIt() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(whiteRun(PAD_MINUS_EIGHT + GLYPH_BACKGROUND))
            .withSlot(1, "testpack:item/marker")
            .build().render(null).getImage();

        // Art fits inside the GUI rect, so the canvas stays the plain GUI rect.
        assertEquals(352, image.getWidth());
        assertEquals(264, image.getHeight());

        // Art top-left at GUI (0, 8) -> canvas (0, 16); the 64x64 GUI cell spans to (127, 143).
        assertEquals(BACKGROUND_ART, image.getRGB(0, 16), "art top-left");
        assertEquals(BACKGROUND_ART, image.getRGB(127, 143), "art bottom-right");
        assertEquals(0, image.getRGB(128, 16), "right of the art (no shadow pass)");
        assertEquals(0, image.getRGB(0, 15), "above the art");

        // The slot 1 item overlaps the art region and must overdraw it (items above title art).
        assertEquals(MARKER, image.getRGB(16, 36), "item above the glyph art");
        assertEquals(MARKER, image.getRGB(47, 67), "item above the glyph art (bottom-right)");
        assertEquals(BACKGROUND_ART, image.getRGB(48, 36), "art visible right next to the item");

        // The transparent container texture painted nothing outside the art.
        assertEquals(0, image.getRGB(351, 263), "transparent pack background paints nothing");
    }

    @Test
    void tallAscentAndLeadingNegativeAdvancesExpandTheCanvasLeftAndTop() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(whiteRun(PAD_MINUS_EIGHT + PAD_MINUS_EIGHT + GLYPH_TALL))
            .withSlot(1, "testpack:item/marker")
            .build().render(null).getImage();

        // Art left edge: GUI 8 - 16 = -8 (16 canvas px past the left edge -> 8 GUI px overdraw).
        // Art top edge: GUI 6 + 7 - 30 = -17 (34 canvas px past the top -> 17 GUI px overdraw).
        assertEquals((8 + 176) * 2, image.getWidth(), "canvas gains the left overdraw");
        assertEquals((17 + 132) * 2, image.getHeight(), "canvas gains the top overdraw");

        // The 40x40 GUI cell now spans canvas (0,0)..(79,79).
        assertEquals(TALL_ART, image.getRGB(0, 0), "art reaches the expanded top-left corner");
        assertEquals(TALL_ART, image.getRGB(79, 79), "art bottom-right");
        assertEquals(0, image.getRGB(80, 0), "right of the art");

        // Slot coordinates stay anchored to the GUI rect origin at canvas (16, 34).
        assertEquals(MARKER, image.getRGB(16 + 16, 34 + 36), "slot 1 anchored to the shifted GUI rect");
        assertEquals(MARKER, image.getRGB(16 + 47, 34 + 67), "slot 1 bottom-right");
    }

    @Test
    void deepGlyphExpandsTheCanvasBottom() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(whiteRun(GLYPH_DEEP))
            .build().render(null).getImage();

        // Art bottom: GUI 6 + 7 - 5 + 160 = 168, 36 GUI px past the 132 GUI px rect.
        assertEquals(352, image.getWidth(), "no horizontal overdraw");
        assertEquals((132 + 36) * 2, image.getHeight(), "canvas gains the bottom overdraw");
        assertEquals(DEEP_ART, image.getRGB(16, 16), "art top-left at GUI (8, 8)");
        assertEquals(DEEP_ART, image.getRGB(335, 335), "art bottom-right at GUI (167, 167)");
        // The expansion is exact: the canvas's last row IS the art's last row.
        assertEquals(0, image.getRGB(15, 335), "left of the art on the expanded rows");
    }

    /**
     * Italic pack glyph art shears each drawn GUI row horizontally by
     * {@code 1 - 0.25 * guiPxBelowLineTop} GUI px: a deep glyph's bottom rows shear far LEFT of
     * the cursor and the canvas must expand to keep them - the never-clipped title contract.
     */
    @Test
    void italicDeepGlyphExpandsTheCanvasForItsLeftShear() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(new MinecraftContainerGenerator.TitleRun(
                GLYPH_DEEP, MENU_FONT, new RgbColor(0xFFFFFF), false, true))
            .build().render(null).getImage();

        // The cell top sits 7 - 5 = 2 GUI px below the line top: first-row shear = +0.5 GUI px,
        // last-row shear = 1 - 0.25 * 161 = -39.25 GUI px. Leftmost art = GUI 8 - 39.25, i.e.
        // 32 whole GUI px of left overdraw; the bottom overdraw stays the plain 36.
        assertEquals((32 + 176) * 2, image.getWidth(), "canvas gains the left shear overdraw");
        assertEquals((132 + 36) * 2, image.getHeight());

        // GUI rect origin lands at canvas x 64, so the glyph cursor sits at GUI 40. Bottom row
        // (161 GUI px below the line top): destX = round((40 - 39.25) * 2) = 2, 320 px wide.
        assertEquals(DEEP_ART, image.getRGB(2, 334), "bottom row start after the left shear");
        assertEquals(DEEP_ART, image.getRGB(321, 334), "bottom row end");
        assertEquals(0, image.getRGB(1, 334), "left of the sheared bottom row");
        assertEquals(0, image.getRGB(322, 334), "right of the sheared bottom row");
        // Top row shears +0.5 GUI px right: destX = round((40 + 0.5) * 2) = 81.
        assertEquals(DEEP_ART, image.getRGB(81, 16), "top row start");
        assertEquals(0, image.getRGB(80, 16), "left of the sheared top row");
    }

    /** The symmetric case: a tall-ascent italic glyph shears RIGHT above the line top. */
    @Test
    void italicTallGlyphExpandsTheCanvasForItsRightShear() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(new MinecraftContainerGenerator.TitleRun(
                PAD_PLUS_120 + PAD_PLUS_120 + GLYPH_TALL, MENU_FONT, new RgbColor(0xFFFFFF), false, true))
            .build().render(null).getImage();

        // The cell top sits 7 - 30 = 23 GUI px ABOVE the line top: first-row shear = +6.75 GUI
        // px. Rightmost art = GUI 8 + 240 + 41 + 6.75 = 295.75, i.e. 120 whole GUI px of right
        // overdraw (cursor travel alone would only grant 113 and clip the top rows).
        assertEquals((176 + 120) * 2, image.getWidth(), "canvas gains the right shear overdraw");
        assertEquals((17 + 132) * 2, image.getHeight(), "ascent 30 lifts the cell 17 GUI px above the GUI rect");

        // Top row: destX = round((8 + 240 + 6.75) * 2) = 510, 80 canvas px wide.
        assertEquals(TALL_ART, image.getRGB(510, 0), "top row start");
        assertEquals(TALL_ART, image.getRGB(589, 0), "top row end inside the expansion");
        assertEquals(0, image.getRGB(590, 0), "right of the sheared top row");
        assertEquals(0, image.getRGB(509, 0), "left of the sheared top row");
    }

    @Test
    void wideCursorTravelExpandsTheCanvasRight() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(whiteRun(PAD_PLUS_120 + GLYPH_BACKGROUND))
            .build().render(null).getImage();

        // Cursor extent: 8 + 120 + 65 = 193 GUI, 17 GUI px past the 176 GUI px rect.
        assertEquals((176 + 17) * 2, image.getWidth(), "canvas gains the right overdraw");
        assertEquals(264, image.getHeight());
        // Art spans GUI x 128..191 -> canvas 256..383 at GUI y 8 -> canvas 16.
        assertEquals(BACKGROUND_ART, image.getRGB(256, 16), "art left edge");
        assertEquals(BACKGROUND_ART, image.getRGB(383, 16), "art right edge inside the expansion");
        assertEquals(0, image.getRGB(384, 16), "advance slack past the art stays empty");
    }

    // ------------------------------------------------------------- title text

    @Test
    void builtInTitleTextUsesVanillaDefaultColorWithoutShadow() {
        BufferedImage image = new MinecraftContainerGenerator.Builder()
            .withRows(1)
            .withTitle(MinecraftContainerGenerator.TitleRun.of("Chest"))
            .build().render(null).getImage();

        boolean foundTitleColor = false;
        boolean foundShadowColor = false;
        for (int y = 8; y < 34 && !(foundTitleColor && foundShadowColor); y++) {
            for (int x = 12; x < 120; x++) {
                int rgb = image.getRGB(x, y);
                if (rgb == 0xFF404040) {
                    foundTitleColor = true;
                }
                if (rgb == 0xFF101010) {
                    foundShadowColor = true;
                }
            }
        }
        assertTrue(foundTitleColor, "title text drawn in the vanilla default #404040");
        assertTrue(!foundShadowColor, "the container title never draws a drop shadow");
    }

    /**
     * Vanilla pin: the title tints glyph textures multiplicatively like any text color, so a
     * glyph-art run WITHOUT an explicit color darkens under the default #404040. Menu packs
     * author their art runs in white (see {@link #whiteRun}).
     */
    @Test
    void defaultTitleColorTintsGlyphArt() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(MinecraftContainerGenerator.TitleRun.of(GLYPH_BACKGROUND, MENU_FONT))
            .build().render(null).getImage();

        // 0xFF336699 tinted by 0x404040: each channel scales by 64/255, rounded to nearest.
        assertEquals(0xFF0D1A26, image.getRGB(16, 16), "glyph art tinted by the default title color");
    }

    @Test
    void titleRunColorOverridesTheDefault() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withTitle(new MinecraftContainerGenerator.TitleRun(
                GLYPH_BACKGROUND, MENU_FONT, new RgbColor(0x00FF00), false, false))
            .build().render(null).getImage();

        // White glyph art tinted multiplicatively by pure green keeps only the green channel:
        // 0xFF336699 * #00FF00 -> 0xFF006600.
        assertEquals(0xFF006600, image.getRGB(16, 16), "glyph art tinted by the run color");
    }

    // ------------------------------------------------------- fallback chrome

    /**
     * Without a pack (or without a {@code generic_54} override) the base layer must be exactly
     * the inventory generator's programmatic chrome plus the slot texture over each grid
     * position - composed here from the same shared helpers.
     */
    @Test
    void fallbackChromeMatchesTheSharedInventoryStyle() {
        BufferedImage actual = new MinecraftContainerGenerator.Builder()
            .withRows(2).build().render(null).getImage();

        BufferedImage expected = new BufferedImage(352, (114 + 36) * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = expected.createGraphics();
        try {
            MinecraftInventoryGenerator.drawVanillaChrome(graphics, expected.getWidth(), expected.getHeight(), 2);
            BufferedImage slotTexture = MinecraftInventoryGenerator.loadSlotTexture(36);
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < 9; column++) {
                    graphics.drawImage(slotTexture, (7 + 18 * column) * 2, (17 + 18 * row) * 2, null);
                }
            }
        } finally {
            graphics.dispose();
        }

        ImageAssertions.assertPixelsEqual(expected, actual, "fallback chrome");
    }

    @Test
    void packWithoutContainerTextureFallsBackToChrome(@TempDir Path fontPackDir) {
        FixturePacks.writeFontPack(fontPackDir);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:containerfonts",
            PackSource.directory(fontPackDir, PackLimits.fromSystemProperties()));

        BufferedImage withPack = new MinecraftContainerGenerator.Builder()
            .withRows(1).withPack(packId).withPackRepository(repository)
            .build().render(null).getImage();
        BufferedImage withoutPack = new MinecraftContainerGenerator.Builder()
            .withRows(1).build().render(null).getImage();

        ImageAssertions.assertPixelsEqual(withoutPack, withPack, "no-texture pack fallback");
    }

    // ------------------------------------------------------- pack background

    /**
     * Vanilla stitches the container background from TWO texture sections - the chest section
     * (texture rows {@code 0..rows*18+17}) directly above the bottom/player-inventory section
     * (96 rows starting at v = 126) - never as one contiguous crop, so opaque restyled chrome
     * shows the authored bottom border at every row count. The fixture marks each boundary row
     * with a distinct color; positions are asserted in canvas space.
     */
    @Test
    void packContainerTextureStitchesTheTwoVanillaSections(@TempDir Path artPackDir) {
        assertStitchedBackground(artPackDir, 256);
    }

    /**
     * The same canvas assertions against a 512x512 fixture: the 256-normalized sampling must
     * scale the source rect, so an HD texture lands every marker on IDENTICAL canvas pixels
     * (a mutant hardcoding the texture ratio to 1.0 samples a quarter of the region and fails).
     */
    @Test
    void hdPackContainerTextureSamplesIdenticalCanvasPositions(@TempDir Path artPackDir) {
        assertStitchedBackground(artPackDir, 512);
    }

    private static void assertStitchedBackground(Path artPackDir, int textureSize) {
        FixturePacks.writeContainerArtPack(artPackDir, textureSize);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:containerart" + textureSize,
            PackSource.directory(artPackDir, PackLimits.fromSystemProperties()));

        BufferedImage image = new MinecraftContainerGenerator.Builder()
            .withRows(1).withPack(packId).withPackRepository(repository)
            .build().render(null).getImage();

        assertEquals(352, image.getWidth());
        assertEquals(264, image.getHeight());
        // Chest section: texture rows 0..34 land on GUI rows 0..34.
        assertEquals(0xFFFF0000, image.getRGB(0, 0), "texel (0,0) marker");
        assertEquals(0xFFFF0000, image.getRGB(1, 1), "2x scale of texel (0,0)");
        assertEquals(0xFF00FF00, image.getRGB(350, 0), "texel (175,0) marker");
        assertEquals(0xFF00FF00, image.getRGB(351, 1), "2x scale of texel (175,0)");
        assertEquals(0xFFFF00FF, image.getRGB(0, 68), "texel (0,34): last chest-section row at GUI 34");
        // Bottom section: texture rows 126..221 land on GUI rows 35..130; the band between the
        // sections (texture rows 35..125) never shows for one row.
        assertEquals(0xFFFFA500, image.getRGB(0, 70), "texel (0,126): first bottom-section row at GUI 35");
        assertEquals(0xFFFFFF00, image.getRGB(0, 260), "texel (0,221): last bottom-section row at GUI 130");
        assertEquals(0xFFFFFF00, image.getRGB(1, 261), "2x scale of texel (0,221)");
        assertEquals(0xFF224488, image.getRGB(176, 132), "section fill");
        // The GUI rect's final row stays unpainted, exactly like the vanilla client.
        assertEquals(0, image.getRGB(0, 262), "last GUI row unpainted");
        assertEquals(0, image.getRGB(351, 263), "last GUI row unpainted (bottom-right)");
    }

    @Test
    void sixRowContainerSkipsOnlyTheSeamTextureRow(@TempDir Path artPackDir) {
        FixturePacks.writeContainerArtPack(artPackDir);
        PackRepository repository = new PackRepository();
        PackId packId = repository.register("test:containerart6",
            PackSource.directory(artPackDir, PackLimits.fromSystemProperties()));

        BufferedImage image = new MinecraftContainerGenerator.Builder()
            .withRows(6).withPack(packId).withPackRepository(repository)
            .build().render(null).getImage();

        assertEquals((114 + 108) * 2, image.getHeight());
        // Six rows: chest section texture rows 0..124 at GUI 0..124, bottom section texture
        // rows 126..221 at GUI 125..220.
        assertEquals(0xFF00FFFF, image.getRGB(0, 70), "texel (0,35) visible inside a 6-row chest section");
        assertEquals(0xFFFFA500, image.getRGB(0, 250), "texel (0,126): first bottom-section row at GUI 125");
        assertEquals(0xFFFFFF00, image.getRGB(0, 440), "texel (0,221): last bottom-section row at GUI 220");
        assertEquals(0, image.getRGB(0, 442), "last GUI row unpainted");
        // Texture row 125 sits between the sections and is never sampled at any row count.
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == 0xFF7700FF) {
                    fail("seam texel (0,125) must never render, found at (" + x + "," + y + ")");
                }
            }
        }
    }

    // ------------------------------------------------------------ item counts

    @Test
    void stackCountsRenderThroughTheSharedBadgeRenderer() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withSlot(1, "testpack:item/marker:32")
            .build().render(null).getImage();

        // Transparent pack background: the only white pixels are the count text over the
        // bottom-right of slot 1's rect (canvas (14,34)..(49,69)).
        boolean foundCountText = false;
        for (int y = 50; y <= 70 && !foundCountText; y++) {
            for (int x = 14; x <= 50; x++) {
                if (image.getRGB(x, y) == 0xFFFFFFFF) {
                    foundCountText = true;
                    break;
                }
            }
        }
        assertTrue(foundCountText, "count badge text drawn in the slot's bottom-right");
    }

    /**
     * Pixel-exact placement pin: the badge must equal {@code drawStackCount} rendered at slot
     * 1's RECT origin (interior minus one GUI px), so a mutant passing the interior origin (or
     * dropping the {@code - pixelSize}) shifts the badge and fails region equality.
     */
    @Test
    void stackCountBadgeMatchesTheSharedRendererAtTheSlotRectOrigin() {
        PackRepository repository = registerContainerPack();
        BufferedImage actual = packBuilder(repository)
            .withRows(1)
            .withSlot(1, "testpack:item/marker:32")
            .build().render(null).getImage();

        BufferedImage expected = packBuilder(repository)
            .withRows(1)
            .withSlot(1, "testpack:item/marker")
            .build().render(null).getImage();
        Graphics2D graphics = expected.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            // Slot 1 interior origin is canvas (16, 36); its rect origin is one GUI px outside.
            MinecraftInventoryGenerator.drawStackCount(graphics, 32, 14, 34, 2);
        } finally {
            graphics.dispose();
        }

        ImageAssertions.assertPixelsEqual(expected, actual, "stack count badge placement");
    }

    @Test
    void amountOneDrawsNoCountBadge() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withRows(1)
            .withSlot(1, "testpack:item/marker:1")
            .build().render(null).getImage();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                assertTrue(rgb == 0 || rgb == MARKER,
                    "only the marker item is drawn, found " + Integer.toHexString(rgb) + " at (" + x + "," + y + ")");
            }
        }
    }

    // ------------------------------------------------------------ determinism

    @Test
    void identicalConfigurationsRenderIdenticalPixels() {
        PackRepository repository = registerContainerPack();
        GeneratedObject first = packBuilder(repository)
            .withRows(1)
            .withTitle(MinecraftContainerGenerator.TitleRun.of(PAD_MINUS_EIGHT + GLYPH_BACKGROUND, MENU_FONT))
            .withSlot(1, "testpack:item/marker:16")
            .build().render(null);
        GeneratedObject second = packBuilder(repository)
            .withRows(1)
            .withTitle(MinecraftContainerGenerator.TitleRun.of(PAD_MINUS_EIGHT + GLYPH_BACKGROUND, MENU_FONT))
            .withSlot(1, "testpack:item/marker:16")
            .build().render(null);

        ImageAssertions.assertPixelsEqual(first.getImage(), second.getImage(), "deterministic render");
    }

    // ------------------------------------------------------------ builder API

    @Test
    void builderRejectsInvalidRows() {
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftContainerGenerator.Builder().withRows(0));
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftContainerGenerator.Builder().withRows(7));
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftContainerGenerator.Builder().build(), "rows are required");
    }

    @Test
    void builderRejectsOutOfRangeSlots() {
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftContainerGenerator.Builder().withRows(1).withSlot(0, "stone"));
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftContainerGenerator.Builder().withRows(1).withSlot(10, "stone").build(),
            "slot 10 is out of range for 1 row");
    }

    @Test
    void builderRejectsMalformedSlotSpecs() {
        MinecraftContainerGenerator.Builder builder = new MinecraftContainerGenerator.Builder().withRows(1);
        assertThrows(IllegalArgumentException.class, () -> builder.withSlot(1, " "));
        assertThrows(IllegalArgumentException.class, () -> builder.withSlot(1, "stone%%stone"));
        assertThrows(IllegalArgumentException.class, () -> builder.withSlot(1, "stone:5,64"),
            "smuggled slot data is rejected");
        assertThrows(IllegalArgumentException.class, () -> builder.withSlot(1, "stone:0"));
        assertThrows(IllegalArgumentException.class, () -> builder.withSlot(1, "stone:65"));
        assertThrows(IllegalArgumentException.class, () -> builder.withSlot(1, "stone:99999999999"));
    }

    @Test
    void settingTheSameSlotAgainReplacesTheItem() {
        PackRepository repository = registerContainerPack();
        BufferedImage replaced = packBuilder(repository)
            .withRows(1)
            .withSlot(1, "stone")
            .withSlot(1, "testpack:item/marker")
            .build().render(null).getImage();
        assertEquals(MARKER, replaced.getRGB(16, 36), "the last spec for a slot wins");
    }

    // ------------------------------------------------------------ item dedupe

    /**
     * Mirrors {@link MinecraftInventoryGeneratorSlotDedupeTest}: identical slot specs must cost
     * ONE item generation per render, not one per slot - 54 individually specified enchanted
     * slots would otherwise eagerly regenerate ~180 glint frames each, the exact timeout the
     * inventory generator's slot visual cache was added to fix.
     */
    @Test
    void duplicateSlotSpecsShareOneGeneratedItemVisual() {
        MinecraftContainerGenerator generator = new MinecraftContainerGenerator.Builder()
            .withRows(1)
            .withSlot(1, "stone")
            .withSlot(2, "stone")
            .build();

        InventoryItem first = new InventoryItem(1, 1, "stone", null, null);
        InventoryItem second = new InventoryItem(2, 1, "stone", null, null);
        InventoryItem enchanted = new InventoryItem(3, 1, "stone", "enchant", null);
        InventoryItem damaged = new InventoryItem(4, 1, "stone", null, 50);

        BufferedImage firstImage = generator.resolveItemImage(first, CustomModelData.EMPTY, null);
        assertNotNull(firstImage, "the pipeline yields an image for stone");
        assertSame(firstImage, generator.resolveItemImage(second, CustomModelData.EMPTY, null),
            "identical specs reuse one generated visual within a render");
        assertNotSame(firstImage, generator.resolveItemImage(enchanted, CustomModelData.EMPTY, null),
            "different modifiers must not share a cached visual");
        assertNotSame(firstImage, generator.resolveItemImage(damaged, CustomModelData.EMPTY, null),
            "different durability must not share a cached visual");
        // Custom model data also enters the key; that participation is pinned end to end by
        // MinecraftContainerGeneratorElementsTest#slotCustomModelDataDrivesFlatSpriteDispatch,
        // where one spec renders two different sprites in one render.
    }

    // ------------------------------------------------------------- cache keys

    /**
     * The slots map enters the reflective render cache key; its canonical form must be
     * injective. A raw {@code TreeMap.toString()} flattens both of these configurations to
     * {@code "{1=stone, 2=dirt}"}, silently serving one build's cached render for the other.
     */
    @Test
    void ambiguousSlotSpecsCannotCollideInTheRenderCacheKey() {
        MinecraftContainerGenerator ambiguous = new MinecraftContainerGenerator.Builder()
            .withRows(1).withSlot(1, "stone, 2=dirt").build();
        MinecraftContainerGenerator twoSlots = new MinecraftContainerGenerator.Builder()
            .withRows(1).withSlot(1, "stone").withSlot(2, "dirt").build();

        assertNotEquals(GeneratorCacheKey.fromGenerator(ambiguous), GeneratorCacheKey.fromGenerator(twoSlots),
            "distinct slot configurations must hash to distinct cache keys");
    }

    @Test
    void titlelessAndTitledRendersDiffer() {
        PackRepository repository = registerContainerPack();
        BufferedImage titled = packBuilder(repository)
            .withRows(1)
            .withTitle(MinecraftContainerGenerator.TitleRun.of(GLYPH_BACKGROUND, MENU_FONT))
            .build().render(null).getImage();
        BufferedImage titleless = packBuilder(repository)
            .withRows(1)
            .build().render(null).getImage();
        assertNotEquals(0, titled.getRGB(16, 16), "titled render draws glyph art");
        assertEquals(0, titleless.getRGB(16, 16), "titleless render stays transparent");
    }
}
