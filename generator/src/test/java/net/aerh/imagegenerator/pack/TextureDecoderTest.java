package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextureDecoderTest {

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage argb(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void decodesPngToArgb() throws IOException {
        BufferedImage source = argb(16, 16);
        source.setRGB(0, 0, 0xFFFF0000);
        BufferedImage decoded = TextureDecoder.decode(png(source), 1024);
        assertEquals(BufferedImage.TYPE_INT_ARGB, decoded.getType());
        assertEquals(0xFFFF0000, decoded.getRGB(0, 0));
    }

    @Test
    void rejectsOversizedDimensionsBeforeDecode() throws IOException {
        byte[] data = png(argb(64, 64));
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(data, 32));
    }

    @Test
    void rejectsNonImageBytes() {
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode("not a png".getBytes(), 1024));
    }

    @Test
    void normalizesEmissiveAlpha252ToOpaque() throws IOException {
        BufferedImage source = argb(2, 1);
        source.setRGB(0, 0, (252 << 24) | 0x00FF00);
        source.setRGB(1, 0, (127 << 24) | 0x0000FF);
        BufferedImage decoded = TextureDecoder.decode(png(source), 1024);
        assertEquals(0xFF00FF00, decoded.getRGB(0, 0), "alpha 252 (full-bright marker) becomes opaque");
        assertEquals((127 << 24) | 0x0000FF, decoded.getRGB(1, 0), "alpha 127 (emissive translucent) is preserved");
    }

    @Test
    void firstFrameCropsVerticalFlipbookAtFirstListEntry() {
        BufferedImage flipbook = argb(16, 48);
        flipbook.setRGB(0, 32, 0xFF0000FF); // distinctive pixel in frame index 2
        BufferedImage frame = TextureDecoder.firstFrame(flipbook, new AnimationMeta(2, null, null));
        assertEquals(16, frame.getWidth());
        assertEquals(16, frame.getHeight());
        assertEquals(0xFF0000FF, frame.getRGB(0, 0));
    }

    @Test
    void firstFrameHonorsExplicitFrameSize() {
        BufferedImage flipbook = argb(16, 64);
        flipbook.setRGB(0, 32, 0xFF00FF00);
        BufferedImage frame = TextureDecoder.firstFrame(flipbook, new AnimationMeta(1, 16, 32));
        assertEquals(32, frame.getHeight());
        assertEquals(0xFF00FF00, frame.getRGB(0, 0));
    }

    @Test
    void firstFrameRejectsIndexOutsideImage() {
        BufferedImage flipbook = argb(16, 32);
        assertThrows(PackLoadException.class,
            () -> TextureDecoder.firstFrame(flipbook, new AnimationMeta(5, null, null)));
    }

    @Test
    void firstFrameRejectsOverflowingFrameOffset() {
        BufferedImage flipbook = argb(16, 32);
        assertThrows(PackLoadException.class,
            () -> TextureDecoder.firstFrame(flipbook, new AnimationMeta(3000, null, 1_000_000)),
            "int overflow in the frame offset must not bypass the bounds check");
    }

    @Test
    void firstFrameRejectsNonPositiveFrameSize() {
        BufferedImage flipbook = argb(16, 32);
        assertThrows(PackLoadException.class,
            () -> TextureDecoder.firstFrame(flipbook, new AnimationMeta(0, 16, -16)),
            "negative frame height should reject");
        assertThrows(PackLoadException.class,
            () -> TextureDecoder.firstFrame(flipbook, new AnimationMeta(0, 0, 16)),
            "zero frame width should reject");
    }
}
