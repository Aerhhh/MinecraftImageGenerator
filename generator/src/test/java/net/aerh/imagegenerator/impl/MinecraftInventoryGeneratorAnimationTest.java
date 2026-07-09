package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.item.InventoryItem;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the animated (animateGlint) inventory output pixel-exactly against a reference composed
 * from the item generator's full-resolution glint frames. Guards the retention optimization that
 * downscales per-slot frames before composition: frame count, frame delay and every composed
 * pixel must be identical to compositing straight from the full-resolution frames.
 */
class MinecraftInventoryGeneratorAnimationTest {

    /** ceil(6000ms / 33ms), see GlintImageEffect. */
    private static final int GLINT_FRAME_COUNT = 182;
    private static final int GLINT_FRAME_DELAY_MS = 33;

    @Test
    void animatedGlintInventoryMatchesFullResolutionComposition() {
        GeneratedObject inventory = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(1)
            .withInventoryString("stone,enchant:1")
            .withAnimateGlint(true)
            .build()
            .generate();

        assertTrue(inventory.isAnimated(), "enchanted slot with animateGlint renders as an animation");
        assertEquals(GLINT_FRAME_COUNT, inventory.getAnimationFrames().size());
        assertEquals(GLINT_FRAME_DELAY_MS, inventory.getFrameDelayMs());
        assertNotNull(inventory.getGifData());

        BufferedImage emptyInventory = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(1)
            .withInventoryString("")
            .build()
            .generate()
            .getImage();

        List<BufferedImage> fullResolutionFrames = new MinecraftItemGenerator.Builder()
            .withItem("stone")
            .isEnchanted(true)
            .withData("enchant")
            .build()
            .generate()
            .getAnimationFrames();
        assertEquals(GLINT_FRAME_COUNT, fullResolutionFrames.size());

        int scaleFactor = MinecraftInventoryGenerator.getScaleFactor();
        int itemSize = Spritesheet.getTexture("stone").getWidth() / 2;
        int slotSize = 18 * scaleFactor;
        int borderSize = 7 * scaleFactor; // drawBorder defaults to true
        int titleHeight = borderSize; // no container title
        int itemPadding = (slotSize - itemSize) / 2;
        int itemX = borderSize + itemPadding;
        int itemY = titleHeight + itemPadding;

        for (int frameIndex = 0; frameIndex < GLINT_FRAME_COUNT; frameIndex++) {
            BufferedImage expected = composeReferenceFrame(emptyInventory, fullResolutionFrames.get(frameIndex), itemX, itemY, itemSize);
            assertFramePixelsEqual(expected, inventory.getAnimationFrames().get(frameIndex), frameIndex);
        }
    }

    /**
     * Pins the reduced retention: during composition, per-slot animation frames must be held at
     * the resolution the composite draws (item render size), not the item generator's full
     * output resolution. With 54 enchanted slots at 182 frames each, full-resolution retention
     * reaches gigabytes of simultaneously live BufferedImages.
     */
    @Test
    void processItemRetainsAnimationFramesAtCompositeResolution() {
        MinecraftInventoryGenerator generator = new MinecraftInventoryGenerator.Builder()
            .withRows(1).withSlotsPerRow(1)
            .withInventoryString("stone,enchant:1")
            .withAnimateGlint(true)
            .build();

        InventoryItem item = new InventoryItem(1, 1, "stone", "enchant", null);
        generator.processItem(item, null);

        int itemRenderSize = Spritesheet.getTexture("stone").getWidth() / 2;
        assertNotNull(item.getAnimationFrames());
        assertEquals(GLINT_FRAME_COUNT, item.getAnimationFrames().size());
        assertEquals(GLINT_FRAME_DELAY_MS, item.getFrameDelayMs());

        for (int frameIndex = 0; frameIndex < item.getAnimationFrames().size(); frameIndex++) {
            BufferedImage frame = item.getAnimationFrames().get(frameIndex);
            assertEquals(itemRenderSize, frame.getWidth(), "retained width of frame " + frameIndex);
            assertEquals(itemRenderSize, frame.getHeight(), "retained height of frame " + frameIndex);
        }

        assertNotNull(item.getItemImage());
        assertTrue(item.getItemImage().getWidth() <= itemRenderSize, "retained item image width is bounded");
        assertTrue(item.getItemImage().getHeight() <= itemRenderSize, "retained item image height is bounded");
    }

    @Test
    void downscaleKeepsImagesAlreadyWithinCompositeResolutionUntouched() {
        int itemRenderSize = Spritesheet.getTexture("stone").getWidth() / 2;
        BufferedImage smallImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        BufferedImage exactImage = new BufferedImage(itemRenderSize, itemRenderSize, BufferedImage.TYPE_INT_ARGB);

        assertSame(smallImage, MinecraftInventoryGenerator.downscaleToCompositeResolution(smallImage));
        assertSame(exactImage, MinecraftInventoryGenerator.downscaleToCompositeResolution(exactImage));

        List<BufferedImage> frames = List.of(smallImage, exactImage);
        List<BufferedImage> result = MinecraftInventoryGenerator.downscaleFramesToCompositeResolution(frames);
        assertEquals(2, result.size());
        assertSame(smallImage, result.get(0));
        assertSame(exactImage, result.get(1));
    }

    @Test
    void downscaleHandlesMissingImagery() {
        assertNull(MinecraftInventoryGenerator.downscaleToCompositeResolution(null));
        assertNull(MinecraftInventoryGenerator.downscaleFramesToCompositeResolution(null));
        assertTrue(MinecraftInventoryGenerator.downscaleFramesToCompositeResolution(List.of()).isEmpty());
    }

    private static BufferedImage composeReferenceFrame(BufferedImage base, BufferedImage itemFrame, int itemX, int itemY, int itemSize) {
        BufferedImage frame = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = frame.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(base, 0, 0, null);
        graphics.drawImage(itemFrame, itemX, itemY, itemSize, itemSize, null);
        graphics.dispose();
        return frame;
    }

    private static void assertFramePixelsEqual(BufferedImage expected, BufferedImage actual, int frameIndex) {
        assertEquals(expected.getWidth(), actual.getWidth(), "frame " + frameIndex + " width");
        assertEquals(expected.getHeight(), actual.getHeight(), "frame " + frameIndex + " height");

        int width = expected.getWidth();
        int height = expected.getHeight();
        int[] expectedPixels = expected.getRGB(0, 0, width, height, null, 0, width);
        int[] actualPixels = actual.getRGB(0, 0, width, height, null, 0, width);

        for (int index = 0; index < expectedPixels.length; index++) {
            if (expectedPixels[index] != actualPixels[index]) {
                fail("frame " + frameIndex + " pixel mismatch at (" + (index % width) + "," + (index / width)
                    + "): expected " + Integer.toHexString(expectedPixels[index])
                    + " but was " + Integer.toHexString(actualPixels[index]));
            }
        }
    }
}
