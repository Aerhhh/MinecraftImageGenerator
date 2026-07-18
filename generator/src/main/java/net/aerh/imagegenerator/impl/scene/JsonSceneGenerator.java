package net.aerh.imagegenerator.impl.scene;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.impl.MinecraftContainerGenerator;
import net.aerh.imagegenerator.impl.MinecraftContainerGenerator.TitleRun;
import net.aerh.imagegenerator.impl.MinecraftHudLineGenerator;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.CustomModelData;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a declarative JSON scene: a document of named regions, each region the output of one
 * other generator (a tooltip, item, container, HUD line stack, or a nested row/column/grid
 * arrangement), positioned either at explicit GUI-px coordinates or anchored to another region's
 * edge. The scene resolves at {@link #render} time: every region's member generator is built and
 * rendered first (measure), anchored positions are then resolved against the measured footprints,
 * the whole layout is shifted so it sits flush in a margin, and the members compose through
 * {@link SceneCompositor} - so an animated member turns the whole scene into a GIF exactly like
 * the template-free arrangements.
 *
 * <p>Construct instances through {@link net.aerh.imagegenerator.impl.scene.MinecraftSceneGenerator#fromScene(String)}
 * and its pack-aware overload; the document is parsed and validated eagerly there, so malformed
 * scenes fail loudly at construction, never with a silently wrong render.
 */
@Slf4j
class JsonSceneGenerator implements Generator {

    // The document text keys the render cache; the parsed model and the repository handle are
    // transient so they never enter it (the model is derived from the text, the repository is a
    // seam like every other generator's).
    private final String json;
    @Nullable
    private final PackId packId;
    private final transient Scene scene;
    private final transient PackRepository packRepository;

    JsonSceneGenerator(String json, Scene scene, @Nullable PackId packId, @Nullable PackRepository packRepository) {
        this.json = json;
        this.scene = scene;
        this.packId = packId;
        this.packRepository = packRepository;
    }

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        int pixelSize = 2 * scene.scale();
        int marginPx = scene.marginGuiPx() * pixelSize;
        List<Region> regions = scene.regions();
        log.debug("Rendering JSON scene with {} regions at scale {}", regions.size(), scene.scale());

        // Measure: build every region's member generator and render it once. A member's footprint
        // is its static image's (or first frame's) size, matching the compositor.
        List<GeneratedObject> objects = new ArrayList<>(regions.size());
        for (Region region : regions) {
            Generator generator = region.content().toGenerator(scene.scale(), packId, packRepository);
            objects.add(resolveMember(region.name(), generator, generationContext));
        }

        long[] positions = resolvePositions(regions, objects, pixelSize);
        Layout layout = normalize(regions, objects, positions, marginPx);

        try {
            return SceneCompositor.compose(layout.placed(), layout.width(), layout.height());
        } catch (IOException exception) {
            throw new GeneratorException("Failed to encode scene GIF: " + exception.getMessage(), exception);
        }
    }

    /** Renders one member, wrapping any failure with the region name so the offender is named. */
    private static GeneratedObject resolveMember(String name, Generator generator,
                                                 @Nullable GenerationContext context) {
        try {
            return generator.generate(context);
        } catch (Exception exception) {
            throw new GeneratorException("Error rendering region `" + name + "`: " + exception.getMessage(), exception);
        }
    }

    /**
     * Resolves each region's raw top-left in canvas px. Explicitly placed regions land at their
     * coordinates scaled by the pixel size; anchored regions resolve against their target's raw
     * position and both footprints, in topological order (the parser has already rejected unknown
     * targets and cycles, so an anchored region's target is always resolvable). Returns one packed
     * {@code (x << 32) | y} entry per region, index-aligned with the region list.
     */
    private long[] resolvePositions(List<Region> regions, List<GeneratedObject> objects, int pixelSize) {
        int count = regions.size();
        long[] positions = new long[count];
        boolean[] resolved = new boolean[count];
        Map<String, Integer> indexByName = new HashMap<>();
        for (int i = 0; i < count; i++) {
            indexByName.put(regions.get(i).name(), i);
        }

        int remaining = count;
        while (remaining > 0) {
            int progressed = 0;
            for (int i = 0; i < count; i++) {
                if (resolved[i]) {
                    continue;
                }
                Placement placement = regions.get(i).placement();
                if (placement instanceof AtPlacement at) {
                    positions[i] = pack(at.x() * pixelSize, at.y() * pixelSize);
                    resolved[i] = true;
                    progressed++;
                } else if (placement instanceof AnchorPlacement anchor) {
                    int targetIndex = indexByName.get(anchor.target());
                    if (!resolved[targetIndex]) {
                        continue;
                    }
                    positions[i] = anchorPosition(anchor, positions[targetIndex],
                        objects.get(targetIndex).getImage(), objects.get(i).getImage(), pixelSize);
                    resolved[i] = true;
                    progressed++;
                }
            }
            remaining -= progressed;
            if (progressed == 0) {
                // Unreachable: the parser rejects cycles and unknown targets before render.
                throw new GeneratorException("Unresolvable anchor chain in scene");
            }
        }
        return positions;
    }

    /** Resolves an anchored region's raw top-left against its target. */
    private static long anchorPosition(AnchorPlacement anchor, long targetPosition,
                                       BufferedImage targetImage, BufferedImage image, int pixelSize) {
        int tx = unpackX(targetPosition);
        int ty = unpackY(targetPosition);
        int tw = targetImage.getWidth();
        int th = targetImage.getHeight();
        int w = image.getWidth();
        int h = image.getHeight();

        int x;
        int y;
        switch (anchor.edge()) {
            case RIGHT -> {
                x = tx + tw;
                y = ty + alignOffset(anchor.align(), th, h);
            }
            case LEFT -> {
                x = tx - w;
                y = ty + alignOffset(anchor.align(), th, h);
            }
            case BOTTOM -> {
                y = ty + th;
                x = tx + alignOffset(anchor.align(), tw, w);
            }
            case TOP -> {
                y = ty - h;
                x = tx + alignOffset(anchor.align(), tw, w);
            }
            default -> throw new GeneratorException("Unknown anchor edge " + anchor.edge());
        }
        return pack(x + anchor.dx() * pixelSize, y + anchor.dy() * pixelSize);
    }

    /** Offset of a member on the perpendicular axis of its anchor edge, in canvas px. */
    private static int alignOffset(Align align, int targetSpan, int memberSpan) {
        return switch (align) {
            case START -> 0;
            case CENTER -> (targetSpan - memberSpan) / 2;
            case END -> targetSpan - memberSpan;
        };
    }

    /**
     * Shifts the resolved layout so its top-left-most corner sits exactly at the margin, then
     * sizes the canvas to cover the deepest member plus the margin on every side, and orders the
     * members by ascending z (ties in declaration order) for the compositor's draw order.
     */
    private Layout normalize(List<Region> regions, List<GeneratedObject> objects, long[] positions, int marginPx) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (long position : positions) {
            minX = Math.min(minX, unpackX(position));
            minY = Math.min(minY, unpackY(position));
        }

        int count = regions.size();
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        // Stable sort on z keeps ties in declaration order, so the compositor draws equal-z
        // members in the order they appear in the document.
        Arrays.sort(order, (a, b) -> Integer.compare(regions.get(a).z(), regions.get(b).z()));

        List<SceneCompositor.PlacedObject> placed = new ArrayList<>(count);
        int width = 0;
        int height = 0;
        for (int i : order) {
            int x = unpackX(positions[i]) - minX + marginPx;
            int y = unpackY(positions[i]) - minY + marginPx;
            BufferedImage image = objects.get(i).getImage();
            placed.add(new SceneCompositor.PlacedObject(objects.get(i), x, y));
            width = Math.max(width, x + image.getWidth() + marginPx);
            height = Math.max(height, y + image.getHeight() + marginPx);
        }
        return new Layout(placed, width, height);
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackY(long packed) {
        return (int) packed;
    }

    private record Layout(List<SceneCompositor.PlacedObject> placed, int width, int height) {
    }

    // ----------------------------------------------------------------- model

    /** A parsed, validated scene document. */
    record Scene(int scale, int marginGuiPx, List<Region> regions) {
    }

    /**
     * One region: a uniquely named member with its draw order and placement. Nested arrangement
     * regions carry no placement or z (the parser rejects those keys); they draw in declaration
     * order inside their parent, so their placement is {@code null}.
     */
    record Region(String name, int z, @Nullable Placement placement, Content content) {
    }

    /** Which side of a scene the anchor edge attaches on / which axis an alignment runs. */
    enum Edge {
        LEFT, RIGHT, TOP, BOTTOM
    }

    /** In-cell / cross-axis alignment. */
    enum Align {
        START, CENTER, END;

        MinecraftSceneGenerator.Alignment toArrangementAlignment() {
            return switch (this) {
                case START -> MinecraftSceneGenerator.Alignment.START;
                case CENTER -> MinecraftSceneGenerator.Alignment.CENTER;
                case END -> MinecraftSceneGenerator.Alignment.END;
            };
        }
    }

    /** How the three template-free arrangements lay their members out. */
    enum ArrangementKind {
        ROW, COLUMN, GRID
    }

    /** A region's placement: explicit coordinates or an anchor to another region. */
    sealed interface Placement permits AtPlacement, AnchorPlacement {
    }

    /** Explicit top-left in GUI px. */
    record AtPlacement(int x, int y) implements Placement {
    }

    /** An anchor attaching this region outside {@code target}'s {@code edge}, in GUI-px offsets. */
    record AnchorPlacement(String target, Edge edge, Align align, int dx, int dy) implements Placement {
    }

    /**
     * A region's content: the member generator it produces at render time, with the scene's scale
     * and pack threaded through every builder that accepts them.
     */
    sealed interface Content permits TooltipContent, ItemContent, ContainerContent, HudContent, ArrangementContent {

        Generator toGenerator(int scale, @Nullable PackId packId, @Nullable PackRepository packRepository);
    }

    /**
     * A tooltip region: either a full NBT/components object parsed through
     * {@link MinecraftTooltipGenerator.Builder#parseNbtJson}, or the simple fields (the region
     * name doubles as the tooltip title, since a region already names itself uniquely).
     */
    record TooltipContent(@Nullable JsonObject nbt, @Nullable String title, @Nullable String rarity,
                          @Nullable String lore, @Nullable Integer maxLineLength) implements Content {

        @Override
        public Generator toGenerator(int scale, @Nullable PackId packId, @Nullable PackRepository packRepository) {
            MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder();
            if (nbt != null) {
                builder.parseNbtJson(nbt);
            } else {
                builder.withName(title);
                if (rarity != null) {
                    builder.withRarity(Rarity.byName(rarity));
                }
                if (lore != null) {
                    builder.withItemLore(lore);
                }
                if (maxLineLength != null) {
                    builder.withMaxLineLength(maxLineLength);
                }
            }
            return builder
                .withScaleFactor(scale)
                .withPack(packId)
                .withPackRepository(packRepository)
                .build();
        }
    }

    /** An item region addressed by item id or item-model reference, with the usual modifiers. */
    record ItemContent(@Nullable String item, @Nullable String model, @Nullable Integer customModelData,
                       boolean enchanted, boolean animatedTextures) implements Content {

        @Override
        public Generator toGenerator(int scale, @Nullable PackId packId, @Nullable PackRepository packRepository) {
            MinecraftItemGenerator.Builder builder = new MinecraftItemGenerator.Builder();
            if (item != null) {
                builder.withItem(item);
            } else {
                builder.withItemModel(model);
            }
            if (customModelData != null) {
                builder.withCustomModelData(new CustomModelData(
                    List.of((float) (int) customModelData), List.of(), List.of(), List.of()));
            }
            // The item generator has no scale factor: it renders on the fixed 256-per-16 canvas.
            return builder
                .isEnchanted(enchanted)
                .withAnimatedTextures(animatedTextures)
                .withPack(packId)
                .withPackRepository(packRepository)
                .build();
        }
    }

    /** A container region built from a menu recipe subtree, re-serialized through the recipe parser. */
    record ContainerContent(String recipeJson, boolean animatedTextures) implements Content {

        @Override
        public Generator toGenerator(int scale, @Nullable PackId packId, @Nullable PackRepository packRepository) {
            return MinecraftContainerGenerator.fromRecipe(recipeJson)
                .withScaleFactor(scale)
                .withAnimatedTextures(animatedTextures)
                .withPack(packId)
                .withPackRepository(packRepository)
                .build();
        }
    }

    /** A HUD region: a stack of bossbar-anchored centered lines. */
    record HudContent(List<List<TitleRun>> lines, @Nullable Integer guiWidth,
                      @Nullable Boolean textShadow) implements Content {

        @Override
        public Generator toGenerator(int scale, @Nullable PackId packId, @Nullable PackRepository packRepository) {
            MinecraftHudLineGenerator.Builder builder = new MinecraftHudLineGenerator.Builder();
            for (List<TitleRun> line : lines) {
                builder.withLine(line);
            }
            if (guiWidth != null) {
                builder.withGuiWidth(guiWidth);
            }
            if (textShadow != null) {
                builder.withTextShadow(textShadow);
            }
            return builder
                .withScaleFactor(scale)
                .withPack(packId)
                .withPackRepository(packRepository)
                .build();
        }
    }

    /**
     * A row/column/grid arrangement of nested regions. Non-slot arrangements reuse the
     * template-free {@link MinecraftSceneGenerator} builders (margin zero, spacing converted from
     * GUI px to canvas px); a grid whose cell mode is {@code slot} pads each cell to at least the
     * 18 GUI-px slot pitch through {@link SlotGridGenerator}.
     */
    record ArrangementContent(ArrangementKind kind, List<Region> regions, int spacingGuiPx,
                              Align alignment, int columns, boolean slotCell) implements Content {

        @Override
        public Generator toGenerator(int scale, @Nullable PackId packId, @Nullable PackRepository packRepository) {
            int pixelSize = 2 * scale;
            int spacingPx = spacingGuiPx * pixelSize;
            List<Generator> members = new ArrayList<>(regions.size());
            for (Region region : regions) {
                members.add(region.content().toGenerator(scale, packId, packRepository));
            }

            if (kind == ArrangementKind.GRID && slotCell) {
                return new SlotGridGenerator(members, columns, spacingPx,
                    alignment.toArrangementAlignment(), 18 * pixelSize);
            }

            MinecraftSceneGenerator.Builder builder = switch (kind) {
                case ROW -> MinecraftSceneGenerator.row();
                case COLUMN -> MinecraftSceneGenerator.column();
                case GRID -> MinecraftSceneGenerator.grid(columns);
            };
            builder.withMargin(0).withSpacing(spacingPx).withAlignment(alignment.toArrangementAlignment());
            for (Generator member : members) {
                builder.add(member);
            }
            return builder.build();
        }
    }
}
