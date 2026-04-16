package net.aerh.jigsaw.core.resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Test helper to generate minimal PNG texture files for resource pack tests.
 * Run the main method to generate test textures at the expected locations.
 */
public class TestTextureGenerator {

    private static final int SIZE = 16;

    public static void main(String[] args) throws IOException {
        String basePath = "jigsaw/src/test/resources/test-resource-pack/assets/minecraft/textures";

        // Create directories if they don't exist
        new File(basePath + "/item").mkdirs();
        new File(basePath + "/block").mkdirs();

        // Test sword - solid red (255,0,0)
        BufferedImage testSword = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        fillColor(testSword, 255, 0, 0, 255);
        ImageIO.write(testSword, "png", new File(basePath + "/item/test_sword.png"));
        System.out.println("Created test_sword.png");

        // Test base - solid blue (0,0,255)
        BufferedImage testBase = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        fillColor(testBase, 0, 0, 255, 255);
        ImageIO.write(testBase, "png", new File(basePath + "/item/test_base.png"));
        System.out.println("Created test_base.png");

        // Test overlay - transparent with green center (0,255,0,128) from (4,4) to (12,12)
        BufferedImage testOverlay = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        fillTransparent(testOverlay);
        fillRectangleARGB(testOverlay, 4, 4, 12, 12, 0, 255, 0, 128);
        ImageIO.write(testOverlay, "png", new File(basePath + "/item/test_overlay.png"));
        System.out.println("Created test_overlay.png");

        // Test block - solid gray (128,128,128)
        BufferedImage testBlock = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        fillColor(testBlock, 128, 128, 128, 255);
        ImageIO.write(testBlock, "png", new File(basePath + "/block/test_block.png"));
        System.out.println("Created test_block.png");

        System.out.println("All test textures generated successfully!");
    }

    private static void fillColor(BufferedImage img, int r, int g, int b, int a) {
        int argb = (a << 24) | (r << 16) | (g << 8) | b;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, argb);
            }
        }
    }

    private static void fillTransparent(BufferedImage img) {
        int transparent = 0x00000000;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, transparent);
            }
        }
    }

    private static void fillRectangleARGB(BufferedImage img, int x1, int y1, int x2, int y2, int r, int g, int b, int a) {
        int argb = (a << 24) | (r << 16) | (g << 8) | b;
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
                    img.setRGB(x, y, argb);
                }
            }
        }
    }
}
