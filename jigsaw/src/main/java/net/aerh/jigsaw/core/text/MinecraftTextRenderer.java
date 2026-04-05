package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextRenderOptions;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders a {@link TextLayout} into a {@link GeneratorResult} using Minecraft-style visuals.
 * <p>
 * Features:
 * <ul>
 *   <li>Dark purple background fill with configurable alpha.</li>
 *   <li>Optional Minecraft-style border: outer dark purple, inner bright purple.</li>
 *   <li>Optional drop shadow at 1/4 brightness of text color, offset by scale factor.</li>
 *   <li>Colored, styled text segments.</li>
 * </ul>
 * Anti-aliasing is explicitly disabled to match the pixel-exact Minecraft aesthetic.
 */
public final class MinecraftTextRenderer {

    /** Minecraft tooltip background color. */
    private static final Color BACKGROUND_COLOR = new Color(16, 0, 16);

    /** Outer border color (dark purple). */
    private static final Color BORDER_OUTER_COLOR = new Color(20, 0, 20);

    /** Inner border highlight color (bright purple). */
    private static final Color BORDER_INNER_COLOR = new Color(80, 0, 255);

    /** Base font for rendering (monospaced to approximate Minecraft character widths). */
    private static final String FONT_NAME = Font.MONOSPACED;

    /** Character width in pixels at scale 1. */
    private static final int CHAR_WIDTH = 6;

    /** Line height in pixels at scale 1. */
    private static final int LINE_HEIGHT = 10;

    private MinecraftTextRenderer() {}

    /**
     * Renders the given {@link TextLayout} to a static {@link GeneratorResult}.
     *
     * @param layout  The laid-out text to render.
     * @param options Rendering configuration.
     * @return A {@link GeneratorResult.StaticImage} containing the rendered tooltip.
     */
    public static GeneratorResult renderLayout(TextLayout layout, TextRenderOptions options) {
        int scale = options.scaleFactor();
        int padding = options.padding() * scale;
        int firstLinePadding = options.firstLinePadding() * scale;
        int charW = CHAR_WIDTH * scale;
        int lineH = LINE_HEIGHT * scale;

        int contentWidth = layout.width() * charW;
        int contentHeight = layout.height() * lineH;

        // Total image dimensions: padding on all sides + firstLinePadding on top
        int imageWidth = contentWidth + padding * 2;
        int imageHeight = contentHeight + padding * 2 + firstLinePadding;

        // Ensure at least 1x1
        imageWidth = Math.max(1, imageWidth);
        imageHeight = Math.max(1, imageHeight);

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Disable anti-aliasing for the pixel-exact Minecraft look
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Background
        Color bg = applyAlpha(BACKGROUND_COLOR, options.alpha());
        g.setColor(bg);
        g.fillRect(0, 0, imageWidth, imageHeight);

        // Border
        if (options.border()) {
            drawBorder(g, imageWidth, imageHeight, scale, options.alpha());
        }

        // Text
        Font font = new Font(FONT_NAME, Font.PLAIN, lineH - 2 * scale);
        g.setFont(font);

        List<TextLine> lines = layout.lines();
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            int y = padding + firstLinePadding + i * lineH;

            int x = padding;
            if (options.centeredText()) {
                int lineWidthPx = line.width() * charW;
                x = (imageWidth - lineWidthPx) / 2;
            }

            for (TextSegment segment : line.segments()) {
                x = renderSegment(g, segment, x, y, charW, lineH, scale, options);
            }
        }

        g.dispose();
        return new GeneratorResult.StaticImage(image);
    }

    private static int renderSegment(
            Graphics2D g, TextSegment segment,
            int x, int y,
            int charW, int lineH,
            int scale, TextRenderOptions options) {

        Color color = segment.style().color();
        String text = segment.text();

        if (options.shadow()) {
            Color shadow = shadowColor(color);
            g.setColor(applyAlpha(shadow, options.alpha()));
            g.drawString(text, x + scale, y + lineH - 2 * scale + scale);
        }

        g.setColor(applyAlpha(color, options.alpha()));
        g.drawString(text, x, y + lineH - 2 * scale);

        return x + text.length() * charW;
    }

    private static void drawBorder(Graphics2D g, int width, int height, int scale, int alpha) {
        // Outer border (1px)
        g.setColor(applyAlpha(BORDER_OUTER_COLOR, alpha));
        g.drawRect(0, 0, width - 1, height - 1);

        // Inner border highlight (inset by 1px)
        g.setColor(applyAlpha(BORDER_INNER_COLOR, alpha));
        g.drawRect(scale, scale, width - 1 - 2 * scale, height - 1 - 2 * scale);
    }

    private static Color shadowColor(Color color) {
        int r = color.getRed() / 4;
        int g = color.getGreen() / 4;
        int b = color.getBlue() / 4;
        return new Color(r, g, b);
    }

    private static Color applyAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
