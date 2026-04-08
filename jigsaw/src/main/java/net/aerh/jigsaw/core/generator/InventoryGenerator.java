package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.effect.EffectPipeline;
import net.aerh.jigsaw.core.util.GraphicsUtil;
import net.aerh.jigsaw.exception.RenderException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders a Minecraft-style inventory container image using the authentic GUI textures and colors.
 *
 * <p>Uses the bundled {@code slot.png} texture for slot backgrounds and draws pixel-perfect
 * Minecraft GUI borders with proper highlight/shadow colors. Title and stack counts use the
 * Minecraft font with drop shadow rendering.
 */
public final class InventoryGenerator implements Generator<InventoryRequest, GeneratorResult> {

    private static final Logger log = LoggerFactory.getLogger(InventoryGenerator.class);

    // Minecraft GUI colors (from the old NerdBot MinecraftInventoryGenerator)
    private static final Color INVENTORY_BACKGROUND = new Color(198, 198, 198);
    private static final Color DARK_BORDER_COLOR = new Color(85, 85, 85);
    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);

    private static final String SLOT_TEXTURE_PATH = "/minecraft/assets/textures/slot.png";
    private static final String FONT_PATH = "/minecraft/assets/fonts/Minecraft-Regular.otf";

    private final SpriteProvider spriteProvider;
    private final EffectPipeline effectPipeline;

    public InventoryGenerator(SpriteProvider spriteProvider, EffectPipeline effectPipeline) {
        this.spriteProvider = Objects.requireNonNull(spriteProvider, "spriteProvider must not be null");
        this.effectPipeline = Objects.requireNonNull(effectPipeline, "effectPipeline must not be null");
    }

    @Override
    public GeneratorResult render(InventoryRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        // Derive scale factor from sprite size (sprites are double-size in atlas)
        int baseSpriteSize = detectSpriteSize();
        int baseScaleFactor = Math.max(1, baseSpriteSize / 2 / 16);

        // Apply request scale on top
        int scaleFactor = baseScaleFactor * input.scale();
        int slotSize = 18 * scaleFactor;
        int itemSize = 16 * scaleFactor;

        // Layout calculations (matching old MinecraftInventoryGenerator)
        int borderSize = input.drawBorder() ? 7 * scaleFactor : 0;
        boolean drawTitle = input.drawTitle() && !input.title().isBlank();
        int titleHeight = borderSize + (drawTitle ? 13 * scaleFactor : 0)
                - (input.drawBorder() && drawTitle ? 3 * scaleFactor : 0);

        int imageWidth = (input.slotsPerRow() * slotSize) + (borderSize * 2);
        int imageHeight = (input.rows() * slotSize) + titleHeight + borderSize;

        BufferedImage canvas = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Load Minecraft font
        Font mcFont = loadMinecraftFont(scaleFactor * 8f);
        g.setFont(mcFont);

        // Draw border
        if (input.drawBorder()) {
            drawMinecraftBorder(g, imageWidth, imageHeight, scaleFactor);
        }

        // Draw title
        if (drawTitle) {
            int titleX = 8 * scaleFactor;
            int titleY = titleHeight - scaleFactor * 4;
            g.setColor(DROP_SHADOW_COLOR);
            g.drawString(input.title(), titleX, titleY);
        }

        // Load and resize slot texture
        BufferedImage slotTexture = loadSlotTexture(slotSize);

        // Draw slots and place items
        List<PlacedItem> placed = new ArrayList<>();
        int slotsOriginY = titleHeight;

        for (int row = 0; row < input.rows(); row++) {
            for (int col = 0; col < input.slotsPerRow(); col++) {
                int sx = borderSize + (col * slotSize);
                int sy = slotsOriginY + (row * slotSize);

                if (input.drawBackground()) {
                    if (slotTexture != null) {
                        g.drawImage(slotTexture, sx, sy, null);
                    } else {
                        drawSlotFallback(g, sx, sy, slotSize, scaleFactor);
                    }
                }
            }
        }

        // Place item sprites
        boolean hasEnchanted = false;
        for (InventoryItem item : input.items()) {
            int slot = item.slot();
            int row = slot / input.slotsPerRow();
            int col = slot % input.slotsPerRow();

            if (row >= input.rows() || col >= input.slotsPerRow()) {
                continue;
            }

            int sx = borderSize + (col * slotSize);
            int sy = slotsOriginY + (row * slotSize);
            int itemPadding = (slotSize - itemSize) / 2;
            int itemX = sx + itemPadding;
            int itemY = sy + itemPadding;

            spriteProvider.getSprite(item.itemId()).ifPresent(sprite ->
                    g.drawImage(sprite, itemX, itemY, itemSize, itemSize, null));

            if (item.enchanted()) {
                hasEnchanted = true;
            }

            placed.add(new PlacedItem(item, sx, sy, slotSize, itemSize));
        }

        // Draw stack counts
        for (PlacedItem pi : placed) {
            if (pi.item().stackCount() > 1) {
                drawStackCount(g, pi, scaleFactor, mcFont);
            }
        }

        g.dispose();

        // Enchantment glint animation
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

    /**
     * Returns the scale factor derived from the sprite provider's actual sprite size.
     */
    public int getScaleFactor() {
        int spriteSize = detectSpriteSize();
        return Math.max(1, spriteSize / 2 / 16);
    }

    private int detectSpriteSize() {
        return spriteProvider.availableSprites().stream()
                .findFirst()
                .flatMap(spriteProvider::getSprite)
                .map(BufferedImage::getWidth)
                .orElse(32); // Default to 32 (16 * 2) if no sprites available
    }

    // -------------------------------------------------------------------------
    // Minecraft GUI border (pixel-perfect port from old MinecraftInventoryGenerator)
    // -------------------------------------------------------------------------

    private static void drawMinecraftBorder(Graphics2D g, int imageWidth, int imageHeight, int s) {
        // Background fill
        g.setColor(INVENTORY_BACKGROUND);
        g.fillRect(s * 3, s * 3, imageWidth - s * 6, imageHeight - s * 6);
        g.fillRect(imageWidth - s * 3, s * 2, s, s); // top right corner fill
        g.fillRect(s * 2, imageHeight - s * 3, s, s); // bottom left corner fill

        // Dark gray shadow (right and bottom edges)
        g.setColor(DARK_BORDER_COLOR);
        g.fillRect(imageWidth - s * 3, s * 3, s * 2, imageHeight - s * 4);
        g.fillRect(s * 3, imageHeight - s * 3, imageWidth - s * 6, s * 2);
        g.fillRect(imageWidth - s * 4, imageHeight - s * 4, s, s);

        // White highlight (left and top edges)
        g.setColor(Color.WHITE);
        g.fillRect(s, s, s * 2, imageHeight - s * 4);
        g.fillRect(s * 3, s, imageWidth - s * 6, s * 2);
        g.fillRect(s * 3, s * 3, s, s);

        // Black outline
        g.setColor(Color.BLACK);
        g.fillRect(0, s * 2, s, imageHeight - s * 5);           // left
        g.fillRect(imageWidth - s, s * 3, s, imageHeight - s * 5); // right
        g.fillRect(s * 2, 0, imageWidth - s * 5, s);            // top
        g.fillRect(s * 3, imageHeight - s, imageWidth - s * 5, s); // bottom
        // Corner pixels
        g.fillRect(s, s, s, s);                                   // top-left
        g.fillRect(imageWidth - s * 3, s, s, s);                  // top-right upper
        g.fillRect(imageWidth - s * 2, s * 2, s, s);              // top-right lower
        g.fillRect(imageWidth - s * 2, imageHeight - s * 2, s, s); // bottom-right
        g.fillRect(s, imageHeight - s * 3, s, s);                 // bottom-left upper
        g.fillRect(s * 2, imageHeight - s * 2, s, s);             // bottom-left lower
    }

    // -------------------------------------------------------------------------
    // Slot rendering
    // -------------------------------------------------------------------------

    private static BufferedImage loadSlotTexture(int slotSize) {
        try (InputStream stream = InventoryGenerator.class.getResourceAsStream(SLOT_TEXTURE_PATH)) {
            if (stream != null) {
                BufferedImage original = ImageIO.read(stream);
                return ImageUtil.resizeImage(original, slotSize, slotSize, BufferedImage.TYPE_INT_ARGB);
            }
        } catch (IOException e) {
            log.warn("Failed to load slot texture, using fallback: {}", e.getMessage());
        }
        return null;
    }

    private static void drawSlotFallback(Graphics2D g, int x, int y, int slotSize, int scaleFactor) {
        // Background
        g.setColor(INVENTORY_BACKGROUND);
        g.fillRect(x + scaleFactor, y + scaleFactor, slotSize - 2 * scaleFactor, slotSize - 2 * scaleFactor);

        // Dark border (top-left)
        g.setColor(DARK_BORDER_COLOR);
        g.drawRect(x, y, slotSize - 1, slotSize - 1);
        g.drawRect(x + 1, y + 1, slotSize - 3, slotSize - 3);

        // White highlight (bottom-right)
        g.setColor(Color.WHITE);
        g.drawLine(x + slotSize - 1, y, x + slotSize - 1, y + slotSize - 1);
        g.drawLine(x, y + slotSize - 1, x + slotSize - 1, y + slotSize - 1);
    }

    // -------------------------------------------------------------------------
    // Stack count rendering
    // -------------------------------------------------------------------------

    private static void drawStackCount(Graphics2D g, PlacedItem pi, int scaleFactor, Font font) {
        String text = String.valueOf(pi.item().stackCount());
        g.setFont(font);

        int textWidth = g.getFontMetrics().stringWidth(text);
        int textX = pi.slotX() + pi.slotSize() - textWidth + 1;
        int textY = pi.slotY() + pi.slotSize() - scaleFactor + 1;

        // Drop shadow
        g.setColor(DROP_SHADOW_COLOR);
        g.drawString(text, textX + scaleFactor - 1, textY + scaleFactor - 1);

        // Foreground
        g.setColor(NORMAL_TEXT_COLOR);
        g.drawString(text, textX - 1, textY - 1);
    }

    // -------------------------------------------------------------------------
    // Font loading
    // -------------------------------------------------------------------------

    private static Font loadMinecraftFont(float size) {
        try (InputStream stream = InventoryGenerator.class.getResourceAsStream(FONT_PATH)) {
            if (stream != null) {
                Font base = Font.createFont(Font.TRUETYPE_FONT, stream);
                return base.deriveFont(size);
            }
        } catch (IOException | FontFormatException e) {
            log.warn("Failed to load Minecraft font, using fallback: {}", e.getMessage());
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
    }

    private record PlacedItem(InventoryItem item, int slotX, int slotY, int slotSize, int spriteSize) {}
}
