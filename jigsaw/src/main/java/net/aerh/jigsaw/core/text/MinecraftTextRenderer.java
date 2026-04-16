package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.font.FontRegistry;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.ChatColor;
import net.aerh.jigsaw.api.text.FormattingParser;
import net.aerh.jigsaw.api.text.TextRenderOptions;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;
import net.aerh.jigsaw.core.font.MinecraftFontId;
import net.aerh.jigsaw.core.util.GraphicsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Base font size: 2x the font's native 8ppem design size for pixel-perfect 2:1 scaling. */
    private static final float BASE_FONT_SIZE = 16.0f;

    private static final int[] UNICODE_BLOCK_RANGES = {
        0x0020, 0x007E, // Basic Latin
        0x00A0, 0x00FF, // Latin-1 Supplement
        0x2500, 0x257F, // Box Drawing
        0x2580, 0x259F  // Block Elements
    };

    private final FontRegistry fontRegistry;
    private final Map<WidthMapKey, Map<Integer, List<Character>>> obfuscationWidthMaps = new ConcurrentHashMap<>();

    /**
     * Cache key for {@link #obfuscationWidthMaps}. Font metrics depend on fontId, style, and
     * final rendered size — the lookup must match the font actually used at draw time, not the
     * base design size.
     */
    private record WidthMapKey(String fontId, boolean bold, boolean italic, int scaleFactor) {}

    /**
     * Creates a new renderer backed by the given font registry.
     *
     * @param fontRegistry the font registry to resolve fonts from; must not be {@code null}
     */
    public MinecraftTextRenderer(FontRegistry fontRegistry) {
        this.fontRegistry = Objects.requireNonNull(fontRegistry, "fontRegistry must not be null");
    }

    /**
     * Returns the obfuscation width map for the given font variant and scale, lazily computing
     * and caching it on first access.
     *
     * <p>Each combination of (fontId, bold, italic, scaleFactor) produces its own map because
     * {@link FontMetrics#charWidth(char)} values depend on all four: they are measured against
     * the exact font instance used at draw time. Using a map built for a different size or font
     * would cause {@link #drawObfuscatedChar} to miss on lookup and silently fall back to the
     * original character.
     */
    private Map<Integer, List<Character>> getOrComputeWidthMap(String fontId, boolean bold, boolean italic, int scaleFactor) {
        return obfuscationWidthMaps.computeIfAbsent(
            new WidthMapKey(fontId, bold, italic, scaleFactor),
            key -> computeWidthMap(key.fontId(), key.bold(), key.italic(), key.scaleFactor())
        );
    }

    private Map<Integer, List<Character>> computeWidthMap(String fontId, boolean bold, boolean italic, int scaleFactor) {
        float fontSize = BASE_FONT_SIZE * scaleFactor;
        Font font = fontRegistry.getStyledFont(fontId, bold, italic, fontSize);

        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG2d = tempImg.createGraphics();
        Map<Integer, List<Character>> map = new HashMap<>();
        try {
            GraphicsUtil.disableAntialiasing(tempG2d);
            FontMetrics metrics = tempG2d.getFontMetrics(font);

            for (int range = 0; range < UNICODE_BLOCK_RANGES.length; range += 2) {
                for (int codePoint = UNICODE_BLOCK_RANGES[range]; codePoint <= UNICODE_BLOCK_RANGES[range + 1]; codePoint++) {
                    char c = (char) codePoint;
                    if (font.canDisplay(c)) {
                        int width = metrics.charWidth(c);
                        if (width > 0) {
                            map.computeIfAbsent(width, k -> new ArrayList<>()).add(c);
                        }
                    }
                }
            }
        } finally {
            tempG2d.dispose();
        }

        int totalChars = map.values().stream().mapToInt(List::size).sum();
        log.debug("Built obfuscation width map for fontId={} bold={} italic={} scale={}: {} chars across {} widths",
            fontId, bold, italic, scaleFactor, totalChars, map.size());
        return map;
    }

    /**
     * Renders a list of formatted text lines into a tooltip image.
     *
     * @param lines   The formatted text lines (with section/ampersand color codes).
     * @param options Rendering configuration.
     * @return A {@link GeneratorResult} containing the rendered tooltip.
     */
    public GeneratorResult renderLines(List<String> lines, TextRenderOptions options) {
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
        GraphicsUtil.disableAntialiasing(measureGraphics);

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

        int canvasWidth = frameWidth + padding * 2;
        int canvasHeight = frameHeight + padding * 2;

        for (int frameIdx = 0; frameIdx < framesToGenerate; frameIdx++) {
            BufferedImage frameImage = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frameImage.createGraphics();
            GraphicsUtil.disableAntialiasing(graphics);

            // Offset all drawing by the padding amount so the content lands in the padded area
            if (padding > 0) {
                graphics.translate(padding, padding);
            }

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

            graphics.dispose();

            frames.add(frameImage);
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
    public GeneratorResult renderLayout(TextLayout layout, TextRenderOptions options) {
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

    private int calculateLineWidth(Graphics2D graphics, List<TextSegment> segments, int scaleFactor) {
        int lineWidth = 0;
        for (TextSegment segment : segments) {
            TextStyle style = segment.style();
            float scaledSize = BASE_FONT_SIZE * scaleFactor;
            Font baseFont = fontRegistry.getStyledFont(style.fontId(), style.bold(), style.italic(), scaledSize);
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
                    Font fallbackFont = fontRegistry.getFallbackFont(codePoint, baseFont.getSize2D());
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

    private void drawLinesInternal(Graphics2D frameGraphics, List<List<TextSegment>> parsedLines,
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

    private int drawString(Graphics2D graphics, TextSegment segment, int locationX, int locationY,
                                  int scaleFactor, int pixelSize) {
        TextStyle style = segment.style();
        float scaledSize = BASE_FONT_SIZE * scaleFactor;
        Font currentFont = fontRegistry.getStyledFont(style.fontId(), style.bold(), style.italic(), scaledSize);
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
                        metrics, locationX, locationY, pixelSize, scaleFactor);
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
    private int drawSymbolAndAdvance(Graphics2D graphics, int codePoint, String charStr,
                                            TextStyle style, Color fgColor, Color bgColor,
                                            Font currentFont, int locationX, int locationY,
                                            int pixelSize, int scaleFactor) {
        Font fallbackFont = fontRegistry.getFallbackFont(codePoint, currentFont.getSize2D());
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
    private int drawObfuscatedChar(Graphics2D graphics, char originalChar, TextStyle style,
                                          Color fgColor, Color bgColor,
                                          FontMetrics metrics, int locationX, int locationY,
                                          int pixelSize, int scaleFactor) {
        int originalWidth = metrics.charWidth(originalChar);
        String charToDrawStr = String.valueOf(originalChar);

        Map<Integer, List<Character>> widthMap = getOrComputeWidthMap(
            style.fontId(), style.bold(), style.italic(), scaleFactor);
        List<Character> matchingWidthChars = widthMap.get(originalWidth);

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
