package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.exception.RenderException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a Minecraft-style inventory container image.
 *
 * <p>The rendering steps are:
 * <ol>
 *   <li>Compute canvas dimensions from rows, slotsPerRow, and the slot texture size.</li>
 *   <li>Optionally draw a gray background, outer/inner borders, and a title.</li>
 *   <li>Draw each slot background using the slot texture.</li>
 *   <li>Place item sprites inside each occupied slot.</li>
 *   <li>Draw stack count labels (bottom-right, white text with dark shadow) when count &gt; 1.</li>
 *   <li>If any items are enchanted the pipeline is run per-item and frames are merged into an animation.</li>
 * </ol>
 */
public final class InventoryGenerator implements Generator<InventoryRequest, GeneratorResult> {

    // Minecraft inventory colors
    private static final Color BG_COLOR = new Color(198, 198, 198);
    private static final Color BORDER_OUTER_DARK = new Color(55, 55, 55);
    private static final Color BORDER_OUTER_LIGHT = new Color(255, 255, 255);
    private static final Color BORDER_INNER_DARK = new Color(139, 139, 139);
    private static final Color BORDER_INNER_LIGHT = new Color(198, 198, 198);
    private static final Color SLOT_OUTER_DARK = new Color(55, 55, 55);
    private static final Color SLOT_OUTER_LIGHT = new Color(255, 255, 255);
    private static final Color SLOT_INNER = new Color(139, 139, 139);

    private static final Color STACK_COUNT_COLOR = new Color(255, 255, 255);
    private static final Color STACK_COUNT_SHADOW = new Color(63, 63, 63);

    private static final int BORDER_THICKNESS = 4;
    private static final int TITLE_HEIGHT = 14;
    private static final int SLOT_PADDING = 2;

    private static final String SLOT_TEXTURE_PATH = "minecraft/assets/textures/slot.png";

    private final SpriteProvider spriteProvider;
    private final EffectPipeline effectPipeline;

    /**
     * Creates a new {@link InventoryGenerator}.
     *
     * @param spriteProvider the sprite provider to load item textures from; must not be {@code null}
     * @param effectPipeline the pipeline of effects to apply per item; must not be {@code null}
     */
    public InventoryGenerator(SpriteProvider spriteProvider, EffectPipeline effectPipeline) {
        this.spriteProvider = Objects.requireNonNull(spriteProvider, "spriteProvider must not be null");
        this.effectPipeline = Objects.requireNonNull(effectPipeline, "effectPipeline must not be null");
    }

    @Override
    public GeneratorResult render(InventoryRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        // Determine sprite size from the first available sprite (or default 16x16)
        int spriteSize = detectSpriteSize();
        int slotSize = spriteSize + SLOT_PADDING * 2;

        int titleHeight = input.drawTitle() ? TITLE_HEIGHT : 0;
        int border = input.drawBorder() ? BORDER_THICKNESS : 0;

        int contentWidth = input.slotsPerRow() * slotSize;
        int contentHeight = input.rows() * slotSize;
        int imageWidth = contentWidth + border * 2;
        int imageHeight = contentHeight + titleHeight + border * 2;

        BufferedImage canvas = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Background
        if (input.drawBackground()) {
            g.setColor(BG_COLOR);
            g.fillRect(0, 0, imageWidth, imageHeight);
        }

        // Outer border (Minecraft bevel style)
        if (input.drawBorder()) {
            drawBorder(g, imageWidth, imageHeight, border);
        }

        // Title
        if (input.drawTitle() && !input.title().isBlank()) {
            drawTitle(g, input.title(), border, border, imageWidth - border * 2);
        }

        // Slot backgrounds
        int slotsOriginX = border;
        int slotsOriginY = border + titleHeight;

        for (int row = 0; row < input.rows(); row++) {
            for (int col = 0; col < input.slotsPerRow(); col++) {
                int sx = slotsOriginX + col * slotSize;
                int sy = slotsOriginY + row * slotSize;
                drawSlot(g, sx, sy, slotSize);
            }
        }

        // Place item sprites and collect enchanted items
        boolean hasEnchanted = false;
        List<PlacedItem> placed = new ArrayList<>();

        for (InventoryItem item : input.items()) {
            int slot = item.slot();
            int row = slot / input.slotsPerRow();
            int col = slot % input.slotsPerRow();

            if (row >= input.rows() || col >= input.slotsPerRow()) {
                continue; // Slot out of range
            }

            int sx = slotsOriginX + col * slotSize + SLOT_PADDING;
            int sy = slotsOriginY + row * slotSize + SLOT_PADDING;

            spriteProvider.getSprite(item.itemId()).ifPresent(sprite -> {
                g.drawImage(sprite, sx, sy, spriteSize, spriteSize, null);
            });

            if (item.enchanted()) {
                hasEnchanted = true;
            }

            placed.add(new PlacedItem(item, sx, sy, slotSize, spriteSize));
        }

        // Draw stack counts
        Font stackFont = new Font(Font.MONOSPACED, Font.PLAIN, Math.max(6, spriteSize / 2));
        g.setFont(stackFont);

        for (PlacedItem pi : placed) {
            if (pi.item().stackCount() > 1) {
                String label = String.valueOf(pi.item().stackCount());
                int textX = pi.slotX() + pi.slotSize() - SLOT_PADDING - label.length() * (stackFont.getSize() / 2);
                int textY = pi.slotY() + pi.slotSize() - SLOT_PADDING;

                // Shadow
                g.setColor(STACK_COUNT_SHADOW);
                g.drawString(label, textX + 1, textY + 1);
                // Foreground
                g.setColor(STACK_COUNT_COLOR);
                g.drawString(label, textX, textY);
            }
        }

        g.dispose();

        // If enchanted items are present run the effect pipeline (glint = animated)
        if (hasEnchanted) {
            EffectContext effectCtx = EffectContext.builder()
                    .image(canvas)
                    .enchanted(true)
                    .build();
            EffectContext result = effectPipeline.execute(effectCtx);
            if (!result.animationFrames().isEmpty()) {
                return new GeneratorResult.AnimatedImage(result.animationFrames(), result.frameDelayMs());
            }
        }

        return new GeneratorResult.StaticImage(canvas);
    }

