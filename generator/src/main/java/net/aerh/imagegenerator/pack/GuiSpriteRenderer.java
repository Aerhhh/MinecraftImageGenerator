package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;

import java.awt.image.BufferedImage;

/**
 * Renders a GUI sprite texture to a target size following the vanilla scaling algorithm for
 * the sprite's {@link GuiScaling} mode. All sizes are in GUI px (one texel = one GUI px inside
 * nine-slice borders); callers upscale the result to canvas pixels afterwards.
 *
 * <p>Vanilla semantics implemented here: nearest sampling with pixel-center mapping for
 * stretches, tiling from the top-left with the last tile clipped, nine-slice borders clamped
 * to half the target (cropping corners to their outer pixels, never scaling them), and
 * {@code stretch_inner} stretching edges and center instead of tiling. Textures whose pixel
 * size differs from the declared nominal size are first normalized to it.</p>
 */
@UtilityClass
public class GuiSpriteRenderer {

    /**
     * Vanilla has no tiling bound (the GPU eats the quads); this cap keeps hostile tiny tile
     * sizes from turning one render into millions of pixel copies.
     */
    private static final long MAX_TILES_PER_RENDER = 65_536;

    public static BufferedImage render(BufferedImage texture, GuiScaling scaling, int targetWidth, int targetHeight) {
        if (texture == null) {
            throw new IllegalArgumentException("texture must not be null");
        }
        if (scaling == null) {
            throw new IllegalArgumentException("scaling must not be null");
        }
        requireTargetDimension(targetWidth, "targetWidth");
        requireTargetDimension(targetHeight, "targetHeight");

        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        switch (scaling) {
            case GuiScaling.Stretch ignored -> stretchRegion(result, 0, 0, targetWidth, targetHeight,
                texture, 0, 0, texture.getWidth(), texture.getHeight());
            case GuiScaling.Tile tile -> renderTile(result, texture, tile);
            case GuiScaling.NineSlice nineSlice -> renderNineSlice(result, texture, nineSlice);
        }
        return result;
    }

    private static void renderTile(BufferedImage result, BufferedImage texture, GuiScaling.Tile tile) {
        BufferedImage source = normalize(texture, tile.width(), tile.height());
        tileRegion(result, 0, 0, result.getWidth(), result.getHeight(),
            source, 0, 0, tile.width(), tile.height(), new TileBudget());
    }

    private static void renderNineSlice(BufferedImage result, BufferedImage texture, GuiScaling.NineSlice nineSlice) {
        int nominalWidth = nineSlice.width();
        int nominalHeight = nineSlice.height();
        BufferedImage source = normalize(texture, nominalWidth, nominalHeight);
        GuiScaling.NineSlice.Border border = nineSlice.border();
        int targetWidth = result.getWidth();
        int targetHeight = result.getHeight();

        int left = Math.min(border.left(), targetWidth / 2);
        int right = Math.min(border.right(), targetWidth / 2);
        int top = Math.min(border.top(), targetHeight / 2);
        int bottom = Math.min(border.bottom(), targetHeight / 2);

        copyRegion(result, 0, 0, source, 0, 0, left, top);
        copyRegion(result, targetWidth - right, 0, source, nominalWidth - right, 0, right, top);
        copyRegion(result, 0, targetHeight - bottom, source, 0, nominalHeight - bottom, left, bottom);
        copyRegion(result, targetWidth - right, targetHeight - bottom,
            source, nominalWidth - right, nominalHeight - bottom, right, bottom);

        int innerSourceX = border.left();
        int innerSourceY = border.top();
        int innerSourceWidth = nominalWidth - border.left() - border.right();
        int innerSourceHeight = nominalHeight - border.top() - border.bottom();
        int innerTargetWidth = targetWidth - left - right;
        int innerTargetHeight = targetHeight - top - bottom;
        boolean stretchInner = nineSlice.stretchInner();
        TileBudget budget = new TileBudget();

        fillSegment(result, left, 0, innerTargetWidth, top,
            source, innerSourceX, 0, innerSourceWidth, top, stretchInner, budget);
        fillSegment(result, left, targetHeight - bottom, innerTargetWidth, bottom,
            source, innerSourceX, nominalHeight - bottom, innerSourceWidth, bottom, stretchInner, budget);
        fillSegment(result, 0, top, left, innerTargetHeight,
            source, 0, innerSourceY, left, innerSourceHeight, stretchInner, budget);
        fillSegment(result, targetWidth - right, top, right, innerTargetHeight,
            source, nominalWidth - right, innerSourceY, right, innerSourceHeight, stretchInner, budget);
        fillSegment(result, left, top, innerTargetWidth, innerTargetHeight,
            source, innerSourceX, innerSourceY, innerSourceWidth, innerSourceHeight, stretchInner, budget);
    }

