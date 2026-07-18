package net.aerh.imagegenerator.impl.scene;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The slot-cell grid arrangement: a row-major grid whose every cell is padded to at least the
 * vanilla 18 GUI-px slot pitch (in canvas px), so small members line up on a slot grid the way an
 * inventory does. Each column is as wide as its widest member (but never below the slot pitch) and
 * each row as tall as its tallest, members align inside their padded cell, and the members compose
 * through {@link SceneCompositor} so an animated member turns the grid into a GIF.
 *
 * <p>The template-free {@link MinecraftSceneGenerator} grid handles every other cell mode; this
 * generator exists only for the minimum-cell padding, which that builder does not express.
 */
@Slf4j
final class SlotGridGenerator implements Generator {

    private final List<Generator> members;
    private final int columns;
    private final int spacingPx;
    private final MinecraftSceneGenerator.Alignment alignment;
    private final int minCellPx;

    SlotGridGenerator(List<Generator> members, int columns, int spacingPx,
                      MinecraftSceneGenerator.Alignment alignment, int minCellPx) {
        this.members = List.copyOf(members);
        this.columns = columns;
        this.spacingPx = spacingPx;
        this.alignment = alignment;
        this.minCellPx = minCellPx;
    }

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        if (members.isEmpty()) {
            throw new GeneratorException("Slot grid has no members");
        }
        log.debug("Rendering slot grid with {} members in {} columns", members.size(), columns);

        List<GeneratedObject> objects = new ArrayList<>(members.size());
        for (Generator member : members) {
            try {
                objects.add(member.generate(generationContext));
            } catch (Exception exception) {
                throw new GeneratorException("Error rendering slot grid member: " + exception.getMessage(), exception);
            }
        }

        int columnCount = Math.min(columns, objects.size());
        int rowCount = (objects.size() + columnCount - 1) / columnCount;

        int[] columnWidths = new int[columnCount];
        int[] rowHeights = new int[rowCount];
        Arrays.fill(columnWidths, minCellPx);
        Arrays.fill(rowHeights, minCellPx);
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
            columnOrigins[column] = contentWidth + column * spacingPx;
            contentWidth += columnWidths[column];
        }
        int[] rowOrigins = new int[rowCount];
        int contentHeight = 0;
        for (int row = 0; row < rowCount; row++) {
            rowOrigins[row] = contentHeight + row * spacingPx;
            contentHeight += rowHeights[row];
        }

        List<SceneCompositor.PlacedObject> placed = new ArrayList<>(objects.size());
        for (int index = 0; index < objects.size(); index++) {
            GeneratedObject object = objects.get(index);
            BufferedImage image = object.getImage();
            int column = index % columnCount;
            int row = index / columnCount;
            int x = columnOrigins[column] + alignOffset(columnWidths[column], image.getWidth());
            int y = rowOrigins[row] + alignOffset(rowHeights[row], image.getHeight());
            placed.add(new SceneCompositor.PlacedObject(object, x, y));
        }

        int width = contentWidth + (columnCount - 1) * spacingPx;
        int height = contentHeight + (rowCount - 1) * spacingPx;
        try {
            return SceneCompositor.compose(placed, width, height);
        } catch (IOException exception) {
            throw new GeneratorException("Failed to encode slot grid GIF: " + exception.getMessage(), exception);
        }
    }

    private int alignOffset(int cellSize, int memberSize) {
        return switch (alignment) {
            case START -> 0;
            case CENTER -> (cellSize - memberSize) / 2;
            case END -> cellSize - memberSize;
        };
    }
}
