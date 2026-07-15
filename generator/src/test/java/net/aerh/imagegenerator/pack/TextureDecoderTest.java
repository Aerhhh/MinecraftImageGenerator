package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureDecoderTest {

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage argb(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /** Palette noise image: 256 opaque distinct colors, deterministic per-pixel indices. */
    private static BufferedImage indexedNoise(int size) {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        for (int i = 0; i < 256; i++) {
            r[i] = (byte) i;
            g[i] = (byte) (i * 7);
            b[i] = (byte) (i * 13);
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_INDEXED,
            new IndexColorModel(8, 256, r, g, b));
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.getRaster().setSample(x, y, 0, (x * 31 + y * 97 + x * y % 13) & 0xFF);
            }
        }
        return image;
    }

    /** Fully transparent palette image, mirroring the real-world truncated pack texture. */
    private static BufferedImage indexedTransparent(int size) {
        byte[] zero = new byte[256];
        byte[] alpha = new byte[256];
        Arrays.fill(alpha, 1, 256, (byte) 255);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_INDEXED,
            new IndexColorModel(8, 256, zero, zero, zero, alpha));
        return image;
    }

    private record Chunk(String type, byte[] data) {
    }

    /**
     * Rewrites a PNG replacing its IDAT chunk(s) with the given chunks, each with correct length
     * and CRC: the file structure stays valid while the image data stream is short, corrupt or
     * split, mirroring real-world pack quirks.
     */
    private static byte[] withIdatReplacedBy(byte[] png, Chunk... replacement) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 8);
        boolean idatWritten = false;
        int pos = 8;
        while (pos + 8 <= png.length) {
            int length = readInt(png, pos);
            String type = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
            if (type.equals("IDAT")) {
                if (!idatWritten) {
                    for (Chunk chunk : replacement) {
                        writeChunk(out, chunk.type(), chunk.data());
                    }
                    idatWritten = true;
                }
            } else {
                writeChunk(out, type, Arrays.copyOfRange(png, pos + 8, pos + 8 + length));
            }
            pos += 12 + length;
        }
        return out.toByteArray();
    }

    private static byte[] withIdat(byte[] png, byte[] newIdatData) {
        return withIdatReplacedBy(png, new Chunk("IDAT", newIdatData));
    }

    /**
     * Splits the zlib stream into two IDAT chunks separated by a stray tEXt chunk. The JDK
     * reader stops image data at the stray chunk (recovering nothing from the first
     * {@code firstChunkBytes} bytes), so decoding must fall through to the manual IDAT salvage,
     * which concatenates every IDAT chunk and recovers the full image.
     */
    private static byte[] splitIdatWithStrayChunk(byte[] png, int firstChunkBytes) {
        byte[] full = idatData(png);
        return withIdatReplacedBy(png,
            new Chunk("IDAT", Arrays.copyOf(full, firstChunkBytes)),
            new Chunk("tEXt", "k\0v".getBytes(StandardCharsets.ISO_8859_1)),
            new Chunk("IDAT", Arrays.copyOfRange(full, firstChunkBytes, full.length)));
    }

    /** Concatenated IDAT data of a PNG (the complete zlib stream). */
    private static byte[] idatData(byte[] png) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int pos = 8;
        while (pos + 8 <= png.length) {
            int length = readInt(png, pos);
            if (new String(png, pos + 4, 4, StandardCharsets.US_ASCII).equals("IDAT")) {
                data.write(png, pos + 8, length);
            }
            pos += 12 + length;
        }
        return data.toByteArray();
    }

    private static byte[] truncateIdat(byte[] png, int keepBytes) {
        byte[] full = idatData(png);
        return withIdat(png, Arrays.copyOf(full, Math.min(keepBytes, full.length)));
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        out.write(data.length >>> 24);
        out.write(data.length >>> 16);
        out.write(data.length >>> 8);
        out.write(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long value = crc.getValue();
        out.write((int) (value >>> 24));
        out.write((int) (value >>> 16));
        out.write((int) (value >>> 8));
        out.write((int) value);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
            | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    @Test
    void decodesPngToArgb() throws IOException {
        BufferedImage source = argb(16, 16);
        source.setRGB(0, 0, 0xFFFF0000);
        BufferedImage decoded = TextureDecoder.decode(png(source), 1024, false);
        assertEquals(BufferedImage.TYPE_INT_ARGB, decoded.getType());
        assertEquals(0xFFFF0000, decoded.getRGB(0, 0));
    }

    @Test
    void rejectsOversizedDimensionsBeforeDecode() throws IOException {
        byte[] data = png(argb(64, 64));
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(data, 32, false));
    }

    @Test
    void rejectsNonImageBytes() {
        assertThrows(PackLoadException.class,
            () -> TextureDecoder.decode("not a png".getBytes(), 1024, false));
    }

    @Test
    void normalizesEmissiveAlpha252ToOpaqueWhenEnabled() throws IOException {
        BufferedImage source = argb(2, 1);
        source.setRGB(0, 0, (252 << 24) | 0x00FF00);
        source.setRGB(1, 0, (127 << 24) | 0x0000FF);
        BufferedImage decoded = TextureDecoder.decode(png(source), 1024, true);
        assertEquals(0xFF00FF00, decoded.getRGB(0, 0), "alpha 252 (full-bright marker) becomes opaque");
        assertEquals((127 << 24) | 0x0000FF, decoded.getRGB(1, 0), "alpha 127 (emissive translucent) is preserved");
    }

    @Test
    void preservesAlpha252WhenNormalizationDisabled() throws IOException {
        BufferedImage source = argb(2, 1);
        source.setRGB(0, 0, (252 << 24) | 0x00FF00);
        source.setRGB(1, 0, (127 << 24) | 0x0000FF);
        BufferedImage decoded = TextureDecoder.decode(png(source), 1024, false);
        assertEquals((252 << 24) | 0x00FF00, decoded.getRGB(0, 0),
            "alpha 252 is a legitimate pixel value outside the Hypixel SkyBlock convention");
        assertEquals((127 << 24) | 0x0000FF, decoded.getRGB(1, 0));
    }

    @Test
    void truncatedIdatRecoversDecodedRowsAndPadsMissingRowsTransparent() throws IOException {
        BufferedImage original = indexedNoise(256);
        byte[] full = png(original);
        byte[] truncated = truncateIdat(full, idatData(full).length / 2);

        BufferedImage decoded = TextureDecoder.decode(truncated, 1024, false);
        assertEquals(256, decoded.getWidth());
        assertEquals(256, decoded.getHeight());
        for (int x = 0; x < 256; x++) {
            assertEquals(original.getRGB(x, 0), decoded.getRGB(x, 0),
                "rows decoded before the truncation point keep their pixels (x=" + x + ")");
        }
        for (int x = 0; x < 256; x++) {
            assertEquals(0, decoded.getRGB(x, 255) >>> 24,
                "rows past the truncation point come back fully transparent (x=" + x + ")");
        }
    }

    @Test
    void fullyTransparentTruncatedPalettePngDecodesFullyTransparent() throws IOException {
        // Mirrors the real-world pack file: a fully transparent 256x256 palette PNG whose IDAT
        // zlib stream is truncated. Every pixel, decoded or padded, must be transparent.
        byte[] full = png(indexedTransparent(256));
        byte[] truncated = truncateIdat(full, idatData(full).length / 2);

        BufferedImage decoded = TextureDecoder.decode(truncated, 1024, false);
        assertEquals(256, decoded.getWidth());
        for (int y = 0; y < 256; y += 51) {
            for (int x = 0; x < 256; x += 51) {
                assertEquals(0, decoded.getRGB(x, y) >>> 24, "pixel (" + x + "," + y + ") is transparent");
            }
        }
    }

    @Test
    void severelyTruncatedIdatSalvagesToFullyTransparentImage() throws IOException {
        // 4 bytes barely cover the zlib header: the image reader recovers zero rows, so this
        // exercises the manual IDAT inflation fallback.
        byte[] truncated = truncateIdat(png(indexedNoise(256)), 4);

        BufferedImage decoded = TextureDecoder.decode(truncated, 1024, false);
        assertEquals(256, decoded.getWidth());
        assertEquals(256, decoded.getHeight());
        for (int y = 0; y < 256; y += 85) {
            for (int x = 0; x < 256; x += 85) {
                assertEquals(0, decoded.getRGB(x, y) >>> 24, "no recoverable rows means all-transparent");
            }
        }
    }

    @Test
    void splitIdatRgbaPngSalvagesEveryPixelIncludingRowFilters() throws IOException {
        // RGBA noise makes the PNG writer use real row filters (Sub/Up/Average/Paeth), so the
        // salvage decoder's unfiltering and RGBA conversion are exercised on every row.
        BufferedImage original = argb(64, 64);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int alpha = (x + y) % 3 == 0 ? 0x40 : 0xFF;
                original.setRGB(x, y, (alpha << 24) | ((x * 37 & 0xFF) << 16) | ((y * 53 & 0xFF) << 8) | (x * y & 0xFF));
            }
        }
        byte[] split = splitIdatWithStrayChunk(png(original), 4);

        BufferedImage decoded = TextureDecoder.decode(split, 1024, false);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(original.getRGB(x, y), decoded.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void splitIdatLowBitDepthPalettePngSalvagesPackedSamples() throws IOException {
        // A 4-color palette writes at bit depth 2: exercises sub-byte sample unpacking and
        // tRNS-driven palette transparency in the salvage decoder.
        byte[] r = {0, (byte) 255, 0, 0};
        byte[] g = {0, 0, (byte) 255, 0};
        byte[] b = {0, 0, 0, (byte) 255};
        byte[] alpha = {0, (byte) 255, (byte) 255, (byte) 255};
        BufferedImage original = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_BINARY,
            new IndexColorModel(2, 4, r, g, b, alpha));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                original.getRaster().setSample(x, y, 0, (x + y * 3) % 4);
            }
        }
        byte[] split = splitIdatWithStrayChunk(png(original), 2);

        BufferedImage decoded = TextureDecoder.decode(split, 1024, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(original.getRGB(x, y), decoded.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void strictDecodePreservesTranslucentChannelValuesExactly() throws IOException {
        // 0x406F0305 is premultiply-lossy: the default SrcOver compositing would round-trip it
        // to 0x40700404. The ARGB conversion deliberately uses AlphaComposite.Src so translucent
        // channels are copied exactly; this pins that behavior.
        BufferedImage source = argb(1, 1);
        source.setRGB(0, 0, 0x406F0305);
        BufferedImage decoded = TextureDecoder.decode(png(source), 1024, false);
        assertEquals(0x406F0305, decoded.getRGB(0, 0),
            "alpha 64 with odd channel values must survive decode without premultiplication rounding");
    }

    @Test
    void pngWithoutAnyIdatChunkFailsLoudly() throws IOException {
        // A PNG with no IDAT chunk at all (e.g. a corrupted chunk type byte) is corruption with
        // nothing recoverable, not a salvageable truncation: decode must fail loudly instead of
        // silently returning an all-transparent image.
        byte[] noIdat = withIdatReplacedBy(png(indexedNoise(16)));
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(noIdat, 1024, false));
    }

    @Test
    void corruptIdatZlibStreamStillFailsLoudly() throws IOException {
        // Structurally valid PNG whose IDAT is garbage (invalid zlib header): corruption, not
        // truncation, and nothing is recoverable.
        byte[] garbageIdat = new byte[32];
        Arrays.fill(garbageIdat, (byte) 0x55);
        byte[] corrupt = withIdat(png(indexedNoise(64)), garbageIdat);
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(corrupt, 1024, false));
    }

    @Test
    void oversizedDimensionsRejectEvenWhenTruncationWouldBeSalvageable() throws IOException {
        byte[] full = png(indexedNoise(256));
        byte[] truncated = truncateIdat(full, idatData(full).length / 2);
        PackLoadException exception = assertThrows(PackLoadException.class,
            () -> TextureDecoder.decode(truncated, 128, false));
        assertTrue(exception.getMessage().contains("exceed limit"),
            "the dimension guard must fire before any decode or salvage work");
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
    void firstFrameCopiesTranslucentChannelValuesExactly() {
        // 0x406F0305 is premultiply-lossy (see strictDecodePreservesTranslucentChannelValuesExactly):
        // the crop composites with AlphaComposite.Src, aligned with the decode path, so translucent
        // frame pixels survive without drifting by one.
        BufferedImage flipbook = argb(16, 32);
        flipbook.setRGB(3, 16, 0x406F0305); // distinctive translucent pixel in frame index 1
        BufferedImage frame = TextureDecoder.firstFrame(flipbook, new AnimationMeta(1, null, null));
        assertEquals(0x406F0305, frame.getRGB(3, 0),
            "translucent pixels must survive the first-frame crop without premultiplication rounding");
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
