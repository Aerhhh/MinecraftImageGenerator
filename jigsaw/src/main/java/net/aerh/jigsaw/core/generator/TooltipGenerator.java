package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.FormattingParser;
import net.aerh.jigsaw.api.text.TextRenderOptions;
import net.aerh.jigsaw.core.text.MinecraftTextRenderer;
import net.aerh.jigsaw.core.text.TextWrapper;
import net.aerh.jigsaw.exception.RenderException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders a Minecraft-style item tooltip from a {@link TooltipRequest}.
 *
 * <p>The caller is responsible for pre-formatting all lines with color codes ({@code &} or
 * {@code /u00a7}). No placeholder expansion or rarity handling is performed here.
 *
 * <p>The rendering pipeline:
 * <ol>
 *   <li>Wrap each input line at {@code maxLineLength} visible characters (unless
 *       {@code bypassMaxLineLength} is set, in which case newlines within lines are still
 *       split but no width-based wrapping is applied).</li>
 *   <li>Parse each line into text segments internally.</li>
 *   <li>Measure all lines to determine the tooltip width.</li>
 *   <li>Render text with shadows, border, and all formatting effects.</li>
 * </ol>
 */
public final class TooltipGenerator implements Generator<TooltipRequest, GeneratorResult> {

    private final MinecraftTextRenderer textRenderer;

    /**
     * Creates a new {@link TooltipGenerator}.
     *
     * @param textRenderer the text renderer to use for tooltip rendering; must not be {@code null}
     */
    public TooltipGenerator(MinecraftTextRenderer textRenderer) {
        this.textRenderer = java.util.Objects.requireNonNull(textRenderer, "textRenderer must not be null");
    }

    /**
     * Renders the tooltip described by the request.
     *
     * @param input   the tooltip request; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     * @return a result containing the rendered tooltip (static or animated if obfuscated text is present)
     * @throws RenderException if rendering fails
     */
    @Override
    public GeneratorResult render(TooltipRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        List<String> wrappedLines = new ArrayList<>();
        for (String line : input.lines()) {
            String resolved = FormattingParser.resolveNamedFormats(line);
            if (input.bypassMaxLineLength()) {
                wrappedLines.addAll(splitOnNewlines(resolved));
            } else {
                wrappedLines.addAll(TextWrapper.wrapString(resolved, input.maxLineLength()));
            }
        }

        int firstLinePaddingPx = input.firstLinePadding() ? 1 : 0;

        TextRenderOptions renderOptions = new TextRenderOptions(
                true,
                input.renderBorder(),
                input.centeredText(),
                input.scaleFactor(),
                input.alpha(),
                input.padding(),
                firstLinePaddingPx,
                input.maxLineLength()
        );

        return textRenderer.renderLines(wrappedLines, renderOptions);
    }

    @Override
    public Class<TooltipRequest> inputType() {
        return TooltipRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Splits a single line string on embedded newline characters ({@code \n}) and on literal
     * {@code \n} markers (backslash + n), returning one entry per resulting line. Used when
     * {@code bypassMaxLineLength} is active and width-based wrapping is intentionally skipped.
     *
     * @param line the line to split; must not be {@code null}
     * @return a list containing at least one element
     */
    private static List<String> splitOnNewlines(String line) {
        String normalized = TextWrapper.normalizeNewlines(line);
        if (normalized == null || normalized.isEmpty()) {
            List<String> result = new ArrayList<>();
            result.add(line);
            return result;
        }
        return List.of(normalized.split("\n", -1));
    }
}
