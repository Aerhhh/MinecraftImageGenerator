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

    /**
     * Asserts the two images are NOT pixel-identical: either a dimension differs or at least
     * one pixel does. The comparison semantics are the exact inverse of
     * {@link #assertPixelsEqual}, so the two assertions can never drift apart.
     */
    public static void assertPixelsDiffer(BufferedImage first, BufferedImage second, String label) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return;
        }
        int width = first.getWidth();
        int height = first.getHeight();
        int[] firstPixels = first.getRGB(0, 0, width, height, null, 0, width);
        int[] secondPixels = second.getRGB(0, 0, width, height, null, 0, width);
        for (int index = 0; index < firstPixels.length; index++) {
            if (firstPixels[index] != secondPixels[index]) {
                return;
            }
        }
        fail(label + ": expected the images to differ, but they are pixel-identical ("
            + width + "x" + height + ")");
    }
}
