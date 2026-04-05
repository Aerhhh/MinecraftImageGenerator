package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.FormattingParser;
import net.aerh.jigsaw.api.text.TextRenderOptions;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;
import net.aerh.jigsaw.core.text.MinecraftTextRenderer;
import net.aerh.jigsaw.core.text.TextLayout;
import net.aerh.jigsaw.core.text.TextLayoutEngine;
import net.aerh.jigsaw.core.text.TextLayoutOptions;
import net.aerh.jigsaw.exception.RenderException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders a Minecraft-style item tooltip from a {@link TooltipRequest}.
 *
 * <p>The caller is responsible for pre-formatting all lines with color codes ({@code &} or
 * {@code §}). No placeholder expansion or rarity handling is performed here.
 *
 * <p>The rendering pipeline:
 * <ol>
 *   <li>Parse each line into {@link TextSegment}s using {@link FormattingParser}.</li>
 *   <li>Lay out the segments via {@link TextLayoutEngine}.</li>
 *   <li>Render via {@link MinecraftTextRenderer}.</li>
 * </ol>
 */
public final class TooltipGenerator implements Generator<TooltipRequest, GeneratorResult> {

    /**
     * Creates a new {@link TooltipGenerator}.
     */
    public TooltipGenerator() {
    }

    /**
     * Renders the tooltip described by the request.
     *
     * @param input   the tooltip request; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     * @return a static image containing the rendered tooltip
     * @throws RenderException if rendering fails
     */
    @Override
    public GeneratorResult render(TooltipRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        List<TextSegment> allSegments = parseLinesToSegments(input.lines());

        TextLayoutOptions layoutOptions = new TextLayoutOptions(
                input.maxLineLength(),
                input.centeredText(),
                input.scaleFactor()
        );

        TextLayout layout = TextLayoutEngine.layout(allSegments, layoutOptions);

        int firstLinePaddingPx = input.firstLinePadding() ? 13 : 0;

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

        return MinecraftTextRenderer.renderLayout(layout, renderOptions);
    }

    @Override
    public Class<TooltipRequest> inputType() {
        return TooltipRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    /**
     * Converts a list of raw formatted lines into a flat list of {@link TextSegment}s,
     * inserting newline segments between lines.
     */
    private static List<TextSegment> parseLinesToSegments(List<String> rawLines) {
        List<TextSegment> allSegments = new ArrayList<>();

        for (int i = 0; i < rawLines.size(); i++) {
            if (i > 0) {
                allSegments.add(new TextSegment("\n", TextStyle.DEFAULT));
            }
            List<TextSegment> lineSegments = FormattingParser.parse(rawLines.get(i));
            allSegments.addAll(lineSegments);
        }

        return allSegments;
    }
}
