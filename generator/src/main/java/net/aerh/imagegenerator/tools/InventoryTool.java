package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.MinecraftInventoryGenerator;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.PackTheming;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Renders an inventory grid with an optional hovered item tooltip beside it. */
public final class InventoryTool {

    /** The side tooltip is capped at 2x so it does not dwarf a grid drawn at a larger scale. */
    static final int MAX_HOVER_TOOLTIP_SCALE = 2;

    /** The side tooltip's scale: the grid scale, capped at {@value #MAX_HOVER_TOOLTIP_SCALE}. */
    static int hoverTooltipScale(int gridScale) {
        return Math.min(MAX_HOVER_TOOLTIP_SCALE, gridScale);
    }

    private final ResourcePackService packService;

    public InventoryTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorException if the request is invalid or the render fails
     */
    public GeneratedObject render(InventoryRequest request, @Nullable GenerationContext context) throws IOException {
        GeneratorImageBuilder generatedObject = new GeneratorImageBuilder().withContext(context)
            .addGenerator(PackTheming.applyInventoryPack(packService, new MinecraftInventoryGenerator.Builder(), request.packId())
                .withRows(request.rows())
                .withSlotsPerRow(request.slotsPerRow())
                .drawBorder(request.drawBorder())
                .drawBackground(true)
                .withAnimateGlint(request.animateGlint())
                .withContainerTitle(request.containerName())
                .withInventoryString(request.inventoryString())
                .build());

        if (request.hoveredItemString() != null && !request.hoveredItemString().isBlank()) {
            MinecraftTooltipGenerator.Builder tooltipBuilder = new MinecraftTooltipGenerator.Builder()
                .withItemLore(TextWrapper.stripActualNewlines(request.hoveredItemString()))
                .withAlpha(MinecraftTooltip.DEFAULT_ALPHA)
                .withPadding(MinecraftTooltip.DEFAULT_PADDING)
                .hasFirstLinePadding(false)
                .withMaxLineLength(request.maxLineLength())
                .withScaleFactor(hoverTooltipScale(MinecraftInventoryGenerator.getScaleFactor()))
                .withRenderBorder(true);
            PackTheming.applyPackTheme(packService, tooltipBuilder, request.packId(), null, null);

            generatedObject.addGenerator(tooltipBuilder.build());
        }

        return generatedObject.build();
    }
}
