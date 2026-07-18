package net.aerh.imagegenerator.util;

import lombok.experimental.UtilityClass;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * GIF encoding with PER-FRAME delays, for tick-timed pack texture animations whose frames hold
 * for different times (a shiny-strip frames list like {@code [0..16, {index: 0, time: 100}]}
 * needs one long-hold frame). Uniform-delay frame lists delegate to the established
 * {@link ImageUtil#toGifBytes} path unchanged.
 *
 * <p>The variable-delay writer applies the same correctness rules that path learned the hard
 * way: frame disposal is {@code restoreToBackgroundColor} so transparent regions never ghost
 * between frames, metadata derives from {@code TYPE_INT_ARGB}, and because the JDK GIF encoder
 * drops EVERY pixel with alpha below 255, visible pixels are forced fully opaque before
 * encoding (fully transparent pixels stay transparent). Callers must pre-composite translucent
 * art onto the scene before encoding - full scene canvases already are.
 */
@UtilityClass
public class AnimatedGifEncoder {

    /**
     * Encodes frames into a looping animated GIF honoring one delay per frame.
     *
     * @param frames   the frames, in playback order
     * @param delaysMs one delay per frame, in milliseconds (GIF timing granularity is 10 ms;
     *                 values round down to whole centiseconds, minimum 1)
     * @return the GIF bytes
     * @throws IOException              when encoding fails
     * @throws IllegalArgumentException when frames and delays are empty or their sizes differ
     */
    public static byte[] encode(List<BufferedImage> frames, List<Integer> delaysMs) throws IOException {
        if (frames == null || frames.isEmpty() || delaysMs == null || delaysMs.size() != frames.size()) {
            throw new IllegalArgumentException("Frames and delays must be non-empty and the same size");
        }
        if (delaysMs.stream().distinct().count() == 1) {
            // The established uniform-delay encoder path, byte-identical to every existing GIF.
            return ImageUtil.toGifBytes(frames, delaysMs.getFirst(), true);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = gifWriter();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writer.setOutput(imageOutput);
            writer.prepareWriteSequence(null);
            for (int index = 0; index < frames.size(); index++) {
                IIOMetadata metadata = frameMetadata(writer, writeParam, delaysMs.get(index), index == 0);
                writer.writeToSequence(new IIOImage(toGifCompatibleFrame(frames.get(index)), null, metadata), writeParam);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static ImageWriter gifWriter() throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("gif");
        if (!writers.hasNext()) {
            throw new IOException("No GIF image writer is available");
        }
        return writers.next();
    }

    /**
     * Per-frame metadata: the frame's own delay (centiseconds), disposal
     * {@code restoreToBackgroundColor} (clears each frame before the next so transparent
     * regions never accumulate ghosting) and, on the FIRST frame only, the NETSCAPE
     * infinite-loop extension. The loop block is emitted once (the GIF89a convention, and the
     * single-block shape the delegated uniform-delay path produces): repeating it on every frame
     * is dead weight some decoders ignore and strict validators flag. Metadata derives from
     * {@code TYPE_INT_ARGB} - deriving from other types makes the JDK encoder palettize against
     * the wrong color table.
     */
    private static IIOMetadata frameMetadata(ImageWriter writer, ImageWriteParam writeParam, int delayMs,
                                             boolean firstFrame) throws IOException {
        IIOMetadata metadata = writer.getDefaultImageMetadata(
            ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB), writeParam);
        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);

        IIOMetadataNode graphicControl = childNode(root, "GraphicControlExtension");
        graphicControl.setAttribute("disposalMethod", "restoreToBackgroundColor");
        graphicControl.setAttribute("userInputFlag", "FALSE");
        graphicControl.setAttribute("transparentColorFlag", "FALSE");
        graphicControl.setAttribute("delayTime", Integer.toString(Math.max(1, delayMs / 10)));
        graphicControl.setAttribute("transparentColorIndex", "0");

        if (firstFrame) {
            IIOMetadataNode applicationExtensions = childNode(root, "ApplicationExtensions");
            IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
            loop.setAttribute("applicationID", "NETSCAPE");
            loop.setAttribute("authenticationCode", "2.0");
            // Loop count 0 = loop forever.
            loop.setUserObject(new byte[]{0x1, 0x0, 0x0});
            applicationExtensions.appendChild(loop);
        }

        metadata.setFromTree(formatName, root);
        return metadata;
    }

    private static IIOMetadataNode childNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    /**
     * Binary-alpha ARGB normalization: the JDK GIF encoder maps every pixel with alpha below
     * 255 to the transparent index, so partially transparent artwork would silently disappear.
     * Visible pixels force fully opaque; fully transparent pixels stay transparent. The input
     * is never mutated - callers reuse frames for the {@code GeneratedObject} frame list.
     */
    private static BufferedImage toGifCompatibleFrame(BufferedImage frame) {
        boolean argb = frame.getType() == BufferedImage.TYPE_INT_ARGB;
        BufferedImage source = frame;
        if (!argb) {
            source = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = source.createGraphics();
            try {
                graphics.drawImage(frame, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        if (!hasPartialAlpha(source)) {
            return source;
        }
        BufferedImage normalized = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                if ((pixel >>> 24) != 0) {
                    pixel |= 0xFF000000;
                }
                normalized.setRGB(x, y, pixel);
            }
        }
        return normalized;
    }

    private static boolean hasPartialAlpha(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha != 0 && alpha != 255) {
                    return true;
                }
            }
        }
        return false;
    }
}
