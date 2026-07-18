package net.aerh.imagegenerator.image;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.pack.AnimatedTooltipSprites;
import net.aerh.imagegenerator.pack.AnimationTimeline;
import net.aerh.imagegenerator.pack.GuiScaling;
import net.aerh.imagegenerator.pack.GuiSpriteRenderer;
import net.aerh.imagegenerator.pack.PackAnimation;
import net.aerh.imagegenerator.pack.TooltipSprites;
import lib.minecraft.text.ChatColor;
import net.aerh.imagegenerator.text.PackGlyphDispatcher;
import net.aerh.imagegenerator.text.TextColorRemap;
import net.aerh.imagegenerator.text.MinecraftFont;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import net.aerh.imagegenerator.text.segment.LineSegment;
import net.aerh.imagegenerator.util.MinecraftFonts;
import net.hypixel.nerdbot.marmalade.Range;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class MinecraftTooltip {

    private static final Map<Integer, Map<Integer, List<Character>>> OBFUSCATION_WIDTH_MAPS = new HashMap<>(); // Integer key represents font style index (0: regular, 1: bold, 2: italic, 3: bold-italic)
    private static final int[] UNICODE_BLOCK_RANGES = {
        0x0020, 0x007E, // Basic Latin
        0x00A0, 0x00FF, // Latin-1 Supplement
        0x2500, 0x257F, // Box Drawing
        0x2580, 0x259F  // Block Elements
    };

    public static final int DEFAULT_PADDING = 0;
    public static final int DEFAULT_ALPHA = 245;
    public static final Range<Integer> LINE_LENGTH = Range.between(1, 128);

    private static final int DEFAULT_PIXEL_SIZE = 2;
    private static final double APRIL_FOOLS_SWAP_CHANCE = 0.33;
    private static final int STRIKETHROUGH_OFFSET = -8;
    private static final int UNDERLINE_OFFSET = 2;

    /**
     * Content inset, in GUI px, for a themed tooltip whose frame does not declare per-side
     * nine-slice borders (a stretched or tiled frame) - the historical symmetric themed inset
     * (vanilla tooltip content padding plus sprite margin). Nine-slice frames inset each side to
     * at least this value; see {@link #contentInsetGuiPx}.
     */
    private static final int THEMED_BASE_INSET_GUI_PX = 12;
    /** Content inset, in GUI px, for an unthemed (programmatic-chrome) tooltip. */
    private static final int PLAIN_INSET_GUI_PX = 5;

    static {
        // Precompute character widths for the obfuscation effect
        precomputeCharacterWidths();
    }

    @Getter
    private List<BufferedImage> animationFrames = new ArrayList<>();

    @Getter
    private final List<LineSegment> lines;
    @Getter
    private final int alpha;
    @Getter
    private final int padding;
    private final boolean firstLinePadding;
    @Getter
    private final boolean renderBorder;
    @Getter
    private final boolean centeredText;
    @Getter
    private final int scaleFactor;
    private final boolean aprilFools;
    /**
     * Whether text draws its drop shadow (built-in runs, pack glyph shadow passes and decoration
     * shadow passes alike). On for tooltips - vanilla tooltips always shadow - and switched off
     * by compositors drawing shadowless vanilla surfaces, e.g. container screen titles.
     */
    private final boolean textShadow;
    private final TooltipSprites themeSprites;
    /**
     * The theme sprites' animations, when the caller opted into animated pack textures and at
     * least one sprite animates; null keeps the render fully on the historical path. Only
     * meaningful together with {@link #themeSprites} (the animated chrome replaces the static
     * theme layer frame by frame).
     */
    private final AnimatedTooltipSprites animatedThemeSprites;
    private final TextColorRemap textColorRemap;
    private final PackGlyphDispatcher packGlyphs;

    // Scaled values based on scale factor
    private final int pixelSize;
    /**
     * Per-side content inset in CANVAS px (GUI px times {@link #pixelSize}). Unthemed and
     * stretched/tiled-frame tooltips carry the historical symmetric inset on all four sides; a
     * themed tooltip whose frame declares nine-slice borders insets each side to clear that
     * border. See {@link #contentInsetGuiPx}.
     */
    private final int insetLeft;
    private final int insetTop;
    private final int insetRight;
    private final int insetBottom;
    private final int yIncrement;

    @Getter
    private BufferedImage image;
    @Getter
    private boolean isAnimated = false;
    @Getter
    private int frameDelayMs;
    /**
     * The frame delay the builder configured, kept apart from {@link #frameDelayMs} (which the
     * animated-chrome path reassigns to the first step's delay) so repeated {@link #render()}
     * calls derive the obfuscation ticker from the configuration, never from a previous render.
     */
    private final int configuredFrameDelayMs;
    @Getter
    private int animationFrameCount;
    /**
     * Per-frame delays in milliseconds when the animated-chrome path produced frames that hold
     * for DIFFERENT times; null on every other path, where {@link #getFrameDelayMs()} applies
     * to all frames.
     */
    @Getter
    private List<Integer> frameDelaysMs;

    private transient ChatColor currentColor;
    private transient Font currentFont;
    /**
     * Text cursor in canvas pixels. A double so fractional and negative pack glyph advances
     * accumulate without per-step rounding; on the built-in (no-pack) path only integer AWT
     * widths are ever added, so the value stays integer-exact and rounding at the draw sites is
     * the identity - no-pack output is pixel-identical to the historical int cursor.
     */
    private transient double locationX;
    private transient int locationY;
    /**
     * Rightmost extent (canvas px) reached by the current line's cursor. In the measure pass the
     * line starts at 0, so this is relative to the line start; the draw pass folds absolute
     * (inset-based) positions into the same field, but only the measure-pass value is ever
     * consumed. Negative pack advances can move the cursor LEFT, so a line's width for canvas
     * sizing and centering is this max extent, not the final cursor position.
     */
    private transient double lineMaxExtent;
    /**
     * Leftmost extent (canvas px, never above 0) reached by the current line's cursor in the
     * measure pass. Leading negative pack advances place glyph art LEFT of the line start; see
     * {@link #leftShift}.
     */
    private transient double lineMinExtent;
    /**
     * Canvas px added to every line's draw start (and to the canvas width) so glyph art reached
     * through leading negative advances draws inside the canvas instead of clipping at x = 0.
     * Zero whenever no line's art crosses the left canvas edge - i.e. always, for normal text,
     * keeping the left padding behavior unchanged.
     */
    private transient int leftShift;
    /**
     * Topmost drawn-art extent (canvas px, never above 0) of the current line, relative to the
     * line top, tracked by the measure pass: pack glyph cells top out at
     * {@code (7 - ascent) * pixelSize} below the line top, which is NEGATIVE for large ascents.
     * Consumed through {@link #measureLineExtents} by external compositors; {@link #render}
     * itself keeps the historical line-height-based canvas (tall art clips there by design).
     */
    private transient double lineArtTop;
    /**
     * Bottommost drawn-art extent (canvas px, never below 0) of the current line, relative to
     * the line top; the counterpart of {@link #lineArtTop}. Built-in runs count as the standard
     * 9 GUI px line box plus the shadow row; pack glyph cells bottom out at
     * {@code (7 - ascent + height) * pixelSize} plus the shadow row.
     */
    private transient double lineArtBottom;
    /** Per-frame counter feeding deterministic pack glyph obfuscation seeds. */
    private transient int packObfuscationCounter;
    private transient int largestWidth = 0;
    private transient Map<Integer, Integer> lineMetrics;
    /** Per-line {@link #lineMinExtent} captured by the measure pass, keyed like {@link #lineMetrics}. */
    private transient Map<Integer, Double> lineMinExtents;

    /**
     * Construct a new {@link MinecraftTooltip} instance.
     *
     * @param lines               A list of {@link LineSegment} objects representing the lines of text.
     * @param defaultColor        The default {@link ChatColor} to use for the text.
     * @param alpha               The alpha value for the tooltip background. Range: 0-255.
     * @param padding             The padding value for the tooltip. Range: 0-255.
     * @param firstLinePadding    Whether to apply padding to the first line.
     * @param renderBorder        Whether to render a border around the tooltip.
     * @param centeredText        Whether to center the text within the tooltip.
     * @param frameDelayMs        The delay in milliseconds between animation frames.
     * @param animationFrameCount The number of frames to generate for the animation.
     * @param scaleFactor         The scale factor to apply to all pixel sizes.
     * @param aprilFools          Whether to randomly swap characters to alternate fonts.
     * @param textShadow          Whether text draws its drop shadow; see {@link #textShadow}.
     * @param themeSprites        Pack tooltip sprites replacing the programmatic chrome, or null.
     * @param animatedThemeSprites The theme sprites' animations, or null; see {@link #animatedThemeSprites}.
     * @param textColorRemap      Shader-equivalent text color replacement table, or null.
     * @param packFontSource      Resolver for pack font ids, or null when no pack is active.
     */
    private MinecraftTooltip(List<LineSegment> lines, ChatColor defaultColor, int alpha, int padding, boolean firstLinePadding, boolean renderBorder, boolean centeredText, int frameDelayMs, int animationFrameCount, int scaleFactor, boolean aprilFools, boolean textShadow, TooltipSprites themeSprites, AnimatedTooltipSprites animatedThemeSprites, TextColorRemap textColorRemap, PackGlyphDispatcher.FontSource packFontSource) {
        this.lines = lines;
        this.currentColor = defaultColor;
        this.alpha = alpha;
        this.padding = padding;
        this.firstLinePadding = firstLinePadding;
        this.renderBorder = renderBorder;
        this.centeredText = centeredText;
        this.frameDelayMs = frameDelayMs;
        this.configuredFrameDelayMs = frameDelayMs;
        this.animationFrameCount = animationFrameCount;
        this.scaleFactor = scaleFactor;
        this.aprilFools = aprilFools;
        this.textShadow = textShadow;
        this.themeSprites = themeSprites;
        this.animatedThemeSprites = animatedThemeSprites;
        this.textColorRemap = textColorRemap;
        this.packGlyphs = PackGlyphDispatcher.of(packFontSource);

        this.pixelSize = DEFAULT_PIXEL_SIZE * scaleFactor;
        // Themed tooltips use the vanilla sprite-rect model: background and frame cover ONE rect
        // expanded per-side around the text, with 1 GUI px equal to one pixelSize unit. A frame
        // that declares nine-slice borders insets the content to clear those borders; every other
        // case keeps the historical symmetric inset. Pack art may legitimately extend past the box.
        int[] insetGuiPx = contentInsetGuiPx();
        this.insetLeft = pixelSize * insetGuiPx[0];
        this.insetTop = pixelSize * insetGuiPx[1];
        this.insetRight = pixelSize * insetGuiPx[2];
        this.insetBottom = pixelSize * insetGuiPx[3];
        this.yIncrement = pixelSize * 10;

        this.locationX = insetLeft;
        this.locationY = insetTop + pixelSize * 2 + yIncrement / 2;
    }

    /**
     * Per-side content inset in GUI px, ordered {@code [left, top, right, bottom]}.
     *
     * <p>An unthemed tooltip insets every side by {@link #PLAIN_INSET_GUI_PX}. A themed tooltip
     * whose frame sprite declares {@link GuiScaling.NineSlice nine-slice} scaling insets each
     * side to that side's declared border, so the content clears the frame's drawn border art
     * exactly as vanilla places content relative to the style's nine-slice declarations - but
     * never tighter than {@link #THEMED_BASE_INSET_GUI_PX} (a border thinner than the base
     * tooltip padding still leaves the vanilla-equivalent gap, keeping thin-border frames
     * pixel-identical to the historical themed inset). A stretched or tiled frame carries no
     * per-side border, so it falls back to the symmetric base inset.
     */
    private int[] contentInsetGuiPx() {
        if (!isThemed()) {
            return new int[]{PLAIN_INSET_GUI_PX, PLAIN_INSET_GUI_PX, PLAIN_INSET_GUI_PX, PLAIN_INSET_GUI_PX};
        }
        if (themeSprites.frame().scaling() instanceof GuiScaling.NineSlice nineSlice) {
            GuiScaling.NineSlice.Border border = nineSlice.border();
            return new int[]{
                Math.max(border.left(), THEMED_BASE_INSET_GUI_PX),
                Math.max(border.top(), THEMED_BASE_INSET_GUI_PX),
                Math.max(border.right(), THEMED_BASE_INSET_GUI_PX),
                Math.max(border.bottom(), THEMED_BASE_INSET_GUI_PX)
            };
        }
        return new int[]{THEMED_BASE_INSET_GUI_PX, THEMED_BASE_INSET_GUI_PX, THEMED_BASE_INSET_GUI_PX, THEMED_BASE_INSET_GUI_PX};
    }

    /** Sprite chrome applies only when a theme is present AND the border is enabled; renderBorder=false means no chrome at all. */
    private boolean isThemed() {
        return themeSprites != null && renderBorder;
    }

    /**
     * Precomputes character widths for the text obfuscation/magic formatting effect.
     */
    private static void precomputeCharacterWidths() {
        List<Font> fonts = MinecraftFonts.getAllFonts();

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

                    if (MinecraftFonts.canRender(font, c)) {
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
     * Creates a new {@link MinecraftTooltip} instance.
     *
     * @return A new {@link Builder} instance for creating a {@link MinecraftTooltip}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether first line padding is enabled.
     *
     * @return true if first line padding is enabled
     */
    public boolean hasFirstLinePadding() {
        return this.firstLinePadding;
    }

    /**
     * Adds padding to the tooltip frame.
     *
     * @param frame The {@link BufferedImage} frame to add padding to.
     *
     * @return The padded {@link BufferedImage} frame.
     */
    private BufferedImage addPadding(BufferedImage frame) {
        if (this.getPadding() <= 0) {
            return frame;
        }

        BufferedImage paddedFrame = new BufferedImage(
            frame.getWidth() + this.getPadding() * 2,
            frame.getHeight() + this.getPadding() * 2,
            BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics2D = paddedFrame.createGraphics();
        graphics2D.drawImage(frame, this.getPadding(), this.getPadding(), frame.getWidth(), frame.getHeight(), null);
        graphics2D.dispose();

        return paddedFrame;
    }

    /**
     * Draws the borders around the tooltip.
     *
     * @param frameGraphics The {@link Graphics2D} object to draw on.
     * @param width         The width of the tooltip.
     * @param height        The height of the tooltip.
     */
    private void drawBorders(Graphics2D frameGraphics, int width, int height) {
        // Draw Darker Purple Border
        frameGraphics.setColor(new Color(18, 3, 18, this.isAnimated ? 255 : this.getAlpha()));
        frameGraphics.fillRect(0, pixelSize, pixelSize, height - pixelSize * 2); // Left
        frameGraphics.fillRect(pixelSize, 0, width - pixelSize * 2, pixelSize); // Top
        frameGraphics.fillRect(width - pixelSize, pixelSize, pixelSize, height - pixelSize * 2); // Right
        frameGraphics.fillRect(pixelSize, height - pixelSize, width - pixelSize * 2, pixelSize); // Bottom

        // Draw Purple Border
        frameGraphics.setColor(new Color(37, 0, 94, this.isAnimated ? 255 : this.getAlpha()));

        int outerInset = pixelSize;
        int outerThickness = Math.max(1, pixelSize / 2);
        drawBorderWithThickness(frameGraphics, width, height, outerInset, outerThickness);

        int gapBetweenBorders = 0;
        int innerInset = outerInset + outerThickness + gapBetweenBorders;
        int innerThickness = Math.max(1, (int) Math.round(pixelSize / 2.0));
        if (innerInset * 2 < width && innerInset * 2 < height) {
            drawBorderWithThickness(frameGraphics, width, height, innerInset, innerThickness);
        }
    }

    private void drawBorderWithThickness(Graphics2D graphics, int width, int height, int inset, int thickness) {
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

    /**
     * Calculates the width of a line segment: the maximum rightward extent the cursor reaches,
     * measured from the line start. Pack glyph codepoints dispatch through the SAME
     * {@link PackGlyphDispatcher} as the draw pass and may advance the cursor by negative or
     * fractional amounts; built-in codepoints contribute their AWT widths unchanged, so no-pack
     * measurements are identical to the historical plain sum.
     *
     * @param graphics The {@link Graphics2D} object to measure text on.
     * @param line     The {@link LineSegment} to measure.
     *
     * @return The width of the line segment.
     */
    private int calculateLineWidth(Graphics2D graphics, LineSegment line) {
        this.locationX = 0;
        this.lineMaxExtent = 0;
        this.lineMinExtent = 0;
        this.lineArtTop = 0;
        this.lineArtBottom = 0;
        for (ColorSegment segment : line.getSegments()) {
            Font baseFont = MinecraftFonts.getFont(segment.getFont(), segment.isBold(), segment.isItalic());
            Font font = scaleFactor > 1 ? baseFont.deriveFont(baseFont.getSize2D() * scaleFactor) : baseFont;
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics(font);
            String segmentText = segment.getText();

            for (int i = 0; i < segmentText.length(); ) {
                int codePoint = segmentText.codePointAt(i);

                // Skip variation selectors (U+FE0E and U+FE0F) - they're zero-width control characters
                if (codePoint == 0xFE0E || codePoint == 0xFE0F) {
                    i += Character.charCount(codePoint);
                    continue;
                }

                PackGlyphDispatcher.Dispatched packGlyph = this.packGlyphs.dispatch(segment, codePoint).orElse(null);
                if (packGlyph != null) {
                    // Obfuscation substitutes have equal ceil(advance), so measuring the
                    // original glyph is exact for them too.
                    foldPackGlyphArtExtents(packGlyph);
                    advanceCursor(packGlyph.advanceGuiPx() * (double) pixelSize);
                    i += Character.charCount(codePoint);
                    continue;
                }

                foldBuiltInArtExtents();
                String charStr = new String(Character.toChars(codePoint));

                if (font.canDisplayUpTo(charStr) == -1) {
                    advanceCursor(metrics.stringWidth(charStr));
                } else {
                    Font fallbackFont = MinecraftFonts.getFallbackFont(codePoint, font.getSize2D());
                    if (fallbackFont != null) {
                        graphics.setFont(fallbackFont);
                        FontMetrics fallbackMetrics = graphics.getFontMetrics(fallbackFont);
                        advanceCursor(fallbackMetrics.stringWidth(charStr));
                        graphics.setFont(font);
                    } else {
                        advanceCursor(metrics.stringWidth(charStr));
                    }
                }
                i += Character.charCount(codePoint);
            }
        }

        return (int) Math.ceil(this.lineMaxExtent);
    }

    /**
     * Moves the text cursor by {@code advanceCanvasPx} (negative moves LEFT) and folds the
     * traversed span into {@link #lineMaxExtent} and {@link #lineMinExtent}. The single
     * advance/extent arithmetic shared by the measure and draw passes.
     */
    private void advanceCursor(double advanceCanvasPx) {
        double next = this.locationX + advanceCanvasPx;
        this.lineMaxExtent = Math.max(this.lineMaxExtent, Math.max(this.locationX, next));
        this.lineMinExtent = Math.min(this.lineMinExtent, Math.min(this.locationX, next));
        this.locationX = next;
    }

    /**
     * Folds one pack glyph's drawn-cell span into the line extents. Space glyphs and blank
     * cells paint nothing and contribute nothing; the shadow pass extends the bottom by one GUI
     * px when shadows are on (bold copies only extend horizontally). Vertically the cell spans
     * {@link #lineArtTop}/{@link #lineArtBottom}; horizontally an ITALIC glyph also shears each
     * drawn row by {@code 1 - 0.25 * guiPxBelowLineTop} GUI px (see {@code BitmapGlyph.draw}),
     * so the shear at the cell's first and last rows is folded into
     * {@link #lineMinExtent}/{@link #lineMaxExtent} - tall-ascent cells shear right above the
     * line top, deep cells shear left below it, and both must stay inside canvases sized from
     * {@link #measureLineExtents}.
     */
    private void foldPackGlyphArtExtents(PackGlyphDispatcher.Dispatched packGlyph) {
        if (!packGlyph.drawsArt()) {
            return;
        }
        double topGuiPx = 7 - packGlyph.ascentGuiPx();
        double top = topGuiPx * (double) pixelSize;
        double bottom = top + packGlyph.heightGuiPx() * (double) pixelSize + (this.textShadow ? pixelSize : 0);
        this.lineArtTop = Math.min(this.lineArtTop, top);
        this.lineArtBottom = Math.max(this.lineArtBottom, bottom);

        if (packGlyph.isItalic()) {
            double firstRowShear = 1 - 0.25 * topGuiPx;
            double lastRowShear = 1 - 0.25 * (topGuiPx + packGlyph.heightGuiPx() - 1);
            // For bitmap glyphs the advance bounds the visible art width (ink width + 1, bold
            // included), so the cursor position plus advance plus rightmost shear covers every
            // sheared row.
            double artWidth = Math.max(0, packGlyph.advanceGuiPx()) * (double) pixelSize;
            double leftShear = Math.min(firstRowShear, lastRowShear) * pixelSize;
            double rightShear = Math.max(firstRowShear, lastRowShear) * pixelSize;
            this.lineMinExtent = Math.min(this.lineMinExtent, this.locationX + leftShear);
            this.lineMaxExtent = Math.max(this.lineMaxExtent, this.locationX + artWidth + rightShear);
        }

        // A TTF glyph can draw ink OUTSIDE [origin, origin + advance]: a negative side bearing or
        // shiftX pulls ink left of the origin, and a glyph wider than its advance (or a positive
        // shiftX) pushes it past the advance. Fold the actual drawn cell box so tight canvases
        // (measureLineExtents consumers) never clip it. Bitmap and space glyphs report a
        // degenerate [0, 0] box the advance already covers, so this is a no-op for them - keeping
        // no-pack and bitmap-pack output byte-identical. The advance-based italic block above
        // still handles bitmap italic shear.
        double inkLeftGuiPx = packGlyph.inkLeftGuiPx();
        double inkRightGuiPx = packGlyph.inkRightGuiPx();
        if (inkRightGuiPx > inkLeftGuiPx) {
            double lowShear = 0;
            double highShear = 0;
            if (packGlyph.isItalic()) {
                double firstRowShear = 1 - 0.25 * topGuiPx;
                double lastRowShear = 1 - 0.25 * (topGuiPx + packGlyph.heightGuiPx() - 1);
                lowShear = Math.min(firstRowShear, lastRowShear);
                highShear = Math.max(firstRowShear, lastRowShear);
            }
            // The bold copy draws one GUI px further right; the shadow copies stay within it and
            // are folded vertically like bitmap glyphs (horizontal shadow is not folded here, matching
            // the bitmap advance model).
            double boldReach = packGlyph.isBold() ? 1.0 : 0.0;
            this.lineMinExtent = Math.min(this.lineMinExtent,
                this.locationX + (inkLeftGuiPx + lowShear) * pixelSize);
            this.lineMaxExtent = Math.max(this.lineMaxExtent,
                this.locationX + (inkRightGuiPx + boldReach + highShear) * pixelSize);
        }
    }

    /**
     * Folds the built-in text art box into the vertical extents: the standard 9 GUI px line
     * (which also covers underline placement at baseline + 1 GUI px) plus the shadow row when
     * shadows are on. Built-in glyphs never draw above the line top.
     */
    private void foldBuiltInArtExtents() {
        this.lineArtBottom = Math.max(this.lineArtBottom, (this.textShadow ? 10 : 9) * (double) pixelSize);
    }

    /**
     * Measured extents of one line of this tooltip, in canvas px relative to the line's origin:
     * the point built-in text is drawn from, whose y is the LINE TOP (the baseline sits 7 GUI px
     * below it).
     *
     * @param minX      leftmost art extent, {@code <= 0}; negative when leading negative pack
     *                  advances move art left of the origin, or when the italic row shear pushes
     *                  a deep glyph's bottom rows left of it
     * @param maxX      rightmost art extent, {@code >= 0}: the cursor travel plus any italic
     *                  shear past it, used for canvas sizing
     * @param artTop    topmost drawn-art extent relative to the line top, {@code <= 0}; negative
     *                  when a pack glyph's ascent lifts its cell above the line
     * @param artBottom bottommost drawn-art extent relative to the line top, {@code >= 0}
     */
    public record LineExtents(double minX, double maxX, double artTop, double artBottom) {
    }

    /**
     * Measures one line's full art extents for external compositors that draw tooltip lines
     * onto their own canvases via {@link #drawLineOnto}. Unlike {@link #render()}'s canvas
     * sizing - which stays line-height-based and clips tall glyph art - these extents cover the
     * complete drawn cells of every pack glyph, so a caller can expand its canvas before
     * drawing.
     *
     * @param lineIndex index into this tooltip's lines
     *
     * @return the measured extents
     * @throws IndexOutOfBoundsException when the line index is out of range
     */
    public LineExtents measureLineExtents(int lineIndex) {
        LineSegment line = this.getLines().get(lineIndex);
        BufferedImage dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = dummyImage.createGraphics();
        try {
            measureGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            calculateLineWidth(measureGraphics, line);
            return new LineExtents(this.lineMinExtent, this.lineMaxExtent, this.lineArtTop, this.lineArtBottom);
        } finally {
            measureGraphics.dispose();
        }
    }

    /**
     * Draws one line's segments onto an EXTERNAL canvas through the exact segment machinery
     * {@link #render()} uses (same {@link PackGlyphDispatcher} dispatch, same built-in font
     * path, same decoration geometry), without any background, border, clipping or padding -
     * the compositor owns the canvas. Art extending past the canvas clips there; size the
     * canvas from {@link #measureLineExtents} first. External composition is single-frame:
     * obfuscated pack glyph runs draw their deterministic frame-0 substitution, while
     * obfuscated built-in runs substitute randomly exactly like a single tooltip frame does -
     * compositors that need deterministic output should not pass obfuscated segments.
     *
     * <p>Sets the text anti-aliasing hint OFF on {@code graphics}, matching every tooltip
     * surface.
     *
     * @param graphics         target canvas graphics
     * @param lineIndex        index into this tooltip's lines
     * @param lineOriginX      canvas x of the line origin (text start; art may draw left of it
     *                         through negative advances)
     * @param lineTopY         canvas y of the line TOP (the baseline lands 7 GUI px lower).
     *                         Must be a whole multiple of the canvas pixel size
     *                         ({@code 2 * scaleFactor}) so the baseline stays GUI-aligned
     * @throws IndexOutOfBoundsException when the line index is out of range
     * @throws IllegalArgumentException  when {@code lineTopY} is not a multiple of the canvas
     *                                   pixel size
     */
    public void drawLineOnto(Graphics2D graphics, int lineIndex, int lineOriginX, int lineTopY) {
        LineSegment line = this.getLines().get(lineIndex);
        if (lineTopY % pixelSize != 0) {
            throw new IllegalArgumentException(
                "lineTopY must be a multiple of the canvas pixel size " + pixelSize + ", got: " + lineTopY);
        }
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        this.locationX = lineOriginX;
        this.locationY = lineTopY + 7 * pixelSize;
        this.packObfuscationCounter = 0;
        for (ColorSegment segment : line.getSegments()) {
            this.drawString(graphics, segment, 0);
        }
    }

    /**
     * Calculates the largest width of all lines in a tooltip and sets the {@link #largestWidth} field.
     *
     * @param measureGraphics The {@link Graphics2D} object to measure text on.
     */
    private void measureLines(Graphics2D measureGraphics) {
        this.lineMetrics = new HashMap<>();
        this.lineMinExtents = new HashMap<>();
        this.locationY = insetTop + pixelSize * 2 + yIncrement / 2;

        for (int lineIndex = 0; lineIndex < this.getLines().size(); lineIndex++) {
            LineSegment line = this.getLines().get(lineIndex);
            int lineWidth = calculateLineWidth(measureGraphics, line);
            this.lineMetrics.put(lineIndex, lineWidth);
            this.lineMinExtents.put(lineIndex, this.lineMinExtent);

            int extraPadding = (lineIndex == 0 && this.hasFirstLinePadding()) ? pixelSize * 2 : 0;
            this.locationY += yIncrement + extraPadding;
        }

        this.largestWidth = this.lineMetrics.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        this.leftShift = calculateLeftShift();
    }

    /**
     * How far every line's draw start must move RIGHT so the leftmost art of any line (leading
     * negative advances push the cursor left of the line start) still lands at canvas x >= 0.
     * Evaluated against each line's planned start - centering included - so only art that would
     * actually cross the canvas edge shifts the content.
     */
    private int calculateLeftShift() {
        int shift = 0;
        for (Map.Entry<Integer, Double> entry : this.lineMinExtents.entrySet()) {
            int lineWidth = this.lineMetrics.getOrDefault(entry.getKey(), 0);
            int lineStart = insetLeft + (this.centeredText ? (this.largestWidth - lineWidth) / 2 : 0);
            shift = Math.max(shift, (int) Math.ceil(-(lineStart + entry.getValue())));
        }
        return shift;
    }

    /**
     * Draws lines of text on the image.
     *
     * @param frameGraphics The {@link Graphics2D} object to draw on.
     * @param frameIndex    The animation frame being drawn; seeds deterministic pack glyph
     *                      obfuscation so identical renders produce identical frames.
     */
    private void drawLinesInternal(Graphics2D frameGraphics, int frameIndex) {
        this.locationY = insetTop + pixelSize * 2 + yIncrement / 2;
        this.isAnimated = false;
        this.packObfuscationCounter = 0;

        for (int lineIndex = 0; lineIndex < this.getLines().size(); lineIndex++) {
            LineSegment line = this.getLines().get(lineIndex);
            int lineWidth = this.lineMetrics.getOrDefault(lineIndex, 0);

            // Adjust X position based on if text is centered; leftShift keeps left-overdrawing
            // glyph art inside the canvas (zero for normal text)
            if (this.centeredText) {
                this.locationX = this.leftShift + insetLeft + (this.largestWidth - lineWidth) / 2;
            } else {
                this.locationX = this.leftShift + insetLeft;
            }

            // Draw segments for the line
            for (ColorSegment segment : line.getSegments()) {
                if (segment.isObfuscated()) {
                    this.isAnimated = true;
                }
                this.drawString(frameGraphics, segment, frameIndex);
            }

            // Increment Y position for the next line
            int extraPadding = (lineIndex == 0 && this.hasFirstLinePadding()) ? pixelSize * 2 : 0;
            this.locationY += yIncrement + extraPadding;
        }
    }

    /**
     * Draws a string with the specified formatting. Every codepoint dispatches through
     * {@link PackGlyphDispatcher} first (pack glyphs WIN over the built-in fonts when the active
     * pack supplies them); undispatched codepoints follow the historical OTF/Unifont path.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param colorSegment The {@link ColorSegment} containing formatted text.
     * @param frameIndex   The animation frame being drawn (see {@link #drawLinesInternal}).
     */
    private void drawString(Graphics2D graphics, @NotNull ColorSegment colorSegment, int frameIndex) {
        Font baseFont = MinecraftFonts.getFont(colorSegment.getFont(), colorSegment.isBold(), colorSegment.isItalic());
        this.currentFont = scaleFactor > 1 ? baseFont.deriveFont(baseFont.getSize2D() * scaleFactor) : baseFont;
        this.currentColor = colorSegment.getColor().orElse(ChatColor.Legacy.GRAY);
        graphics.setFont(this.currentFont);
        FontMetrics metrics = graphics.getFontMetrics(this.currentFont);

        String text = colorSegment.getText();
        log.debug("Drawing text segment '{}' with font: {} (bold: {}, italic: {})",
            text, this.currentFont.getName(), colorSegment.isBold(), colorSegment.isItalic());
        StringBuilder subWord = new StringBuilder();

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);

            // Skip variation selectors (U+FE0E and U+FE0F) - they're zero-width control characters
            if (codePoint == 0xFE0E || codePoint == 0xFE0F) {
                i += Character.charCount(codePoint);
                continue;
            }

            String charStr = new String(Character.toChars(codePoint));
            int charCount = Character.charCount(codePoint);

            PackGlyphDispatcher.Dispatched packGlyph = this.packGlyphs.dispatch(colorSegment, codePoint).orElse(null);
            if (packGlyph != null) {
                // Flush the pending built-in run so draw order matches text order
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                drawPackGlyph(graphics, packGlyph, colorSegment, frameIndex);
                i += charCount;
                continue;
            }

            if (colorSegment.isObfuscated()) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                // Draw obfuscated character
                if (codePoint <= 0xFFFF) {
                    drawObfuscatedChar(graphics, (char) codePoint, colorSegment, metrics);
                } else {
                    drawSymbolAndAdvance(graphics, codePoint, charStr, colorSegment);
                }

                i += charCount;
                continue;
            }

            if (this.currentFont.canDisplayUpTo(charStr) != -1) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                // Draw symbol using unicode fallback font
                drawSymbolAndAdvance(graphics, codePoint, charStr, colorSegment);
                i += charCount;
                continue;
            }

            if (aprilFools && Character.isLetterOrDigit(codePoint) && ThreadLocalRandom.current().nextDouble() < APRIL_FOOLS_SWAP_CHANCE) {
                // Flush any pending subWord before swapping font
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                // Randomly pick Galactic or Illageralt
                MinecraftFont altFont = ThreadLocalRandom.current().nextBoolean() ? MinecraftFont.GALACTIC : MinecraftFont.ILLAGERALT;
                Font swappedFont = MinecraftFonts.getFont(altFont, colorSegment.isBold(), colorSegment.isItalic());
                Font scaledSwapped = scaleFactor > 1 ? swappedFont.deriveFont(swappedFont.getSize2D() * scaleFactor) : swappedFont;

                graphics.setFont(scaledSwapped);
                FontMetrics swappedMetrics = graphics.getFontMetrics(scaledSwapped);
                int width = swappedMetrics.stringWidth(charStr);

                drawTextWithEffects(graphics, charStr, colorSegment, width);
                advanceCursor(width);

                // Restore the original font
                graphics.setFont(this.currentFont);
                i += charCount;
                continue;
            }

            subWord.append(charStr);
            i += charCount;
        }

        // Draw any remaining subWord, if any
        if (!subWord.isEmpty()) {
            drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
        }
    }

    /**
     * Draw a sub-word with text effects.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param subWord      The sub-word to draw.
     * @param colorSegment The {@link ColorSegment} containing the color and style information.
     * @param metrics      The {@link FontMetrics} object to measure the character width.
     */
    private void drawSubWord(Graphics2D graphics, String subWord, ColorSegment colorSegment, FontMetrics metrics) {
        if (subWord.isEmpty()) {
            return;
        }

        int width = metrics.stringWidth(subWord);
        drawTextWithEffects(graphics, subWord, colorSegment, width);
        advanceCursor(width);
    }

    /**
     * Draws one dispatched pack glyph at the cursor and advances it (possibly LEFT, for negative
     * space advances). Obfuscated segments substitute an equal-width glyph deterministically per
     * frame. The glyph draws its shadow pass at +1,+1 GUI px tinted with the pipeline's
     * {@link #shadowColor()} - the same color built-in runs shadow with - before the main pass;
     * strikethrough and underline wrap the glyph exactly like {@link #drawTextWithEffects} does
     * for built-in runs and span EVERY glyph's advance, space glyphs included (vanilla emits
     * decoration quads for spaces too). Space glyphs draw no glyph art - no shadow, no bold
     * copy - but their advance fully counts.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param packGlyph    The dispatched glyph for the current codepoint.
     * @param colorSegment The {@link ColorSegment} containing the color and style information.
     * @param frameIndex   The animation frame being drawn, part of the obfuscation seed.
     */
    private void drawPackGlyph(Graphics2D graphics, PackGlyphDispatcher.Dispatched packGlyph,
                               ColorSegment colorSegment, int frameIndex) {
        if (colorSegment.isObfuscated()) {
            // Every (frame, position) pair gets a unique seed; the dispatcher scrambles it.
            long seed = ((long) frameIndex << 32) ^ this.packObfuscationCounter++;
            packGlyph = packGlyph.obfuscated(seed);
        }

        double advanceCanvasPx = packGlyph.advanceGuiPx() * (double) pixelSize;
        int drawX = (int) Math.round(this.locationX);
        int width = (int) Math.round(advanceCanvasPx);

        drawDecorations(graphics, colorSegment, width, drawX, true);

        if (!packGlyph.isSpace()) {
            // The AWT baseline (locationY) is the vanilla baseline, which sits 7 GUI px below
            // the line top; locationY is always a whole multiple of pixelSize.
            double lineTopGuiPx = this.locationY / (double) pixelSize - 7.0;
            packGlyph.draw(graphics, this.locationX / (double) pixelSize, lineTopGuiPx, pixelSize,
                foregroundColor(), this.textShadow && colorSegment.isShadowEnabled() ? shadowColor() : null);
        }

        drawDecorations(graphics, colorSegment, width, drawX, false);

        advanceCursor(advanceCanvasPx);
    }

    /**
     * Draws a symbol using a fallback font when the Minecraft font cannot render it.
     *
     * @param graphics  The {@link Graphics2D} object to draw on.
     * @param codePoint The Unicode code point of the character.
     * @param charStr   The character as a string (handles surrogate pairs).
     * @param segment   The color segment containing style information.
     */
    private void drawSymbolAndAdvance(Graphics2D graphics, int codePoint, String charStr, ColorSegment segment) {
        log.warn("Character '{}' (U+{}) cannot be displayed by font '{}'",
            charStr, String.format("%04X", codePoint), this.currentFont.getName());

        Font fallbackFont = MinecraftFonts.getFallbackFont(codePoint, this.currentFont.getSize2D());
        Font fontToUse = fallbackFont != null ? fallbackFont : this.currentFont;

        if (fallbackFont != null) {
            log.debug("Switching font: '{}' -> '{}'", this.currentFont.getName(), fallbackFont.getName());
        }

        graphics.setFont(fontToUse);
        FontMetrics symbolMetrics = graphics.getFontMetrics(fontToUse);
        int width = symbolMetrics.stringWidth(charStr);

        drawTextWithEffects(graphics, charStr, segment, width);

        advanceCursor(width);
        graphics.setFont(this.currentFont);

        if (fallbackFont != null) {
            log.debug("Switching font: '{}' -> '{}'", fallbackFont.getName(), this.currentFont.getName());
        }
    }

    /**
     * Draw an obfuscated character with a random character of the same width.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param originalChar The original character to obfuscate.
     * @param colorSegment The {@link ColorSegment} containing the color and style information.
     * @param metrics      The {@link FontMetrics} object to measure the character width.
     */
    private void drawObfuscatedChar(Graphics2D graphics, char originalChar, ColorSegment colorSegment, FontMetrics metrics) {
        int originalWidth = metrics.charWidth(originalChar);
        String charToDrawStr = String.valueOf(originalChar); // Default fallback

        int fontStyleIndex = (colorSegment.isBold() ? 1 : 0) + (colorSegment.isItalic() ? 2 : 0);
        Map<Integer, List<Character>> widthMap = OBFUSCATION_WIDTH_MAPS.get(fontStyleIndex);
        List<Character> matchingWidthChars = (widthMap != null) ? widthMap.get(originalWidth) : null;

        if (matchingWidthChars != null && !matchingWidthChars.isEmpty()) {
            char randomChar = matchingWidthChars.get(ThreadLocalRandom.current().nextInt(matchingWidthChars.size()));
            charToDrawStr = String.valueOf(randomChar);
            log.trace("Obfuscating character '{}' (U+{}) with '{}' (U+{}) using font: {}",
                originalChar, String.format("%04X", (int) originalChar),
                randomChar, String.format("%04X", (int) randomChar),
                this.currentFont.getName());
        } else {
            log.warn("No matching character found with width {} for original character '{}' (U+{}), using original",
                originalWidth, originalChar, String.format("%04X", (int) originalChar));
        }

        // Recalculate width for the potentially different character
        int drawnWidth = metrics.stringWidth(charToDrawStr);
        drawTextWithEffects(graphics, charToDrawStr, colorSegment, drawnWidth);
        advanceCursor(drawnWidth);
    }

    /**
     * Draws the text with strikethrough, underline and drop shadow effects
     *
     * @param frameGraphics The {@link Graphics2D} object to draw on.
     * @param textToDraw    The text to draw.
     * @param colorSegment  The {@link ColorSegment} containing the color and style information.
     * @param width         The width of the text to draw.
     */
    private void drawTextWithEffects(Graphics2D frameGraphics, String textToDraw, ColorSegment colorSegment, int width) {
        // Built-in runs draw at whole canvas pixels; the cursor is integer-exact here unless a
        // fractional pack advance preceded this run, in which case it rounds to the nearest.
        int drawX = (int) Math.round(this.locationX);

        drawDecorations(frameGraphics, colorSegment, width, drawX, true);

        // Draw Drop Shadow Text
        if (this.textShadow && colorSegment.isShadowEnabled()) {
            frameGraphics.setColor(shadowColor());
            frameGraphics.drawString(textToDraw, drawX + pixelSize, this.locationY + pixelSize);
        }

        // Draw Text
        frameGraphics.setColor(foregroundColor());
        frameGraphics.drawString(textToDraw, drawX, this.locationY);

        drawDecorations(frameGraphics, colorSegment, width, drawX, false);
    }

    /**
     * One pass of the strikethrough/underline decorations spanning {@code width} canvas px from
     * {@code drawX}: the drop-shadow pass draws before the text or glyph, the foreground pass
     * after it. The single decoration geometry shared by the built-in run path and the pack
     * glyph path, so the two can never drift apart.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param colorSegment The {@link ColorSegment} containing the style information.
     * @param width        The advance the decorations span, in canvas px.
     * @param drawX        The left edge of the decorated run, in canvas px.
     * @param dropShadow   Whether this is the shadow pass or the foreground pass.
     */
    private void drawDecorations(Graphics2D graphics, ColorSegment colorSegment, int width, int drawX, boolean dropShadow) {
        // A shadow-disabled segment (shadow_color alpha 0) skips the decoration shadow pass too,
        // so strikethrough/underline match the shadowless glyph and text of the same run.
        if (dropShadow && !colorSegment.isShadowEnabled()) {
            return;
        }
        if (colorSegment.isStrikethrough()) {
            this.drawThickLineInternal(graphics, width, drawX, this.locationY, -1, STRIKETHROUGH_OFFSET * scaleFactor, dropShadow);
        }
        if (colorSegment.isUnderlined()) {
            this.drawThickLineInternal(graphics, width, drawX - pixelSize, this.locationY, 1, UNDERLINE_OFFSET * scaleFactor, dropShadow);
        }
    }

    /**
     * Draws a thick line on the image with optional drop shadow.
     */
    private void drawThickLineInternal(Graphics2D frameGraphics, int width, int xPosition, int yPosition, int xOffset, int yOffset, boolean dropShadow) {
        if (dropShadow && !this.textShadow) {
            return;
        }
        int xPosition1 = xPosition;
        int xPosition2 = xPosition + width + xOffset;
        yPosition += yOffset;

        if (dropShadow) {
            xPosition1 += pixelSize;
            xPosition2 += pixelSize;
            yPosition += pixelSize;
        }

        frameGraphics.setColor(dropShadow ? shadowColor() : foregroundColor());
        frameGraphics.drawLine(xPosition1, yPosition, xPosition2, yPosition);
        frameGraphics.drawLine(xPosition1, yPosition + 1, xPosition2, yPosition + 1);
    }

    /** Single source for drawn text color, with the shader-equivalent remap applied when present. */
    private Color foregroundColor() {
        Color color = this.currentColor.color();
        return this.textColorRemap != null ? this.textColorRemap.foreground(color) : color;
    }

    /** Single source for drawn shadow color; remapped shadows derive from the remapped foreground. */
    private Color shadowColor() {
        Color shadow = this.currentColor.backgroundColor();
        return this.textColorRemap != null ? this.textColorRemap.shadow(this.currentColor.color(), shadow) : shadow;
    }

    /**
     * Draws all tooltip frames.
     */
    public MinecraftTooltip render() {
        this.animationFrames.clear();
        this.frameDelaysMs = null;

        // Determine the largest width using the measureLines method
        BufferedImage dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = dummyImage.createGraphics();
        measureGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        measureLines(measureGraphics);
        int measuredHeight = this.locationY;
        measureGraphics.dispose();

        // Calculate final dimensions based on the measured largestWidth and height; leftShift
        // widens the canvas for glyph art that would otherwise clip at the left edge
        int finalWidth = this.leftShift + insetLeft + this.largestWidth + insetRight;
        int finalHeight = measuredHeight - (yIncrement + (this.lines.isEmpty() || !this.firstLinePadding ? 0 : pixelSize * 2)) + insetBottom + pixelSize * 2;

        if (isThemed()) {
            // Sprite texels map 1:pixelSize, so the themed canvas must be a whole number of GUI px.
            finalWidth = ceilToMultiple(finalWidth, pixelSize);
            finalHeight = ceilToMultiple(finalHeight, pixelSize);
        }

        // Determine if we need to animate the image beforehand
        this.isAnimated = this.lines.stream()
            .flatMap(line -> line.getSegments().stream())
            .anyMatch(ColorSegment::isObfuscated);

        int frameWidth = Math.max(1, finalWidth);
        int frameHeight = Math.max(1, finalHeight);

        if (isThemed() && this.animatedThemeSprites != null) {
            return renderAnimatedChrome(frameWidth, frameHeight, this.isAnimated);
        }

        int framesToGenerate = this.isAnimated ? this.animationFrameCount : 1;
        BufferedImage themeLayer = isThemed() ? buildThemeLayer(frameWidth, frameHeight, this.themeSprites) : null;

        for (int i = 0; i < framesToGenerate; i++) {
            BufferedImage frameImage = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frameImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

            if (themeLayer != null) {
                // Background and frame sprites carry their own alpha; the alpha knob is not applied.
                graphics.drawImage(themeLayer, 0, 0, null);
            } else {
                // Draw background first
                graphics.setColor(new Color(18, 3, 18, this.isAnimated ? 255 : this.getAlpha()));
                graphics.fillRect(
                    pixelSize * 2, // Inner edge of border
                    pixelSize * 2,
                    frameWidth - pixelSize * 4, // Width inside borders
                    frameHeight - pixelSize * 4 // Height inside borders
                );
            }

            drawLinesInternal(graphics, i);

            // Draw borders onto the frame
            if (this.renderBorder && themeLayer == null) {
                this.drawBorders(graphics, frameWidth, frameHeight);
            }

            // Add padding after rendering tooltip content and borders
            BufferedImage processedFrame = this.addPadding(frameImage);
            graphics.dispose();

            this.animationFrames.add(processedFrame);
        }

        // Set the static image to the first frame if there are any
        if (!this.animationFrames.isEmpty()) {
            this.image = this.animationFrames.get(0);
        }

        return this;
    }

    /**
     * The animated-chrome render: the theme sprites' animations - and, when obfuscated segments
     * are present, the obfuscation ticker - share one {@link AnimationTimeline}, and every
     * timeline step composes a frame from its theme layer (cached per distinct sprite-frame
     * pair) plus the text pass. Obfuscation keeps its per-frame determinism: the frame index it
     * seeds with is its own ticker position, exactly as the obfuscation-only path counts
     * frames. The obfuscation ticker approximates {@link #frameDelayMs} to whole ticks
     * (minimum one; the 50 ms default maps exactly).
     *
     * @param obfuscated whether obfuscated segments are present (precomputed by {@link #render})
     */
    private MinecraftTooltip renderAnimatedChrome(int frameWidth, int frameHeight, boolean obfuscated) {
        PackAnimation background = this.animatedThemeSprites.background();
        PackAnimation frameAnimation = this.animatedThemeSprites.frame();
        List<List<Integer>> sources = new ArrayList<>(3);
        int backgroundSource = -1;
        int frameSource = -1;
        int obfuscationSource = -1;
        if (background != null) {
            backgroundSource = sources.size();
            sources.add(background.frameTicks());
        }
        if (frameAnimation != null) {
            frameSource = sources.size();
            sources.add(frameAnimation.frameTicks());
        }
        if (obfuscated) {
            int obfuscationTicks = Math.max(1,
                Math.round(this.configuredFrameDelayMs / (float) AnimationTimeline.MILLIS_PER_TICK));
            obfuscationSource = sources.size();
            sources.add(Collections.nCopies(this.animationFrameCount, obfuscationTicks));
        }
        AnimationTimeline timeline = AnimationTimeline.of(sources);

        Map<Long, BufferedImage> themeLayers = new HashMap<>();
        List<Integer> delays = new ArrayList<>(timeline.steps().size());
        for (AnimationTimeline.Step step : timeline.steps()) {
            int backgroundPosition = backgroundSource >= 0 ? step.framePositions().get(backgroundSource) : 0;
            int framePosition = frameSource >= 0 ? step.framePositions().get(frameSource) : 0;
            BufferedImage themeLayer = themeLayers.computeIfAbsent(
                ((long) backgroundPosition << 32) | framePosition,
                key -> buildThemeLayer(frameWidth, frameHeight,
                    this.animatedThemeSprites.spritesAt(backgroundPosition, framePosition)));

            BufferedImage frameImage = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frameImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.drawImage(themeLayer, 0, 0, null);
            drawLinesInternal(graphics, obfuscationSource >= 0 ? step.framePositions().get(obfuscationSource) : 0);
            BufferedImage processedFrame = this.addPadding(frameImage);
            graphics.dispose();

            this.animationFrames.add(processedFrame);
            delays.add(AnimationTimeline.ticksToMillis(step.durationTicks()));
        }

        this.isAnimated = true;
        this.frameDelaysMs = List.copyOf(delays);
        this.frameDelayMs = delays.getFirst();
        this.image = this.animationFrames.getFirst();
        return this;
    }

    /**
     * Composes the sprite chrome for one canvas: background rendered first, frame blitted over
     * the identical rect (vanilla layering; any apparent inset is transparent pixels in the art),
     * both at GUI-px resolution and then upscaled so one texel covers pixelSize canvas px.
     */
    private BufferedImage buildThemeLayer(int width, int height, TooltipSprites sprites) {
        int guiWidth = width / pixelSize;
        int guiHeight = height / pixelSize;
        BufferedImage layer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = layer.createGraphics();
        try {
            graphics.drawImage(upscale(GuiSpriteRenderer.render(
                sprites.background().texture(), sprites.background().scaling(), guiWidth, guiHeight), pixelSize), 0, 0, null);
            graphics.drawImage(upscale(GuiSpriteRenderer.render(
                sprites.frame().texture(), sprites.frame().scaling(), guiWidth, guiHeight), pixelSize), 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return layer;
    }

    private static BufferedImage upscale(BufferedImage source, int factor) {
        if (factor <= 1) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(source.getWidth() * factor, source.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
                scaled.setRGB(x, y, source.getRGB(x / factor, y / factor));
            }
        }
        return scaled;
    }

    private static int ceilToMultiple(int value, int unit) {
        return (value + unit - 1) / unit * unit;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<MinecraftTooltip> {
        @Getter
        private final List<LineSegment> lines = new ArrayList<>();
        private ChatColor defaultColor = ChatColor.Legacy.GRAY;
        private int alpha = DEFAULT_ALPHA;
        private int padding = 0;
        private boolean firstLinePadding = true;
        private boolean renderBorder = true;
        private boolean centeredText = false;
        private int frameDelayMs = 50;
        private int animationFrameCount = 10;
        private int scaleFactor = 1;
        private boolean aprilFools = false;
        private boolean textShadow = true;
        private TooltipSprites themeSprites;
        private AnimatedTooltipSprites animatedThemeSprites;
        private TextColorRemap textColorRemap;
        private PackGlyphDispatcher.FontSource packFontSource;

        public Builder hasFirstLinePadding() {
            return this.hasFirstLinePadding(true);
        }

        public Builder hasFirstLinePadding(boolean value) {
            this.firstLinePadding = value;
            return this;
        }

        public Builder setRenderBorder(boolean renderBorder) {
            this.renderBorder = renderBorder;
            return this;
        }

        public Builder isTextCentered(boolean value) {
            this.centeredText = value;
            return this;
        }

        public Builder withAlpha(int value) {
            // If renderBorder, force alpha to 255 so it shows up
            if (this.renderBorder) {
                this.alpha = 255;
                return this;
            }

            this.alpha = Range.between(0, 255).fit(value);
            return this;
        }

        public Builder withDefaultColor(@NotNull ChatColor color) {
            this.defaultColor = color;
            return this;
        }

        public Builder withLines(@NotNull LineSegment... lines) {
            return this.withLines(Arrays.asList(lines));
        }

        public Builder withLines(@NotNull Iterable<LineSegment> lines) {
            lines.forEach(this.lines::add);
            return this;
        }

        public Builder withPadding(int padding) {
            this.padding = Math.max(0, padding);
            return this;
        }

        public Builder withSegments(@NotNull ColorSegment... segments) {
            return this.withSegments(Arrays.asList(segments));
        }

        public Builder withSegments(@NotNull Iterable<ColorSegment> segments) {
            this.lines.add(LineSegment.builder().withSegments(segments).build());
            return this;
        }

        public Builder withFrameDelayMs(int delay) {
            this.frameDelayMs = Math.max(10, delay);
            return this;
        }

        public Builder withAnimationFrameCount(int count) {
            this.animationFrameCount = Math.max(1, count);
            return this;
        }

        public Builder withScaleFactor(int scaleFactor) {
            this.scaleFactor = Math.max(1, scaleFactor);
            return this;
        }

        public Builder withAprilFools(boolean aprilFools) {
            this.aprilFools = aprilFools;
            return this;
        }

        /**
         * Whether text draws its drop shadow (default true, the vanilla tooltip behavior).
         * Compositors rendering shadowless vanilla text - the container screen title - disable
         * this; it turns off the shadow pass of built-in runs, pack glyphs and
         * strikethrough/underline decorations alike.
         */
        public Builder withTextShadow(boolean textShadow) {
            this.textShadow = textShadow;
            return this;
        }

        /**
         * Replaces the programmatic background and borders with pack tooltip sprites. Only
         * applies while the border is enabled; the sprites carry their own alpha, so the alpha
         * knob is ignored in themed renders.
         */
        public Builder withThemeSprites(TooltipSprites themeSprites) {
            this.themeSprites = themeSprites;
            return this;
        }

        /**
         * Animates the sprite chrome: each timeline step composes its own theme layer from the
         * animated sprites' frames, and obfuscated text (when present) ticks on the same shared
         * timeline while keeping its per-frame determinism. Only takes effect together with
         * {@link #withThemeSprites} while the border is enabled; null (the default) keeps the
         * static chrome.
         */
        public Builder withAnimatedThemeSprites(AnimatedTooltipSprites animatedThemeSprites) {
            this.animatedThemeSprites = animatedThemeSprites;
            return this;
        }

        public Builder withTextColorRemap(TextColorRemap textColorRemap) {
            this.textColorRemap = textColorRemap;
            return this;
        }

        /**
         * Enables pack font glyphs: every codepoint of every segment first resolves against the
         * given source (the segment's pack font id, or its built-in font's resource location)
         * and renders as a pack glyph when supplied, falling back to the built-in fonts
         * otherwise. Null (the default) leaves rendering fully on the built-in path.
         */
        public Builder withPackFontSource(PackGlyphDispatcher.FontSource packFontSource) {
            this.packFontSource = packFontSource;
            return this;
        }

        @Override
        public @NotNull MinecraftTooltip build() {
            return new MinecraftTooltip(
                this.lines,
                this.defaultColor,
                this.alpha,
                this.padding,
                this.firstLinePadding,
                this.renderBorder,
                this.centeredText,
                this.frameDelayMs,
                this.animationFrameCount,
                this.scaleFactor,
                this.aprilFools,
                this.textShadow,
                this.themeSprites,
                this.animatedThemeSprites,
                this.textColorRemap,
                this.packFontSource
            );
        }
    }
}