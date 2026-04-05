package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.font.FontRegistry;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.ChatColor;
import net.aerh.jigsaw.api.text.FormattingParser;
import net.aerh.jigsaw.api.text.TextRenderOptions;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;
import net.aerh.jigsaw.core.font.DefaultFontRegistry;
import net.aerh.jigsaw.core.font.MinecraftFontId;
import net.aerh.jigsaw.core.util.GraphicsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Renders Minecraft-style tooltip text into a {@link GeneratorResult}.
 *
 * <p>This is a faithful port of the old {@code MinecraftTooltip} rendering logic. It renders
 * text character-by-character using the actual Minecraft .otf fonts at 15.5f base size, with
 * proper drop shadows, borders, and all formatting effects (bold, italic, strikethrough,
 * underline, obfuscation).
 *
 * <p>Anti-aliasing is explicitly disabled to match the pixel-exact Minecraft aesthetic.
 */
public final class MinecraftTextRenderer {

    private static final Logger log = LoggerFactory.getLogger(MinecraftTextRenderer.class);

    private static final int DEFAULT_PIXEL_SIZE = 2;
    private static final int STRIKETHROUGH_OFFSET = -8;
    private static final int UNDERLINE_OFFSET = 2;

    /** Base font size matching the original MinecraftTooltip rendering. */
    private static final float BASE_FONT_SIZE = 15.5f;

    private static final Map<Integer, Map<Integer, List<Character>>> OBFUSCATION_WIDTH_MAPS = new HashMap<>();
    private static final int[] UNICODE_BLOCK_RANGES = {
        0x0020, 0x007E, // Basic Latin
        0x00A0, 0x00FF, // Latin-1 Supplement
        0x2500, 0x257F, // Box Drawing
        0x2580, 0x259F  // Block Elements
    };

    private static final FontRegistry FONT_REGISTRY;

    static {
        FONT_REGISTRY = DefaultFontRegistry.withBuiltins();
        precomputeCharacterWidths();
    }

    private MinecraftTextRenderer() {
    }

    /**
     * Precomputes character widths for the text obfuscation/magic formatting effect.
     *
     * <p>The four style variants (regular, bold, italic, bold+italic) are indexed 0-3 matching
     * the bit pattern: bold=bit0, italic=bit1.
     */
    private static void precomputeCharacterWidths() {
        // Index 0=regular, 1=bold, 2=italic, 3=bold+italic
        List<Font> fonts = List.of(
            FONT_REGISTRY.getStyledFont(MinecraftFontId.DEFAULT, false, false, BASE_FONT_SIZE),
            FONT_REGISTRY.getStyledFont(MinecraftFontId.DEFAULT, true,  false, BASE_FONT_SIZE),
            FONT_REGISTRY.getStyledFont(MinecraftFontId.DEFAULT, false, true,  BASE_FONT_SIZE),
            FONT_REGISTRY.getStyledFont(MinecraftFontId.DEFAULT, true,  true,  BASE_FONT_SIZE)
        );

        for (int i = 0; i < fonts.size(); i++) {
            OBFUSCATION_WIDTH_MAPS.put(i, new HashMap<>());
        }

        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG2d = tempImg.createGraphics();
        tempG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        FontMetrics[] metrics = new FontMetrics[fonts.size()];

        for (int i = 0; i < fonts.size(); i++) {
            metrics[i] = tempG2d.getFontMetrics(fonts.get(i));
        }

        for (int fontIndex = 0; fontIndex < fonts.size(); fontIndex++) {
            Font font = fonts.get(fontIndex);
            FontMetrics fontMetrics = metrics[fontIndex];

            Map<Integer, List<Character>> map = OBFUSCATION_WIDTH_MAPS.get(fontIndex);

            for (int range = 0; range < UNICODE_BLOCK_RANGES.length; range += 2) {
                for (int codePoint = UNICODE_BLOCK_RANGES[range]; codePoint <= UNICODE_BLOCK_RANGES[range + 1]; codePoint++) {
                    char c = (char) codePoint;

                    if (font.canDisplay(c)) {
                        int width = fontMetrics.charWidth(c);
                        if (width > 0) {
                            map.computeIfAbsent(width, k -> new ArrayList<>()).add(c);
                        }
                    }
                }
            }
        }

        tempG2d.dispose();

        log.info("Precomputed obfuscation character widths. Regular: {} chars, Bold: {} chars, Italic: {} chars, BoldItalic: {} chars.",
            OBFUSCATION_WIDTH_MAPS.get(0).values().stream().mapToInt(List::size).sum(),
            OBFUSCATION_WIDTH_MAPS.get(1).values().stream().mapToInt(List::size).sum(),
            OBFUSCATION_WIDTH_MAPS.get(2).values().stream().mapToInt(List::size).sum(),
            OBFUSCATION_WIDTH_MAPS.get(3).values().stream().mapToInt(List::size).sum()
        );
    }

