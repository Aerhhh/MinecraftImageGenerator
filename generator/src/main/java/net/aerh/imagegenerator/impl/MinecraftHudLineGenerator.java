package net.aerh.imagegenerator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.MinecraftContainerGenerator.TitleRun;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.text.PackGlyphDispatcher;
import net.aerh.imagegenerator.text.RgbColor;
import net.aerh.imagegenerator.text.TextColor;
import net.aerh.imagegenerator.text.segment.ColorSegment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a stack of horizontally centered text lines at the vanilla bossbar HUD anchors - the
 * surface servers repurpose as a HUD by pushing text-only bossbars (the bar sprites are blanked
 * by the pack, so only the names show).
 *
 * <p><b>Vanilla anchor mapping (GUI px):</b> the client draws the first bossbar's bar (182x5,
 * at {@code x = guiWidth / 2 - 91}) with its top edge at {@code y = 12}, the bar's NAME centered
 * on {@code guiWidth / 2} with its top edge 9 GUI px above the bar ({@code y = 3}), and stacks
 * each further bossbar 19 GUI px below the previous one. This generator renders the TEXT-ONLY
 * stack: line {@code k} (0-based) is a bossbar name whose top edge sits at GUI
 * {@code y = 3 + 19 * k} (see {@link #lineTopGuiPx(int)}); no bar sprites are drawn.
 *
 * <p><b>Canvas:</b> a caller-specified GUI-px width (default {@link #DEFAULT_GUI_WIDTH}: the
 * full width of a 1280 px screen at GUI scale 4) at the standard {@code 2 * scaleFactor} canvas
 * px per GUI px. The canvas top edge is the SCREEN top edge (GUI y = 0). Its height covers the
 * deepest drawn art of the stack, rounded up to whole GUI px, with every line reserving at
 * least the standard text box (9 GUI px plus the shadow row when shadows are on) - a plain
 * {@code n}-line stack is {@code 13 + 19 * (n - 1)} GUI px tall. Tall glyph art on lower lines
 * grows the canvas bottom; art crossing the top or side edges clips exactly like the screen
 * edges clip it in game.
 *
 * <p><b>Text:</b> each line is a list of {@link TitleRun styled runs} rendered as ONE line
 * through the exact tooltip segment machinery (pack glyph dispatch included). Runs without an
 * explicit color draw in white WITH a drop shadow, matching vanilla bossbar names; negative
 * pack advances, glyph fonts and styles behave exactly as they do in tooltips and container
 * titles. Centering is extent-aware: a line's width is its maximum rightward art extent (not
 * the final cursor position), so trailing negative advances do not skew the centering. The
 * measured width ceils to whole GUI px like the client's text measurement, and the line origin
 * is vanilla's exact integer math ({@code guiWidth / 2 - textWidth / 2} in GUI px), so every
 * line lands on the same GUI pixel column as an in-game screenshot - including odd-width lines,
 * which finer canvas-px centering would shift half a GUI px left of the client.
 *
 * <p>Rendering is deterministic for non-obfuscated runs; like the container title, the stack is
 * single-frame (obfuscated pack glyph runs draw their deterministic frame-0 substitution).
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftHudLineGenerator implements Generator {

    /** Default canvas width in GUI px: the full width of a 1280 px screen at GUI scale 4. */
    public static final int DEFAULT_GUI_WIDTH = 320;
    /** GUI y of line 0's top edge: the first bossbar's bar top (12) minus the 9 GUI px name offset. */
    public static final int FIRST_LINE_TOP_Y = 3;
    /** Vertical pitch between stacked bossbar anchors in GUI px. */
    public static final int LINE_PITCH = 19;
    /** Vanilla bossbar names draw in plain white (with shadow). */
    public static final TextColor DEFAULT_TEXT_COLOR = new RgbColor(0xFFFFFF);

    /** Lines of the stack, outermost list per line, inner list the line's styled runs. */
    private final List<List<TitleRun>> lines;
    private final int guiWidth;
    private final int scaleFactor;
    private final boolean textShadow;
    // packId is final non-transient so it enters the render cache key; the repository reference
    // is transient so instances never split it.
    @Nullable
    private final PackId packId;
    @ToString.Exclude
    private final transient PackRepository packRepository;

    /**
     * GUI y of line {@code lineIndex}'s top edge: the vanilla name anchor of the
     * {@code lineIndex}-th stacked bossbar, {@code 3 + 19 * lineIndex}.
     *
     * @param lineIndex 0-based line index
     *
     * @throws IllegalArgumentException when the line index is negative
     */
    public static int lineTopGuiPx(int lineIndex) {
        if (lineIndex < 0) {
            throw new IllegalArgumentException("Line index must not be negative, got: " + lineIndex);
        }
        return FIRST_LINE_TOP_Y + LINE_PITCH * lineIndex;
    }

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        log.debug("Rendering HUD line stack ({})", this);

        int pixelSize = 2 * scaleFactor;
        int canvasWidth = guiWidth * pixelSize;
        MinecraftTooltip lineStack = buildLineStack();

        // Every line reserves at least the standard text box so blank spacer lines still hold
        // their slot open at the canvas bottom; deeper glyph art grows the canvas past it.
        int lineBoxGuiPx = textShadow ? 10 : 9;
        int[] lineOriginsX = new int[lines.size()];
        int canvasHeightGuiPx = 0;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            MinecraftTooltip.LineExtents extents = lineStack.measureLineExtents(lineIndex);
            // Extent-aware line width (the maximum rightward art extent, not the cursor end),
            // ceiled to whole GUI px like the client's text measurement. Centering uses
            // vanilla's integer GUI-px math so odd-width lines land on the exact in-game
            // pixel column instead of half a GUI px left of it.
            int lineWidthGuiPx = (int) Math.ceil(extents.maxX() / pixelSize);
            lineOriginsX[lineIndex] = (guiWidth / 2 - lineWidthGuiPx / 2) * pixelSize;
            int artBottomGuiPx = Math.max(lineBoxGuiPx, (int) Math.ceil(extents.artBottom() / pixelSize));
            canvasHeightGuiPx = Math.max(canvasHeightGuiPx, lineTopGuiPx(lineIndex) + artBottomGuiPx);
        }

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeightGuiPx * pixelSize,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                lineStack.drawLineOnto(graphics, lineIndex, lineOriginsX[lineIndex],
                    lineTopGuiPx(lineIndex) * pixelSize);
            }
        } finally {
            graphics.dispose();
        }

        log.debug("Rendered HUD line stack image (dimensions {}x{})", canvas.getWidth(), canvas.getHeight());
        return new GeneratedObject(canvas);
    }

    /**
     * The whole stack as one borderless multi-line tooltip: measurement and drawing both run
     * through the exact tooltip segment machinery (pack glyph dispatch included), so HUD lines
     * behave identically to tooltip text and container titles - zero new glyph dispatch logic.
     */
    private MinecraftTooltip buildLineStack() {
        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .setRenderBorder(false)
            .hasFirstLinePadding(false)
            .withScaleFactor(scaleFactor)
            .withTextShadow(textShadow)
            .withPackFontSource(PackGlyphDispatcher.FontSource.forPack(packId, packRepository));
        for (List<TitleRun> line : lines) {
            List<ColorSegment> segments = new ArrayList<>(line.size());
            for (TitleRun run : line) {
                segments.add(run.toSegment(DEFAULT_TEXT_COLOR));
            }
            builder.withSegments(segments);
        }
        return builder.build();
    }

    /**
     * Builds {@link MinecraftHudLineGenerator} instances. Add lines top-down with
     * {@link #withLine}; each line occupies the next stacked bossbar anchor.
     */
    public static class Builder implements ClassBuilder<MinecraftHudLineGenerator> {

        private final List<List<TitleRun>> lines = new ArrayList<>();
        private int guiWidth = DEFAULT_GUI_WIDTH;
        private int scaleFactor = 1;
        private boolean textShadow = true;
        private PackId packId;
        private PackRepository packRepository;

        /**
         * Appends one line to the stack: the name of the next stacked bossbar, rendered as ONE
         * centered line from the given styled runs. An empty run list is a blank spacer line -
         * it draws nothing but its anchor slot stays occupied, exactly like a bossbar with an
         * empty name.
         *
         * @param runs the line's styled runs; runs without an explicit color draw in the
         *             vanilla bossbar white
         */
        public Builder withLine(@NotNull List<TitleRun> runs) {
            this.lines.add(List.copyOf(runs));
            return this;
        }

        /** Varargs convenience for {@link #withLine(List)}; no arguments appends a blank spacer line. */
        public Builder withLine(@NotNull TitleRun... runs) {
            return withLine(List.of(runs));
        }

        /**
         * Canvas width in GUI px (default {@link #DEFAULT_GUI_WIDTH}); lines center on its
         * middle. Art wider than the canvas clips at the side edges, exactly like the screen
         * edges clip it in game.
         *
         * @throws IllegalArgumentException when the width is not positive
         */
        public Builder withGuiWidth(int guiWidth) {
            if (guiWidth < 1) {
                throw new IllegalArgumentException("guiWidth must be at least 1, got: " + guiWidth);
            }
            this.guiWidth = guiWidth;
            return this;
        }

        /** Scale factor applied to all pixel sizes; one GUI px covers {@code 2 * scaleFactor} canvas px. */
        public Builder withScaleFactor(int scaleFactor) {
            this.scaleFactor = Math.max(1, scaleFactor);
            return this;
        }

        /**
         * Whether the lines draw their drop shadow (default true - vanilla bossbar names draw
         * WITH shadow, unlike the container title).
         */
        public Builder withTextShadow(boolean textShadow) {
            this.textShadow = textShadow;
            return this;
        }

        /**
         * Selects the resource pack the lines' pack font ids resolve against. Null or
         * {@link PackId#VANILLA} renders entirely through the built-in fonts.
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

        /**
         * Builds the generator.
         *
         * @throws IllegalArgumentException when no line was added
         */
        @Override
        public @NotNull MinecraftHudLineGenerator build() {
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("At least one line is required; add lines with withLine");
            }
            return new MinecraftHudLineGenerator(List.copyOf(lines), guiWidth, scaleFactor,
                textShadow, packId, packRepository);
        }
    }
}
