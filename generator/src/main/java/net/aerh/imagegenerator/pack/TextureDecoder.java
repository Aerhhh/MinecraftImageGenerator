package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;
import net.aerh.imagegenerator.exception.PackLoadException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes pack textures defensively: image dimensions are read from the header and checked
 * against the cap BEFORE full pixel decode (image-bomb guard). Also handles the pack's emissive
 * alpha encoding and static first-frame extraction from animated flipbooks.
 */
@UtilityClass
public class TextureDecoder {

    private static final int EMISSIVE_OPAQUE_ALPHA = 252;

    public static BufferedImage decode(byte[] pngBytes, int maxTextureDim) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(pngBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new PackLoadException("Pack texture is not a decodable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > maxTextureDim || height > maxTextureDim || width < 1 || height < 1) {
                    throw new PackLoadException("Pack texture dimensions %sx%s exceed limit %s",
                        String.valueOf(width), String.valueOf(height), String.valueOf(maxTextureDim));
                }
                return normalize(reader.read(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new PackLoadException("Failed to decode pack texture", e);
        }
    }

    private static BufferedImage normalize(BufferedImage source) {
        BufferedImage argb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        // The pack's item shader treats alpha 252 as "opaque full-bright"; without the shader the
        // closest static equivalent is plain opaque. Alpha 127 (translucent emissive) stays as-is.
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