    @Override
    public Class<InventoryRequest> inputType() {
        return InventoryRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    private int detectSpriteSize() {
        return spriteProvider.availableSprites().stream()
                .findFirst()
                .flatMap(spriteProvider::getSprite)
                .map(BufferedImage::getWidth)
                .orElse(16);
    }

    /** Draws a Minecraft-style raised/sunken bevel border around the canvas. */
    private static void drawBorder(Graphics2D g, int w, int h, int border) {
        // Top and left edges (light)
        g.setColor(BORDER_OUTER_LIGHT);
        g.fillRect(0, 0, w, border);
        g.fillRect(0, 0, border, h);

        // Bottom and right edges (dark)
        g.setColor(BORDER_OUTER_DARK);
        g.fillRect(0, h - border, w, border);
        g.fillRect(w - border, 0, border, h);

        // Inner top-left (dark, inset by 2px)
        int inset = border / 2;
        g.setColor(BORDER_INNER_DARK);
        g.fillRect(inset, inset, w - inset * 2, inset);
        g.fillRect(inset, inset, inset, h - inset * 2);

        // Inner bottom-right (light, inset by 2px)
        g.setColor(BORDER_INNER_LIGHT);
        g.fillRect(inset, h - inset * 2, w - inset * 2, inset);
        g.fillRect(w - inset * 2, inset, inset, h - inset * 2);
    }

    /** Draws a slot at the given position using a sunken bevel. */
    private static void drawSlot(Graphics2D g, int x, int y, int size) {
        // Slot outer (dark top-left, light bottom-right) - sunken look
        g.setColor(SLOT_OUTER_DARK);
        g.fillRect(x, y, size, 1);
        g.fillRect(x, y, 1, size);

        g.setColor(SLOT_OUTER_LIGHT);
        g.fillRect(x, y + size - 1, size, 1);
        g.fillRect(x + size - 1, y, 1, size);

        // Slot inner fill (slightly darker than bg)
        g.setColor(SLOT_INNER);
        g.fillRect(x + 1, y + 1, size - 2, size - 2);
    }

    private static void drawTitle(Graphics2D g, String title, int x, int y, int width) {
        g.setColor(new Color(64, 64, 64));
        Font titleFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
        g.setFont(titleFont);
        g.drawString(title, x + 4, y + TITLE_HEIGHT - 4);
    }

    /** Internal record bundling a placed item with its draw coordinates. */
    private record PlacedItem(InventoryItem item, int slotX, int slotY, int slotSize, int spriteSize) {}
}
