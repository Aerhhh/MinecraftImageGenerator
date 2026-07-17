package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.text.ChatFormat;
import net.aerh.imagegenerator.text.MinecraftFont;
import net.aerh.imagegenerator.text.TextColorRemap;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import net.aerh.imagegenerator.util.MinecraftFonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering coverage for pack font glyphs inside {@link MinecraftTooltip}, against the synthetic
 * {@link FixturePacks#writeTextFontPack text font pack}.
 *
 * <p>Fixed geometry at scale factor 1 (pixelSize 2, borderless): the text origin is 5 GUI px
 * (canvas x 10), the first baseline is 12 GUI px (canvas y 24), so the line top is 5 GUI px and a
 * height-3/ascent-3 glyph cell tops out at {@code 5 + 7 - 3 = 9} GUI px - canvas rows 18..23.
 * Fixture glyphs: U+E000 solid red (advance 4), U+E001 solid blue (advance 4), U+E002 green
 * (ink 2, advance 3); space advances U+E00A = -4.0, U+E00C = 0.75, U+E00D = 3.0 and
 * U+E00E = -6.0.
 */
class MinecraftTooltipPackFontTest {

    private static final String GLYPH_RED = Character.toString(0xE000);
    private static final String GLYPH_BLUE = Character.toString(0xE001);
    private static final String GLYPH_GREEN = Character.toString(0xE002);
    private static final String SPACE_MINUS_FOUR = Character.toString(0xE00A);
    private static final String SPACE_THREE_QUARTERS = Character.toString(0xE00C);
    private static final String SPACE_THREE = Character.toString(0xE00D);
    private static final String SPACE_MINUS_SIX = Character.toString(0xE00E);

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int MAGENTA = 0xFFFF00FF;
    private static final int RED_SHADOW = 0xFF3F0000;
    private static final int BLUE_SHADOW = 0xFF00003F;

    private static final int START = 10;
    private static final int GLYPH_TOP = 18;
    private static final int GLYPH_BOTTOM = 23;
    private static final int SINGLE_GLYPH_CANVAS_WIDTH = 28;
    private static final int SINGLE_LINE_CANVAS_HEIGHT = 38;

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void setUp() {
        repository = new PackRepository();
        FixturePacks.writeTextFontPack(packDir);
        packId = repository.register("test:fonts", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftTooltip.Builder baseTooltip() {
        return MinecraftTooltip.builder()
            .setRenderBorder(false)
            .withAlpha(0)
            .hasFirstLinePadding(false)
            .withPackFontSource(fontId -> repository.resolveFont(packId, fontId));
    }

    private static ColorSegment.Builder whiteSegment(String text) {
        return ColorSegment.builder().withText(text).withColor(ChatFormat.WHITE).withPackFontId("testpack:glyphs");
    }

    private BufferedImage render(ColorSegment segment) {
        return baseTooltip().withSegments(segment).build().render().getImage();
    }

    private static void assertRect(BufferedImage image, int x0, int x1, int y0, int y1, int argb, String what) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                assertEquals(argb, image.getRGB(x, y), what + " at (" + x + "," + y + ")");
            }
        }
    }

    private static void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth(), "width");
        assertEquals(expected.getHeight(), actual.getHeight(), "height");
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    /** AWT advance of a string under the built-in default font at scale factor 1. */
    private static int builtInWidth(String text) {
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = dummy.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            Font font = MinecraftFonts.getFont(MinecraftFont.DEFAULT, false, false);
            FontMetrics metrics = graphics.getFontMetrics(font);
            return metrics.stringWidth(text);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void singleGlyphDrawsCellQuarteredShadowAndSizesCanvas() {
        BufferedImage image = render(whiteSegment(GLYPH_RED).build());

        assertRect(image, START, START + 5, GLYPH_TOP, GLYPH_BOTTOM, RED, "main glyph");
        // Shadow sits +1,+1 GUI px (+2,+2 canvas); visible only where the main cell doesn't cover.
        assertEquals(RED_SHADOW, image.getRGB(16, 20), "shadow right of the cell");
        assertEquals(RED_SHADOW, image.getRGB(13, 24), "shadow below the cell");
        assertEquals(0, image.getRGB(START - 1, GLYPH_TOP), "nothing left of the cell");
        assertEquals(0, image.getRGB(START, GLYPH_TOP - 1), "nothing above the cell");
        assertEquals(SINGLE_GLYPH_CANVAS_WIDTH, image.getWidth(), "canvas covers the 4 GUI px advance");
        assertEquals(SINGLE_LINE_CANVAS_HEIGHT, image.getHeight());
    }

    @Test
    void shadowDisabledSegmentDrawsGlyphWithoutItsDropShadow() {
        // shadow_color alpha 0 (shadowEnabled=false) suppresses only the shadow pass; the glyph
        // cell itself draws identically to the shadowed case.
        BufferedImage image = render(whiteSegment(GLYPH_RED).withShadowEnabled(false).build());

        assertRect(image, START, START + 5, GLYPH_TOP, GLYPH_BOTTOM, RED, "glyph cell still drawn");
        assertEquals(0, image.getRGB(16, 20), "no shadow to the right of the cell");
        assertEquals(0, image.getRGB(13, 24), "no shadow below the cell");
    }

    @Test
    void glyphBetweenAsciiCharactersUsesThePackAdvance() {
        int widthA = builtInWidth("A");
        int widthB = builtInWidth("B");
        BufferedImage image = render(whiteSegment("A" + GLYPH_RED + "B").build());

        // The glyph starts right after 'A' and 'B' starts a full 4 GUI px (8 canvas px) later.
        assertRect(image, START + widthA, START + widthA + 5, GLYPH_TOP, GLYPH_BOTTOM, RED, "glyph after 'A'");
        assertEquals(20 + widthA + 8 + widthB, image.getWidth(),
            "line width = AWT('A') + pack advance + AWT('B')");
    }

    @Test
    void negativeAdvanceOverlapsAndLaterGlyphOverdrawsEarlier() {
        BufferedImage image = render(whiteSegment(GLYPH_RED + SPACE_MINUS_FOUR + GLYPH_BLUE).build());

        // The -4.0 space advance returns the cursor to the line start, so blue lands exactly on
        // red; blue is drawn later and wins every overlapping pixel.
        assertRect(image, START, START + 5, GLYPH_TOP, GLYPH_BOTTOM, BLUE, "blue overdraws red");
        assertEquals(BLUE_SHADOW, image.getRGB(16, 20), "blue shadow overdraws red's too");
        assertEquals(SINGLE_GLYPH_CANVAS_WIDTH, image.getWidth(),
            "canvas covers the max extent even though the cursor doubled back");
    }

    @Test
    void trailingNegativeAdvanceKeepsCanvasAtMaxExtent() {
        BufferedImage image = render(whiteSegment(GLYPH_RED + SPACE_MINUS_FOUR).build());

        assertRect(image, START, START + 5, GLYPH_TOP, GLYPH_BOTTOM, RED, "glyph art");
        assertEquals(SINGLE_GLYPH_CANVAS_WIDTH, image.getWidth(),
            "line width is the max extent (4 GUI px), not the final cursor position (0)");
    }

    @Test
    void boldGlyphDrawsTwiceAndAdvancesOneExtra() {
        BufferedImage image = render(whiteSegment(GLYPH_RED).isBold().build());

        // Main pass draws at x and x+1 GUI px, merging into an 8-canvas-px-wide block.
        assertRect(image, START, START + 7, GLYPH_TOP, GLYPH_BOTTOM, RED, "bold double draw");
        assertEquals(RED_SHADOW, image.getRGB(18, 20), "bold shadow copy extends further right");
        assertEquals(RED_SHADOW, image.getRGB(19, 25), "bold shadow copy corner");
        assertEquals(SINGLE_GLYPH_CANVAS_WIDTH + 2, image.getWidth(), "advance gains exactly +1 GUI px");
    }

    @Test
    void italicGlyphShearsPerGuiRow() {
        BufferedImage image = render(whiteSegment(GLYPH_RED).isItalic().build());

        // Rows sit 4, 5 and 6 GUI px below the line top: shear 0, -0.25 and -0.5 GUI px, which
        // at pixelSize 2 rounds to x offsets 0, 0 and -1 canvas px.
        assertEquals(RED, image.getRGB(START, GLYPH_TOP), "top row unsheared");
        assertEquals(0, image.getRGB(START - 1, GLYPH_TOP), "top row not shifted left");
        assertEquals(RED, image.getRGB(START - 1, 22), "bottom row sheared 1 canvas px left");
        assertEquals(0, image.getRGB(START - 2, 22), "shear is exactly one canvas px");
    }

    @Test
    void fractionalAdvancesAccumulateWithoutPerStepRounding() {
        BufferedImage image = render(whiteSegment(SPACE_THREE_QUARTERS.repeat(10) + GLYPH_RED).build());

        // Ten 0.75 GUI px advances = exactly 7.5 GUI px (15 canvas px). The cursor accumulates
        // in canvas px (advance * pixelSize = 1.5 per step), so any per-step rounding - GUI px
        // (0.75 -> 0 or 1) or canvas px (1.5 -> 1 or 2) - lands the glyph at 0, 10 or 20 canvas
        // px past the origin instead of 15.
        assertRect(image, START + 15, START + 20, GLYPH_TOP, GLYPH_BOTTOM, RED, "glyph after 7.5 GUI px");
        assertRect(image, START + 14, START + 14, GLYPH_TOP, GLYPH_BOTTOM, 0, "column before the glyph");
        assertEquals(43, image.getWidth(), "canvas covers 7.5 + 4 GUI px of text");
    }

    @Test
    void fractionalLineWidthRoundsUpForCanvasSizing() {
        BufferedImage image = render(whiteSegment(SPACE_THREE_QUARTERS).build());

        assertEquals(22, image.getWidth(), "0.75 GUI px extent (1.5 canvas px) is ceiled to 2 canvas px");
        assertRect(image, 0, image.getWidth() - 1, 0, image.getHeight() - 1, 0,
            "space glyphs draw nothing at all");
    }

    @Test
    void leadingNegativeAdvanceShiftsContentInsteadOfClipping() {
        // Line 2 leads with a -6 GUI px advance: its art starts 12 canvas px left of the line
        // start, 2 px past the 10 px text origin. The whole canvas gains a 2 px left shift so
        // the art keeps every column, both lines stay aligned, and the width covers the shift.
        BufferedImage image = baseTooltip()
            .withSegments(whiteSegment(GLYPH_RED).build())
            .withSegments(whiteSegment(SPACE_MINUS_SIX + GLYPH_BLUE).build())
            .build().render().getImage();

        assertRect(image, 0, 5, 38, 43, BLUE, "left-overdrawing art fully inside the canvas");
        assertRect(image, START + 2, START + 7, GLYPH_TOP, GLYPH_BOTTOM, RED, "other lines shift equally");
        assertEquals(SINGLE_GLYPH_CANVAS_WIDTH + 2, image.getWidth(), "canvas width covers the left shift");
    }

    @Test
    void tallGlyphClipsVerticallyWithoutGrowingTheCanvas() {
        // Wave 2 canvas height policy: the canvas stays line-height-based, so glyph art taller
        // than the line clips at the canvas edges (the Wave 3 container compositor handles
        // full-bleed art). Height 32 / ascent 16 tops out at 5 + 7 - 16 = -4 GUI px (canvas -8)
        // and bottoms out at +28 GUI px (canvas 56), beyond both edges of the 38 px canvas.
        BufferedImage image = render(
            whiteSegment(GLYPH_RED).withPackFontId("testpack:tall").build());

        assertEquals(SINGLE_LINE_CANVAS_HEIGHT, image.getHeight(), "canvas height unchanged by tall art");
        assertEquals(SINGLE_GLYPH_CANVAS_WIDTH, image.getWidth());
        assertEquals(MAGENTA, image.getRGB(START, 0), "art reaches the clipped top edge");
        assertEquals(MAGENTA, image.getRGB(START, image.getHeight() - 1), "art reaches the clipped bottom edge");
    }

    @Test
    void packGlyphsWinOverBuiltInFontsForSuppliedCodepoints() {
        // 'A' is displayable by the OTF font, but the pack's minecraft:default override supplies
        // no 'A' glyph, so it must render via OTF; the PUA codepoint must render via the pack.
        BufferedImage image = render(whiteSegment(GLYPH_RED).build());
        assertEquals(RED, image.getRGB(START, GLYPH_TOP), "pack glyph rendered from pack art");
    }

    @Test
    void unsuppliedCodepointsRenderIdenticallyToTheNoPackPath() {
        ColorSegment segment = whiteSegment("Zz!").build();
        BufferedImage withPack = render(segment);
        BufferedImage withoutPack = MinecraftTooltip.builder()
            .setRenderBorder(false)
            .withAlpha(0)
            .hasFirstLinePadding(false)
            .withSegments(whiteSegment("Zz!").build())
            .build().render().getImage();
        assertImagesEqual(withoutPack, withPack);
    }

    @Test
    void segmentWithoutFontIdResolvesThePacksDefaultFontOverride() {
        // No packFontId on the segment: the effective font id is minecraft:default, which the
        // fixture overrides with a reference to testpack:glyphs.
        ColorSegment segment = ColorSegment.builder()
            .withText(GLYPH_RED).withColor(ChatFormat.WHITE).build();
        BufferedImage image = render(segment);
        assertRect(image, START, START + 5, GLYPH_TOP, GLYPH_BOTTOM, RED, "default-font pack glyph");
    }

    @Test
    void decorationsWrapPackGlyphsLikeBuiltInRuns() {
        BufferedImage image = render(whiteSegment(GLYPH_RED).isStrikethrough().isUnderlined().build());

        // Strikethrough draws at baseline - 8 canvas px (rows 16-17), underline at +2 (rows
        // 26-27), both in the foreground color over their own drop shadows.
        assertEquals(0xFFFFFFFF, image.getRGB(START, 16), "strikethrough line");
        assertEquals(0xFFFFFFFF, image.getRGB(START - 2, 26), "underline line");
    }

    @Test
    void decorationsSpanPackSpaceGlyphAdvances() {
        // Vanilla emits underline/strikethrough quads for EVERY glyph, space-provider glyphs
        // included, so a decorated +3.0 GUI px space must not punch a hole in the lines.
        BufferedImage image = render(
            whiteSegment(GLYPH_RED + SPACE_THREE + GLYPH_BLUE).isStrikethrough().isUnderlined().build());

        // Glyph spans: red at 10..17, space at 18..23, blue at 24..31 (canvas px). Underline
        // pieces run from drawX - 2 to drawX + width + 1, so only the space's own piece covers
        // columns 18..21; strikethrough pieces run drawX to drawX + width - 1.
        assertRect(image, START - 2, 31, 26, 27, 0xFFFFFFFF, "continuous underline");
        assertRect(image, START, 31, 16, 17, 0xFFFFFFFF, "continuous strikethrough");
    }

    @Test
    void packGlyphShadowUsesThePipelineShadowColor() {
        // Gold's legacy shadow color is 0x2A2A00, not the true quartered 0x3F2A00. The glyph's
        // shadow pass must tint with the same pipeline shadow color built-in runs use, so one
        // gold segment never shows two shadow hues.
        BufferedImage image = render(ColorSegment.builder()
            .withText(GLYPH_RED).withColor(ChatFormat.GOLD).withPackFontId("testpack:glyphs").build());

        assertEquals(RED, image.getRGB(START, GLYPH_TOP), "gold tint keeps the red texture's red channel");
        assertEquals(0xFF2A0000, image.getRGB(16, 20), "shadow tints with GOLD's background color");
    }

    @Test
    void textColorRemapAppliesToPackGlyphShadows() {
        // The remap contract covers every drawn shadow, glyph textures included: the shadow
        // pass must tint with the remapped shadow color, not a quartered foreground.
        BufferedImage image = baseTooltip()
            .withTextColorRemap(TextColorRemap.builder().remap(0xFFFFFF, 0xFFFFFF, 0x123456).build())
            .withSegments(whiteSegment(GLYPH_RED).build())
            .build().render().getImage();

        assertEquals(RED, image.getRGB(START, GLYPH_TOP), "identity foreground remap keeps the art");
        assertEquals(0xFF120000, image.getRGB(16, 20), "shadow tints with the remapped shadow color");
    }

    @Test
    void obfuscatedPackGlyphsAreDeterministicPerFrame() {
        ColorSegment segment = whiteSegment(GLYPH_RED + GLYPH_BLUE + GLYPH_GREEN).isObfuscated().build();
        MinecraftTooltip first = baseTooltip().withSegments(segment).build().render();
        MinecraftTooltip second = baseTooltip().withSegments(segment).build().render();

        assertTrue(first.isAnimated(), "obfuscated segments animate");
        List<BufferedImage> firstFrames = first.getAnimationFrames();
        List<BufferedImage> secondFrames = second.getAnimationFrames();
        assertEquals(firstFrames.size(), secondFrames.size());
        for (int frame = 0; frame < firstFrames.size(); frame++) {
            assertImagesEqual(firstFrames.get(frame), secondFrames.get(frame));
        }

        // Substitution keeps ceil(advance): the first slot only ever shows the two advance-4
        // glyphs, and the lone advance-3 glyph in the third slot always substitutes itself.
        for (BufferedImage frame : firstFrames) {
            int firstSlot = frame.getRGB(START, GLYPH_TOP);
            assertTrue(firstSlot == RED || firstSlot == BLUE,
                "first slot substitutes among equal-advance glyphs, got " + Integer.toHexString(firstSlot));
            assertEquals(GREEN, frame.getRGB(START + 16, GLYPH_TOP), "third slot keeps its width class");
        }
    }
}
