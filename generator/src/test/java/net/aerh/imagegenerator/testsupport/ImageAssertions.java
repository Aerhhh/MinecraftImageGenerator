package net.aerh.imagegenerator.testsupport;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** Pixel-exact image comparison shared by golden and regression tests. */
public final class ImageAssertions {

    private ImageAssertions() {
    }

    public static void assertPixelsEqual(BufferedImage expected, BufferedImage actual, String label) {
        assertEquals(expected.getWidth(), actual.getWidth(), label + " width");
        assertEquals(expected.getHeight(), actual.getHeight(), label + " height");

        int width = expected.getWidth();
        int height = expected.getHeight();
        int[] expectedPixels = expected.getRGB(0, 0, width, height, null, 0, width);
        int[] actualPixels = actual.getRGB(0, 0, width, height, null, 0, width);

        for (int index = 0; index < expectedPixels.length; index++) {
            if (expectedPixels[index] != actualPixels[index]) {
                fail(label + " pixel mismatch at (" + (index % width) + "," + (index / width)
                    + "): expected " + Integer.toHexString(expectedPixels[index])
                    + " but was " + Integer.toHexString(actualPixels[index]));
            }
        }
    }
}
