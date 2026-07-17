package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;
import net.aerh.imagegenerator.exception.PackLoadException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.event.IIOReadUpdateListener;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decodes pack textures defensively: image dimensions are read from the header and checked
 * against the cap BEFORE full pixel decode (image-bomb guard). Tolerates the real-world pack
 * quirks of PNGs whose IDAT zlib stream carries a deliberately wrong Adler32 checksum (server
 * packs corrupt every texture's checksum as a soft obfuscation; the client's stb_image-based
 * loader never verifies it) or is outright truncated (Minecraft itself renders those too),
 * optionally applies the Hypixel SkyBlock emissive alpha encoding, and extracts static first
 * frames from animated flipbooks.
 */
@UtilityClass
class TextureDecoder {

    private static final int EMISSIVE_OPAQUE_ALPHA = 252;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int COLOR_TYPE_GRAY = 0;
    private static final int COLOR_TYPE_RGB = 2;
    private static final int COLOR_TYPE_PALETTE = 3;
    private static final int COLOR_TYPE_GRAY_ALPHA = 4;
    private static final int COLOR_TYPE_RGBA = 6;

    /**
     * Decodes a pack texture to TYPE_INT_ARGB.
     *
     * @param imageBytes            raw texture file bytes (PNG in practice)
     * @param maxTextureDim         dimension cap enforced from the image header before any pixel
     *                              decode work
     * @param normalizeEmissiveAlpha when true, pixels with alpha {@value EMISSIVE_OPAQUE_ALPHA}
     *                              (the Hypixel SkyBlock "opaque full-bright" shader marker) are
     *                              converted to fully opaque; other packs may ship legitimate
     *                              alpha-252 pixels, so callers enable this per pack
     * @return the TYPE_INT_ARGB image; translucent pixels keep their exact channel values - the
     *     ARGB conversion copies with {@link AlphaComposite#Src}, a deliberate behavior change
     *     from earlier releases whose default SrcOver compositing premultiplied and could shift
     *     translucent channels by one
     * @throws PackLoadException when the bytes are not a decodable image, exceed the dimension
     *                           cap, or are corrupt beyond recovery
     */
    public static BufferedImage decode(byte[] imageBytes, int maxTextureDim, boolean normalizeEmissiveAlpha) {
        try {
            return normalize(readStrict(imageBytes, maxTextureDim), normalizeEmissiveAlpha);
        } catch (IOException strictFailure) {
            // Decode tiers, strictest first: (1) the strict ImageIO read above; (2) a
            // checksum-tolerant FULL manual decode for the real-world pack quirk of complete
            // zlib streams with a deliberately wrong Adler32 trailer - pixel-identical to a
            // strict decode of the same data; (3) the lenient partial ImageIO retry and the
            // manual truncated-IDAT salvage, which keep whatever rows are recoverable; then
            // (4) loud failure.
            BufferedImage recovered = readChecksumTolerant(imageBytes, maxTextureDim);
            if (recovered == null) {
                recovered = readPartial(imageBytes, maxTextureDim);
            }
            if (recovered == null) {
                recovered = salvageTruncatedPng(imageBytes, maxTextureDim);
            }
            if (recovered != null) {
                return normalize(recovered, normalizeEmissiveAlpha);
            }
            throw new PackLoadException("Failed to decode pack texture", strictFailure);
        }
    }

    /**
     * The straightforward decode path: header dimension check first (unchanged security
     * property), then a full read. Dimension violations throw {@link PackLoadException} which
     * intentionally bypasses the lenient retry in {@link #decode}.
     */
    private static BufferedImage readStrict(byte[] imageBytes, int maxTextureDim) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new PackLoadException("Pack texture is not a decodable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                checkDimensions(reader.getWidth(0), reader.getHeight(0), maxTextureDim);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void checkDimensions(int width, int height, int maxTextureDim) {
        if (width > maxTextureDim || height > maxTextureDim || width < 1 || height < 1) {
            throw new PackLoadException("Pack texture dimensions %sx%s exceed limit %s",
                String.valueOf(width), String.valueOf(height), String.valueOf(maxTextureDim));
        }
    }