    private static void fillSegment(BufferedImage destination, int destinationX, int destinationY,
                                    int destinationWidth, int destinationHeight,
                                    BufferedImage source, int sourceX, int sourceY,
                                    int sourceWidth, int sourceHeight,
                                    boolean stretchInner, TileBudget budget) {
        if (destinationWidth <= 0 || destinationHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        if (stretchInner) {
            stretchRegion(destination, destinationX, destinationY, destinationWidth, destinationHeight,
                source, sourceX, sourceY, sourceWidth, sourceHeight);
        } else {
            tileRegion(destination, destinationX, destinationY, destinationWidth, destinationHeight,
                source, sourceX, sourceY, sourceWidth, sourceHeight, budget);
        }
    }

    private static void tileRegion(BufferedImage destination, int destinationX, int destinationY,
                                   int destinationWidth, int destinationHeight,
                                   BufferedImage source, int sourceX, int sourceY,
                                   int tileWidth, int tileHeight, TileBudget budget) {
        if (destinationWidth <= 0 || destinationHeight <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            return;
        }
        long tilesAcross = (destinationWidth + tileWidth - 1L) / tileWidth;
        long tilesDown = (destinationHeight + tileHeight - 1L) / tileHeight;
        budget.consume(tilesAcross * tilesDown);

        for (int offsetY = 0; offsetY < destinationHeight; offsetY += tileHeight) {
            int copyHeight = Math.min(tileHeight, destinationHeight - offsetY);
            for (int offsetX = 0; offsetX < destinationWidth; offsetX += tileWidth) {
                int copyWidth = Math.min(tileWidth, destinationWidth - offsetX);
                copyRegion(destination, destinationX + offsetX, destinationY + offsetY,
                    source, sourceX, sourceY, copyWidth, copyHeight);
            }
        }
    }

    private static void stretchRegion(BufferedImage destination, int destinationX, int destinationY,
                                      int destinationWidth, int destinationHeight,
                                      BufferedImage source, int sourceX, int sourceY,
                                      int sourceWidth, int sourceHeight) {
        if (destinationWidth <= 0 || destinationHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        for (int y = 0; y < destinationHeight; y++) {
            int sampledY = sourceY + (int) ((2L * y + 1) * sourceHeight / (2L * destinationHeight));
            for (int x = 0; x < destinationWidth; x++) {
                int sampledX = sourceX + (int) ((2L * x + 1) * sourceWidth / (2L * destinationWidth));
                destination.setRGB(destinationX + x, destinationY + y, source.getRGB(sampledX, sampledY));
            }
        }
    }

    private static void copyRegion(BufferedImage destination, int destinationX, int destinationY,
                                   BufferedImage source, int sourceX, int sourceY, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                destination.setRGB(destinationX + x, destinationY + y, source.getRGB(sourceX + x, sourceY + y));
            }
        }
    }

    private static BufferedImage normalize(BufferedImage texture, int nominalWidth, int nominalHeight) {
        if (texture.getWidth() == nominalWidth && texture.getHeight() == nominalHeight) {
            return texture;
        }
        BufferedImage normalized = new BufferedImage(nominalWidth, nominalHeight, BufferedImage.TYPE_INT_ARGB);
        stretchRegion(normalized, 0, 0, nominalWidth, nominalHeight,
            texture, 0, 0, texture.getWidth(), texture.getHeight());
        return normalized;
    }

    private static void requireTargetDimension(int value, String name) {
        if (value <= 0 || value > GuiScaling.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                name + " must be between 1 and " + GuiScaling.MAX_DIMENSION + ", got " + value);
        }
    }

    private static final class TileBudget {

        private long remaining = MAX_TILES_PER_RENDER;

        void consume(long tiles) {
            remaining -= tiles;
            if (remaining < 0) {
                throw new IllegalArgumentException(
                    "gui sprite render exceeds the " + MAX_TILES_PER_RENDER + " tile cap");
            }
        }
    }
}
