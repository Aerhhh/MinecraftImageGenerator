package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.impl.MinecraftContainerGenerator.TitleRun;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import lib.minecraft.text.ChatColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bossbar-anchored HUD line stack coverage against the synthetic
 * {@link FixturePacks#writeContainerPack container pack}.
 *
 * <p>Fixed geometry at scale factor 1 (2 canvas px per GUI px): the default canvas is
 * {@code 320 * 2 = 640} px wide; line k's top edge sits at GUI {@code y = 3 + 19 * k}
 * (canvas {@code 6 + 38 * k}).
 *
 * <p>Fixture glyphs (font {@code testpack:menu}): the placement pin U+E103 is an 8x8 GUI
 * 0xFF44CC88 cell with ascent 7, so its cell top lands EXACTLY on the line top
 * ({@code 7 - ascent = 0}) and its advance is 9 GUI px; U+E100 is 64x64 GUI 0xFF336699
 * (ascent 5, advance 65); space advance U+E10A = -8.0.
 */
class MinecraftHudLineGeneratorTest {

    private static final String MENU_FONT = "testpack:menu";
    private static final String GLYPH_ANCHOR = Character.toString(0xE103);
    private static final String GLYPH_BACKGROUND = Character.toString(0xE100);
    private static final String PAD_MINUS_EIGHT = Character.toString(0xE10A);

    private static final int ANCHOR_ART = 0xFF44CC88;
    private static final int BACKGROUND_ART = 0xFF336699;
    /** 0xFF44CC88 tinted by the quartered-white shadow color 0x3F3F3F, each channel rounded. */
    private static final int ANCHOR_SHADOW = 0xFF113222;
    /** The drop shadow of white built-in text: white quartered. */
    private static final int WHITE_SHADOW = 0xFF3F3F3F;

    @TempDir
    Path packDir;

    private PackRepository registerContainerPack() {
        FixturePacks.writeContainerPack(packDir);
        PackRepository repository = new PackRepository();
        repository.register("test:hud", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
        return repository;
    }

    private MinecraftHudLineGenerator.Builder packBuilder(PackRepository repository) {
        return new MinecraftHudLineGenerator.Builder()
            .withPack(PackId.parse("test:hud"))
            .withPackRepository(repository);
    }

    /** A glyph run in explicit white, preserving the fixture art exactly (multiplicative tint). */
    private static TitleRun whiteRun(String text) {
        return new TitleRun(text, MENU_FONT, ChatColor.of(0xFFFFFF), false, false);
    }

    // ------------------------------------------------------------- placement

    /** The vanilla anchor math: {@code lineTopGuiPx(k) = 3 + 19 * k}. */
    @Test
    void lineTopFollowsTheVanillaBossbarAnchors() {
        assertEquals(3, MinecraftHudLineGenerator.lineTopGuiPx(0), "line 0: bar top 12 minus the 9 px name offset");
        assertEquals(22, MinecraftHudLineGenerator.lineTopGuiPx(1));
        assertEquals(41, MinecraftHudLineGenerator.lineTopGuiPx(2));
        assertThrows(IllegalArgumentException.class, () -> MinecraftHudLineGenerator.lineTopGuiPx(-1));
    }

    /**
     * Two anchor-glyph lines: the E103 cell top lands exactly on the line top, so line 0's art
     * must start at canvas y 6 (GUI 3) and line 1's at canvas y 44 (GUI 22). Centering is
     * vanilla's whole-GUI-px integer math: at advance 9 GUI px on the default 320 GUI px
     * canvas, the origin is GUI {@code 320 / 2 - 9 / 2 = 156} (canvas x 312) - exactly where
     * the client draws it, NOT the canvas-precision 311 (GUI 155.5).
     */
    @Test
    void linesRenderAtTheVanillaBossbarNameAnchors() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withLine(whiteRun(GLYPH_ANCHOR))
            .withLine(whiteRun(GLYPH_ANCHOR))
            .build().render(null).getImage();

        assertEquals(640, image.getWidth(), "default 320 GUI px canvas");
        assertEquals(64, image.getHeight(), "line 1 top (22) plus the 10 GUI px line box");

        // Line 0: 16x16 canvas px cell at (312, 6).
        assertEquals(ANCHOR_ART, image.getRGB(312, 6), "line 0 art top-left");
        assertEquals(ANCHOR_ART, image.getRGB(327, 21), "line 0 art bottom-right");
        assertEquals(0, image.getRGB(311, 6), "left of line 0 art");
        assertEquals(0, image.getRGB(312, 5), "above line 0 art");

        // Line 1: identical x, one line pitch (19 GUI px = 38 canvas px) lower.
        assertEquals(ANCHOR_ART, image.getRGB(312, 44), "line 1 art top-left");
        assertEquals(ANCHOR_ART, image.getRGB(327, 59), "line 1 art bottom-right");
        assertEquals(0, image.getRGB(312, 43), "above line 1 art (line 0's shadow ends at y 23)");
    }

    /** An empty run list is a blank spacer line: nothing drawn, the anchor slot stays occupied. */
    @Test
    void blankLinesKeepTheirAnchorSlot() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withLine(whiteRun(GLYPH_ANCHOR))
            .withLine()
            .withLine(whiteRun(GLYPH_ANCHOR))
            .build().render(null).getImage();

        assertEquals(102, image.getHeight(), "line 2 top (41) plus the 10 GUI px line box");
        assertEquals(ANCHOR_ART, image.getRGB(312, 6), "line 0 art");
        assertEquals(0, image.getRGB(312, 44), "the blank line 1 draws nothing");
        assertEquals(ANCHOR_ART, image.getRGB(312, 82), "line 2 art at GUI y 41");
    }

    // ------------------------------------------------------------- centering

    /**
     * Centering is extent-based: a trailing negative advance moves the CURSOR left but not the
     * line's rightmost art extent, so it must not shift the centered art. A cursor-end
     * implementation would shift the second render 8 canvas px right.
     */
    @Test
    void centeringUsesTheArtExtentNotTheCursorEnd() {
        PackRepository repository = registerContainerPack();
        BufferedImage plain = packBuilder(repository)
            .withLine(whiteRun(GLYPH_BACKGROUND))
            .build().render(null).getImage();
        BufferedImage trailingPad = packBuilder(repository)
            .withLine(whiteRun(GLYPH_BACKGROUND + PAD_MINUS_EIGHT))
            .build().render(null).getImage();

        // Sanity-pin the shared placement: advance 65 GUI px centered on 320 GUI px puts the
        // line origin at GUI 160 - 65 / 2 = 128 (canvas x 256); the cell top sits at GUI
        // 3 + 7 - 5 = 5 (canvas 10).
        assertEquals(BACKGROUND_ART, plain.getRGB(256, 10), "art top-left");
        assertEquals(BACKGROUND_ART, plain.getRGB(383, 10), "art top-right");
        assertEquals(0, plain.getRGB(255, 10), "left of the art");

        ImageAssertions.assertPixelsEqual(plain, trailingPad, "trailing negative advance must not skew centering");
    }

    @Test
    void customGuiWidthRecentersTheLines() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withGuiWidth(100)
            .withLine(whiteRun(GLYPH_ANCHOR))
            .build().render(null).getImage();

        assertEquals(200, image.getWidth());
        // Line origin: GUI 100 / 2 - 9 / 2 = 46 (canvas 92).
        assertEquals(ANCHOR_ART, image.getRGB(92, 6), "art recentered on the narrower canvas");
        assertEquals(0, image.getRGB(91, 6), "left of the art");
    }

    /** Art wider than the canvas clips at the side edges, like the screen edges in game. */
    @Test
    void lineWiderThanTheCanvasClipsAtTheCanvasEdges() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withGuiWidth(10)
            .withLine(whiteRun(GLYPH_BACKGROUND))
            .build().render(null).getImage();

        assertEquals(20, image.getWidth(), "the canvas never grows for wide lines");
        assertEquals(140, image.getHeight(), "deep glyph art still grows the canvas bottom (3 + 67 GUI px)");
        assertEquals(BACKGROUND_ART, image.getRGB(0, 10), "clipped art still covers the full canvas width");
        assertEquals(BACKGROUND_ART, image.getRGB(19, 10), "clipped art at the right edge");
    }

    // ---------------------------------------------------------------- shadow

    @Test
    void linesDrawWhiteWithShadowByDefault() {
        BufferedImage image = new MinecraftHudLineGenerator.Builder()
            .withLine(TitleRun.of("Boss"))
            .build().render(null).getImage();

        assertEquals(640, image.getWidth());
        assertEquals(26, image.getHeight(), "line 0 top (3) plus the 10 GUI px line box");
        assertTrue(containsColor(image, 0xFFFFFFFF), "runs without an explicit color draw in white");
        assertTrue(containsColor(image, WHITE_SHADOW), "the drop shadow draws by default");
    }

    @Test
    void textShadowCanBeDisabled() {
        BufferedImage image = new MinecraftHudLineGenerator.Builder()
            .withTextShadow(false)
            .withLine(TitleRun.of("Boss"))
            .build().render(null).getImage();

        assertEquals(24, image.getHeight(), "the line box loses its shadow row (9 GUI px)");
        assertTrue(containsColor(image, 0xFFFFFFFF), "text still draws in white");
        assertFalse(containsColor(image, WHITE_SHADOW), "no shadow pixels when shadows are off");
    }

    /** Pack glyph shadows draw at +1,+1 GUI px tinted with the quartered text color. */
    @Test
    void packGlyphsShadowLikeVanillaBossbarNames() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withLine(whiteRun(GLYPH_ANCHOR))
            .build().render(null).getImage();

        // Main art covers canvas (312, 6)..(327, 21); its shadow covers (314, 8)..(329, 23) and
        // stays visible in the 2 px band right of and below the art.
        assertEquals(ANCHOR_SHADOW, image.getRGB(329, 8), "shadow right band");
        assertEquals(ANCHOR_SHADOW, image.getRGB(329, 23), "shadow bottom-right corner");
        assertEquals(ANCHOR_SHADOW, image.getRGB(314, 23), "shadow bottom band");
        assertEquals(0, image.getRGB(330, 8), "right of the shadow");

        BufferedImage shadowless = packBuilder(repository)
            .withTextShadow(false)
            .withLine(whiteRun(GLYPH_ANCHOR))
            .build().render(null).getImage();
        assertEquals(0, shadowless.getRGB(329, 8), "no glyph shadow when shadows are off");
        assertEquals(ANCHOR_ART, shadowless.getRGB(312, 6), "the main pass still draws");
    }

    // ----------------------------------------------------------------- scale

    @Test
    void scaleFactorScalesEveryAnchor() {
        PackRepository repository = registerContainerPack();
        BufferedImage image = packBuilder(repository)
            .withScaleFactor(2)
            .withLine(whiteRun(GLYPH_ANCHOR))
            .build().render(null).getImage();

        assertEquals(1280, image.getWidth(), "320 GUI px at 4 canvas px per GUI px");
        assertEquals(52, image.getHeight(), "13 GUI px at pixel size 4");
        // Line origin: GUI 160 - 9 / 2 = 156 (canvas 624); art top at GUI 3 -> canvas 12; 32x32 px cell.
        assertEquals(ANCHOR_ART, image.getRGB(624, 12), "art top-left");
        assertEquals(ANCHOR_ART, image.getRGB(655, 43), "art bottom-right");
        assertEquals(0, image.getRGB(623, 12), "left of the art");
        assertEquals(ANCHOR_SHADOW, image.getRGB(659, 16), "shadow right band at +1 GUI px = +4 canvas px");
    }

    // ----------------------------------------------------------- determinism

    @Test
    void identicalConfigurationsRenderIdenticalPixels() {
        PackRepository repository = registerContainerPack();
        BufferedImage first = packBuilder(repository)
            .withLine(whiteRun(GLYPH_ANCHOR), TitleRun.of(" Boss"))
            .withLine(new TitleRun("Phase 2", null, ChatColor.of(0xFFAA00), true, false))
            .build().render(null).getImage();
        BufferedImage second = packBuilder(repository)
            .withLine(whiteRun(GLYPH_ANCHOR), TitleRun.of(" Boss"))
            .withLine(new TitleRun("Phase 2", null, ChatColor.of(0xFFAA00), true, false))
            .build().render(null).getImage();

        ImageAssertions.assertPixelsEqual(first, second, "deterministic render");
    }

    // ------------------------------------------------------------ builder API

    @Test
    void builderRejectsInvalidConfigurations() {
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftHudLineGenerator.Builder().build(), "at least one line is required");
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftHudLineGenerator.Builder().withGuiWidth(0));
    }

    /** Every configuration knob must reach the render cache key. */
    @Test
    void distinctConfigurationsProduceDistinctCacheKeys() {
        MinecraftHudLineGenerator base = new MinecraftHudLineGenerator.Builder()
            .withLine(TitleRun.of("Boss")).build();
        MinecraftHudLineGenerator otherWidth = new MinecraftHudLineGenerator.Builder()
            .withLine(TitleRun.of("Boss")).withGuiWidth(321).build();
        MinecraftHudLineGenerator noShadow = new MinecraftHudLineGenerator.Builder()
            .withLine(TitleRun.of("Boss")).withTextShadow(false).build();
        MinecraftHudLineGenerator extraLine = new MinecraftHudLineGenerator.Builder()
            .withLine(TitleRun.of("Boss")).withLine(TitleRun.of("Boss")).build();

        assertNotEquals(GeneratorCacheKey.fromGenerator(base), GeneratorCacheKey.fromGenerator(otherWidth));
        assertNotEquals(GeneratorCacheKey.fromGenerator(base), GeneratorCacheKey.fromGenerator(noShadow));
        assertNotEquals(GeneratorCacheKey.fromGenerator(base), GeneratorCacheKey.fromGenerator(extraLine));
    }

    private static boolean containsColor(BufferedImage image, int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == argb) {
                    return true;
                }
            }
        }
        return false;
    }
}
