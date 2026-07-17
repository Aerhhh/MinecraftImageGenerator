package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.pack.GuiScaling;
import net.aerh.imagegenerator.pack.GuiSprite;
import net.aerh.imagegenerator.pack.TooltipSprites;
import net.aerh.imagegenerator.text.ChatFormat;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins themed tooltip content placement against the resolved style's nine-slice frame borders.
 * A themed frame that declares per-side nine-slice borders insets the content to clear those
 * borders (the Wynncraft-class legendary frame declares a thicker top/bottom border than
 * left/right, and vanilla places content relative to those declarations); a stretched frame, or
 * a border thinner than the historical themed padding, keeps the symmetric base inset so existing
 * themed goldens stay byte-identical.
 *
 * <p>Frames are fully transparent so the solid stretched background shows behind the white text,
 * making the content's top-left position directly measurable. The canvas grows by exactly the
 * per-side inset the border adds, so the deltas below isolate the inset math from glyph metrics.
 */
class MinecraftTooltipThemedInsetTest {

    private static final int PIXEL_SIZE = 2; // scaleFactor 1
    private static final int BASE_INSET_GUI = 12;
    private static final int BACKGROUND = 0xFF203040;
    private static final int WHITE = 0xFFFFFFFF;

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    /** A fully transparent frame texture at least as large as the nine-slice it declares. */
    private static BufferedImage transparent(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static TooltipSprites sprites(GuiScaling frameScaling, int frameW, int frameH) {
        GuiSprite background = new GuiSprite(solid(4, 4, BACKGROUND), new GuiScaling.Stretch());
        GuiSprite frame = new GuiSprite(transparent(frameW, frameH), frameScaling);
        return new TooltipSprites(background, frame);
    }

    private static BufferedImage renderThemed(GuiScaling frameScaling, int frameW, int frameH) {
        return MinecraftTooltip.builder()
            .setRenderBorder(true)
            .withThemeSprites(sprites(frameScaling, frameW, frameH))
            .withSegments(ColorSegment.builder().withText("Themed").withColor(ChatFormat.WHITE).build())
            .build()
            .render()
            .getImage();
    }

    private static GuiScaling.NineSlice nineSlice(int left, int top, int right, int bottom, int width, int height) {
        return new GuiScaling.NineSlice(width, height,
            new GuiScaling.NineSlice.Border(left, top, right, bottom), false);
    }

    /** Row of the first white text pixel - the content's drawn top. */
    private static int firstTextRow(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == WHITE) {
                    return y;
                }
            }
        }
        throw new AssertionError("no white text pixel found");
    }

    private static void assertPixelsEqual(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth(), "width");
        assertEquals(expected.getHeight(), actual.getHeight(), "height");
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void stretchedFrameKeepsTheSymmetricBaseInset() {
        BufferedImage stretched = renderThemed(new GuiScaling.Stretch(), 4, 4);
        // A symmetric nine-slice border equal to the base inset must render identically - it
        // clears the same amount the stretched fallback already reserved.
        BufferedImage nineSliceAtBase = renderThemed(
            nineSlice(BASE_INSET_GUI, BASE_INSET_GUI, BASE_INSET_GUI, BASE_INSET_GUI, 48, 48), 48, 48);
        assertPixelsEqual(stretched, nineSliceAtBase);
    }

    @Test
    void borderThinnerThanTheBaseFloorsToTheBaseInset() {
        BufferedImage stretched = renderThemed(new GuiScaling.Stretch(), 4, 4);
        // Borders below the base padding still leave the vanilla-equivalent gap: byte-identical.
        BufferedImage thinBorder = renderThemed(nineSlice(4, 4, 4, 4, 16, 16), 16, 16);
        assertPixelsEqual(stretched, thinBorder);
    }

    @Test
    void thickerTopBottomBorderInsetsContentVerticallyLikeTheLegendaryFrame() {
        BufferedImage base = renderThemed(new GuiScaling.Stretch(), 4, 4);
        // The reference legendary frame shape: left/right 12 (= base), top/bottom 20.
        BufferedImage legendaryShape = renderThemed(nineSlice(12, 20, 12, 20, 48, 48), 48, 48);

        // Left/right borders equal the base, so the width is unchanged.
        assertEquals(base.getWidth(), legendaryShape.getWidth(), "left/right at base leaves width unchanged");
        // Top and bottom each grow by (20 - 12) GUI px.
        int expectedHeightDelta = 2 * (20 - BASE_INSET_GUI) * PIXEL_SIZE;
        assertEquals(base.getHeight() + expectedHeightDelta, legendaryShape.getHeight(),
            "top and bottom borders above the base each enlarge the canvas");

        // The content itself moves down by the extra top inset, not merely the canvas.
        int expectedTopShift = (20 - BASE_INSET_GUI) * PIXEL_SIZE;
        assertEquals(firstTextRow(base) + expectedTopShift, firstTextRow(legendaryShape),
            "content drops by the extra top border");
        assertNotEquals(base.getHeight(), legendaryShape.getHeight(), "the legendary shape must differ from the base");
    }

    @Test
    void asymmetricBordersInsetEachSideIndependently() {
        BufferedImage base = renderThemed(new GuiScaling.Stretch(), 4, 4);
        // Distinct value per side catches any left/top/right/bottom transposition; all above base.
        int left = 16;
        int top = 20;
        int right = 24;
        int bottom = 30;
        BufferedImage asymmetric = renderThemed(nineSlice(left, top, right, bottom, 64, 80), 64, 80);

        int expectedWidthDelta = ((left - BASE_INSET_GUI) + (right - BASE_INSET_GUI)) * PIXEL_SIZE;
        int expectedHeightDelta = ((top - BASE_INSET_GUI) + (bottom - BASE_INSET_GUI)) * PIXEL_SIZE;
        assertEquals(base.getWidth() + expectedWidthDelta, asymmetric.getWidth(),
            "width grows by the left plus right borders above the base");
        assertEquals(base.getHeight() + expectedHeightDelta, asymmetric.getHeight(),
            "height grows by the top plus bottom borders above the base");

        int expectedLeftShift = (left - BASE_INSET_GUI) * PIXEL_SIZE;
        int expectedTopShift = (top - BASE_INSET_GUI) * PIXEL_SIZE;
        assertEquals(firstTextRow(base) + expectedTopShift, firstTextRow(asymmetric),
            "content drops by the extra top border");
        // The left shift moves the first white pixel's column right by the extra left border.
        assertEquals(firstTextColumn(base) + expectedLeftShift, firstTextColumn(asymmetric),
            "content moves right by the extra left border");
    }

    /** Column of the first white text pixel - the content's drawn left edge. */
    private static int firstTextColumn(BufferedImage image) {
        int best = Integer.MAX_VALUE;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == WHITE) {
                    best = Math.min(best, x);
                    break;
                }
            }
        }
        if (best == Integer.MAX_VALUE) {
            throw new AssertionError("no white text pixel found");
        }
        return best;
    }
}
