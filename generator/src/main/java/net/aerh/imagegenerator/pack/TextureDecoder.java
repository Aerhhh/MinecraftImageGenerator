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
import java.awt.image.BufferedImage;
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
 * quirk of PNGs whose IDAT zlib stream is truncated (Minecraft itself renders them), optionally
 * applies the Hypixel SkyBlock emissive alpha encoding, and extracts static first frames from
 * animated flipbooks.
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
            // Real-world packs ship PNGs whose IDAT zlib stream is truncated while Minecraft
            // still renders them; salvage whatever rows are recoverable before failing.
            BufferedImage recovered = readPartial(imageBytes, maxTextureDim);
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
     * Final PNG-only fallback: inflates whatever IDAT bytes are present, reconstructs the
     * complete scanlines and leaves missing rows fully transparent. Handles the
     * manually-recoverable subset: non-interlaced PNGs, palette images at any legal bit depth,
     * and 8-bit gray / gray+alpha / RGB / RGBA images.
     *
     * @return the salvaged image, or null when the bytes are not a PNG, use an unsupported
     *     format, or are corrupt rather than merely truncated
     */
    private static BufferedImage salvageTruncatedPng(byte[] bytes, int maxTextureDim) {
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
        byte[] raw = new byte[(int) needed];
        int produced = inflateAvailable(idat.toByteArray(), raw);
        if (produced < 0) {
            return null;
        }
        return reconstructRows(raw, produced, width, height, (int) stride, bitDepth, colorType,
            palette, transparency);
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

    /** Unfilters every complete scanline and converts it to ARGB; missing rows stay transparent. */
    private static BufferedImage reconstructRows(byte[] raw, int produced, int width, int height,
                                                 int stride, int bitDepth, int colorType,
                                                 byte[] palette, byte[] transparency) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int rowBytes = stride + 1;
        int completeRows = Math.min(height, produced / rowBytes);
        int bytesPerPixel = Math.max(1, bitsPerPixel(colorType, bitDepth) / 8);
        byte[] previous = new byte[stride];
        byte[] current = new byte[stride];
        for (int y = 0; y < completeRows; y++) {
            int filterType = raw[y * rowBytes] & 0xFF;
            System.arraycopy(raw, y * rowBytes + 1, current, 0, stride);
            if (!unfilterRow(filterType, current, previous, bytesPerPixel)) {
                break;
            }
            writeRow(image, y, width, bitDepth, colorType, current, palette, transparency);
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

    private static void writeRow(BufferedImage image, int y, int width, int bitDepth, int colorType,
                                 byte[] row, byte[] palette, byte[] transparency) {
        for (int x = 0; x < width; x++) {
            int argb;
            switch (colorType) {
                case COLOR_TYPE_PALETTE -> argb = paletteColor(sampleAt(row, x, bitDepth), palette, transparency);
                case COLOR_TYPE_GRAY -> {
                    int v = row[x] & 0xFF;
                    int alpha = matchesTransparentSample(transparency, 0, v) ? 0 : 0xFF;
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

    /** Bits per pixel for the supported salvage subset, or -1 for unsupported combinations. */
    private static int bitsPerPixel(int colorType, int bitDepth) {
        return switch (colorType) {
            case COLOR_TYPE_PALETTE ->
                bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 ? bitDepth : -1;
            case COLOR_TYPE_GRAY -> bitDepth == 8 ? 8 : -1;
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

    public static BufferedImage firstFrame(BufferedImage image, AnimationMeta meta) {
        int frameWidth = meta.frameWidth() != null ? meta.frameWidth() : image.getWidth();
        // Vanilla default: square frames sized by texture width, stacked vertically.
        int frameHeight = meta.frameHeight() != null ? meta.frameHeight() : frameWidth;
        // long arithmetic: untrusted index * height can overflow int and wrap past the bounds check.
        long y = (long) meta.firstFrameIndex() * frameHeight;
        if (frameWidth <= 0 || frameHeight <= 0 || meta.firstFrameIndex() < 0
            || frameWidth > image.getWidth() || y + frameHeight > image.getHeight()) {
            throw new PackLoadException("Animation frame %s is outside texture bounds (%sx%s, frame %sx%s)",
                String.valueOf(meta.firstFrameIndex()), String.valueOf(image.getWidth()),
                String.valueOf(image.getHeight()), String.valueOf(frameWidth), String.valueOf(frameHeight));
        }
        BufferedImage frame = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = frame.createGraphics();
        try {
            graphics.drawImage(image.getSubimage(0, (int) y, frameWidth, frameHeight), 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return frame;
    }
}
