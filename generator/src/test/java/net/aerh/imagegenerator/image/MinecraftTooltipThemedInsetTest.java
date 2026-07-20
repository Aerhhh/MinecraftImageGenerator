package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.pack.GuiScaling;
import net.aerh.imagegenerator.pack.GuiSprite;
import net.aerh.imagegenerator.pack.TooltipSprites;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins themed tooltip content placement to the fixed vanilla inset (content padding 3 plus
 * sprite margin 9 GUI px per side). A frame's nine-slice border declaration governs how the
 * frame art slices across the tooltip rect and must never move the content or grow the canvas:
 * production packs declare large borders purely to keep ornate corner art unscaled, and vanilla
 * draws the sprites over the same fixed rect regardless of those declarations.
 *
 * <p>Frames are fully transparent so the solid stretched background shows behind the white text,
 * making any content displacement directly measurable against the stretched-frame baseline.
 */
class MinecraftTooltipThemedInsetTest {

    private static final int BACKGROUND = 0xFF203040;

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
            .withSegments(ColorSegment.builder().withText("Themed").withColor(ChatColor.Legacy.WHITE).build())
            .build()
            .render()
            .getImage();
    }

    private static GuiScaling.NineSlice nineSlice(int left, int top, int right, int bottom, int width, int height) {
        return new GuiScaling.NineSlice(width, height,
            new GuiScaling.NineSlice.Border(left, top, right, bottom), false);
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
    void thinNineSliceBorderRendersIdenticallyToAStretchedFrame() {
        BufferedImage stretched = renderThemed(new GuiScaling.Stretch(), 4, 4);
        BufferedImage thinBorder = renderThemed(nineSlice(4, 4, 4, 4, 16, 16), 16, 16);
        assertPixelsEqual(stretched, thinBorder);
    }

    @Test
    void thickUniformBorderLikeTheProductionFramesDoesNotMoveContent() {
        BufferedImage stretched = renderThemed(new GuiScaling.Stretch(), 4, 4);
        // The production rarity frames declare border 24 on every side (100x100 sprites) purely
        // for corner slicing; content placement and canvas size must not change.
        BufferedImage thickBorder = renderThemed(nineSlice(24, 24, 24, 24, 100, 100), 100, 100);
        assertPixelsEqual(stretched, thickBorder);
    }

    @Test
    void asymmetricBordersDoNotMoveContentEither() {
        BufferedImage stretched = renderThemed(new GuiScaling.Stretch(), 4, 4);
        BufferedImage asymmetric = renderThemed(nineSlice(16, 20, 24, 30, 64, 80), 64, 80);
        assertPixelsEqual(stretched, asymmetric);
    }
}