    /**
     * Lenient retry after a strict decode failure: reads into a caller-owned destination image so
     * rows decoded before the failure survive, tracking decoded rows through an update listener.
     * Rows the reader never reached are cleared to fully transparent.
     *
     * @return the (possibly partial) image, or null when the reader produced no raster data at all
     */
    private static BufferedImage readPartial(byte[] imageBytes, int maxTextureDim) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkDimensions(width, height, maxTextureDim);
                Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
                if (!types.hasNext()) {
                    return null;
                }
                BufferedImage destination = types.next().createBufferedImage(width, height);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setDestination(destination);
                RowTracker tracker = new RowTracker();
                reader.addIIOReadUpdateListener(tracker);
                try {
                    return reader.read(0, param);
                } catch (IOException | RuntimeException readFailure) {
                    if (tracker.lastRow < 0) {
                        return null;
                    }
                    return clearRowsFrom(toArgb(destination), tracker.lastRow + 1);
                }
            } finally {
                reader.dispose();
            }
        } catch (PackLoadException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Records the furthest raster row a reader reported as updated during a (failed) read. */
    private static final class RowTracker implements IIOReadUpdateListener {

        private int lastRow = -1;

        @Override
        public void imageUpdate(ImageReader source, BufferedImage theImage, int minX, int minY,
                                int width, int height, int periodX, int periodY, int[] bands) {
            lastRow = Math.max(lastRow, minY + height - 1);
        }

        @Override
        public void passStarted(ImageReader source, BufferedImage theImage, int pass, int minPass,
                                int maxPass, int minX, int minY, int periodX, int periodY, int[] bands) {
        }

        @Override
        public void passComplete(ImageReader source, BufferedImage theImage) {
        }

        @Override
        public void thumbnailPassStarted(ImageReader source, BufferedImage theThumbnail, int pass,
                                         int minPass, int maxPass, int minX, int minY, int periodX,
                                         int periodY, int[] bands) {
        }

        @Override
        public void thumbnailUpdate(ImageReader source, BufferedImage theThumbnail, int minX, int minY,
                                    int width, int height, int periodX, int periodY, int[] bands) {
        }

        @Override
        public void thumbnailPassComplete(ImageReader source, BufferedImage theThumbnail) {
        }
    }

    private static BufferedImage clearRowsFrom(BufferedImage argb, int firstMissingRow) {
        for (int y = firstMissingRow; y < argb.getHeight(); y++) {
            for (int x = 0; x < argb.getWidth(); x++) {
                argb.setRGB(x, y, 0);
            }
        }
        return argb;
    }

    /**
     * The parsed structure of a PNG for the manual decode tiers: header fields, ancillary color
     * data and the concatenated IDAT payload. {@code stride} is the filtered scanline length in
     * bytes WITHOUT the leading filter-type byte.
     */
    private record PngStructure(int width, int height, int bitDepth, int colorType,
                                byte[] palette, byte[] transparency, byte[] idat, int stride) {

        /** The raw buffer covering every filter-prefixed scanline of the image. */
        byte[] newRawBuffer() {
            return new byte[(stride + 1) * height];
        }
    }

    /**
     * Walks the PNG chunk stream and validates the header for the manual decode tiers
     * ({@link #readChecksumTolerant} and {@link #salvageTruncatedPng}). Handles the
     * manually-decodable subset: non-interlaced PNGs, palette images at any legal bit depth,
     * grayscale at 1/2/4/8 bits, and 8-bit gray+alpha / RGB / RGBA images.
     *
     * @return the parsed structure, or null when the bytes are not a PNG, the header is
     *     malformed or over the dimension cap, the format is unsupported, or no IDAT data exists
     */
    private static PngStructure parsePngStructure(byte[] bytes, int maxTextureDim) {
        if (!hasPngSignature(bytes)) {
            return null;
        }
        byte[] ihdr = null;
        byte[] palette = null;
        byte[] transparency = null;
        boolean sawIdat = false;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        int pos = PNG_SIGNATURE.length;
        while (pos + 8 <= bytes.length) {
            int length = readInt(bytes, pos);
            if (length < 0) {
                return null;
            }
            String type = new String(bytes, pos + 4, 4, StandardCharsets.US_ASCII);
            int dataStart = pos + 8;
            int available = (int) Math.min(length, (long) (bytes.length - dataStart));
            switch (type) {
                case "IHDR" -> ihdr = Arrays.copyOfRange(bytes, dataStart, dataStart + Math.min(available, 13));
                case "PLTE" -> palette = Arrays.copyOfRange(bytes, dataStart, dataStart + available);
                case "tRNS" -> transparency = Arrays.copyOfRange(bytes, dataStart, dataStart + available);
                case "IDAT" -> {
                    idat.write(bytes, dataStart, available);
                    sawIdat = true;
                }
                default -> {
                }
            }
            long next = (long) dataStart + length + 4;
            if (next > bytes.length) {
                break;
            }
            pos = (int) next;
        }
        if (ihdr == null || ihdr.length < 13) {
            return null;
        }
        if (!sawIdat) {
            // A PNG with no IDAT chunk at all (e.g. a corrupted chunk type byte) is corruption
            // with nothing recoverable, not a truncated-but-salvageable stream: keep failing
            // loudly instead of silently returning an all-transparent image.
            return null;
        }
        int width = readInt(ihdr, 0);
        int height = readInt(ihdr, 4);
        int bitDepth = ihdr[8] & 0xFF;
        int colorType = ihdr[9] & 0xFF;
        int interlace = ihdr[12] & 0xFF;
        if (width < 1 || height < 1 || width > maxTextureDim || height > maxTextureDim || interlace != 0) {
            return null;
        }
        int bitsPerPixel = bitsPerPixel(colorType, bitDepth);
        if (bitsPerPixel < 0 || (colorType == COLOR_TYPE_PALETTE && palette == null)) {
            return null;
        }
        long stride = ((long) width * bitsPerPixel + 7) / 8;
        long needed = (stride + 1) * height;
        if (needed > Integer.MAX_VALUE - 8) {
            return null;
        }
        return new PngStructure(width, height, bitDepth, colorType, palette, transparency,
            idat.toByteArray(), (int) stride);
    }

    /**
     * Checksum-tolerant FULL decode tier: real server packs deliberately corrupt the Adler32
     * trailer of every texture's (otherwise complete) IDAT zlib stream, which the vanilla
     * client's stb_image-based loader never verifies. The payload is inflated raw - the 2-byte
     * zlib header skipped, the trailer (missing or wrong) ignored entirely - and the decode
     * succeeds only when every scanline inflates and unfilters completely, making the result
     * pixel-identical to a strict decode of the same pixel data.
     *
     * @return the fully decoded image, or null when the bytes are not a manually-decodable PNG,
     *     the stream is truncated or corrupt, or any scanline is malformed - the partial-salvage
     *     tiers own everything short of a complete decode
     */
    private static BufferedImage readChecksumTolerant(byte[] bytes, int maxTextureDim) {
        PngStructure png = parsePngStructure(bytes, maxTextureDim);
        if (png == null) {
            return null;
        }
        byte[] raw = png.newRawBuffer();
        if (!inflateRawFully(png.idat(), raw)) {
            return null;
        }
        return reconstructRows(raw, raw.length, png, true);
    }

    /**
     * Inflates a zlib stream raw: the 2-byte header is validated minimally and skipped, and the
     * Adler32 trailer is never read, so a missing or wrong checksum cannot fail the decode.
     *
     * @return true when the deflate payload produced exactly {@code out.length} bytes; false on
     *     a non-deflate header, a truncated stream (the partial tiers own that case), corrupt
     *     deflate data, or a payload of the wrong size
     */
    private static boolean inflateRawFully(byte[] compressed, byte[] out) {
        // Minimal zlib header validation: CM (low nibble of CMF) must be 8 (deflate) and FDICT
        // must be clear - PNG forbids preset dictionaries. The FCHECK bits are deliberately not
        // verified; they guard a header that packs are known to ship damaged.
        if (compressed.length < 2 || (compressed[0] & 0x0F) != 8 || (compressed[1] & 0x20) != 0) {
            return false;
        }
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed, 2, compressed.length - 2);
        int produced = 0;
        try {
            while (produced < out.length && !inflater.finished()) {
                int n = inflater.inflate(out, produced, out.length - produced);
                produced += n;
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return false;
                }
            }
            return produced == out.length;
        } catch (DataFormatException e) {
            // Corrupt DEFLATE data, not just a bad trailer: never a full decode.
            return false;
        } finally {
            inflater.end();
        }
    }

    /**
     * Final PNG-only fallback: inflates whatever IDAT bytes are present, reconstructs the
     * complete scanlines and leaves missing rows fully transparent. Supports the same formats as
     * {@link #parsePngStructure}.
     *
     * @return the salvaged image, or null when the bytes are not a PNG, use an unsupported
     *     format, or are corrupt rather than merely truncated
     */
    private static BufferedImage salvageTruncatedPng(byte[] bytes, int maxTextureDim) {
        PngStructure png = parsePngStructure(bytes, maxTextureDim);
        if (png == null) {
            return null;
        }
        byte[] raw = png.newRawBuffer();
        int produced = inflateAvailable(png.idat(), raw);
        if (produced < 0) {
            return null;
        }
        return reconstructRows(raw, produced, png, false);
    }

    /**
     * Inflates as much of a possibly-truncated zlib stream as the input allows.
     *
     * @return bytes produced (0 is legal for a stream truncated inside the header), or -1 when
     *     the stream is corrupt rather than truncated and produced nothing usable
     */
    private static int inflateAvailable(byte[] compressed, byte[] out) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        int produced = 0;
        try {
            while (produced < out.length && !inflater.finished()) {
                int n = inflater.inflate(out, produced, out.length - produced);
                produced += n;
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
            }
        } catch (DataFormatException e) {
            // Corrupt data: rows inflated before the error are still usable; nothing at all is not.
            if (produced == 0) {
                return -1;
            }
        } finally {
            inflater.end();
        }
        return produced;
    }

    /**
     * Unfilters scanlines and converts them to ARGB. With {@code requireAllRows} (the
     * checksum-tolerant full-decode tier) every row must be present and valid or the result is
     * null; without it (the salvage tier) decoding stops at the first bad row and missing rows
     * stay transparent.
     */
    private static BufferedImage reconstructRows(byte[] raw, int produced, PngStructure png,
                                                 boolean requireAllRows) {
        int stride = png.stride();
        BufferedImage image = new BufferedImage(png.width(), png.height(), BufferedImage.TYPE_INT_ARGB);
        int rowBytes = stride + 1;
        int completeRows = Math.min(png.height(), produced / rowBytes);
        if (requireAllRows && completeRows < png.height()) {
            return null;
        }
        int bytesPerPixel = Math.max(1, bitsPerPixel(png.colorType(), png.bitDepth()) / 8);
        byte[] previous = new byte[stride];
        byte[] current = new byte[stride];
        for (int y = 0; y < completeRows; y++) {
            int filterType = raw[y * rowBytes] & 0xFF;
            System.arraycopy(raw, y * rowBytes + 1, current, 0, stride);
            if (!unfilterRow(filterType, current, previous, bytesPerPixel)) {
                if (requireAllRows) {
                    // An unknown filter type means wrong data, never a complete valid image.
                    return null;
                }
                break;
            }
            writeRow(image, y, png, current);
            byte[] swap = previous;
            previous = current;
            current = swap;
        }
        return image;
    }

    /**
     * Applies the PNG row filter in place.
     *
     * @return false for an unknown filter type; the caller stops at the last good row
     */
    private static boolean unfilterRow(int filterType, byte[] current, byte[] previous, int bytesPerPixel) {
        switch (filterType) {
            case 0 -> {
                // None
            }
            case 1 -> {
                for (int i = bytesPerPixel; i < current.length; i++) {
                    current[i] += current[i - bytesPerPixel];
                }
            }
            case 2 -> {
                for (int i = 0; i < current.length; i++) {
                    current[i] += previous[i];
                }
            }
            case 3 -> {
                for (int i = 0; i < current.length; i++) {
                    int left = i >= bytesPerPixel ? current[i - bytesPerPixel] & 0xFF : 0;
                    current[i] += (byte) ((left + (previous[i] & 0xFF)) >>> 1);
                }
            }
            case 4 -> {
                for (int i = 0; i < current.length; i++) {
                    int left = i >= bytesPerPixel ? current[i - bytesPerPixel] & 0xFF : 0;
                    int up = previous[i] & 0xFF;
                    int upLeft = i >= bytesPerPixel ? previous[i - bytesPerPixel] & 0xFF : 0;
                    current[i] += (byte) paethPredictor(left, up, upLeft);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int paethPredictor(int left, int up, int upLeft) {
        int p = left + up - upLeft;
        int pa = Math.abs(p - left);
        int pb = Math.abs(p - up);
        int pc = Math.abs(p - upLeft);
        if (pa <= pb && pa <= pc) {
            return left;
        }
        return pb <= pc ? up : upLeft;
    }

    private static void writeRow(BufferedImage image, int y, PngStructure png, byte[] row) {
        int width = png.width();
        int bitDepth = png.bitDepth();
        byte[] palette = png.palette();
        byte[] transparency = png.transparency();
        for (int x = 0; x < width; x++) {
            int argb;
            switch (png.colorType()) {
                case COLOR_TYPE_PALETTE -> argb = paletteColor(sampleAt(row, x, bitDepth), palette, transparency);
                case COLOR_TYPE_GRAY -> {
                    int sample = sampleAt(row, x, bitDepth);
                    // The tRNS sample is compared in the ORIGINAL bit depth's value space,
                    // before expansion to 8 bits.
                    int alpha = matchesTransparentSample(transparency, 0, sample) ? 0 : 0xFF;
                    int v = scaleTo8Bit(sample, bitDepth);
                    argb = (alpha << 24) | (v << 16) | (v << 8) | v;
                }
                case COLOR_TYPE_GRAY_ALPHA -> {
                    int v = row[x * 2] & 0xFF;
                    argb = ((row[x * 2 + 1] & 0xFF) << 24) | (v << 16) | (v << 8) | v;
                }
                case COLOR_TYPE_RGB -> {
                    int r = row[x * 3] & 0xFF;
                    int g = row[x * 3 + 1] & 0xFF;
                    int b = row[x * 3 + 2] & 0xFF;
                    boolean transparent = matchesTransparentSample(transparency, 0, r)
                        && matchesTransparentSample(transparency, 2, g)
                        && matchesTransparentSample(transparency, 4, b);
                    argb = (transparent ? 0 : 0xFF000000) | (r << 16) | (g << 8) | b;
                }
                case COLOR_TYPE_RGBA -> argb = ((row[x * 4 + 3] & 0xFF) << 24) | ((row[x * 4] & 0xFF) << 16)
                    | ((row[x * 4 + 1] & 0xFF) << 8) | (row[x * 4 + 2] & 0xFF);
                default -> argb = 0;
            }
            image.setRGB(x, y, argb);
        }
    }

    private static int paletteColor(int index, byte[] palette, byte[] transparency) {
        if (index * 3 + 2 >= palette.length) {
            // Out-of-palette index in salvaged data: transparent beats failing the whole texture.
            return 0;
        }
        int alpha = transparency != null && index < transparency.length ? transparency[index] & 0xFF : 0xFF;
        return (alpha << 24) | ((palette[index * 3] & 0xFF) << 16)
            | ((palette[index * 3 + 1] & 0xFF) << 8) | (palette[index * 3 + 2] & 0xFF);
    }

    /** Whether a tRNS 16-bit sample at the given offset matches an 8-bit channel value. */
    private static boolean matchesTransparentSample(byte[] transparency, int offset, int value) {
        return transparency != null && transparency.length >= offset + 2
            && (((transparency[offset] & 0xFF) << 8) | (transparency[offset + 1] & 0xFF)) == value;
    }

    private static int sampleAt(byte[] row, int x, int bitDepth) {
        if (bitDepth == 8) {
            return row[x] & 0xFF;
        }
        int samplesPerByte = 8 / bitDepth;
        int packed = row[x / samplesPerByte] & 0xFF;
        int shift = 8 - bitDepth - (x % samplesPerByte) * bitDepth;
        return (packed >> shift) & ((1 << bitDepth) - 1);
    }

    /** Bits per pixel for the manually-decodable subset, or -1 for unsupported combinations. */
    private static int bitsPerPixel(int colorType, int bitDepth) {
        return switch (colorType) {
            // Palette and grayscale share the sub-byte packing rules; some server packs ship 1-bit
            // grayscale font sheets, so low-depth gray is part of the real-world subset.
            case COLOR_TYPE_PALETTE, COLOR_TYPE_GRAY ->
                bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 ? bitDepth : -1;
            case COLOR_TYPE_GRAY_ALPHA -> bitDepth == 8 ? 16 : -1;
            case COLOR_TYPE_RGB -> bitDepth == 8 ? 24 : -1;
            case COLOR_TYPE_RGBA -> bitDepth == 8 ? 32 : -1;
            default -> -1;
        };
    }

    private static boolean hasPngSignature(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        return Arrays.equals(bytes, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
            | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static BufferedImage normalize(BufferedImage source, boolean normalizeEmissiveAlpha) {
        BufferedImage argb = toArgb(source);
        if (!normalizeEmissiveAlpha) {
            return argb;
        }
        // The Hypixel SkyBlock item shader treats alpha 252 as "opaque full-bright"; without the
        // shader the closest static equivalent is plain opaque. Alpha 127 (translucent emissive)
        // stays as-is.
        for (int y = 0; y < argb.getHeight(); y++) {
            for (int x = 0; x < argb.getWidth(); x++) {
                int pixel = argb.getRGB(x, y);
                if ((pixel >>> 24) == EMISSIVE_OPAQUE_ALPHA) {
                    argb.setRGB(x, y, pixel | 0xFF000000);
                }
            }
        }
        return argb;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
            return grayToArgb(source);
        }
        BufferedImage argb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argb.createGraphics();
        try {
            // Src, not the default SrcOver: compositing premultiplies and rounds translucent
            // pixels (e.g. alpha 64, red 111 comes back as red 112). Src copies values exactly.
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return argb;
    }

    /**
     * Manual ARGB conversion for grayscale sources, replicating each raw sample into the color
     * channels. {@code drawImage} would color-manage the JDK's LINEAR gray color space into
     * sRGB for gray+alpha images (gray 40 comes back as 110) while the TYPE_BYTE_GRAY fast blit
     * copies tonal values straight across - and the game's stb-based loader always replicates
     * the raw sample. Copying samples here keeps every gray variant and every decode tier
     * agreeing with the game and with each other. Samples wider than 8 bits (16-bit gray PNGs)
     * scale down linearly.
     */
    private static BufferedImage grayToArgb(BufferedImage source) {
        Raster raster = source.getRaster();
        boolean hasAlpha = source.getColorModel().hasAlpha();
        int grayBits = source.getColorModel().getComponentSize(0);
        int alphaBits = hasAlpha ? source.getColorModel().getComponentSize(1) : 8;
        BufferedImage argb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int v = scaleTo8Bit(raster.getSample(x, y, 0), grayBits);
                int alpha = hasAlpha ? scaleTo8Bit(raster.getSample(x, y, 1), alphaBits) : 0xFF;
                argb.setRGB(x, y, (alpha << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return argb;
    }

    /** Scales a sample of the given bit width to 8 bits ({@code sample * 255 / maxSample}). */
    private static int scaleTo8Bit(int sample, int bits) {
        if (bits == 8) {
            return sample;
        }
        return (int) ((long) sample * 255 / ((1L << bits) - 1));
    }

    /**
     * Crops a decoded flipbook texture to its static first frame (the frame the mcmeta's frames
     * list starts at). See {@link #frame(BufferedImage, int, int, int)} for the crop semantics.
     *
     * @throws PackLoadException when the frame index or size falls outside the texture bounds
     */
    public static BufferedImage firstFrame(BufferedImage image, AnimationMeta meta) {
        return frame(image, frameWidth(image, meta), frameHeight(image, meta), meta.firstFrameIndex());
    }

    /** The effective frame width: the explicit override, defaulting to the texture width. */
    public static int frameWidth(BufferedImage image, AnimationMeta meta) {
        return meta.frameWidth() != null ? meta.frameWidth() : image.getWidth();
    }

    /**
     * The effective frame height: the explicit override, defaulting to the frame width - the
     * square-frames vertical-flipbook convention this library uses everywhere.
     */
    public static int frameHeight(BufferedImage image, AnimationMeta meta) {
        return meta.frameHeight() != null ? meta.frameHeight() : frameWidth(image, meta);
    }

    /**
     * Crops one frame of a flipbook texture, indexed the vanilla way: the frame size cuts the
     * sheet into a grid ({@code width / frameWidth} columns by {@code height / frameHeight} rows)
     * and frames run left to right then top to bottom (row-major), so frame {@code i} sits at
     * column {@code i % columns}, row {@code i / columns}. A single-column vertical flipbook
     * (the common case, {@code frameWidth == sheet width}) reduces to column 0 and
     * {@code row == frameIndex}. The crop is an exact per-pixel copy: like {@link #decode}'s ARGB
     * conversion it composites with {@link AlphaComposite#Src}, so translucent pixels keep their
     * exact channel values instead of drifting by one through SrcOver premultiplication.
     *
     * @throws PackLoadException when the frame index or size falls outside the texture bounds
     */
    public static BufferedImage frame(BufferedImage image, int frameWidth, int frameHeight, int frameIndex) {
        if (frameWidth <= 0 || frameHeight <= 0 || frameIndex < 0
            || frameWidth > image.getWidth() || frameHeight > image.getHeight()) {
            throw new PackLoadException("Animation frame %s is outside texture bounds (%sx%s, frame %sx%s)",
                String.valueOf(frameIndex), String.valueOf(image.getWidth()),
                String.valueOf(image.getHeight()), String.valueOf(frameWidth), String.valueOf(frameHeight));
        }
        int columns = image.getWidth() / frameWidth;
        int rows = image.getHeight() / frameHeight;
        // long arithmetic: an untrusted index could overflow int before the grid bounds check.
        if (frameIndex >= (long) columns * rows) {
            throw new PackLoadException("Animation frame %s is outside texture bounds (%sx%s, frame %sx%s)",
                String.valueOf(frameIndex), String.valueOf(image.getWidth()),
                String.valueOf(image.getHeight()), String.valueOf(frameWidth), String.valueOf(frameHeight));
        }
        int x = (frameIndex % columns) * frameWidth;
        int y = (frameIndex / columns) * frameHeight;
        BufferedImage frame = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = frame.createGraphics();
        try {
            // Src, not the default SrcOver: see toArgb - the crop must copy pixels exactly.
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(image.getSubimage(x, y, frameWidth, frameHeight), 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return frame;
    }
}