    /**
     * Renders a list of formatted text lines into a tooltip image.
     *
     * @param lines   The formatted text lines (with section/ampersand color codes).
     * @param options Rendering configuration.
     * @return A {@link GeneratorResult} containing the rendered tooltip.
     */
    public static GeneratorResult renderLines(List<String> lines, TextRenderOptions options) {
        int scaleFactor = options.scaleFactor();
        int pixelSize = DEFAULT_PIXEL_SIZE * scaleFactor;
        int startXY = pixelSize * 5;
        int yIncrement = pixelSize * 10;
        int alpha = options.alpha();
        boolean firstLinePadding = options.firstLinePadding() > 0;
        boolean renderBorder = options.border();
        boolean centeredText = options.centeredText();
        int padding = options.padding();

        // Parse each line into segments
        List<List<TextSegment>> parsedLines = new ArrayList<>();
        for (String line : lines) {
            parsedLines.add(FormattingParser.parse(line));
        }

        // Measure lines to find largest width
        BufferedImage dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = dummyImage.createGraphics();
        measureGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        Map<Integer, Integer> lineWidths = new HashMap<>();
        int locationY = startXY + pixelSize * 2 + yIncrement / 2;

        for (int lineIndex = 0; lineIndex < parsedLines.size(); lineIndex++) {
            int lineWidth = calculateLineWidth(measureGraphics, parsedLines.get(lineIndex), scaleFactor);
            lineWidths.put(lineIndex, lineWidth);

            int extraPadding = (lineIndex == 0 && firstLinePadding) ? pixelSize * 2 : 0;
            locationY += yIncrement + extraPadding;
        }

        int largestWidth = lineWidths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int measuredHeight = locationY;
        measureGraphics.dispose();

        // Calculate final dimensions
        int finalWidth = startXY + largestWidth + startXY;
        int finalHeight = measuredHeight - (yIncrement + (parsedLines.isEmpty() || !firstLinePadding ? 0 : pixelSize * 2)) + startXY + pixelSize * 2;

        // Check if any segments are obfuscated
        boolean hasObfuscated = parsedLines.stream()
            .flatMap(List::stream)
            .anyMatch(seg -> seg.style().obfuscated());

        int frameWidth = Math.max(1, finalWidth);
        int frameHeight = Math.max(1, finalHeight);

        // For animated content, generate multiple frames
        int framesToGenerate = hasObfuscated ? options.animationFrameCount() : 1;
        List<BufferedImage> frames = new ArrayList<>(framesToGenerate);

        for (int frameIdx = 0; frameIdx < framesToGenerate; frameIdx++) {
            BufferedImage frameImage = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frameImage.createGraphics();
            GraphicsUtil.disableAntialiasing(graphics);

            // Draw background
            graphics.setColor(new Color(18, 3, 18, hasObfuscated ? 255 : alpha));
            graphics.fillRect(
                pixelSize * 2,
                pixelSize * 2,
                frameWidth - pixelSize * 4,
                frameHeight - pixelSize * 4
            );

            // Draw text lines
            drawLinesInternal(graphics, parsedLines, lineWidths, largestWidth,
                scaleFactor, pixelSize, startXY, yIncrement, firstLinePadding, centeredText, hasObfuscated);

            // Draw borders
            if (renderBorder) {
                drawBorders(graphics, frameWidth, frameHeight, pixelSize, hasObfuscated ? 255 : alpha);
            }

            // Add padding
            BufferedImage processedFrame = addPadding(frameImage, padding);
            graphics.dispose();

            frames.add(processedFrame);
        }

        if (hasObfuscated && frames.size() > 1) {
            return new GeneratorResult.AnimatedImage(frames, options.frameDelayMs());
        }

        return new GeneratorResult.StaticImage(frames.isEmpty() ?
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) : frames.get(0));
    }

    /**
     * Renders a pre-laid-out {@link TextLayout} into a static image.
     *
     * <p>This method bridges the old layout-based API. It extracts the text from each line's
     * segments, reconstructs formatted strings, and delegates to {@link #renderLines}.
     *
     * @param layout  The laid-out text to render.
     * @param options Rendering configuration.
     * @return A {@link GeneratorResult} containing the rendered tooltip.
     */
    public static GeneratorResult renderLayout(TextLayout layout, TextRenderOptions options) {
        // Convert TextLayout lines back to formatted strings for rendering
        List<String> formattedLines = new ArrayList<>();
        for (TextLine line : layout.lines()) {
            StringBuilder sb = new StringBuilder();
            for (TextSegment segment : line.segments()) {
                sb.append(reconstructFormatCodes(segment));
                sb.append(segment.text());
            }
            formattedLines.add(sb.toString());
        }
        return renderLines(formattedLines, options);
    }

    /**
     * Reconstructs formatting codes from a TextSegment's style.
     */
    private static String reconstructFormatCodes(TextSegment segment) {
        StringBuilder codes = new StringBuilder();
        TextStyle style = segment.style();

        // Find the ChatColor that matches this style's color
        ChatColor chatColor = ChatColor.byRgb(style.color().getRGB());
        if (chatColor != null) {
            codes.append('\u00a7').append(chatColor.code());
        }

        // Font codes
        if (MinecraftFontId.GALACTIC.equals(style.fontId())) {
            codes.append("\u00a7g");
        } else if (MinecraftFontId.ILLAGERALT.equals(style.fontId())) {
            codes.append("\u00a7h");
        }

        // Formatting codes
        if (style.obfuscated()) {
            codes.append("\u00a7k");
        }
        if (style.bold()) {
            codes.append("\u00a7l");
        }
        if (style.strikethrough()) {
            codes.append("\u00a7m");
        }
        if (style.underlined()) {
            codes.append("\u00a7n");
        }
        if (style.italic()) {
            codes.append("\u00a7o");
        }

        return codes.toString();
    }

    // --- Line width calculation ---

    private static int calculateLineWidth(Graphics2D graphics, List<TextSegment> segments, int scaleFactor) {
        int lineWidth = 0;
        for (TextSegment segment : segments) {
            TextStyle style = segment.style();
            float scaledSize = BASE_FONT_SIZE * scaleFactor;
            Font baseFont = FONT_REGISTRY.getStyledFont(style.fontId(), style.bold(), style.italic(), scaledSize);
            graphics.setFont(baseFont);
            FontMetrics metrics = graphics.getFontMetrics(baseFont);
            String segmentText = segment.text();

            for (int i = 0; i < segmentText.length(); ) {
                int codePoint = segmentText.codePointAt(i);

                // Skip variation selectors (U+FE0E and U+FE0F)
                if (codePoint == 0xFE0E || codePoint == 0xFE0F) {
                    i += Character.charCount(codePoint);
                    continue;
                }

                String charStr = new String(Character.toChars(codePoint));

                if (baseFont.canDisplayUpTo(charStr) == -1) {
                    lineWidth += metrics.stringWidth(charStr);
                } else {
                    Font fallbackFont = FONT_REGISTRY.getFallbackFont(codePoint, baseFont.getSize2D());
                    if (fallbackFont != null) {
                        graphics.setFont(fallbackFont);
                        FontMetrics fallbackMetrics = graphics.getFontMetrics(fallbackFont);
                        lineWidth += fallbackMetrics.stringWidth(charStr);
                        graphics.setFont(baseFont);
                    } else {
                        lineWidth += metrics.stringWidth(charStr);
                    }
                }
                i += Character.charCount(codePoint);
            }
        }
        return lineWidth;
    }

    // --- Line drawing ---

    private static void drawLinesInternal(Graphics2D frameGraphics, List<List<TextSegment>> parsedLines,
                                          Map<Integer, Integer> lineWidths, int largestWidth,
                                          int scaleFactor, int pixelSize, int startXY, int yIncrement,
                                          boolean firstLinePadding, boolean centeredText, boolean isAnimated) {
        int locationY = startXY + pixelSize * 2 + yIncrement / 2;

        for (int lineIndex = 0; lineIndex < parsedLines.size(); lineIndex++) {
            List<TextSegment> segments = parsedLines.get(lineIndex);
            int lineWidth = lineWidths.getOrDefault(lineIndex, 0);

            int locationX;
            if (centeredText) {
                locationX = startXY + (largestWidth - lineWidth) / 2;
            } else {
                locationX = startXY;
            }

            // Draw segments for the line
            for (TextSegment segment : segments) {
                locationX = drawString(frameGraphics, segment, locationX, locationY, scaleFactor, pixelSize);
            }

            // Increment Y position for the next line
            int extraPadding = (lineIndex == 0 && firstLinePadding) ? pixelSize * 2 : 0;
            locationY += yIncrement + extraPadding;
        }
    }

    // --- String drawing ---

    private static int drawString(Graphics2D graphics, TextSegment segment, int locationX, int locationY,
                                  int scaleFactor, int pixelSize) {
        TextStyle style = segment.style();
        float scaledSize = BASE_FONT_SIZE * scaleFactor;
        Font currentFont = FONT_REGISTRY.getStyledFont(style.fontId(), style.bold(), style.italic(), scaledSize);
        graphics.setFont(currentFont);
        FontMetrics metrics = graphics.getFontMetrics(currentFont);

        Color currentColor = style.color();
        Color currentBgColor = resolveBackgroundColor(currentColor);

        String text = segment.text();
        StringBuilder subWord = new StringBuilder();

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);

            // Skip variation selectors (U+FE0E and U+FE0F)
            if (codePoint == 0xFE0E || codePoint == 0xFE0F) {
                i += Character.charCount(codePoint);
                continue;
            }

            String charStr = new String(Character.toChars(codePoint));
            int charCount = Character.charCount(codePoint);

            if (style.obfuscated()) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    int width = metrics.stringWidth(subWord.toString());
                    drawTextWithEffects(graphics, subWord.toString(), style, currentColor, currentBgColor,
                        locationX, locationY, width, pixelSize, scaleFactor);
                    locationX += width;
                    subWord.setLength(0);
                }

                // Draw obfuscated character
                if (codePoint <= 0xFFFF) {
                    locationX = drawObfuscatedChar(graphics, (char) codePoint, style, currentColor, currentBgColor,
                        currentFont, metrics, locationX, locationY, pixelSize, scaleFactor);
                } else {
                    locationX = drawSymbolAndAdvance(graphics, codePoint, charStr, style, currentColor, currentBgColor,
                        currentFont, locationX, locationY, pixelSize, scaleFactor);
                }

                i += charCount;
                continue;
            }

            if (currentFont.canDisplayUpTo(charStr) != -1) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    int width = metrics.stringWidth(subWord.toString());
                    drawTextWithEffects(graphics, subWord.toString(), style, currentColor, currentBgColor,
                        locationX, locationY, width, pixelSize, scaleFactor);
                    locationX += width;
                    subWord.setLength(0);
                }

                // Draw symbol using unicode fallback font
                locationX = drawSymbolAndAdvance(graphics, codePoint, charStr, style, currentColor, currentBgColor,
                    currentFont, locationX, locationY, pixelSize, scaleFactor);
                i += charCount;
                continue;
            }

            subWord.append(charStr);
            i += charCount;
        }

        // Draw any remaining subWord
        if (!subWord.isEmpty()) {
            int width = metrics.stringWidth(subWord.toString());
            drawTextWithEffects(graphics, subWord.toString(), style, currentColor, currentBgColor,
                locationX, locationY, width, pixelSize, scaleFactor);
            locationX += width;
        }

        return locationX;
    }

    /**
     * Draws a symbol using a fallback font when the Minecraft font cannot render it.
     */
    private static int drawSymbolAndAdvance(Graphics2D graphics, int codePoint, String charStr,
                                            TextStyle style, Color fgColor, Color bgColor,
                                            Font currentFont, int locationX, int locationY,
                                            int pixelSize, int scaleFactor) {
        Font fallbackFont = FONT_REGISTRY.getFallbackFont(codePoint, currentFont.getSize2D());
        Font fontToUse = fallbackFont != null ? fallbackFont : currentFont;

        graphics.setFont(fontToUse);
        FontMetrics symbolMetrics = graphics.getFontMetrics(fontToUse);
        int width = symbolMetrics.stringWidth(charStr);

        drawTextWithEffects(graphics, charStr, style, fgColor, bgColor,
            locationX, locationY, width, pixelSize, scaleFactor);

        locationX += width;
        graphics.setFont(currentFont);
        return locationX;
    }

    /**
     * Draw an obfuscated character with a random character of the same width.
     */
    private static int drawObfuscatedChar(Graphics2D graphics, char originalChar, TextStyle style,
                                          Color fgColor, Color bgColor, Font currentFont,
                                          FontMetrics metrics, int locationX, int locationY,
                                          int pixelSize, int scaleFactor) {
        int originalWidth = metrics.charWidth(originalChar);
        String charToDrawStr = String.valueOf(originalChar);

        int fontStyleIndex = (style.bold() ? 1 : 0) + (style.italic() ? 2 : 0);
        Map<Integer, List<Character>> widthMap = OBFUSCATION_WIDTH_MAPS.get(fontStyleIndex);
        List<Character> matchingWidthChars = (widthMap != null) ? widthMap.get(originalWidth) : null;

        if (matchingWidthChars != null && !matchingWidthChars.isEmpty()) {
            char randomChar = matchingWidthChars.get(ThreadLocalRandom.current().nextInt(matchingWidthChars.size()));
            charToDrawStr = String.valueOf(randomChar);
        }

        int drawnWidth = metrics.stringWidth(charToDrawStr);
        drawTextWithEffects(graphics, charToDrawStr, style, fgColor, bgColor,
            locationX, locationY, drawnWidth, pixelSize, scaleFactor);
        return locationX + drawnWidth;
    }

    /**
     * Draws the text with strikethrough, underline and drop shadow effects.
     */
    private static void drawTextWithEffects(Graphics2D frameGraphics, String textToDraw, TextStyle style,
                                            Color fgColor, Color bgColor, int locationX, int locationY,
                                            int width, int pixelSize, int scaleFactor) {
        // Draw Strikethrough Drop Shadow
        if (style.strikethrough()) {
            drawThickLine(frameGraphics, width, locationX, locationY, -1,
                STRIKETHROUGH_OFFSET * scaleFactor, true, bgColor, pixelSize);
        }

        // Draw Underlined Drop Shadow
        if (style.underlined()) {
            drawThickLine(frameGraphics, width, locationX - pixelSize, locationY, 1,
                UNDERLINE_OFFSET * scaleFactor, true, bgColor, pixelSize);
        }

        // Draw Drop Shadow Text
        frameGraphics.setColor(bgColor);
        frameGraphics.drawString(textToDraw, locationX + pixelSize, locationY + pixelSize);

        // Draw Text
        frameGraphics.setColor(fgColor);
        frameGraphics.drawString(textToDraw, locationX, locationY);

        // Draw Strikethrough
        if (style.strikethrough()) {
            drawThickLine(frameGraphics, width, locationX, locationY, -1,
                STRIKETHROUGH_OFFSET * scaleFactor, false, fgColor, pixelSize);
        }

        // Draw Underlined
        if (style.underlined()) {
            drawThickLine(frameGraphics, width, locationX - pixelSize, locationY, 1,
                UNDERLINE_OFFSET * scaleFactor, false, fgColor, pixelSize);
        }
    }

    /**
     * Draws a thick line on the image with optional drop shadow.
     */
    private static void drawThickLine(Graphics2D frameGraphics, int width, int xPosition, int yPosition,
                                      int xOffset, int yOffset, boolean dropShadow, Color color, int pixelSize) {
        int xPosition1 = xPosition;
        int xPosition2 = xPosition + width + xOffset;
        yPosition += yOffset;

        if (dropShadow) {
            xPosition1 += pixelSize;
            xPosition2 += pixelSize;
            yPosition += pixelSize;
        }

        frameGraphics.setColor(color);
        frameGraphics.drawLine(xPosition1, yPosition, xPosition2, yPosition);
        frameGraphics.drawLine(xPosition1, yPosition + 1, xPosition2, yPosition + 1);
    }

    // --- Borders ---

    private static void drawBorders(Graphics2D frameGraphics, int width, int height, int pixelSize, int alpha) {
        // Draw Darker Purple Border
        frameGraphics.setColor(new Color(18, 3, 18, alpha));
        frameGraphics.fillRect(0, pixelSize, pixelSize, height - pixelSize * 2); // Left
        frameGraphics.fillRect(pixelSize, 0, width - pixelSize * 2, pixelSize); // Top
        frameGraphics.fillRect(width - pixelSize, pixelSize, pixelSize, height - pixelSize * 2); // Right
        frameGraphics.fillRect(pixelSize, height - pixelSize, width - pixelSize * 2, pixelSize); // Bottom

        // Draw Purple Border
        frameGraphics.setColor(new Color(37, 0, 94, alpha));

        int outerInset = pixelSize;
        int outerThickness = Math.max(1, pixelSize / 2);
        drawBorderWithThickness(frameGraphics, width, height, outerInset, outerThickness);

        int innerInset = outerInset + outerThickness;
        int innerThickness = Math.max(1, (int) Math.round(pixelSize / 2.0));
        if (innerInset * 2 < width && innerInset * 2 < height) {
            drawBorderWithThickness(frameGraphics, width, height, innerInset, innerThickness);
        }
    }

    private static void drawBorderWithThickness(Graphics2D graphics, int width, int height, int inset, int thickness) {
        if (thickness <= 0) {
            return;
        }

        int innerWidth = width - inset * 2;
        int innerHeight = height - inset * 2;
        if (innerWidth <= 0 || innerHeight <= 0) {
            return;
        }

        // Top edge
        graphics.fillRect(inset, inset, innerWidth, thickness);
        // Bottom edge
        graphics.fillRect(inset, height - inset - thickness, innerWidth, thickness);

        int verticalHeight = innerHeight - thickness * 2;
        if (verticalHeight <= 0) {
            return;
        }

        // Left edge
        graphics.fillRect(inset, inset + thickness, thickness, verticalHeight);
        // Right edge
        graphics.fillRect(width - inset - thickness, inset + thickness, thickness, verticalHeight);
    }

    // --- Padding ---

    private static BufferedImage addPadding(BufferedImage frame, int padding) {
        if (padding <= 0) {
            return frame;
        }

        BufferedImage paddedFrame = new BufferedImage(
            frame.getWidth() + padding * 2,
            frame.getHeight() + padding * 2,
            BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics2D = paddedFrame.createGraphics();
        graphics2D.drawImage(frame, padding, padding, frame.getWidth(), frame.getHeight(), null);
        graphics2D.dispose();

        return paddedFrame;
    }

    // --- Color resolution ---

    /**
     * Resolves the background (shadow) color for a given foreground color.
     * If the color matches a known {@link ChatColor}, uses its hardcoded background color.
     * Otherwise, computes the shadow as each component divided by 4.
     */
    private static Color resolveBackgroundColor(Color fgColor) {
        ChatColor chatColor = ChatColor.byRgb(fgColor.getRGB());
        if (chatColor != null) {
            return chatColor.backgroundColor();
        }
        return ChatColor.computeShadowColor(fgColor);
    }
}
