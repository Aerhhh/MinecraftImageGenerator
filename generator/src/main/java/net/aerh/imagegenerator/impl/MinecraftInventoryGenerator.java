package net.aerh.imagegenerator.impl;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.image.ImageCoordinates;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.item.InventoryItem;
import net.aerh.imagegenerator.pack.CustomModelData;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackItemVisual;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.parser.inventory.InventoryStringParser;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import net.aerh.imagegenerator.util.MinecraftFonts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ToString
public class MinecraftInventoryGenerator implements Generator {

    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);
    private static final Color INVENTORY_BACKGROUND = new Color(198, 198, 198);
    private static final Color BORDER_COLOR = new Color(198, 198, 198);
    private static final Color DARK_BORDER_COLOR = new Color(85, 85, 85);

    private static final int SLOT_BORDER_THICKNESS = 1;
    private static final int SLOT_INNER_BORDER_OFFSET = 1;
    private static final int SLOT_INNER_BORDER_REDUCTION = 3;
    @Getter
    private static final int scaleFactor;
    private static final int slotSize;
    private static final int itemSize;
    private static BufferedImage slotTexture;

    static {
        // Detect item texture size automatically
        BufferedImage sampleItem = Spritesheet.getTexture("stone");
        if (sampleItem != null) {
            itemSize = sampleItem.getWidth() / 2;
            scaleFactor = itemSize / 16;
            slotSize = 18 * scaleFactor;
            log.info("Detected item texture size: {}x{}, factor: {}, slot size: {}", itemSize, itemSize, scaleFactor, slotSize);
        } else {
            // Fallback values if no item found
            itemSize = 128;
            scaleFactor = 8;
            slotSize = 144;
            log.info("Using fallback values: item size: {}, scale factor: {}, slot size: {}", itemSize, scaleFactor, slotSize);
        }

        slotTexture = loadSlotTexture(slotSize);
    }

    /**
     * Loads the bundled vanilla slot texture resized to {@code size} x {@code size} canvas px,
     * or null when the resource is missing or unreadable (callers fall back to the programmatic
     * outlines in {@link #drawSlot(Graphics2D, int, int, int, BufferedImage)}). The SINGLE
     * production loader, shared with {@link MinecraftContainerGenerator}, so the resource path
     * and missing-resource behavior can never fork between the two composites.
     */
    @Nullable
    static BufferedImage loadSlotTexture(int size) {
        try (InputStream slotStream = MinecraftInventoryGenerator.class.getResourceAsStream("/minecraft/assets/textures/slot.png")) {
            if (slotStream == null) {
                log.warn("Slot texture resource missing; slots render with programmatic outlines");
                return null;
            }
            return ImageUtil.resizeImage(ImageIO.read(slotStream), size, size, BufferedImage.TYPE_INT_ARGB);
        } catch (IOException e) {
            // Warn, not error, matching the missing-resource branch above: both outcomes are
            // recovered by the programmatic slot outlines.
            log.warn("Failed to load slot texture; slots render with programmatic outlines", e);
            return null;
        }
    }

    private final int rows;
    private final int slotsPerRow;
    private final String containerTitle;
    private final boolean drawTitle;
    private final boolean drawBorder;
    private final boolean drawBackground;
    private final int totalSlots;
    private final String inventoryString;
    private final boolean animateGlint;
    @Nullable
    private final PackId packId;
    @ToString.Exclude
    private final transient PackRepository packRepository;

    private BufferedImage inventoryImage;
    private Graphics2D g2d;
    private int borderSize;
    private int titleHeight;
    private List<ImageCoordinates> slotCoordinates;
    private GeneratedObject generatedObject;
    /**
     * Deduplicates identical slot item specs within this render. Individually specified
     * duplicates ("iron_block,enchant:1%%iron_block,enchant:2%%...") would otherwise each
     * re-run the full item pipeline - for enchanted items that is 182 glint frames per slot,
     * which made large individually-specified inventories time out while the equivalent bulk
     * range rendered fine. Scoped to the generator instance, so the global GeneratorCache's
     * cross-repository key concerns do not apply.
     */
    private final Map<SlotVisualKey, SlotVisual> slotVisualCache = new HashMap<>();

    /**
     * Everything that determines a slot item's rendered appearance: the material, the raw
     * modifier string (enchant/hover flags, skin value, overlay data) and durability. Amount
     * is deliberately absent - it only affects the stack count drawn per slot, not the visual.
     */
    private record SlotVisualKey(String itemName, String extraContent, Integer durabilityPercent) {
    }

    /** Downscaled per-slot imagery shared by every slot item with the same spec. */
    private record SlotVisual(BufferedImage itemImage, List<BufferedImage> animationFrames, Integer frameDelayMs) {
    }

    public MinecraftInventoryGenerator(int rows, int slotsPerRow, String containerTitle, String inventoryString,
                                        boolean drawBorder, boolean drawBackground, boolean animateGlint) {
        this(rows, slotsPerRow, containerTitle, inventoryString, drawBorder, drawBackground, animateGlint, null, null);
    }

    public MinecraftInventoryGenerator(int rows, int slotsPerRow, String containerTitle, String inventoryString,
                                        boolean drawBorder, boolean drawBackground, boolean animateGlint,
                                        @Nullable PackId packId, @Nullable PackRepository packRepository) {
        this.rows = rows;
        this.slotsPerRow = slotsPerRow;
        this.containerTitle = containerTitle;
        this.inventoryString = inventoryString;
        this.drawTitle = containerTitle != null;
        this.drawBorder = drawBorder;
        this.drawBackground = drawBackground;
        this.totalSlots = rows * slotsPerRow;
        this.animateGlint = animateGlint;
        this.packId = packId;
        this.packRepository = packRepository != null ? packRepository : PackRepository.global();

        initializeImage();
    }

    private void initializeImage() {
        this.borderSize = drawBorder ? 7 * scaleFactor : 0;
        this.titleHeight = borderSize + (drawTitle ? 13 * scaleFactor : 0) - (drawBorder && drawTitle ? 3 * scaleFactor : 0);

        int imageWidth = (slotsPerRow * slotSize) + (borderSize * 2);
        int imageHeight = (rows * slotSize) + titleHeight + borderSize;

        this.inventoryImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        this.g2d = inventoryImage.createGraphics();
        this.g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        this.g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        this.g2d.setFont(MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scaleFactor * 8));
    }

    private void drawInventoryBackground() {
        if (!drawBorder) return;

        drawMinecraftBorder();
    }

    private void drawMinecraftBorder() {
        drawVanillaChrome(g2d, inventoryImage.getWidth(), inventoryImage.getHeight(), scaleFactor);
    }

    /**
     * Draws the programmatic vanilla-style container chrome (gray background, black outline,
     * white highlight and dark shadow) over a {@code width} x {@code height} canvas at
     * {@code scale} canvas px per GUI px. Shared by this generator's bordered inventories and
     * {@link MinecraftContainerGenerator}'s no-pack fallback so the two styles can never drift
     * apart.
     */
    static void drawVanillaChrome(Graphics2D g2d, int width, int height, int scale) {
        // Drawing the background
        g2d.setColor(BORDER_COLOR);
        g2d.fillRect(scale * 3, scale * 3, width - scale * 6, height - scale * 6);
        g2d.fillRect(width - scale * 3, scale * 2, scale, scale); // top right
        g2d.fillRect(scale * 2, height - scale * 3, scale, scale); // bottom left

        // Drawing the dark gray shadow
        g2d.setColor(DARK_BORDER_COLOR);
        g2d.fillRect(width - scale * 3, scale * 3, scale * 2, height - scale * 4); // vertical right
        g2d.fillRect(scale * 3, height - scale * 3, width - scale * 6, scale * 2); // horizontal bottom
        g2d.fillRect(width - scale * 4, height - scale * 4, scale, scale); // square bottom right

        // Drawing the white highlight
        g2d.setColor(Color.WHITE);
        g2d.fillRect(scale, scale, scale * 2, height - scale * 4); // vertical left
        g2d.fillRect(scale * 3, scale, width - scale * 6, scale * 2); // horizontal top
        g2d.fillRect(scale * 3, scale * 3, scale, scale); // square top left

        g2d.setColor(Color.BLACK);
        // vertical black lines
        g2d.fillRect(0, scale * 2, scale, height - scale * 5); // vertical left
        g2d.fillRect(width - scale, scale * 3, scale, height - scale * 5); // vertical right
        // horizontal black lines
        g2d.fillRect(scale * 2, 0, width - scale * 5, scale); // horizontal top
        g2d.fillRect(scale * 3, height - scale, width - scale * 5, scale); // horizontal bottom
        // black corners
        g2d.fillRect(scale, scale, scale, scale); // top left
        g2d.fillRect(width - scale * 3, scale, scale, scale); // top right - upper
        g2d.fillRect(width - scale * 2, scale * 2, scale, scale); // top right - lower
        g2d.fillRect(width - scale * 2, height - scale * 2, scale, scale); // bottom right
        g2d.fillRect(scale, height - scale * 3, scale, scale); // bottom left - upper
        g2d.fillRect(scale * 2, height - scale * 2, scale, scale); // bottom left - lower
    }

    private void drawSlots() {
        slotCoordinates = new ArrayList<>();

        int startY = titleHeight;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < slotsPerRow; col++) {
                int x = borderSize + (col * slotSize);
                int y = startY + (row * slotSize);

                slotCoordinates.add(new ImageCoordinates(x, y));

                if (drawBackground) {
                    drawSlot(x, y);
                }
            }
        }
    }

    private void drawSlot(int x, int y) {
        drawSlot(g2d, x, y, scaleFactor, slotTexture);
    }

    /**
     * Draws one 18-GUI-px slot whose rect origin is {@code (x, y)} at {@code scale} canvas px
     * per GUI px: the resized slot texture when present, the programmatic outlines (background,
     * double border, highlight) otherwise. Shared by this generator and
     * {@link MinecraftContainerGenerator}'s fallback chrome so slot rendering - including the
     * missing-texture fallback - can never drift between the two composites.
     */
    static void drawSlot(Graphics2D target, int x, int y, int scale, @Nullable BufferedImage slotTexture) {
        if (slotTexture != null) {
            target.drawImage(slotTexture, x, y, null);
            return;
        }

        int size = 18 * scale;

        // Background
        target.setColor(INVENTORY_BACKGROUND);
        target.fillRect(x + scale, y + scale, size - 2 * scale, size - 2 * scale);

        // Slot border
        target.setColor(DARK_BORDER_COLOR);
        target.drawRect(x, y, size - SLOT_BORDER_THICKNESS, size - SLOT_BORDER_THICKNESS);
        target.drawRect(x + SLOT_INNER_BORDER_OFFSET, y + SLOT_INNER_BORDER_OFFSET,
            size - SLOT_INNER_BORDER_REDUCTION, size - SLOT_INNER_BORDER_REDUCTION);

        // Highlight
        target.setColor(Color.WHITE);
        target.drawLine(x + size - SLOT_BORDER_THICKNESS, y,
            x + size - SLOT_BORDER_THICKNESS, y + size - SLOT_BORDER_THICKNESS);
        target.drawLine(x, y + size - SLOT_BORDER_THICKNESS,
            x + size - SLOT_BORDER_THICKNESS, y + size - SLOT_BORDER_THICKNESS);
    }

    private void drawTitle() {
        if (!drawTitle || slotCoordinates == null || slotCoordinates.isEmpty()) {
            return;
        }

        int titleX = 8 * scaleFactor;
        int titleY = slotCoordinates.getFirst().getY() - scaleFactor * 4;

        g2d.setColor(DROP_SHADOW_COLOR);
        g2d.drawString(containerTitle, titleX, titleY);
    }

    private void drawItems(@Nullable GenerationContext generationContext) {
        if (inventoryString == null || inventoryString.isBlank()) {
            return;
        }

        InventoryStringParser parser = new InventoryStringParser(totalSlots);
        List<InventoryItem> items = resolveSlotConflicts(parser.parse(inventoryString));

        boolean hasAnimation = false;
        for (InventoryItem item : items) {
            processItem(item, generationContext);
            if (item.getAnimationFrames() != null && !item.getAnimationFrames().isEmpty()) {
                hasAnimation = true;
            }
        }

        if (animateGlint && hasAnimation) {
            List<BufferedImage> frames = buildAnimationFrames(items);
            if (!frames.isEmpty()) {
                int frameDelay = determineFrameDelay(items);

                try {
                    byte[] gifData = ImageUtil.toGifBytes(frames, frameDelay, true);
                    this.inventoryImage = frames.getFirst();

                    if (this.g2d != null) {
                        this.g2d.dispose();
                    }

                    this.g2d = inventoryImage.createGraphics();
                    this.g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    this.g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    this.g2d.setFont(MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scaleFactor * 8));

                    this.generatedObject = new GeneratedObject(gifData, frames, frameDelay);

                    return;
                } catch (IOException e) {
                    // Warn, not error: the render still succeeds with the static first frame.
                    log.warn("Failed to encode animated inventory, falling back to static frame", e);
                    this.inventoryImage = frames.getFirst();
                }
            }
        }

        for (InventoryItem item : items) {
            drawItem(g2d, item);
        }
    }

    /**
     * Ensures each slot is filled by the last item referencing it within the inventory string.
     */
    private List<InventoryItem> resolveSlotConflicts(List<InventoryItem> parsedItems) {
        if (parsedItems == null || parsedItems.isEmpty()) {
            return Collections.emptyList();
        }

        boolean[] slotHasItem = new boolean[this.totalSlots + 1];
        ArrayList<InventoryItem> filteredItems = new ArrayList<>(parsedItems.size());

        for (int index = parsedItems.size() - 1; index >= 0; index--) {
            InventoryItem item = parsedItems.get(index);
            int[] slots = item.getSlot();
            int[] amounts = item.getAmount();

            if (slots == null || amounts == null || slots.length != amounts.length) {
                continue;
            }

            int keptCount = 0;
            for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
                int slot = slots[slotIndex];
                if (slot >= 1 && slot <= this.totalSlots && !slotHasItem[slot]) {
                    slotHasItem[slot] = true;
                    slots[keptCount] = slot;
                    amounts[keptCount] = amounts[slotIndex];
                    keptCount++;
                }
            }

            if (keptCount == 0) {
                continue;
            }

            if (keptCount != slots.length) {
                item.setSlot(Arrays.copyOf(slots, keptCount));
                item.setAmount(Arrays.copyOf(amounts, keptCount));
            }

            filteredItems.add(item);
        }

        Collections.reverse(filteredItems);
        return filteredItems;
    }

    // Package-private for tests pinning the retained per-slot frame resolution and dedupe.
    void processItem(InventoryItem item, @Nullable GenerationContext generationContext) {
        if (item.getItemName().equalsIgnoreCase("null")) {
            return;
        }

        SlotVisualKey cacheKey = new SlotVisualKey(item.getItemName(), item.getExtraContent(), item.getDurabilityPercent());
        SlotVisual visual = slotVisualCache.get(cacheKey);
        if (visual == null) {
            BufferedImage elementsImage = resolveElementsImage(item);
            if (elementsImage != null) {
                visual = new SlotVisual(elementsImage, null, null);
            } else {
                GeneratedObject generated = generateSlotObject(item, generationContext);
                visual = new SlotVisual(
                    downscaleToCompositeResolution(generated.getImage()),
                    downscaleFramesToCompositeResolution(generated.getAnimationFrames()),
                    generated.getFrameDelayMs() > 0 ? generated.getFrameDelayMs() : null);
            }
            slotVisualCache.put(cacheKey, visual);
        }

        item.setItemImage(visual.itemImage());
        item.setAnimationFrames(visual.animationFrames());
        item.setFrameDelayMs(visual.frameDelayMs());
    }

    /**
     * Resolves a slot item spec against the active pack's elements-model path directly at this
     * composite's slot resolution, mirroring {@link MinecraftContainerGenerator}'s elements
     * handling. Null when the spec is not an elements model - flat pack sprites and vanilla
     * items keep the exact pre-elements pipeline, effects included.
     *
     * <p><b>Oversized art clips at the slot box in inventories</b>, a documented difference
     * from the container compositor: the container anchors {@code oversized_in_gui} art on the
     * slot center and lets it span neighboring slots, while this composite draws every slot
     * visual inside its fixed item box, so overflow is cropped at the 16-GUI-px slot interior.
     * Item modifiers (enchant, hover, durability) do not apply to elements renders, matching
     * the container; a spec declaring any logs a warning (the shared
     * {@link MinecraftContainerGenerator#warnIgnoredElementsModifiers} condition and wording).
     */
    @Nullable
    private BufferedImage resolveElementsImage(InventoryItem item) {
        if (!PackId.isActive(packId) || MinecraftContainerGenerator.isVanillaPlayerHead(item.getItemName())) {
            return null;
        }
        PackItemVisual.ElementsRaster raster = MinecraftContainerGenerator.elementsRasterOf(
                packRepository.resolveItemVisual(packId, item.getItemName(), CustomModelData.EMPTY, scaleFactor))
            .orElse(null);
        if (raster == null) {
            return null;
        }
        MinecraftContainerGenerator.warnIgnoredElementsModifiers(item);
        BufferedImage clipped = new BufferedImage(itemSize, itemSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = clipped.createGraphics();
        try {
            // The raster offsets anchor the art on the slot box origin; drawing into the fixed
            // item box crops any oversized overflow at the box edge.
            graphics.drawImage(raster.image(), raster.offsetX(), raster.offsetY(), null);
        } finally {
            graphics.dispose();
        }
        return clipped;
    }

    private GeneratedObject generateSlotObject(InventoryItem item, @Nullable GenerationContext generationContext) {
        return generateSlotObject(item, generationContext, packId, packRepository);
    }

    /**
     * Generates one slot item's imagery through the standard item pipeline: player heads route
     * to {@link MinecraftPlayerHeadGenerator}, everything else to {@link MinecraftItemGenerator}
     * with the item's enchant/hover/durability modifiers (and the selected pack, when one is
     * active). Shared by this generator and {@link MinecraftContainerGenerator} so slot items
     * render identically in both composites. Evaluates dispatch nodes with
     * {@link CustomModelData#EMPTY}; slots carrying data use the extended overload.
     */
    static GeneratedObject generateSlotObject(InventoryItem item, @Nullable GenerationContext generationContext,
                                              @Nullable PackId packId, @Nullable PackRepository packRepository) {
        return generateSlotObject(item, generationContext, packId, packRepository, CustomModelData.EMPTY);
    }

    /**
     * Like {@link #generateSlotObject(InventoryItem, GenerationContext, PackId, PackRepository)}
     * but evaluating {@code custom_model_data} dispatch nodes and tint sources against
     * {@code customModelData}, so a per-slot component value drives flat-sprite dispatch through
     * the standard item pipeline (elements-model dispatch is handled by the callers' dedicated
     * raster path before this pipeline runs). The dedicated player head pipeline carries no
     * custom model data; head specs ignore it.
     */
    static GeneratedObject generateSlotObject(InventoryItem item, @Nullable GenerationContext generationContext,
                                              @Nullable PackId packId, @Nullable PackRepository packRepository,
                                              CustomModelData customModelData) {
        if (item.getItemName().contains("player_head")) {
            String skinValue = item.getExtraContent();
            if (skinValue != null && skinValue.contains(",")) {
                String[] tokens = skinValue.split(",");
                for (String token : tokens) {
                    token = token.trim();
                    if (token.toLowerCase().startsWith("skin=")) {
                        skinValue = token.substring(token.indexOf('=') + 1).trim();
                        break;
                    }
                }
            }
            if (skinValue != null && skinValue.toLowerCase().startsWith("skin=")) {
                skinValue = skinValue.substring(skinValue.indexOf('=') + 1).trim();
            }

            return new MinecraftPlayerHeadGenerator.Builder()
                .withSkin(skinValue)
                .build()
                .generate(generationContext);
        }

        String contentLower = item.getExtraContent() != null ? item.getExtraContent().toLowerCase() : null;
        MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
            .withItem(item.getItemName())
            .withCustomModelData(customModelData)
            .isEnchanted(contentLower != null && contentLower.contains("enchant"))
            .withHoverEffect(contentLower != null && contentLower.contains("hover"))
            .withData(item.getExtraContent());

        if (packId != null) {
            itemBuilder.withPack(packId).withPackRepository(packRepository);
        }

        if (item.getDurabilityPercent() != null) {
            itemBuilder.withDurability(item.getDurabilityPercent());
        }

        return itemBuilder.build().generate(generationContext);
    }

    /**
     * Bounds a retained slot image to the resolution the composite actually draws at:
     * {@link #drawItem} always scales slot imagery to {@code itemSize} x {@code itemSize} with
     * nearest-neighbor sampling, so pre-scaling here keeps the composed output pixel-identical
     * while retaining a fraction of the memory per animation frame. {@link AlphaComposite#Src}
     * copies the sampled pixels exactly, avoiding SrcOver rounding drift on partially
     * transparent pixels.
     */
    static BufferedImage downscaleToCompositeResolution(BufferedImage image) {
        if (image == null || (image.getWidth() <= itemSize && image.getHeight() <= itemSize)) {
            return image;
        }

        BufferedImage scaled = new BufferedImage(itemSize, itemSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(image, 0, 0, itemSize, itemSize, null);
        graphics.dispose();
        return scaled;
    }

    static List<BufferedImage> downscaleFramesToCompositeResolution(List<BufferedImage> frames) {
        if (frames == null || frames.isEmpty()) {
            return frames;
        }

        List<BufferedImage> scaledFrames = new ArrayList<>(frames.size());
        for (BufferedImage frame : frames) {
            scaledFrames.add(downscaleToCompositeResolution(frame));
        }
        return scaledFrames;
    }

    private void drawItem(Graphics2D target, InventoryItem item) {
        BufferedImage itemImage = item.getItemImage();
        int[] slots = item.getSlot();
        int[] amounts = item.getAmount();

        if (slots == null || amounts == null || slots.length != amounts.length) {
            return;
        }

        for (int index = 0; index < slots.length; index++) {
            if (slots[index] < 1 || slots[index] > slotCoordinates.size()) {
                continue;
            }

            ImageCoordinates slotCoord = slotCoordinates.get(slots[index] - 1);
            int slotX = slotCoord.getX();
            int slotY = slotCoord.getY();

            if (itemImage == null) {
                // Draw placeholder for null items
                target.setColor(INVENTORY_BACKGROUND);
                target.fillRect(slotX + scaleFactor, slotY + scaleFactor,
                    slotSize - 2 * scaleFactor, slotSize - 2 * scaleFactor);
                continue;
            }

            // Calculate item position
            int itemPadding = (slotSize - itemSize) / 2;
            int itemX = slotX + itemPadding;
            int itemY = slotY + itemPadding;

            // Draw item image
            target.drawImage(itemImage, itemX, itemY, itemSize, itemSize, null);

            // Draw stack count if > 1
            if (amounts[index] > 1) {
                drawStackCount(target, amounts[index], slotX, slotY);
            }
        }
    }

    private void drawStackCount(Graphics2D target, int amount, int slotX, int slotY) {
        drawStackCount(target, amount, slotX, slotY, scaleFactor);
    }

    /**
     * Draws a stack count badge at the bottom-right of an 18-GUI-px slot whose rect origin is
     * {@code (slotX, slotY)}, white with a drop shadow, at {@code scale} canvas px per GUI px.
     * Shared by this generator and {@link MinecraftContainerGenerator} so count rendering never
     * drifts between the two composites.
     */
    static void drawStackCount(Graphics2D target, int amount, int slotX, int slotY, int scale) {
        String amountText = String.valueOf(amount);
        int scaledSlotSize = 18 * scale;

        Font originalFont = target.getFont();
        Font stackFont = MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scale * 8);
        target.setFont(stackFont);

        // Calculate text position (bottom-right of slot)
        int textWidth = target.getFontMetrics().stringWidth(amountText);
        int textX = slotX + scaledSlotSize - textWidth + 1;
        int textY = slotY + scaledSlotSize - scale + 1;

        // Draw text with drop shadow
        int shadowOffset = scale;
        target.setColor(DROP_SHADOW_COLOR);
        target.drawString(amountText, textX + shadowOffset - 1, textY + shadowOffset - 1);

        target.setColor(NORMAL_TEXT_COLOR);
        target.drawString(amountText, textX - 1, textY - 1);

        target.setFont(originalFont);
    }

    private List<BufferedImage> buildAnimationFrames(List<InventoryItem> items) {
        List<BufferedImage> frames = new ArrayList<>();
        int maxFrames = items.stream()
            .mapToInt(item -> item.getAnimationFrames() != null ? item.getAnimationFrames().size() : 1)
            .max()
            .orElse(0);

        if (maxFrames <= 1) {
            return frames;
        }

        for (int frameIndex = 0; frameIndex < maxFrames; frameIndex++) {
            BufferedImage frame = new BufferedImage(inventoryImage.getWidth(), inventoryImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D frameGraphics = frame.createGraphics();
            frameGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            frameGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            frameGraphics.setFont(MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scaleFactor * 8));
            frameGraphics.drawImage(inventoryImage, 0, 0, null);

            for (InventoryItem item : items) {
                BufferedImage original = item.getItemImage();
                if (item.getAnimationFrames() != null && !item.getAnimationFrames().isEmpty()) {
                    BufferedImage animatedFrame = item.getAnimationFrames().get(frameIndex % item.getAnimationFrames().size());
                    item.setItemImage(animatedFrame);
                }

                drawItem(frameGraphics, item);
                item.setItemImage(original);
            }

            frameGraphics.dispose();
            frames.add(frame);
        }

        return frames;
    }

    private int determineFrameDelay(List<InventoryItem> items) {
        return items.stream()
            .map(InventoryItem::getFrameDelayMs)
            .filter(delay -> delay != null && delay > 0)
            .findFirst()
            .orElse(33);
    }

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        log.debug("Rendering inventory ({})", this);

        drawInventoryBackground();
        drawSlots();
        drawTitle();
        drawItems(generationContext);

        g2d.dispose();
        log.debug("Rendered inventory image (dimensions {}x{})", inventoryImage.getWidth(), inventoryImage.getHeight());

        if (generatedObject != null) {
            return generatedObject;
        }

        return new GeneratedObject(inventoryImage);
    }

    public static class Builder extends net.hypixel.nerdbot.marmalade.pattern.Builder<MinecraftInventoryGenerator> implements ClassBuilder<MinecraftInventoryGenerator> {
        private int rows;
        private int slotsPerRow;
        private String containerTitle;
        private boolean drawBorder = true;
        private boolean drawBackground = true;
        private String inventoryString;
        private boolean animateGlint;
        private PackId packId;
        private PackRepository packRepository;

        public Builder withRows(int rows) {
            if (rows <= 0) {
                throw new IllegalArgumentException("rows must be positive");
            }
            this.rows = rows;
            return this;
        }

        public Builder withSlotsPerRow(int slotsPerRow) {
            if (slotsPerRow <= 0) {
                throw new IllegalArgumentException("slotsPerRow must be positive");
            }
            this.slotsPerRow = slotsPerRow;
            return this;
        }

        public Builder withContainerTitle(String containerTitle) {
            this.containerTitle = containerTitle;
            return this;
        }

        public Builder drawBorder(boolean drawBorder) {
            this.drawBorder = drawBorder;
            return this;
        }

        public Builder drawBackground(boolean drawBackground) {
            this.drawBackground = drawBackground;
            return this;
        }

        public Builder withInventoryString(String inventoryString) {
            this.inventoryString = inventoryString;
            return this;
        }

        public Builder withAnimateGlint(boolean animateGlint) {
            this.animateGlint = animateGlint;
            return this;
        }

        /**
         * Selects the resource pack to resolve every item slot from. Propagated to every
         * {@link MinecraftItemGenerator.Builder} built while rendering item slots. Null or
         * {@link PackId#VANILLA} renders from the built-in vanilla spritesheet exactly as before.
         * Rendering with a pack that was never registered, or an item the pack cannot resolve,
         * throws PackResolveException from {@code generate()}.
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

        @Override
        protected void validate() {
            if (rows <= 0 || slotsPerRow <= 0) {
                throw new IllegalArgumentException("rows and slotsPerRow must be positive");
            }
        }

        @Override
        protected MinecraftInventoryGenerator construct() {
            return new MinecraftInventoryGenerator(rows, slotsPerRow, containerTitle, inventoryString, drawBorder,
                drawBackground, animateGlint, packId, packRepository);
        }
    }
}
