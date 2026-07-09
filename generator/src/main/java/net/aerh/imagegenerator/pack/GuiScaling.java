package net.aerh.imagegenerator.pack;

/**
 * GUI sprite scaling behavior declared in a texture's {@code .png.mcmeta} under
 * {@code gui.scaling}, matching the vanilla schema: {@code stretch} (the default when no
 * mcmeta or no scaling entry exists), {@code tile}, or {@code nine_slice}.
 */
public sealed interface GuiScaling permits GuiScaling.Stretch, GuiScaling.Tile, GuiScaling.NineSlice {

    /**
     * Hard upper bound on declared sprite dimensions and border sizes. Keeps every downstream
     * size computation comfortably inside int range regardless of configured texture limits.
     */
    int MAX_DIMENSION = 65_536;

    /** Whole texture stretched to the target rectangle. */
    record Stretch() implements GuiScaling {
    }

    /**
     * Texture repeated from the top-left at its nominal size, last tiles clipped.
     *
     * @param width  nominal sprite width in GUI px; must be positive
     * @param height nominal sprite height in GUI px; must be positive
     */
    record Tile(int width, int height) implements GuiScaling {

        public Tile {
            requireDimension(width, "width");
            requireDimension(height, "height");
        }
    }

    /**
     * Corners drawn 1:1, edges and center tiled (or stretched when {@code stretchInner}).
     *
     * @param width        nominal sprite width in GUI px; must be positive
     * @param height       nominal sprite height in GUI px; must be positive
     * @param border       per-side border sizes in GUI px; opposite sides must fit within the dimension
     * @param stretchInner whether edges and center stretch instead of tiling
     */
    record NineSlice(int width, int height, Border border, boolean stretchInner) implements GuiScaling {

        public NineSlice {
            requireDimension(width, "width");
            requireDimension(height, "height");
            if (border == null) {
                throw new IllegalArgumentException("nine_slice border must be provided");
            }
            if (border.left() + border.right() > width) {
                throw new IllegalArgumentException(
                    "nine_slice horizontal borders (" + border.left() + "+" + border.right()
                        + ") exceed width " + width);
            }
            if (border.top() + border.bottom() > height) {
                throw new IllegalArgumentException(
                    "nine_slice vertical borders (" + border.top() + "+" + border.bottom()
                        + ") exceed height " + height);
            }
        }

        /**
         * Per-side nine-slice border sizes in GUI px; each side must be non-negative.
         */
        public record Border(int left, int top, int right, int bottom) {

            public Border {
                requireBorderSide(left, "left");
                requireBorderSide(top, "top");
                requireBorderSide(right, "right");
                requireBorderSide(bottom, "bottom");
            }

            private static void requireBorderSide(int value, String side) {
                if (value < 0 || value > MAX_DIMENSION) {
                    throw new IllegalArgumentException(
                        "nine_slice border '" + side + "' must be between 0 and " + MAX_DIMENSION + ", got " + value);
                }
            }
        }
    }

    private static void requireDimension(int value, String member) {
        if (value <= 0 || value > MAX_DIMENSION) {
            throw new IllegalArgumentException(
                "gui scaling '" + member + "' must be between 1 and " + MAX_DIMENSION + ", got " + value);
        }
    }
}
