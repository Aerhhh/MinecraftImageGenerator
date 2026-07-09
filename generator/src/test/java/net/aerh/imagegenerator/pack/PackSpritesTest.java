package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackSpritesTest {

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    @Test
    void largerThanCanvasSpriteDownscalesToFillCanvasExactly() {
        BufferedImage sprite = solid(512, 512, 0xFF112233);
        BufferedImage canvas = PackSprites.scaleToCanvas(sprite, 256);
        assertEquals(256, canvas.getWidth());
        assertEquals(256, canvas.getHeight());
        // 512x512 -> exactly 256x256 at factor 0.5: no transparent border, fills the whole canvas.
        assertEquals(0xFF112233, canvas.getRGB(0, 0), "top-left corner");
        assertEquals(0xFF112233, canvas.getRGB(255, 255), "bottom-right corner");
        assertEquals(0xFF112233, canvas.getRGB(128, 128), "center");
    }

    @Test
    void nonSquareSpriteIsCenteredWithTransparentBorder() {
        // 16x48 at integer factor 5 (256 / 48 = 5) scales to 80x240, centered on a 256 canvas:
        // offsets (88, 8). The corners fall outside that content rectangle on every side.
        BufferedImage sprite = solid(16, 48, 0xFF445566);
        BufferedImage canvas = PackSprites.scaleToCanvas(sprite, 256);
        assertEquals(256, canvas.getWidth());
        assertEquals(256, canvas.getHeight());
        assertEquals(0, canvas.getRGB(0, 0) >>> 24, "top-left corner is transparent");
        assertEquals(0, canvas.getRGB(255, 0) >>> 24, "top-right corner is transparent");
        assertEquals(0, canvas.getRGB(0, 255) >>> 24, "bottom-left corner is transparent");
        assertEquals(0, canvas.getRGB(255, 255) >>> 24, "bottom-right corner is transparent");
        // The center column (x=128) runs through the scaled content for its full height.
        for (int y = 10; y < 246; y += 20) {
            assertEquals(0xFF445566, canvas.getRGB(128, y), "center column pixel at y=" + y);
        }
    }

    @Test
    void singlePixelSpriteFillsEntireCanvas() {
        BufferedImage sprite = solid(1, 1, 0xFF998877);
        BufferedImage canvas = PackSprites.scaleToCanvas(sprite, 256);
        assertEquals(256, canvas.getWidth());
        assertEquals(256, canvas.getHeight());
        assertEquals(0xFF998877, canvas.getRGB(0, 0), "top-left corner");
        assertEquals(0xFF998877, canvas.getRGB(255, 255), "bottom-right corner");
        assertEquals(0xFF998877, canvas.getRGB(128, 128), "center");
    }
}
