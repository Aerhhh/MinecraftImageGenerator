package net.aerh.imagegenerator.impl.scene;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bundles the output of other generators into one composite image without a layout
 * specification: members line up side by side ({@link #row()}), stacked ({@link #column()}), or
 * in a row-major grid ({@link #grid(int)}), with default spacing, margins, and centered
 * alignment. Nothing beyond the arrangement needs configuring.
 * <p>
 * Members are added either as {@link Generator}s (rendered with this scene's
 * {@link GenerationContext} when the scene renders) or as pre-rendered
 * {@link GeneratedObject}s. Scenes are themselves generators, so they nest: a row of tooltips
 * can be one member of a larger column.
 * <p>
 * Animated members compose exactly like the existing composite paths: every animated member
 * becomes one source on a shared tick timeline, so mixed static, uniform-delay, and tick-timed
 * members produce one GIF with every member's authored timing intact; an all-static scene stays
 * a PNG. A member's footprint is its static image's (or first frame's) size.
 */
@Slf4j
public class MinecraftSceneGenerator implements Generator {

    /** Default gap between adjacent members, matching the composite image builder. */
    public static final int DEFAULT_SPACING_PX = 25;
    /** Default border margin around the whole scene, matching the composite image builder. */
    public static final int DEFAULT_MARGIN_PX = 15;

    private final Arrangement arrangement;
    private final int columns;
    private final int spacingPx;
    private final int marginPx;
    private final Alignment alignment;
    private final List<Member> members;

    private MinecraftSceneGenerator(Arrangement arrangement, int columns, int spacingPx, int marginPx,
                                    Alignment alignment, List<Member> members) {
        this.arrangement = arrangement;
        this.columns = columns;
        this.spacingPx = spacingPx;
        this.marginPx = marginPx;
        this.alignment = alignment;
        this.members = List.copyOf(members);
    }

    /**
     * Starts a scene whose members lay out left to right in one row.
     *
     * @return a builder for the row scene
     */
    public static Builder row() {
        return new Builder(Arrangement.ROW, 0);
    }

    /**
     * Starts a scene whose members stack top to bottom in one column.
     *
     * @return a builder for the column scene
     */
    public static Builder column() {
        return new Builder(Arrangement.COLUMN, 0);
    }

    /**
     * Starts a scene whose members fill a grid row-major: member {@code i} lands in row
     * {@code i / columns}, column {@code i % columns}. Each grid column is as wide as its widest
     * member and each grid row as tall as its tallest, so differently sized members line up on
     * shared edges.
     *
     * @param columns the number of grid columns
     * @return a builder for the grid scene
     * @throws IllegalArgumentException when {@code columns} is not positive
     */
    public static Builder grid(int columns) {
        if (columns < 1) {
            throw new IllegalArgumentException("columns must be at least 1, got: " + columns);
        }
        return new Builder(Arrangement.GRID, columns);
    }

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        if (members.isEmpty()) {
            throw new GeneratorException("Scene has no members");
        }
        log.debug("Rendering {} scene with {} members", arrangement, members.size());

        List<GeneratedObject> objects = new ArrayList<>(members.size());
        for (Member member : members) {
            objects.add(member.resolve(generationContext));
        }

        Layout layout = computeLayout(objects);
        try {
            return SceneCompositor.compose(layout.placed(), layout.width(), layout.height());
        } catch (IOException exception) {
            throw new GeneratorException("Failed to encode scene GIF: " + exception.getMessage(), exception);
        }
    }

    /**
     * Computes each member's canvas position from the arrangement. All three arrangements are
     * grid layouts at heart: a row is one grid row, a column one grid column, so one pass
     * computes per-column widths and per-row heights and places members in their cells.
     */
    private Layout computeLayout(List<GeneratedObject> objects) {
        int columnCount = switch (arrangement) {
            case ROW -> objects.size();
            case COLUMN -> 1;
            case GRID -> Math.min(columns, objects.size());
        };
        int rowCount = (objects.size() + columnCount - 1) / columnCount;

        int[] columnWidths = new int[columnCount];
        int[] rowHeights = new int[rowCount];
        for (int index = 0; index < objects.size(); index++) {
            BufferedImage image = objects.get(index).getImage();
            int column = index % columnCount;
            int row = index / columnCount;
            columnWidths[column] = Math.max(columnWidths[column], image.getWidth());
            rowHeights[row] = Math.max(rowHeights[row], image.getHeight());
        }

        int[] columnOrigins = new int[columnCount];
        int contentWidth = 0;
        for (int column = 0; column < columnCount; column++) {
            columnOrigins[column] = marginPx + contentWidth + column * spacingPx;
            contentWidth += columnWidths[column];
        }
        int[] rowOrigins = new int[rowCount];
        int contentHeight = 0;
        for (int row = 0; row < rowCount; row++) {
            rowOrigins[row] = marginPx + contentHeight + row * spacingPx;
            contentHeight += rowHeights[row];
        }

        List<SceneCompositor.PlacedObject> placed = new ArrayList<>(objects.size());
        for (int index = 0; index < objects.size(); index++) {
            GeneratedObject object = objects.get(index);
            BufferedImage image = object.getImage();
            int column = index % columnCount;
            int row = index / columnCount;
            int x = columnOrigins[column] + alignment.offset(columnWidths[column], image.getWidth());
            int y = rowOrigins[row] + alignment.offset(rowHeights[row], image.getHeight());
            placed.add(new SceneCompositor.PlacedObject(object, x, y));
        }

        int width = contentWidth + (columnCount - 1) * spacingPx + 2 * marginPx;
        int height = contentHeight + (rowCount - 1) * spacingPx + 2 * marginPx;
        return new Layout(placed, width, height);
    }

    private record Layout(List<SceneCompositor.PlacedObject> placed, int width, int height) {
    }

    /**
     * How a member sits inside its cell on the axis where the cell is larger than the member: a
     * row's cells share the row height, a column's cells the column width, and a grid cell both.
     */
    public enum Alignment {
        /** Flush with the cell's top or left edge. */
        START,
        /** Centered in the cell; the default. */
        CENTER,
        /** Flush with the cell's bottom or right edge. */
        END;

        private int offset(int cellSize, int memberSize) {
            return switch (this) {
                case START -> 0;
                case CENTER -> (cellSize - memberSize) / 2;
                case END -> cellSize - memberSize;
            };
        }
    }

    private enum Arrangement {
        ROW, COLUMN, GRID
    }

    /**
     * One scene member: either a deferred generator or a pre-rendered object. Exactly one of the
     * two is set.
     */
    private record Member(@Nullable Generator generator, @Nullable GeneratedObject prerendered) {

        GeneratedObject resolve(@Nullable GenerationContext context) {
            if (generator == null) {
                return prerendered;
            }
            try {
                return generator.generate(context);
            } catch (Exception exception) {
                throw new GeneratorException("Error generating object from generator: " + exception.getMessage(), exception);
            }
        }
    }

    /**
     * Builds {@link MinecraftSceneGenerator} instances. Members are placed in the order they are
     * added, which is also the draw order.
     */
    public static class Builder implements ClassBuilder<MinecraftSceneGenerator> {

        private final Arrangement arrangement;
        private final int columns;
        private final List<Member> members = new ArrayList<>();
        private int spacingPx = DEFAULT_SPACING_PX;
        private int marginPx = DEFAULT_MARGIN_PX;
        private Alignment alignment = Alignment.CENTER;

        private Builder(Arrangement arrangement, int columns) {
            this.arrangement = arrangement;
            this.columns = columns;
        }

        /**
         * Adds a member rendered when the scene renders, with the scene's generation context.
         *
         * @param generator the generator whose output joins the scene
         * @return this builder
         */
        public Builder add(@NotNull Generator generator) {
            this.members.add(new Member(generator, null));
            return this;
        }

        /**
         * Adds an already rendered member.
         *
         * @param object the pre-rendered output to place in the scene
         * @return this builder
         */
        public Builder add(@NotNull GeneratedObject object) {
            this.members.add(new Member(null, object));
            return this;
        }

        /**
         * Gap between adjacent members in canvas px (default {@link #DEFAULT_SPACING_PX}).
         *
         * @throws IllegalArgumentException when the spacing is negative
         */
        public Builder withSpacing(int spacingPx) {
            if (spacingPx < 0) {
                throw new IllegalArgumentException("spacing must not be negative, got: " + spacingPx);
            }
            this.spacingPx = spacingPx;
            return this;
        }

        /**
         * Border margin around the whole scene in canvas px (default {@link #DEFAULT_MARGIN_PX}).
         *
         * @throws IllegalArgumentException when the margin is negative
         */
        public Builder withMargin(int marginPx) {
            if (marginPx < 0) {
                throw new IllegalArgumentException("margin must not be negative, got: " + marginPx);
            }
            this.marginPx = marginPx;
            return this;
        }

        /** In-cell alignment for members smaller than their cell (default {@link Alignment#CENTER}). */
        public Builder withAlignment(@NotNull Alignment alignment) {
            this.alignment = alignment;
            return this;
        }

        @Override
        public MinecraftSceneGenerator build() {
            return new MinecraftSceneGenerator(arrangement, columns, spacingPx, marginPx, alignment, members);
        }
    }
}
