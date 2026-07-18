package net.aerh.imagegenerator.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.item.InventoryItem;
import net.aerh.imagegenerator.pack.AnimationTimeline;
import net.aerh.imagegenerator.pack.CustomModelData;
import net.aerh.imagegenerator.pack.PackAnimatedVisual;
import net.aerh.imagegenerator.pack.PackAnimation;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackItemVisual;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.parser.inventory.InventoryStringParser;
import net.aerh.imagegenerator.util.AnimatedGifEncoder;
import net.aerh.imagegenerator.text.PackGlyphDispatcher;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.Colors;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Renders a vanilla-geometry generic chest container screen ("chest menu") as a static image,
 * including the title-glyph background technique large server packs use: a menu whose entire
 * background is glyph art drawn by the TITLE through a custom font, over a fully transparent
 * {@code generic_54} container texture.
 *
 * <p><b>Geometry (vanilla-exact, in GUI px):</b> the GUI rect is 176 wide and
 * {@code 114 + 18 * rows} tall (rows 1..6). The top-left slot interior sits at (8, 18) with an
 * 18 px pitch and 16x16 interiors. The title draws at (8, 6) UNCLIPPED - it may extend past
 * every edge of the GUI rect - in {@code #404040} by default, with no shadow, exactly like the
 * vanilla container title. Items render ABOVE the title art (vanilla z-order: container
 * texture, then title text, then items), and stack count badges above items.
 *
 * <p><b>Canvas:</b> the canvas is the GUI rect expanded (in whole GUI px) to cover the measured
 * title-line extents, so full-bleed menu art reached through glyph ascents/heights and negative
 * advances is never clipped. Slot and item coordinates stay anchored to the GUI rect origin
 * wherever it lands on the canvas. One GUI px covers {@code 2 * scaleFactor} canvas px,
 * consistent with the tooltip renderer.
 *
 * <p><b>Background:</b> with an active pack that overrides
 * {@code minecraft:textures/gui/container/generic_54.png}, the texture is drawn as the base
 * layer exactly like the vanilla client stitches it - the chest section (texture rows 0 to
 * {@code rows * 18 + 17}) above the bottom/player-inventory section (96 texture rows starting
 * at v = 126) - sampled in the vanilla 256x256-normalized texture space and nearest-neighbor
 * scaled (a fully transparent override, a common pack style, simply paints nothing). Without a pack,
 * or when the pack does not override the texture, the procedural vanilla-style chrome shared
 * with {@link MinecraftInventoryGenerator} is drawn instead, slot outlines included.
 *
 * <p><b>Elements models:</b> slot item specs that resolve to elements-based pack models render
 * through the GUI projection directly at this generator's pixel size (no intermediate
 * downscale; see {@link Builder#withFullGuiRotations} for gui rotations beyond identity and
 * the mirror), and {@code oversized_in_gui} art anchors on the slot center exactly like the
 * vanilla client - the model-space [0, 16] box maps onto the slot and overflow spans the
 * neighboring slots. The canvas does NOT expand for oversized item art (it clips at the canvas
 * edge); only title-extent expansion grows the canvas. Item modifiers (enchant, hover,
 * durability) do not apply to elements renders this wave. Slots accept per-slot
 * {@link CustomModelData} via {@link Builder#withSlot(int, String, CustomModelData)} for
 * dispatch-node evaluation; the recipe format carries none.
 *
 * <p><b>Out of scope</b> (documented, not rendered): hover/selected slot highlights, the player
 * inventory section (the rows grid only; the remaining GUI rect is background), spectator
 * header/footer composition helpers (callers compose those via multiple title runs), and
 * animation (animated slot items contribute their first frame).
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftContainerGenerator implements Generator {

    /** Vanilla generic container GUI rect width in GUI px. */
    public static final int GUI_WIDTH = 176;
    /** Vanilla generic container GUI rect height in GUI px is {@code 114 + 18 * rows}. */
    public static final int BASE_GUI_HEIGHT = 114;
    /** Slot grid pitch in GUI px. */
    public static final int SLOT_PITCH = 18;
    /** Columns of the generic chest grid. */
    public static final int COLUMNS = 9;
    /** Vanilla default container title color. */
    public static final ChatColor DEFAULT_TITLE_COLOR = ChatColor.of(0x404040);

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 6;
    private static final int SLOT_ORIGIN_X = 8;
    private static final int SLOT_ORIGIN_Y = 18;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int SLOT_INTERIOR = 16;
    /** Vanilla samples container art in a 256x256-normalized texture space. */
    private static final double TEXTURE_UV_BASE = 256.0;
    /** Texture row where the vanilla bottom/player-inventory section of {@code generic_54} starts. */
    private static final int BOTTOM_SECTION_V = 126;
    /** Height of the vanilla bottom/player-inventory section in GUI px. */
    private static final int BOTTOM_SECTION_HEIGHT = 96;
    private static final Set<String> RECIPE_KEYS = Set.of("rows", "title", "slots");
    private static final Set<String> TITLE_RUN_KEYS = Set.of("text", "font", "color", "bold", "italic");
    /**
     * Lightweight resource-location shape check for recipe font ids (namespace optional),
     * mirroring the pack layer's rules closely enough to reject transcription typos loudly at
     * parse time. Ids that pass but resolve to no pack font still fall back to the built-in
     * fonts per the dispatcher policy.
     */
    private static final Pattern FONT_ID = Pattern.compile("(?:[a-z0-9_.-]{1,64}:)?[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");
    /** Resized slot textures for the fallback chrome, keyed by canvas slot size. */
    private static final Map<Integer, BufferedImage> SLOT_TEXTURES = new ConcurrentHashMap<>();

    private final int rows;
    private final List<TitleRun> title;
    /** Slot index (1-based, row-major) to raw item spec; sorted so the cache key is stable. */
    private final SortedMap<Integer, String> slots;
    /**
     * Slot index to the custom model data its item evaluates against; slots absent here use
     * {@link CustomModelData#EMPTY}. Sorted so the cache key is stable.
     */
    private final SortedMap<Integer, CustomModelData> slotCustomModelData;
    private final int scaleFactor;
    // Final non-transient so it enters the render cache key: the flag changes rendered pixels
    // for slots whose models carry unsupported gui rotations.
    private final boolean fullGuiRotations;
    // Final non-transient so the flag enters the reflective render cache key.
    private final boolean animatedTextures;
    // packId is final non-transient so it enters the render cache key; the repository reference
    // is transient so instances never split it.
    @Nullable
    private final PackId packId;
    @ToString.Exclude
    private final transient PackRepository packRepository;
    /**
     * Per-instance dedupe of identical slot item specs, mirroring the inventory generator's
     * slot visual cache: without it a 54-slot menu of the same enchanted item runs the full
     * item pipeline (~180 eagerly generated glint frames per invocation) once per slot instead
     * of once per distinct spec - the exact timeout that cache was added to fix. Transient and
     * initialized inline so it never enters the reflective render cache key.
     */
    @ToString.Exclude
    private final transient Map<ItemVisualKey, BufferedImage> itemVisualCache = new ConcurrentHashMap<>();
    /**
     * Per-instance dedupe of elements-model rasters by item name and custom model data
     * (modifiers never affect an elements render). {@link Optional#empty()} records a spec that
     * resolved to a flat sprite or a vanilla item, so the elements probe runs once per distinct
     * name-and-data pair.
     */
    @ToString.Exclude
    private final transient Map<ElementsRasterKey, Optional<PackItemVisual.ElementsRaster>> elementsRasterCache = new ConcurrentHashMap<>();

    /** Cache key for {@link #elementsRasterCache}: the item name plus its evaluated data. */
    private record ElementsRasterKey(String itemName, CustomModelData data) {
    }

    /**
     * Everything that determines a slot item's rendered appearance, the slot's custom model
     * data included (it drives flat-sprite dispatch through the item pipeline); the amount is
     * deliberately absent - it only affects the count badge drawn per slot, never the item
     * visual.
     */
    private record ItemVisualKey(String itemName, @Nullable String extraContent, @Nullable Integer durabilityPercent,
                                 CustomModelData customModelData) {
    }

    /**
     * One styled run of a composed text line - the container title here, HUD lines in
     * {@link MinecraftHudLineGenerator}. Runs concatenate into a single line rendered through
     * the same segment machinery as tooltip lines, so pack font glyphs (including
     * negative-advance padding fonts) work exactly as they do in tooltips.
     *
     * @param text   the run's text (required; may be empty)
     * @param fontId optional resource-pack font id (e.g. {@code mypack:menu}); null uses the
     *               default font (which a pack may still override via {@code minecraft:default})
     * @param color  optional text color; null uses the rendering generator's default - the
     *               vanilla container title {@link #DEFAULT_TITLE_COLOR} here, white for HUD
     *               lines
     * @param bold   whether the run renders bold
     * @param italic whether the run renders italic
     */
    public record TitleRun(String text, @Nullable String fontId, @Nullable ChatColor color,
                           boolean bold, boolean italic) {

        public TitleRun {
            Objects.requireNonNull(text, "text");
        }

        /** A plain run: default font, default color, no styles. */
        public static TitleRun of(String text) {
            return new TitleRun(text, null, null, false, false);
        }

        /** A run in a specific (pack) font with default color and no styles. */
        public static TitleRun of(String text, @Nullable String fontId) {
            return new TitleRun(text, fontId, null, false, false);
        }

        /**
         * Converts this run to a tooltip segment, the single run-to-segment conversion shared
         * by every compositor that renders run lists through the tooltip line machinery.
         *
         * @param defaultColor the color runs without an explicit {@link #color()} render in
         */
        ColorSegment toSegment(ChatColor defaultColor) {
            return ColorSegment.builder()
                .withText(text)
                .withColor(color != null ? color : defaultColor)
                .withPackFontId(fontId)
                .isBold(bold)
                .isItalic(italic)
                .build();
        }
    }

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        log.debug("Rendering container ({})", this);

        if (animatedTextures) {
            GeneratedObject animated = renderAnimated(generationContext);
            if (animated != null) {
                return animated;
            }
        }
        return renderStatic(generationContext);
    }

    /** The historical static render, byte-identical with the animated-textures flag off. */
    private GeneratedObject renderStatic(@Nullable GenerationContext generationContext) {
        int pixelSize = 2 * scaleFactor;
        Layout layout = computeLayout(pixelSize);
        BufferedImage canvas = new BufferedImage(layout.canvasWidth(), layout.canvasHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            drawBackground(graphics, layout.originX(), layout.originY(), pixelSize, layout.guiHeight());
            if (layout.titleLine() != null) {
                layout.titleLine().drawLineOnto(graphics, 0,
                    layout.originX() + TITLE_X * pixelSize, layout.originY() + TITLE_Y * pixelSize);
            }
            drawItems(graphics, layout.originX(), layout.originY(), pixelSize, generationContext);
        } finally {
            graphics.dispose();
        }

        log.debug("Rendered container image (dimensions {}x{})", canvas.getWidth(), canvas.getHeight());
        return new GeneratedObject(canvas);
    }

    /**
     * The animated-textures render: the pack container background's animation and every
     * texture-animated slot item join one shared scene timeline (LCM of the cycles, capped per
     * {@link AnimationTimeline}); each step composes the full scene - background frame, title,
     * items - exactly in the static z-order, with slot visuals resolved once and reused across
     * steps. Null when nothing in the scene is texture-animated (the static render runs
     * instead), and on an encoding failure (warned; the static render is the fallback).
     */
    @Nullable
    private GeneratedObject renderAnimated(@Nullable GenerationContext generationContext) {
        int pixelSize = 2 * scaleFactor;
        PackAnimation backgroundAnimation = PackId.isActive(packId)
            ? repository().resolveContainerBackgroundAnimation(packId).orElse(null)
            : null;
        List<SlotPlacement> placements = resolvePlacements(pixelSize, generationContext);
        boolean anyAnimatedSlot = placements.stream().anyMatch(placement ->
            placement.visual() instanceof SlotVisualSource.AnimatedRaster
                || placement.visual() instanceof SlotVisualSource.AnimatedImage);
        if (backgroundAnimation == null && !anyAnimatedSlot) {
            return null;
        }

        // Timeline sources in deterministic order: the background first (when animated), then
        // every animated slot in placement order.
        List<List<Integer>> sources = new ArrayList<>();
        int backgroundSource = -1;
        if (backgroundAnimation != null) {
            backgroundSource = sources.size();
            sources.add(backgroundAnimation.frameTicks());
        }
        int[] placementSources = new int[placements.size()];
        for (int index = 0; index < placements.size(); index++) {
            placementSources[index] = switch (placements.get(index).visual()) {
                case SlotVisualSource.AnimatedRaster animated -> {
                    sources.add(animated.stepTicks());
                    yield sources.size() - 1;
                }
                case SlotVisualSource.AnimatedImage animated -> {
                    sources.add(animated.stepTicks());
                    yield sources.size() - 1;
                }
                default -> -1;
            };
        }
        AnimationTimeline timeline = AnimationTimeline.of(sources);

        Layout layout = computeLayout(pixelSize);
        BufferedImage staticBackground = backgroundAnimation == null && PackId.isActive(packId)
            ? repository().resolveContainerBackground(packId).orElse(null)
            : null;
        List<BufferedImage> frames = new ArrayList<>(timeline.steps().size());
        for (AnimationTimeline.Step step : timeline.steps()) {
            BufferedImage canvas = new BufferedImage(layout.canvasWidth(), layout.canvasHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = canvas.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                BufferedImage background = backgroundAnimation != null
                    ? backgroundAnimation.frameImage(step.framePositions().get(backgroundSource))
                    : staticBackground;
                drawResolvedBackground(graphics, layout.originX(), layout.originY(), pixelSize,
                    layout.guiHeight(), background);
                if (layout.titleLine() != null) {
                    layout.titleLine().drawLineOnto(graphics, 0,
                        layout.originX() + TITLE_X * pixelSize, layout.originY() + TITLE_Y * pixelSize);
                }
                drawPlacements(graphics, layout.originX(), layout.originY(), pixelSize,
                    placements, placementSources, step);
            } finally {
                graphics.dispose();
            }
            frames.add(canvas);
        }

        List<Integer> delaysMs = timeline.stepDelaysMillis();
        try {
            byte[] gifData = AnimatedGifEncoder.encode(frames, delaysMs);
            log.debug("Rendered texture-animated container ({} frames)", frames.size());
            return new GeneratedObject(gifData, frames, delaysMs);
        } catch (IOException e) {
            // Warn, not error: the render still succeeds through the static path.
            log.warn("Failed to encode animated container, falling back to the static render", e);
            return null;
        }
    }

    /** One slot's resolved visual for the animated compose. */
    private sealed interface SlotVisualSource {

        /** A static elements raster, drawn with its slot-anchored offsets. */
        record StaticRaster(PackItemVisual.ElementsRaster raster) implements SlotVisualSource {
        }

        /** A static item image, scaled into the slot interior like the classic pipeline. */
        record StaticImage(BufferedImage image) implements SlotVisualSource {
        }

        /** A texture-animated elements raster: one raster per timeline step of its own cycle. */
        record AnimatedRaster(List<PackItemVisual.ElementsRaster> steps,
                              List<Integer> stepTicks) implements SlotVisualSource {
        }

        /** A texture-animated item image: one frame per timeline step of its own cycle. */
        record AnimatedImage(List<BufferedImage> steps, List<Integer> stepTicks) implements SlotVisualSource {
        }
    }

    /** One occupied slot of the animated compose: index, count badge and resolved visual. */
    private record SlotPlacement(int slotIndex, int amount, SlotVisualSource visual) {
    }

    /**
     * Resolves every occupied slot's visual for the animated compose, mirroring
     * {@link #drawItems}' iteration exactly (parse order, per-slot custom model data, null and
     * malformed entries skipped) so placements draw in the same order the static path paints.
     * Identical specs share one resolution via per-call caches.
     */
    private List<SlotPlacement> resolvePlacements(int pixelSize, @Nullable GenerationContext generationContext) {
        List<SlotPlacement> placements = new ArrayList<>();
        if (slots.isEmpty()) {
            return placements;
        }
        List<InventoryItem> items = new InventoryStringParser(rows * COLUMNS).parse(buildInventoryString());
        Map<ElementsRasterKey, Optional<PackAnimatedVisual>> animationCache = new HashMap<>();
        Map<ItemVisualKey, GeneratedObject> generatedCache = new HashMap<>();
        for (InventoryItem item : items) {
            if (item.getItemName().equalsIgnoreCase("null")) {
                continue;
            }
            int[] itemSlots = item.getSlot();
            int[] amounts = item.getAmount();
            if (itemSlots == null || amounts == null || itemSlots.length != amounts.length) {
                continue;
            }
            for (int index = 0; index < itemSlots.length; index++) {
                CustomModelData data = slotCustomModelData.getOrDefault(itemSlots[index], CustomModelData.EMPTY);
                SlotVisualSource visual = resolveSlotVisualSource(item, data, pixelSize, generationContext,
                    animationCache, generatedCache);
                if (visual != null) {
                    placements.add(new SlotPlacement(itemSlots[index], amounts[index], visual));
                }
            }
        }
        return placements;
    }

    /**
     * Resolves one slot's visual source: elements-based specs probe the animated resolution
     * first (falling back to their static raster), everything else runs the shared item
     * pipeline with the animated-textures flag - a texture-animated result carries per-frame
     * delays, which convert to the tick durations the scene timeline consumes.
     */
    @Nullable
    private SlotVisualSource resolveSlotVisualSource(InventoryItem item, CustomModelData data, int pixelSize,
                                                     @Nullable GenerationContext generationContext,
                                                     Map<ElementsRasterKey, Optional<PackAnimatedVisual>> animationCache,
                                                     Map<ItemVisualKey, GeneratedObject> generatedCache) {
        PackItemVisual.ElementsRaster staticRaster = resolveElementsRaster(item, data, pixelSize);
        if (staticRaster != null) {
            PackAnimatedVisual animation = animationCache.computeIfAbsent(
                    new ElementsRasterKey(item.getItemName(), data),
                    key -> repository().resolveItemVisualAnimation(packId, key.itemName(), key.data(), null,
                        pixelSize, fullGuiRotations))
                .orElse(null);
            if (animation != null && animation.steps().getFirst() instanceof PackItemVisual.ElementsRaster) {
                List<PackItemVisual.ElementsRaster> steps = animation.steps().stream()
                    .map(PackItemVisual.ElementsRaster.class::cast)
                    .toList();
                return new SlotVisualSource.AnimatedRaster(steps, animation.stepTicks());
            }
            return new SlotVisualSource.StaticRaster(staticRaster);
        }
        ItemVisualKey visualKey = new ItemVisualKey(item.getItemName(), item.getExtraContent(),
            item.getDurabilityPercent(), data);
        GeneratedObject generated = generatedCache.computeIfAbsent(visualKey,
            key -> MinecraftInventoryGenerator.generateSlotObject(item, generationContext,
                PackId.isActive(packId) ? packId : null, packRepository, key.customModelData(), true));
        if (generated.getFrameDelaysMs() != null) {
            List<Integer> stepTicks = generated.getFrameDelaysMs().stream()
                .map(AnimationTimeline::millisToTicks)
                .toList();
            return new SlotVisualSource.AnimatedImage(generated.getAnimationFrames(), stepTicks);
        }
        // A static flat item resolved here with the animated-textures flag is byte-identical to
        // the static-flag pipeline result (a non-animated item's renderAnimatedTextures returns
        // null and the static path runs). Seed the instance cache the static render reads so a
        // fall-through static render - the flag is on but nothing in the scene animates - reuses
        // this result instead of running the item pipeline a second time per slot.
        itemVisualCache.putIfAbsent(visualKey, generated.getImage());
        return new SlotVisualSource.StaticImage(generated.getImage());
    }

    /** Canvas x of a 0-based slot offset's 16x16 interior top-left, anchored to the GUI origin. */
    private static int slotInteriorX(int slotOffset, int originX, int pixelSize) {
        return originX + (SLOT_ORIGIN_X + SLOT_PITCH * (slotOffset % COLUMNS)) * pixelSize;
    }

    /** Canvas y of a 0-based slot offset's 16x16 interior top-left, anchored to the GUI origin. */
    private static int slotInteriorY(int slotOffset, int originY, int pixelSize) {
        return originY + (SLOT_ORIGIN_Y + SLOT_PITCH * (slotOffset / COLUMNS)) * pixelSize;
    }

    /**
     * Draws one timeline step's slot items in placement order with the exact draw calls of the
     * static path: rasters at their slot-anchored offsets, images scaled into the slot
     * interior, count badges above.
     */
    private void drawPlacements(Graphics2D graphics, int originX, int originY, int pixelSize,
                                List<SlotPlacement> placements, int[] placementSources,
                                AnimationTimeline.Step step) {
        for (int index = 0; index < placements.size(); index++) {
            SlotPlacement placement = placements.get(index);
            int slotOffset = placement.slotIndex() - 1;
            int x = slotInteriorX(slotOffset, originX, pixelSize);
            int y = slotInteriorY(slotOffset, originY, pixelSize);
            int position = placementSources[index] >= 0 ? step.framePositions().get(placementSources[index]) : 0;
            switch (placement.visual()) {
                case SlotVisualSource.StaticRaster source -> graphics.drawImage(source.raster().image(),
                    x + source.raster().offsetX(), y + source.raster().offsetY(), null);
                case SlotVisualSource.AnimatedRaster source -> {
                    PackItemVisual.ElementsRaster raster = source.steps().get(position);
                    graphics.drawImage(raster.image(), x + raster.offsetX(), y + raster.offsetY(), null);
                }
                case SlotVisualSource.StaticImage source -> graphics.drawImage(source.image(),
                    x, y, SLOT_INTERIOR * pixelSize, SLOT_INTERIOR * pixelSize, null);
                case SlotVisualSource.AnimatedImage source -> graphics.drawImage(source.steps().get(position),
                    x, y, SLOT_INTERIOR * pixelSize, SLOT_INTERIOR * pixelSize, null);
            }
            if (placement.amount() > 1) {
                MinecraftInventoryGenerator.drawStackCount(
                    graphics, placement.amount(), x - pixelSize, y - pixelSize, pixelSize);
            }
        }
    }

    /**
     * The canvas layout of one render: the title line (built once), the GUI rect origin on the
     * title-expanded canvas and the canvas dimensions. Shared by the static render and the
     * per-step animated compose so their geometry can never drift.
     */
    private record Layout(MinecraftTooltip titleLine, int originX, int originY,
                          int canvasWidth, int canvasHeight, int guiHeight) {
    }

    private Layout computeLayout(int pixelSize) {
        int guiHeight = BASE_GUI_HEIGHT + SLOT_PITCH * rows;
        int guiWidthPx = GUI_WIDTH * pixelSize;
        int guiHeightPx = guiHeight * pixelSize;

        MinecraftTooltip titleLine = title.isEmpty() ? null : buildTitleLine();
        int overdrawLeft = 0;
        int overdrawTop = 0;
        int overdrawRight = 0;
        int overdrawBottom = 0;
        if (titleLine != null) {
            // The title line origin sits at GUI (8, 6): its y is the LINE TOP (the baseline
            // lands at 13), matching the vanilla drawString origin. Expansion is measured from
            // the full art extents and rounded up to whole GUI px so the GUI rect stays
            // GUI-aligned on the canvas.
            MinecraftTooltip.LineExtents extents = titleLine.measureLineExtents(0);
            overdrawLeft = ceilToWholeGuiPx(-(TITLE_X * pixelSize + extents.minX()), pixelSize);
            overdrawTop = ceilToWholeGuiPx(-(TITLE_Y * pixelSize + extents.artTop()), pixelSize);
            overdrawRight = ceilToWholeGuiPx(TITLE_X * pixelSize + extents.maxX() - guiWidthPx, pixelSize);
            overdrawBottom = ceilToWholeGuiPx(TITLE_Y * pixelSize + extents.artBottom() - guiHeightPx, pixelSize);
        }

        int originX = overdrawLeft * pixelSize;
        int originY = overdrawTop * pixelSize;
        return new Layout(titleLine, originX, originY,
            originX + guiWidthPx + overdrawRight * pixelSize,
            originY + guiHeightPx + overdrawBottom * pixelSize,
            guiHeight);
    }

    /** Canvas px rounded UP to whole GUI px of overdraw; never negative. */
    private static int ceilToWholeGuiPx(double canvasPx, int pixelSize) {
        return Math.max(0, (int) Math.ceil(canvasPx / pixelSize));
    }

    /**
     * The title as a single borderless, shadowless tooltip line: measurement and drawing both
     * run through the exact Wave 2 segment machinery (pack glyph dispatch included), so title
     * runs behave identically to tooltip text.
     */
    private MinecraftTooltip buildTitleLine() {
        List<ColorSegment> segments = new ArrayList<>(title.size());
        for (TitleRun run : title) {
            segments.add(run.toSegment(DEFAULT_TITLE_COLOR));
        }
        return MinecraftTooltip.builder()
            .setRenderBorder(false)
            .hasFirstLinePadding(false)
            .withScaleFactor(scaleFactor)
            .withTextShadow(false)
            .withPackFontSource(PackGlyphDispatcher.FontSource.forPack(packId, packRepository))
            .withSegments(segments)
            .build();
    }

    /**
     * Base layer: the pack's {@code generic_54} texture when the active pack overrides it, the
     * shared procedural vanilla-style chrome otherwise. Vanilla does NOT crop the texture as one
     * contiguous region: the client stitches the chest section (texture rows {@code 0} to
     * {@code rows * 18 + 17}) directly above the bottom/player-inventory section (the 96 texture
     * rows starting at {@code v = 126}), so opaque restyled chrome shows the authored bottom
     * border for every row count. The two sections sum to {@code guiHeight - 1} GUI px, leaving
     * the GUI rect's last row unpainted - exactly like the in-game screen.
     */
    private void drawBackground(Graphics2D graphics, int originX, int originY, int pixelSize, int guiHeight) {
        BufferedImage packBackground = PackId.isActive(packId)
            ? repository().resolveContainerBackground(packId).orElse(null)
            : null;
        drawResolvedBackground(graphics, originX, originY, pixelSize, guiHeight, packBackground);
    }

    /**
     * Draws an already-resolved background texture (or the fallback chrome when null) - the
     * base-layer drawing shared by the static render and the per-step animated compose, so the
     * two-section stitch can never drift between them.
     */
    private void drawResolvedBackground(Graphics2D graphics, int originX, int originY, int pixelSize, int guiHeight,
                                        @Nullable BufferedImage packBackground) {
        if (packBackground == null) {
            drawFallbackChrome(graphics, originX, originY, pixelSize, guiHeight);
            return;
        }
        int chestSectionHeight = rows * SLOT_PITCH + 17;
        drawBackgroundSection(graphics, packBackground, originX, originY, pixelSize,
            0, 0, chestSectionHeight);
        drawBackgroundSection(graphics, packBackground, originX, originY, pixelSize,
            chestSectionHeight, BOTTOM_SECTION_V, BOTTOM_SECTION_HEIGHT);
    }

    /**
     * Draws one vertical section of the container texture, {@code heightGuiPx} tall, from
     * texture row {@code sourceVGuiPx} (in the vanilla 256x256-normalized texture space) to GUI
     * row {@code destYGuiPx} of the GUI rect, nearest-neighbor scaled.
     */
    private static void drawBackgroundSection(Graphics2D graphics, BufferedImage texture, int originX, int originY,
                                              int pixelSize, int destYGuiPx, int sourceVGuiPx, int heightGuiPx) {
        double ratioX = texture.getWidth() / TEXTURE_UV_BASE;
        double ratioY = texture.getHeight() / TEXTURE_UV_BASE;
        int sourceRight = (int) Math.round(GUI_WIDTH * ratioX);
        int sourceTop = (int) Math.round(sourceVGuiPx * ratioY);
        int sourceBottom = (int) Math.round((sourceVGuiPx + heightGuiPx) * ratioY);
        if (sourceRight <= 0 || sourceBottom <= sourceTop) {
            return;
        }
        graphics.drawImage(texture,
            originX, originY + destYGuiPx * pixelSize,
            originX + GUI_WIDTH * pixelSize, originY + (destYGuiPx + heightGuiPx) * pixelSize,
            0, sourceTop, sourceRight, sourceBottom, null);
    }

    /**
     * No-pack base layer: the programmatic chrome shared with
     * {@link MinecraftInventoryGenerator}, plus the slot texture over each grid position (the
     * slot rect starts 1 GUI px outside its 16x16 interior).
     */
    private void drawFallbackChrome(Graphics2D graphics, int originX, int originY, int pixelSize, int guiHeight) {
        Graphics2D chromeGraphics = (Graphics2D) graphics.create();
        try {
            chromeGraphics.translate(originX, originY);
            MinecraftInventoryGenerator.drawVanillaChrome(
                chromeGraphics, GUI_WIDTH * pixelSize, guiHeight * pixelSize, pixelSize);
        } finally {
            chromeGraphics.dispose();
        }

        BufferedImage slotTexture = slotTexture(SLOT_PITCH * pixelSize);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                MinecraftInventoryGenerator.drawSlot(graphics,
                    originX + (SLOT_ORIGIN_X - 1 + SLOT_PITCH * column) * pixelSize,
                    originY + (SLOT_ORIGIN_Y - 1 + SLOT_PITCH * row) * pixelSize,
                    pixelSize, slotTexture);
            }
        }
    }

    /**
     * The bundled slot texture resized to {@code size} canvas px through the loader shared with
     * {@link MinecraftInventoryGenerator}, cached per size; null when the resource is missing
     * (slots then draw the shared programmatic outlines instead, never nothing).
     */
    @Nullable
    private static BufferedImage slotTexture(int size) {
        return SLOT_TEXTURES.computeIfAbsent(size, MinecraftInventoryGenerator::loadSlotTexture);
    }

    /**
     * Renders every slot item through the shared inventory item pipeline and draws it (with its
     * count badge) over the background and title layers - the vanilla z-order. Animated item
     * imagery contributes its first frame; the container render is a static image. Identical
     * specs share one generated visual via {@link #resolveItemImage}.
     */
    private void drawItems(Graphics2D graphics, int originX, int originY, int pixelSize,
                           @Nullable GenerationContext generationContext) {
        if (slots.isEmpty()) {
            return;
        }
        List<InventoryItem> items = new InventoryStringParser(rows * COLUMNS).parse(buildInventoryString());
        for (InventoryItem item : items) {
            if (item.getItemName().equalsIgnoreCase("null")) {
                continue;
            }
            int[] itemSlots = item.getSlot();
            int[] amounts = item.getAmount();
            if (itemSlots == null || amounts == null || itemSlots.length != amounts.length) {
                continue;
            }
            for (int index = 0; index < itemSlots.length; index++) {
                // Custom model data is per slot, so the elements probe runs inside the slot
                // loop; both resolution paths dedupe internally, so repeated specs stay cheap.
                CustomModelData data = slotCustomModelData.getOrDefault(itemSlots[index], CustomModelData.EMPTY);
                PackItemVisual.ElementsRaster raster = resolveElementsRaster(item, data, pixelSize);
                BufferedImage itemImage = raster == null ? resolveItemImage(item, data, generationContext) : null;
                if (raster == null && itemImage == null) {
                    continue;
                }
                int slotOffset = itemSlots[index] - 1;
                int x = slotInteriorX(slotOffset, originX, pixelSize);
                int y = slotInteriorY(slotOffset, originY, pixelSize);
                if (raster != null) {
                    // Already at this generator's pixel size; the offsets anchor the art on the
                    // slot exactly like the vanilla client (oversized art spans neighbors and
                    // clips at the canvas edge).
                    graphics.drawImage(raster.image(), x + raster.offsetX(), y + raster.offsetY(), null);
                } else {
                    graphics.drawImage(itemImage, x, y, SLOT_INTERIOR * pixelSize, SLOT_INTERIOR * pixelSize, null);
                }
                if (amounts[index] > 1) {
                    MinecraftInventoryGenerator.drawStackCount(
                        graphics, amounts[index], x - pixelSize, y - pixelSize, pixelSize);
                }
            }
        }
    }

    /**
     * Resolves a slot item spec against the active pack's elements-model path at this
     * generator's pixel size, deduped per distinct item name and custom model data. Null when
     * the spec is not an elements model (flat pack sprites and vanilla items keep the exact
     * pre-elements pipeline, effects included). Elements renders ignore item modifiers (extra
     * content flags and durability alike), with a warning when a spec declares any.
     */
    @Nullable
    private PackItemVisual.ElementsRaster resolveElementsRaster(InventoryItem item, CustomModelData data,
                                                                int pixelSize) {
        if (!PackId.isActive(packId) || isVanillaPlayerHead(item.getItemName())) {
            return null;
        }
        Optional<PackItemVisual.ElementsRaster> raster = elementsRasterCache.computeIfAbsent(
            new ElementsRasterKey(item.getItemName(), data),
            // The full-rotation flag is per generator instance, so it needs no key field.
            key -> elementsRasterOf(repository().resolveItemVisual(packId, key.itemName(), key.data(), null,
                pixelSize, fullGuiRotations)));
        if (raster.isPresent()) {
            warnIgnoredElementsModifiers(item);
        }
        return raster.orElse(null);
    }

    /**
     * Narrows a resolved pack visual to its elements raster: present when the visual is
     * elements-based, empty when the item is absent or resolves to a flat sprite (the classic
     * slot pipeline renders those). Shared by this generator and
     * {@link MinecraftInventoryGenerator} so the elements probe can never drift between the
     * two composites.
     */
    static Optional<PackItemVisual.ElementsRaster> elementsRasterOf(Optional<PackItemVisual> visual) {
        return visual.filter(PackItemVisual.ElementsRaster.class::isInstance)
            .map(PackItemVisual.ElementsRaster.class::cast);
    }

    /**
     * Elements renders ignore every slot item modifier the classic pipeline applies - the extra
     * content flags (enchant, hover, skin/overlay data) AND the durability bar. Warns with
     * everything the spec declared and returns the logged description, or null (nothing logged)
     * for an unmodified spec. Shared by this generator and {@link MinecraftInventoryGenerator}
     * so the condition and wording can never drift between the two composites; the returned
     * description lets tests pin both without a log capture.
     */
    @Nullable
    static String warnIgnoredElementsModifiers(InventoryItem item) {
        boolean hasContent = item.getExtraContent() != null && !item.getExtraContent().isBlank();
        boolean hasDurability = item.getDurabilityPercent() != null;
        if (!hasContent && !hasDurability) {
            return null;
        }
        StringBuilder declared = new StringBuilder();
        if (hasContent) {
            declared.append(item.getExtraContent());
        }
        if (hasDurability) {
            if (hasContent) {
                declared.append(", ");
            }
            declared.append("durability ").append(item.getDurabilityPercent()).append('%');
        }
        String description = declared.toString();
        log.warn("Item modifiers `{}` are ignored for elements-model slot item `{}`",
            description, item.getItemName());
        return description;
    }

    /**
     * The dedicated head pipeline owns the vanilla {@code player_head} item (skin data lives in
     * the spec modifiers); only that exact id skips the elements probe - a pack item whose path
     * merely CONTAINS the substring (e.g. {@code x:item/player_head_frame}) is an ordinary pack
     * model. Shared with {@link MinecraftInventoryGenerator}'s elements probe so the two
     * composites gate the head pipeline identically.
     */
    static boolean isVanillaPlayerHead(String itemName) {
        String normalized = itemName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized.equals("player_head");
    }

    /**
     * Resolves one slot item's static image through the shared inventory item pipeline, deduped
     * per distinct (material, modifiers, durability, custom model data) spec within this
     * generator instance so render time scales with distinct specs rather than slot count. The
     * slot's custom model data reaches the pipeline so dispatch nodes that resolve to FLAT
     * SPRITES render the data-selected sprite (elements results never arrive here - the
     * elements raster path resolves them first). Package-private for tests pinning the dedupe.
     *
     * @return the generated item image, or null when the pipeline yields none (the slot is
     *     skipped, matching the inventory generator)
     */
    @Nullable
    BufferedImage resolveItemImage(InventoryItem item, CustomModelData data,
                                   @Nullable GenerationContext generationContext) {
        return itemVisualCache.computeIfAbsent(
            new ItemVisualKey(item.getItemName(), item.getExtraContent(), item.getDurabilityPercent(), data),
            key -> MinecraftInventoryGenerator
                .generateSlotObject(item, generationContext, PackId.isActive(packId) ? packId : null,
                    packRepository, key.customModelData())
                .getImage());
    }

    /** Joins the slot map into the existing inventory string DSL, one single-slot entry per slot. */
    private String buildInventoryString() {
        StringBuilder joined = new StringBuilder();
        for (Map.Entry<Integer, String> entry : slots.entrySet()) {
            SlotSpec spec = SlotSpec.parse(entry.getValue());
            if (!joined.isEmpty()) {
                joined.append("%%");
            }
            joined.append(spec.spec()).append(':').append(entry.getKey()).append(',').append(spec.amount());
        }
        return joined.toString();
    }

    private PackRepository repository() {
        return packRepository != null ? packRepository : PackRepository.global();
    }

    /**
     * A validated slot item spec: the existing inventory item spec (material, optional
     * modifiers, optional durability) with an optional trailing {@code :amount}.
     */
    private record SlotSpec(String spec, int amount) {

        /**
         * Strictly parses {@code "<material>[,<modifiers...>][,<durability>][:<amount>]"}. Any
         * other colon must start a namespaced-id tail (next non-space character is a letter,
         * mirroring the inventory DSL), so slot data can never be smuggled into the spec.
         *
         * @throws IllegalArgumentException on a blank spec, an embedded item separator
         *                                  ({@code %%}), an out-of-range amount, or a stray colon
         */
        static SlotSpec parse(String itemSpec) {
            if (itemSpec == null || itemSpec.isBlank()) {
                throw new IllegalArgumentException("Slot item spec must not be blank");
            }
            if (itemSpec.contains("%%")) {
                throw new IllegalArgumentException(
                    "Slot item spec must describe a single item (no `%%`): " + itemSpec);
            }
            String spec = itemSpec.trim();
            int amount = 1;
            int lastColon = spec.lastIndexOf(':');
            if (lastColon >= 0) {
                String suffix = spec.substring(lastColon + 1).trim();
                if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                    try {
                        amount = Integer.parseInt(suffix);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Slot amount out of range in: " + itemSpec, e);
                    }
                    if (amount < 1 || amount > 64) {
                        throw new IllegalArgumentException(
                            "Slot amount must be between 1 and 64, got " + amount + " in: " + itemSpec);
                    }
                    spec = spec.substring(0, lastColon).trim();
                }
            }
            if (spec.isBlank()) {
                throw new IllegalArgumentException("Slot item spec must not be blank: " + itemSpec);
            }
            for (int i = spec.indexOf(':'); i >= 0; i = spec.indexOf(':', i + 1)) {
                int next = i + 1;
                while (next < spec.length() && Character.isWhitespace(spec.charAt(next))) {
                    next++;
                }
                if (next >= spec.length() || !Character.isLetter(spec.charAt(next))) {
                    throw new IllegalArgumentException("Malformed slot item spec `" + itemSpec
                        + "`: `:` may only appear inside a namespaced id or as a trailing `:amount`");
                }
            }
            return new SlotSpec(spec, amount);
        }
    }

    /**
     * Parses a menu recipe document into a preconfigured {@link Builder} - the transcription
     * format for server-style menu captures. Pack selection and scale factor are NOT
     * part of the document; set them on the returned builder.
     *
     * <p>Format (strict; unknown keys anywhere are rejected):
     * <pre>{@code
     * {
     *   "rows": 3,
     *   "title": [
     *     {"text": "", "font": "mypack:menu"},
     *     {"text": "Bank", "color": "#3F3F3F", "bold": true, "italic": false}
     *   ],
     *   "slots": {
     *     "13": "diamond_sword,enchant",
     *     "14": "testpack:item/gem:32"
     *   }
     * }
     * }</pre>
     *
     * <ul>
     * <li>{@code rows} (required): integer 1..6.</li>
     * <li>{@code title} (optional): array of run objects; each requires {@code text} and allows
     *     {@code font} (resource location), {@code color} (strict {@code #RRGGBB}) and boolean
     *     {@code bold} / {@code italic}.</li>
     * <li>{@code slots} (optional): object mapping 1-based row-major slot indices
     *     ({@code 1..rows*9}, slot 1 is the top-left slot) to item spec strings - the existing
     *     inventory item spec (material, optional modifiers such as {@code enchant} or
     *     {@code hover}, optional durability) with an optional trailing {@code :amount}
     *     (1..64). Keys must be canonical integers (no leading zeros or signs) so no two keys
     *     can address the same slot.</li>
     * </ul>
     *
     * <p>The document must be a single JSON object with no trailing content, and duplicate keys
     * anywhere in it are rejected - a duplicated {@code rows} or slot entry is a transcription
     * error, never a silent last-one-wins.
     *
     * @param json the recipe document
     *
     * @return a builder preloaded with the document's rows, title and slots
     * @throws IllegalArgumentException on malformed JSON, trailing content, duplicate or unknown
     *                                  keys, a missing or out-of-range {@code rows}, a malformed
     *                                  title run (bad color, bad font id, wrong types), a bad or
     *                                  non-canonical slot index, or a malformed item spec
     */
    public static Builder fromRecipe(String json) {
        JsonObject root = StrictJson.parseObject(json, "menu recipe");
        StrictJson.requireOnlyKeys(root, RECIPE_KEYS, "menu recipe");
        if (!root.has("rows")) {
            throw new IllegalArgumentException("Menu recipe requires `rows`");
        }
        int rows = StrictJson.requireInt(root, "rows");
        Builder builder = new Builder().withRows(rows);

        if (root.has("title")) {
            JsonElement titleElement = root.get("title");
            if (!titleElement.isJsonArray()) {
                throw new IllegalArgumentException("`title` must be an array of run objects");
            }
            List<TitleRun> runs = new ArrayList<>();
            for (JsonElement runElement : titleElement.getAsJsonArray()) {
                runs.add(parseTitleRun(runElement));
            }
            builder.withTitle(runs);
        }

        if (root.has("slots")) {
            JsonElement slotsElement = root.get("slots");
            if (!slotsElement.isJsonObject()) {
                throw new IllegalArgumentException("`slots` must be an object mapping slot indices to item specs");
            }
            for (Map.Entry<String, JsonElement> entry : slotsElement.getAsJsonObject().entrySet()) {
                int slotIndex;
                try {
                    slotIndex = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Slot key must be an integer, got: " + entry.getKey(), e);
                }
                // Canonical keys only: aliases like "01" or "+1" would silently overwrite the
                // entry for "1" (the parser already rejects an exact duplicate key).
                if (!String.valueOf(slotIndex).equals(entry.getKey())) {
                    throw new IllegalArgumentException("Slot key must be a canonical integer (no leading zeros or signs), got: "
                        + entry.getKey());
                }
                if (slotIndex < 1 || slotIndex > rows * COLUMNS) {
                    throw new IllegalArgumentException("Slot index " + slotIndex
                        + " is out of range for " + rows + " rows (1.." + rows * COLUMNS + ")");
                }
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Slot `" + entry.getKey() + "` must map to an item spec string");
                }
                builder.withSlot(slotIndex, value.getAsString());
            }
        }
        return builder;
    }

    /**
     * Parses one styled title run through the shared strict helpers. Unknown keys are a hard
     * rejection here (the recipe format's transcription-error policy), unlike the scene format.
     */
    private static TitleRun parseTitleRun(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Each title run must be an object, got: " + element);
        }
        JsonObject run = element.getAsJsonObject();
        StrictJson.requireOnlyKeys(run, TITLE_RUN_KEYS, "title run");
        return parseRunFields(run, "Title run");
    }

    /**
     * Validates one styled run's fields and builds the {@link TitleRun}, the field-level rules
     * shared by this menu recipe title format and the declarative scene format's hud lines:
     * required {@code text}, optional resource-location {@code font}, optional strict
     * {@code #RRGGBB} {@code color}, and boolean {@code bold} / {@code italic}. Callers apply
     * their own unknown-key policy first - a hard rejection in the recipe format, tolerant in the
     * scene format - so only that policy differs between the two, never a run's field validation.
     *
     * @param run  the already object-typed run
     * @param what the enclosing-element label used in the field error messages
     *
     * @return the built run
     * @throws IllegalArgumentException on a missing or mistyped field, a {@code font} that is not
     *                                  a resource location, or a {@code color} that is not strict
     *                                  {@code #RRGGBB}, each message naming {@code what}
     */
    public static TitleRun parseRunFields(JsonObject run, String what) {
        String text = StrictJson.requireString(run, "text", what);
        String fontId = run.has("font") ? StrictJson.requireString(run, "font", what) : null;
        if (fontId != null && !FONT_ID.matcher(fontId).matches()) {
            throw new IllegalArgumentException(what + " `font` must be a resource location, got: " + fontId);
        }
        ChatColor color = null;
        if (run.has("color")) {
            String colorValue = StrictJson.requireString(run, "color", what);
            color = Colors.tryParseHex(colorValue);
            if (color == null) {
                throw new IllegalArgumentException(what + " `color` must be strict #RRGGBB, got: " + colorValue);
            }
        }
        return new TitleRun(text, fontId, color,
            StrictJson.requireBoolean(run, "bold", what),
            StrictJson.requireBoolean(run, "italic", what));
    }

    /**
     * Builds {@link MinecraftContainerGenerator} instances. Construct one directly, or preloaded
     * with a document's rows, title and slots via {@link #fromRecipe(String)}.
     */
    public static class Builder implements ClassBuilder<MinecraftContainerGenerator> {

        private int rows;
        private List<TitleRun> title = List.of();
        private final SortedMap<Integer, String> slots = new TreeMap<>();
        private final SortedMap<Integer, CustomModelData> slotCustomModelData = new TreeMap<>();
        private int scaleFactor = 1;
        private boolean fullGuiRotations;
        private boolean animatedTextures;
        private PackId packId;
        private PackRepository packRepository;

        /** Chest rows of the container, 1..6; the GUI rect is {@code 114 + 18 * rows} GUI px tall. */
        public Builder withRows(int rows) {
            if (rows < MIN_ROWS || rows > MAX_ROWS) {
                throw new IllegalArgumentException("rows must be between 1 and 6, got: " + rows);
            }
            this.rows = rows;
            return this;
        }

        /**
         * The title as styled runs, replacing any previously set title. Runs render as ONE line
         * at GUI (8, 6) through the tooltip segment machinery; see {@link TitleRun}.
         */
        public Builder withTitle(@NotNull List<TitleRun> title) {
            this.title = List.copyOf(title);
            return this;
        }

        /** Varargs convenience for {@link #withTitle(List)}. */
        public Builder withTitle(@NotNull TitleRun... title) {
            return withTitle(List.of(title));
        }

        /**
         * Places an item in a slot. Slot indices are 1-based and row-major like the existing
         * inventory DSL: slot 1 is the top-left slot, slot {@code rows * 9} the bottom-right.
         * Setting the same slot again replaces its item. The spec is validated here (fail
         * fast); the slot index upper bound is validated at {@link #build()} once the row count
         * is final.
         *
         * @param slotIndex 1-based slot index
         * @param itemSpec  the existing inventory item spec (material, optional modifiers,
         *                  optional durability) with an optional trailing {@code :amount}, e.g.
         *                  {@code "diamond_sword,enchant"} or {@code "stone:64"}
         * @throws IllegalArgumentException when the slot index is not positive or the spec is
         *                                  malformed
         */
        public Builder withSlot(int slotIndex, @NotNull String itemSpec) {
            if (slotIndex < 1) {
                throw new IllegalArgumentException("Slot index must be at least 1, got: " + slotIndex);
            }
            SlotSpec.parse(itemSpec);
            this.slots.put(slotIndex, itemSpec);
            // Replacement semantics: re-setting a slot resets any previously supplied data.
            this.slotCustomModelData.remove(slotIndex);
            return this;
        }

        /**
         * Places an item in a slot together with the {@code minecraft:custom_model_data}
         * component value its item model definition evaluates against ({@code range_dispatch}
         * floats, {@code condition} flags, {@code select} strings and tint colors). The data
         * drives dispatch for every pack-resolved visual - elements-model rasters AND flat
         * sprite results both render the data-selected model; vanilla items ignore it. The
         * recipe format ({@link #fromRecipe(String)}) carries no custom model data - use this
         * overload to supply it programmatically.
         *
         * @param slotIndex       1-based slot index
         * @param itemSpec        the item spec, exactly as for {@link #withSlot(int, String)}
         * @param customModelData the component value; {@link CustomModelData#EMPTY} behaves
         *                        like the two-argument overload
         * @throws IllegalArgumentException when the slot index is not positive or the spec is
         *                                  malformed
         */
        public Builder withSlot(int slotIndex, @NotNull String itemSpec, @NotNull CustomModelData customModelData) {
            Objects.requireNonNull(customModelData, "customModelData");
            withSlot(slotIndex, itemSpec);
            if (!CustomModelData.EMPTY.equals(customModelData)) {
                this.slotCustomModelData.put(slotIndex, customModelData);
            }
            return this;
        }

        /**
         * Selects the resource pack backing the render: the container background texture, title
         * pack fonts and slot item sprites all resolve against it. Null or
         * {@link PackId#VANILLA} renders the built-in vanilla style.
         */
        public Builder withPack(@Nullable PackId packId) {
            this.packId = packId;
            return this;
        }

        /** Convenience overload accepting {@code "namespace:name"}. */
        public Builder withPack(String packId) {
            return withPack(PackId.parse(packId));
        }

        /** Inject a custom pack repository (tests); defaults to {@link PackRepository#global()}. */
        public Builder withPackRepository(PackRepository packRepository) {
            this.packRepository = packRepository;
            return this;
        }

        /** Scale factor applied to all pixel sizes; one GUI px covers {@code 2 * scaleFactor} canvas px. */
        public Builder withScaleFactor(int scaleFactor) {
            this.scaleFactor = Math.max(1, scaleFactor);
            return this;
        }

        /**
         * Opts slot items with elements-based pack models into the true orthographic
         * projection of arbitrary {@code display.gui} rotations - the vanilla GUI presentation
         * of 3D models (no perspective), so [30, 225, 0]-style block angles show three shaded
         * faces instead of failing loudly. Identity and (0, 180, 0)-mirror rotations (within
         * the 5-degree decorative-tilt tolerance) keep their exact flat renders with or
         * without the flag. Default false: rotations beyond those keep throwing
         * PackResolveException.
         */
        public Builder withFullGuiRotations(boolean fullGuiRotations) {
            this.fullGuiRotations = fullGuiRotations;
            return this;
        }

        /**
         * Opts the render into animated pack textures (default false): an animated pack
         * container background and every texture-animated slot item join one shared scene
         * timeline (the LCM of the cycles, capped per
         * {@link net.aerh.imagegenerator.pack.AnimationTimeline}) and {@code build().generate()}
         * returns the GIF form of {@link GeneratedObject} with per-frame delays. Scenes without
         * animated textures render the static image exactly as before.
         */
        public Builder withAnimatedTextures(boolean animatedTextures) {
            this.animatedTextures = animatedTextures;
            return this;
        }

        /**
         * Validates the slot indices against the final row count and builds the generator.
         *
         * @throws IllegalArgumentException when {@code rows} was never set (or is out of range),
         *                                  or a slot index exceeds {@code rows * 9}
         */
        @Override
        public @NotNull MinecraftContainerGenerator build() {
            if (rows < MIN_ROWS || rows > MAX_ROWS) {
                throw new IllegalArgumentException("rows must be between 1 and 6, got: " + rows);
            }
            int totalSlots = rows * COLUMNS;
            for (Integer slotIndex : slots.keySet()) {
                if (slotIndex > totalSlots) {
                    throw new IllegalArgumentException("Slot index " + slotIndex
                        + " is out of range for " + rows + " rows (1.." + totalSlots + ")");
                }
            }
            return new MinecraftContainerGenerator(rows, title, new TreeMap<>(slots),
                new TreeMap<>(slotCustomModelData), scaleFactor, fullGuiRotations, animatedTextures,
                packId, packRepository);
        }
    }
}
