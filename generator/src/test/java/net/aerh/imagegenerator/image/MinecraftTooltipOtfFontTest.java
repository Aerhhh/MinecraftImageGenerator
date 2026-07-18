package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont;
import net.aerh.imagegenerator.testsupport.MinimalTrueTypeFont.Glyph;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end rendering of TTF (OtfGlyph) pack glyphs through {@link MinecraftTooltip}, against a
 * synthetic pack that SHIPS a runtime-built pixel TTF ({@link FixturePacks#writeOtfFontPack}).
 *
 * <p>Fixed geometry at scale factor 1 (pixelSize 2, borderless): the text origin is 5 GUI px
 * (canvas x 10) and the line top is 5 GUI px. The fixture's 'A' rasterizes to a 4x6 GUI-px box on
 * the baseline (ascent 6, advance 5), so its cell tops out at {@code 5 + 7 - 6 = 6} GUI px (canvas
 * y 12) down to the baseline at 7 GUI px (canvas y 24) - canvas rows 12..23, columns 10..17. 'B'
 * rasterizes to a 2x4 box advancing 3.
 */
class MinecraftTooltipOtfFontTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int START = 10;

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    private static byte[] pixelTtf() {
        LinkedHashMap<Integer, Glyph> glyphs = new LinkedHashMap<>();
        glyphs.put((int) 'A', Glyph.box(20, 0, 0, 16, 24));
        glyphs.put((int) 'B', Glyph.box(12, 0, 0, 8, 16));
        // 'W' advances only 8 device px (2 GUI px) but its ink is 16 device px (4 GUI px) wide, so
        // its drawn cell overhangs the advance - the case advance-only measurement clipped.
        glyphs.put((int) 'W', Glyph.box(8, 0, 0, 16, 24));
        return MinimalTrueTypeFont.build(32, glyphs);
    }

    @BeforeEach
    void setUp() {
        repository = new PackRepository();
        FixturePacks.writeOtfFontPack(packDir, pixelTtf());
        packId = repository.register("test:otf", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftTooltip.Builder baseTooltip() {
        return MinecraftTooltip.builder()
            .setRenderBorder(false)
            .withAlpha(0)
            .hasFirstLinePadding(false)
            .withPackFontSource(fontId -> repository.resolveFont(packId, fontId));
    }

    private static ColorSegment.Builder segment(String text) {
        return ColorSegment.builder().withText(text).withColor(ChatColor.Legacy.WHITE).withPackFontId("testpack:otf");
    }

    private BufferedImage render(ColorSegment segment) {
        return baseTooltip().withSegments(segment).build().render().getImage();
    }

    private static void assertSolidBox(BufferedImage image, int x0, int x1, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                assertEquals(WHITE, image.getRGB(x, y), "box pixel (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void singleTtfGlyphRendersRealBoxAndSizesCanvasToItsAdvance() {
        BufferedImage image = render(segment("A").build());
        // The 4x6 GUI-px box: columns 10..17, rows 12..23 (baseline at 24).
        assertSolidBox(image, START, START + 7, 12, 23);
        assertEquals(0, image.getRGB(START + 8, 12), "nothing past the cell's 4 GUI px width");
        assertEquals(0, image.getRGB(START, 11), "nothing above the cell");
        // measure==draw: the single advance-5 glyph sizes the line to 2 * origin + 5 GUI px * 2.
        assertEquals(2 * START + 5 * 2, image.getWidth(), "canvas width is the drawn advance extent");
    }

    @Test
    void multipleTtfGlyphsAdvanceThroughTheSharedPath() {
        BufferedImage image = render(segment("AB").build());
        // 'A' box at 10..17; 'B' (2x4 box, advance 3) starts a full 5 GUI px (10 canvas px) later.
        assertSolidBox(image, START, START + 7, 12, 23);
        assertSolidBox(image, START + 10, START + 13, 16, 23);
        // measure==draw: advances 5 + 3 = 8 GUI px => 2 * origin + 16 canvas px.
        assertEquals(2 * START + 8 * 2, image.getWidth(), "canvas width matches the summed advances");
    }

    @Test
    void ttfGlyphInkWiderThanAdvanceIsFoldedIntoTheMeasuredExtent() {
        // measure==draw: 'W' advances 2 GUI px but draws a 4 GUI px wide cell. A tight canvas from
        // measureLineExtents must cover the drawn ink (4 GUI px * pixelSize 2 = 8), not the
        // advance (2 GUI px * 2 = 4), or the two right columns clip.
        MinecraftTooltip tooltip = baseTooltip().withSegments(segment("W").build()).build();
        MinecraftTooltip.LineExtents extents = tooltip.measureLineExtents(0);
        assertEquals(8, (int) Math.ceil(extents.maxX()), "measured extent covers the wider ink, not the advance");
    }

    @Test
    void ttfGlyphInkWiderThanAdvanceRendersWithoutClippingOnATightCanvas() {
        // Draw 'W' onto a canvas sized exactly to the measured extents and confirm the full 4 GUI
        // px (8 canvas px) box is present - proof the fold prevents the right-edge clip.
        MinecraftTooltip tooltip = baseTooltip().withSegments(segment("W").build()).build();
        MinecraftTooltip.LineExtents extents = tooltip.measureLineExtents(0);
        int originX = (int) Math.ceil(-Math.min(0, extents.minX()));
        int topY = (int) Math.ceil(-extents.artTop());
        int width = (int) Math.ceil(extents.maxX()) + originX;
        int height = (int) Math.ceil(extents.artBottom() - extents.artTop());
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            tooltip.drawLineOnto(graphics, 0, originX, topY);
        } finally {
            graphics.dispose();
        }
        // The 4x6 GUI-px box at pixelSize 2: 8 canvas px wide. The rightmost drawn column must be
        // inked, which the advance-only measurement (4 px wide) would have cropped away.
        assertEquals(WHITE, canvas.getRGB(7, height / 2), "rightmost ink column present, not clipped");
    }

    @Test
    void codepointsTheTtfCannotDisplayFallBackToBuiltInIdentically() {
        // 'Z' is not in the ttf, so minecraft:default resolves but supplies no glyph: the codepoint
        // must render exactly like the no-pack path (byte-identical).
        ColorSegment plain = ColorSegment.builder().withText("Zz").withColor(ChatColor.Legacy.WHITE).build();
        BufferedImage withPack = render(plain);
        BufferedImage withoutPack = MinecraftTooltip.builder()
            .setRenderBorder(false)
            .withAlpha(0)
            .hasFirstLinePadding(false)
            .withSegments(ColorSegment.builder().withText("Zz").withColor(ChatColor.Legacy.WHITE).build())
            .build().render().getImage();
        assertEquals(withoutPack.getWidth(), withPack.getWidth(), "width");
        assertEquals(withoutPack.getHeight(), withPack.getHeight(), "height");
        for (int y = 0; y < withoutPack.getHeight(); y++) {
            for (int x = 0; x < withoutPack.getWidth(); x++) {
                assertEquals(withoutPack.getRGB(x, y), withPack.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }
}
