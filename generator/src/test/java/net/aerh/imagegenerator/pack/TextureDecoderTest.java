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
import java.util.zip.Deflater;

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

    /**
     * Flips a bit in the last byte of the zlib stream - the Adler32 trailer's low byte - the
     * exact corruption real server packs apply to every texture. The deflate payload stays
     * complete and valid.
     */
    private static byte[] corruptAdler32(byte[] png) {
        byte[] data = idatData(png);
        data[data.length - 1] ^= 0x55;
        return withIdat(png, data);
    }

    /** Removes the 4-byte Adler32 trailer entirely, keeping the complete deflate payload. */
    private static byte[] stripAdler32(byte[] png) {
        byte[] data = idatData(png);
        return withIdat(png, Arrays.copyOf(data, data.length - 4));
    }

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /**
     * Hand-builds a valid non-interlaced PNG (filter-0 rows, correct CRCs and Adler32) so tests
     * control the exact color type and bit depth - ImageIO's writer chooses its own encodings.
     *
     * @param scanlines packed rows WITHOUT filter bytes, {@code ceil(width * bpp / 8)} bytes each
     */
    private static byte[] buildPng(int width, int height, int bitDepth, int colorType,
                                   byte[] palette, byte[] transparency, byte[] scanlines) {
        int bitsPerPixel = bitDepth * switch (colorType) {
            case 0, 3 -> 1;
            case 4 -> 2;
            case 2 -> 3;
            case 6 -> 4;
            default -> throw new IllegalArgumentException("color type " + colorType);
        };
        int stride = (width * bitsPerPixel + 7) / 8;
        byte[] filtered = new byte[(stride + 1) * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(scanlines, y * stride, filtered, y * (stride + 1) + 1, stride);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PNG_SIGNATURE, 0, PNG_SIGNATURE.length);
        byte[] ihdr = new byte[13];
        writeInt(ihdr, 0, width);
        writeInt(ihdr, 4, height);
        ihdr[8] = (byte) bitDepth;
        ihdr[9] = (byte) colorType;
        writeChunk(out, "IHDR", ihdr);
        if (palette != null) {
            writeChunk(out, "PLTE", palette);
        }
        if (transparency != null) {
            writeChunk(out, "tRNS", transparency);
        }
        writeChunk(out, "IDAT", deflate(filtered));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    /** Packed grayscale rows where sample (x, y) cycles through the depth's full value range. */
    private static byte[] cyclingGrayRows(int width, int height, int bitDepth) {
        int stride = (width * bitDepth + 7) / 8;
        byte[] rows = new byte[stride * height];
        int maxSample = (1 << bitDepth) - 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sample = (x + y) % (maxSample + 1);
                int bitPosition = x * bitDepth;
                rows[y * stride + bitPosition / 8] |= (byte) (sample << (8 - bitDepth - bitPosition % 8));
            }
        }
        return rows;
    }

    /** Asserts the corrupted bytes decode to the exact pixels of the valid bytes' strict decode. */
    private static void assertDecodesPixelIdentical(byte[] validPng, byte[] corruptedPng) {
        BufferedImage expected = TextureDecoder.decode(validPng, 1024, false);
        BufferedImage actual = TextureDecoder.decode(corruptedPng, 1024, false);
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
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
    void corruptedAdler32RgbaWithRealRowFiltersDecodesPixelIdentical() throws IOException {
        // The C1 headline case: real server packs deliberately corrupt the Adler32 checksum of
        // every texture's otherwise-complete zlib stream. RGBA noise makes ImageIO's writer use
        // real row filters (Sub/Up/Average/Paeth), so the checksum-tolerant tier's unfiltering
        // is exercised on every row - and the output must be pixel-identical to a strict decode.
        BufferedImage original = argb(64, 64);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int alpha = (x + y) % 3 == 0 ? 0x40 : 0xFF;
                original.setRGB(x, y, (alpha << 24) | ((x * 37 & 0xFF) << 16) | ((y * 53 & 0xFF) << 8) | (x * y & 0xFF));
            }
        }
        byte[] valid = png(original);
        assertDecodesPixelIdentical(valid, corruptAdler32(valid));
    }

    @Test
    void corruptedAdler32PalettePngDecodesPixelIdentical() throws IOException {
        byte[] valid = png(indexedNoise(64));
        assertDecodesPixelIdentical(valid, corruptAdler32(valid));
    }

    @Test
    void corruptedAdler32SplitIdatStillDecodesTheCompleteImage() throws IOException {
        // Corrupt checksum AND image data split across IDAT chunks separated by a stray chunk:
        // the JDK reader recovers nothing past the stray chunk, so only a manual tier that
        // concatenates every IDAT chunk and ignores the trailer can produce the complete image.
        BufferedImage original = argb(48, 48);
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                original.setRGB(x, y, 0xFF000000 | ((x * 41 & 0xFF) << 16) | ((y * 67 & 0xFF) << 8) | (x + y & 0xFF));
            }
        }
        byte[] valid = png(original);
        assertDecodesPixelIdentical(valid, splitIdatWithStrayChunk(corruptAdler32(valid), 4));
    }

    @Test
    void corruptedAdler32OneBitGrayDecodesPixelIdentical() {
        // Some server packs ship 1-bit grayscale font sheets with corrupted checksums. Width 20 keeps
        // the row stride off byte boundaries, exercising sub-byte sample unpacking.
        byte[] valid = buildPng(20, 10, 1, 0, null, null, cyclingGrayRows(20, 10, 1));
        BufferedImage strict = TextureDecoder.decode(valid, 1024, false);
        assertEquals(0xFF000000, strict.getRGB(0, 0), "sample 0 is black");
        assertEquals(0xFFFFFFFF, strict.getRGB(1, 0), "sample 1 expands to 255, not 1");
        assertDecodesPixelIdentical(valid, corruptAdler32(valid));
    }

    @Test
    void corruptedAdler32LowDepthGrayDecodesPixelIdentical() {
        // 2-bit gray: levels 0/85/170/255. 4-bit gray: multiples of 17. Both sub-byte packed.
        byte[] twoBit = buildPng(9, 6, 2, 0, null, null, cyclingGrayRows(9, 6, 2));
        BufferedImage strictTwo = TextureDecoder.decode(twoBit, 1024, false);
        assertEquals(0xFF555555, strictTwo.getRGB(1, 0), "2-bit sample 1 expands to 85");
        assertDecodesPixelIdentical(twoBit, corruptAdler32(twoBit));

        byte[] fourBit = buildPng(5, 7, 4, 0, null, null, cyclingGrayRows(5, 7, 4));
        BufferedImage strictFour = TextureDecoder.decode(fourBit, 1024, false);
        assertEquals(0xFF111111, strictFour.getRGB(1, 0), "4-bit sample 1 expands to 17");
        assertDecodesPixelIdentical(fourBit, corruptAdler32(fourBit));
    }

    @Test
    void corruptedAdler32EightBitGrayAndGrayAlphaAndRgbDecodePixelIdentical() {
        byte[] gray = buildPng(6, 4, 8, 0, null, null, cyclingGrayRows(6, 4, 8));
        assertDecodesPixelIdentical(gray, corruptAdler32(gray));

        byte[] grayAlphaRows = new byte[7 * 5 * 2];
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                grayAlphaRows[(y * 7 + x) * 2] = (byte) (x * 40);
                grayAlphaRows[(y * 7 + x) * 2 + 1] = (byte) (60 + y * 45);
            }
        }
        byte[] grayAlpha = buildPng(7, 5, 8, 4, null, null, grayAlphaRows);
        assertDecodesPixelIdentical(grayAlpha, corruptAdler32(grayAlpha));

        byte[] rgbRows = new byte[8 * 6 * 3];
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 8; x++) {
                rgbRows[(y * 8 + x) * 3] = (byte) (x * 30);
                rgbRows[(y * 8 + x) * 3 + 1] = (byte) (y * 50);
                rgbRows[(y * 8 + x) * 3 + 2] = (byte) ((x + y) * 20);
            }
        }
        byte[] rgb = buildPng(8, 6, 8, 2, null, null, rgbRows);
        assertDecodesPixelIdentical(rgb, corruptAdler32(rgb));
    }

    @Test
    void corruptedAdler32LowDepthPaletteWithTrnsDecodesPixelIdentical() {
        byte[] palette = {0, 0, 0, (byte) 255, 0, 0, 0, (byte) 255, 0, 0, 0, (byte) 255};
        byte[] transparency = {0, (byte) 255, (byte) 255, (byte) 128};
        byte[] valid = buildPng(10, 5, 2, 3, palette, transparency, cyclingGrayRows(10, 5, 2));
        assertDecodesPixelIdentical(valid, corruptAdler32(valid));
    }

    @Test
    void missingAdler32TrailerStillDecodesFully() {
        // Some packs strip the trailer instead of corrupting it; the raw-inflate tier never
        // reads it, so the complete deflate payload decodes fully either way.
        byte[] valid = buildPng(20, 10, 1, 0, null, null, cyclingGrayRows(20, 10, 1));
        assertDecodesPixelIdentical(valid, stripAdler32(valid));
    }

    @Test
    void corruptedAdler32OneBitGrayHonorsTrnsTransparency() {
        // The gray tRNS sample lives in the ORIGINAL bit depth's value space: {0, 0} marks
        // sample 0 transparent for a 1-bit image.
        byte[] valid = buildPng(20, 10, 1, 0, null, new byte[]{0, 0}, cyclingGrayRows(20, 10, 1));
        BufferedImage decoded = TextureDecoder.decode(corruptAdler32(valid), 1024, false);
        assertEquals(0, decoded.getRGB(0, 0) >>> 24, "sample 0 matches the tRNS entry");
        assertEquals(0xFFFFFFFF, decoded.getRGB(1, 0), "sample 1 stays opaque white");
    }

    @Test
    void corruptDeflatePayloadBehindAValidZlibHeaderStillFailsLoudly() throws IOException {
        // Wrong DATA (not just a wrong trailer) must keep failing: a valid 2-byte zlib header
        // followed by garbage inflates to nothing on every tier.
        byte[] garbage = new byte[34];
        Arrays.fill(garbage, (byte) 0x55);
        garbage[0] = 0x78;
        garbage[1] = (byte) 0x9C;
        byte[] corrupt = withIdat(png(indexedNoise(64)), garbage);
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(corrupt, 1024, false));
    }

    @Test
    void truncatedOneBitGraySalvagesDecodedRowsAndPadsTheRest() {
        // The salvage tier must handle low-depth grayscale too: pseudo-random bits keep the
        // deflate stream incompressible, so truncating it halfway leaves the early rows
        // recoverable and the late rows missing.
        int size = 64;
        int stride = size / 8;
        byte[] rows = new byte[stride * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (((x * 31 + y * 97 + x * y) & 1) != 0) {
                    rows[y * stride + x / 8] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        byte[] valid = buildPng(size, size, 1, 0, null, null, rows);
        BufferedImage original = TextureDecoder.decode(valid, 1024, false);
        byte[] truncated = truncateIdat(valid, idatData(valid).length / 2);

        BufferedImage decoded = TextureDecoder.decode(truncated, 1024, false);
        assertEquals(size, decoded.getWidth());
        for (int x = 0; x < size; x++) {
            assertEquals(original.getRGB(x, 0), decoded.getRGB(x, 0),
                "rows decoded before the truncation point keep their pixels (x=" + x + ")");
            assertEquals(0, decoded.getRGB(x, size - 1) >>> 24,
                "rows past the truncation point come back fully transparent (x=" + x + ")");
        }
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
