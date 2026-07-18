package net.aerh.imagegenerator.impl.scene;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.testsupport.GifBytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the template-free scene arrangements: row, column, and grid placement geometry,
 * alignment, spacing and margin overrides, pre-rendered and nested members, and the shared-tick
 * timeline compositing of mixed static, uniform-delay, and tick-timed members.
 */
@DisplayName("Scene generator arrangements")
class MinecraftSceneGeneratorTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int YELLOW = 0xFFFFFF00;
    private static final byte[] DUMMY_GIF = {0x47, 0x49, 0x46};

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(argb, true));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static GeneratedObject staticObject(int width, int height, int argb) {
        return new GeneratedObject(solid(width, height, argb));
    }

    private static GeneratedObject uniformAnimation(int delayMs, int width, int height, int... frameColors) {
        return new GeneratedObject(DUMMY_GIF, solidFrames(width, height, frameColors), delayMs);
    }

    private static GeneratedObject tickTimedAnimation(List<Integer> delaysMs, int width, int height, int... frameColors) {
        return new GeneratedObject(DUMMY_GIF, solidFrames(width, height, frameColors), delaysMs);
    }

    private static List<BufferedImage> solidFrames(int width, int height, int... frameColors) {
        return Arrays.stream(frameColors)
            .mapToObj(color -> solid(width, height, color))
            .toList();
    }

    @Test
    @DisplayName("a row lays members left to right with default spacing, margin, and vertical centering")
    void rowGeometry() {
        GeneratedObject result = MinecraftSceneGenerator.row()
            .add(staticObject(10, 20, RED))
            .add(staticObject(30, 40, BLUE))
            .build()
            .render(null);

        assertFalse(result.isAnimated());
        BufferedImage image = result.getImage();
        assertEquals(15 + 10 + 25 + 30 + 15, image.getWidth());
        assertEquals(15 + 40 + 15, image.getHeight());
        // A is 20 px tall in a 40 px row, centered: y = 15 + (40 - 20) / 2 = 25
        assertEquals(RED, image.getRGB(15, 25));
        assertEquals(RED, image.getRGB(15 + 9, 25 + 19));
        assertEquals(BLUE, image.getRGB(15 + 10 + 25, 15));
        assertEquals(BLUE, image.getRGB(15 + 10 + 25 + 29, 15 + 39));
        // corners stay transparent
        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0, image.getRGB(image.getWidth() - 1, image.getHeight() - 1) >>> 24);
    }

    @Test
    @DisplayName("a column stacks members top to bottom with horizontal centering")
    void columnGeometry() {
        GeneratedObject result = MinecraftSceneGenerator.column()
            .add(staticObject(10, 20, RED))
            .add(staticObject(30, 40, BLUE))
            .build()
            .render(null);

        BufferedImage image = result.getImage();
        assertEquals(15 + 30 + 15, image.getWidth());
        assertEquals(15 + 20 + 25 + 40 + 15, image.getHeight());
        // A is 10 px wide in a 30 px column, centered: x = 15 + (30 - 10) / 2 = 25
        assertEquals(RED, image.getRGB(25, 15));
        assertEquals(BLUE, image.getRGB(15, 15 + 20 + 25));
    }

    @Test
    @DisplayName("a grid fills row-major with per-column widths and per-row heights")
    void gridGeometry() {
        GeneratedObject result = MinecraftSceneGenerator.grid(2)
            .withAlignment(MinecraftSceneGenerator.Alignment.START)
            .add(staticObject(10, 10, RED))
            .add(staticObject(20, 10, GREEN))
            .add(staticObject(10, 30, BLUE))
            .build()
            .render(null);

        BufferedImage image = result.getImage();
        // columns: max(10, 10) = 10 and 20 wide; rows: 10 and 30 tall
        assertEquals(15 + 10 + 25 + 20 + 15, image.getWidth());
        assertEquals(15 + 10 + 25 + 30 + 15, image.getHeight());
        assertEquals(RED, image.getRGB(15, 15));
        assertEquals(GREEN, image.getRGB(15 + 10 + 25, 15));
        // C wraps to the second row, first column
        assertEquals(BLUE, image.getRGB(15, 15 + 10 + 25));
    }

    @Test
    @DisplayName("START and END alignment pin members to opposite cell edges")
    void alignmentVariants() {
        BufferedImage start = MinecraftSceneGenerator.row()
            .withAlignment(MinecraftSceneGenerator.Alignment.START)
            .add(staticObject(10, 20, RED))
            .add(staticObject(10, 40, BLUE))
            .build()
            .render(null)
            .getImage();
        assertEquals(RED, start.getRGB(15, 15));
        assertEquals(0, start.getRGB(15, 15 + 39) >>> 24);

        BufferedImage end = MinecraftSceneGenerator.row()
            .withAlignment(MinecraftSceneGenerator.Alignment.END)
            .add(staticObject(10, 20, RED))
            .add(staticObject(10, 40, BLUE))
            .build()
            .render(null)
            .getImage();
        assertEquals(RED, end.getRGB(15, 15 + 20));
        assertEquals(0, end.getRGB(15, 15) >>> 24);
    }

    @Test
    @DisplayName("spacing and margin overrides apply exactly")
    void spacingAndMarginOverrides() {
        BufferedImage image = MinecraftSceneGenerator.row()
            .withSpacing(0)
            .withMargin(0)
            .add(staticObject(10, 10, RED))
            .add(staticObject(10, 10, BLUE))
            .build()
            .render(null)
            .getImage();

        assertEquals(20, image.getWidth());
        assertEquals(10, image.getHeight());
        assertEquals(RED, image.getRGB(0, 0));
        assertEquals(BLUE, image.getRGB(10, 0));
    }

    @Test
    @DisplayName("scenes nest: a scene is a member like any other generator")
    void nestedScene() {
        MinecraftSceneGenerator inner = MinecraftSceneGenerator.row()
            .withSpacing(0)
            .withMargin(0)
            .add(staticObject(10, 10, RED))
            .add(staticObject(10, 10, GREEN))
            .build();

        BufferedImage image = MinecraftSceneGenerator.column()
            .withSpacing(0)
            .withMargin(0)
            .add(inner)
            .add(staticObject(20, 10, BLUE))
            .build()
            .render(null)
            .getImage();

        assertEquals(20, image.getWidth());
        assertEquals(20, image.getHeight());
        assertEquals(RED, image.getRGB(0, 0));
        assertEquals(GREEN, image.getRGB(10, 0));
        assertEquals(BLUE, image.getRGB(0, 10));
    }

    @Test
    @DisplayName("a tick-timed member keeps its authored per-frame delays and statics draw every step")
    void tickTimedMemberKeepsDelays() {
        GeneratedObject result = MinecraftSceneGenerator.row()
            .withSpacing(0)
            .withMargin(0)
            .add(tickTimedAnimation(List.of(100, 300), 10, 10, RED, GREEN))
            .add(staticObject(10, 10, BLUE))
            .build()
            .render(null);

        assertTrue(result.isAnimated());
        assertEquals(List.of(100, 300), result.getFrameDelaysMs());
        List<BufferedImage> frames = result.getAnimationFrames();
        assertEquals(2, frames.size());
        assertEquals(RED, frames.get(0).getRGB(0, 0));
        assertEquals(GREEN, frames.get(1).getRGB(0, 0));
        // the static member draws in every step
        assertEquals(BLUE, frames.get(0).getRGB(10, 0));
        assertEquals(BLUE, frames.get(1).getRGB(10, 0));
        // the encoded GIF carries the honest delays (1 tick = 5 centiseconds)
        assertEquals(List.of(10, 30), GifBytes.frameDelaysCentiseconds(result.getGifData()));
    }

    @Test
    @DisplayName("uniform members with different cycle lengths compose on their LCM timeline")
    void uniformMembersComposeOnLcmTimeline() {
        GeneratedObject result = MinecraftSceneGenerator.row()
            .withSpacing(0)
            .withMargin(0)
            .add(uniformAnimation(100, 10, 10, RED, GREEN))
            .add(uniformAnimation(100, 10, 10, BLUE, YELLOW, RED))
            .build()
            .render(null);

        assertTrue(result.isAnimated());
        // cycles of 4 and 6 ticks share a 12-tick timeline: 6 steps of 100 ms
        List<BufferedImage> frames = result.getAnimationFrames();
        assertEquals(6, frames.size());
        assertEquals(List.of(100, 100, 100, 100, 100, 100), result.getFrameDelaysMs());
        // step 4 covers ticks 8..10: source A at (8 % 4) / 2 = 0, source B at (8 % 6) / 2 = 1
        assertEquals(RED, frames.get(4).getRGB(0, 0));
        assertEquals(YELLOW, frames.get(4).getRGB(10, 0));
    }

    @Test
    @DisplayName("an empty scene fails loud")
    void emptySceneThrows() {
        MinecraftSceneGenerator scene = MinecraftSceneGenerator.row().build();
        assertThrows(GeneratorException.class, () -> scene.render(null));
    }

    @Test
    @DisplayName("invalid arrangement parameters are rejected at the builder")
    void invalidParametersRejected() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftSceneGenerator.grid(0));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSceneGenerator.row().withSpacing(-1));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSceneGenerator.row().withMargin(-1));
    }
}
