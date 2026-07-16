package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackAnimationTest {

    /** A 16x48 vertical flipbook: frame 0 red, frame 1 green, frame 2 blue. */
    private static BufferedImage flipbook() {
        BufferedImage sheet = new BufferedImage(16, 48, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
        for (int frame = 0; frame < 3; frame++) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    sheet.setRGB(x, frame * 16 + y, colors[frame]);
                }
            }
        }
        return sheet;
    }

    /**
     * A 32x32 grid sheet: four 16x16 quadrants - top-left red, top-right green, bottom-left
     * blue, bottom-right yellow - so a row-major crop is distinguishable from a column-0-only
     * crop at every index.
     */
    private static BufferedImage grid() {
        BufferedImage sheet = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        fillCell(sheet, 0, 0, 0xFFFF0000);
        fillCell(sheet, 16, 0, 0xFF00FF00);
        fillCell(sheet, 0, 16, 0xFF0000FF);
        fillCell(sheet, 16, 16, 0xFFFFFF00);
        return sheet;
    }

    /** A 48x16 single-row strip: three 16x16 frames - red, green, blue - left to right. */
    private static BufferedImage horizontalStrip() {
        BufferedImage sheet = new BufferedImage(48, 16, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
        for (int frame = 0; frame < 3; frame++) {
            fillCell(sheet, frame * 16, 0, colors[frame]);
        }
        return sheet;
    }

    private static void fillCell(BufferedImage sheet, int x0, int y0, int argb) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                sheet.setRGB(x0 + x, y0 + y, argb);
            }
        }
    }

    private static AnimationMeta meta(String json) {
        return PackJsonParser.parseAnimationMeta(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void absentFramesListPlaysEveryFlipbookFrameTopToBottom() {
        PackAnimation animation = PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"frametime":3}}""")).orElseThrow();

        assertEquals(3, animation.frames().size());
        assertEquals(List.of(3, 3, 3), animation.frameTicks());
        assertEquals(9, animation.cycleTicks());
        assertEquals(16, animation.frameWidth());
        assertEquals(16, animation.frameHeight());
        assertEquals(0xFFFF0000, animation.frameImage(0).getRGB(8, 8));
        assertEquals(0xFF00FF00, animation.frameImage(1).getRGB(8, 8));
        assertEquals(0xFF0000FF, animation.frameImage(2).getRGB(8, 8));
    }

    @Test
    void explicitFramesListReordersAndTimesFrames() {
        PackAnimation animation = PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"frametime":2,"frames":[2,0,{"index":1,"time":7}]}}""")).orElseThrow();

        assertEquals(List.of(2, 2, 7), animation.frameTicks());
        assertEquals(11, animation.cycleTicks());
        assertEquals(0xFF0000FF, animation.frameImage(0).getRGB(0, 0), "position 0 shows flipbook frame 2");
        assertEquals(0xFFFF0000, animation.frameImage(1).getRGB(0, 0));
        assertEquals(0xFF00FF00, animation.frameImage(2).getRGB(0, 0));
    }

    @Test
    void repeatedFlipbookIndicesShareOneMemoizedCrop() {
        PackAnimation animation = PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"frames":[0,1,{"index":0,"time":100}]}}""")).orElseThrow();

        assertSame(animation.frameImage(0), animation.frameImage(2),
            "positions 0 and 2 both show flipbook frame 0");
    }

    @Test
    void interpolateFlagIsParsedAndCarried() {
        PackAnimation animation = PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"interpolate":true}}""")).orElseThrow();
        assertTrue(animation.interpolate());

        PackAnimation plain = PackAnimation.resolve(flipbook(), meta("""
            {"animation":{}}""")).orElseThrow();
        assertFalse(plain.interpolate());
    }

    @Test
    void frameSizeOverridesChangeTheCropAndCount() {
        PackAnimation animation = PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"width":16,"height":24}}""")).orElseThrow();

        assertEquals(2, animation.frames().size(), "48 rows cut into 24-row frames");
        assertEquals(24, animation.frameHeight());
        assertEquals(0xFFFF0000, animation.frameImage(0).getRGB(0, 0));
        assertEquals(0xFF0000FF, animation.frameImage(1).getRGB(0, 23));
    }

    @Test
    void singleFrameAnimationsResolveEmpty() {
        assertEquals(Optional.empty(), PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"frames":[1]}}""")));
        assertEquals(Optional.empty(), PackAnimation.resolve(
            FixturePacks.solid(16, 16, 0xFF123456), meta("""
                {"animation":{"frametime":5}}""")), "a 16x16 sheet holds a single frame");
    }

    @Test
    void outOfBoundsFrameIndexFailsLoudly() {
        assertThrows(PackLoadException.class, () -> PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"frames":[0,99]}}""")));
        assertThrows(PackLoadException.class, () -> PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"frames":[0,-1]}}""")));
    }

    @Test
    void oversizedFrameSizeFailsLoudly() {
        assertThrows(PackLoadException.class, () -> PackAnimation.resolve(flipbook(), meta("""
            {"animation":{"width":32}}""")));
    }

    @Test
    void multiColumnGridPlaysFramesRowMajor() {
        // A 32x32 sheet with 16x16 frames is a 2x2 grid; vanilla plays (0,0), (16,0), (0,16),
        // (16,16). A column-0-only crop would only ever reach the left quadrants.
        PackAnimation animation = PackAnimation.resolve(grid(), meta("""
            {"animation":{"width":16,"height":16}}""")).orElseThrow();

        assertEquals(4, animation.frames().size(), "2 columns x 2 rows");
        assertEquals(16, animation.frameWidth());
        assertEquals(16, animation.frameHeight());
        assertEquals(0xFFFF0000, animation.frameImage(0).getRGB(8, 8), "frame 0 top-left");
        assertEquals(0xFF00FF00, animation.frameImage(1).getRGB(8, 8), "frame 1 top-right");
        assertEquals(0xFF0000FF, animation.frameImage(2).getRGB(8, 8), "frame 2 bottom-left");
        assertEquals(0xFFFFFF00, animation.frameImage(3).getRGB(8, 8), "frame 3 bottom-right");
    }

    @Test
    void gridFramesListReachesEveryColumn() {
        // An explicit frames list referencing grid indices 2 and 3 (column 1 and row 1) resolves
        // instead of throwing, and crops the correct grid cells.
        PackAnimation animation = PackAnimation.resolve(grid(), meta("""
            {"animation":{"width":16,"height":16,"frames":[3,1]}}""")).orElseThrow();

        assertEquals(2, animation.frames().size());
        assertEquals(0xFFFFFF00, animation.frameImage(0).getRGB(8, 8), "position 0 shows grid frame 3 (bottom-right)");
        assertEquals(0xFF00FF00, animation.frameImage(1).getRGB(8, 8), "position 1 shows grid frame 1 (top-right)");
    }

    @Test
    void singleRowStripPlaysFramesLeftToRight() {
        PackAnimation animation = PackAnimation.resolve(horizontalStrip(), meta("""
            {"animation":{"width":16,"height":16}}""")).orElseThrow();

        assertEquals(3, animation.frames().size(), "3 columns x 1 row");
        assertEquals(0xFFFF0000, animation.frameImage(0).getRGB(8, 8));
        assertEquals(0xFF00FF00, animation.frameImage(1).getRGB(8, 8));
        assertEquals(0xFF0000FF, animation.frameImage(2).getRGB(8, 8));
    }
}
