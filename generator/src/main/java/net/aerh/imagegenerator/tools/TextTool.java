package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.PackTheming;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Renders free text as a tooltip with no name line, transparent and borderless by default. */
public final class TextTool {

    private final ResourcePackService packService;

    public TextTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorException if the request is invalid or the render fails
     */
    public GeneratedObject render(TextRequest request, @Nullable GenerationContext context) throws IOException {
        MinecraftTooltipGenerator.Builder tooltipBuilder = new MinecraftTooltipGenerator.Builder()
            .withItemLore(TextWrapper.stripActualNewlines(request.text()))
            .withAlpha(request.alpha())
            .withPadding(request.padding())
            .withMaxLineLength(request.maxLineLength())
            .isTextCentered(request.centered())
            .hasFirstLinePadding(false)
            .withRenderBorder(request.renderBorder());
        PackTheming.requireBorderForTooltipStyle(request.tooltipStyle(), request.renderBorder());
        PackTheming.applyPackTheme(packService, tooltipBuilder, request.packId(), request.tooltipStyle(), null);

        return new GeneratorImageBuilder().withContext(context)
            .addGenerator(tooltipBuilder.build())
            .build();
    }
}
