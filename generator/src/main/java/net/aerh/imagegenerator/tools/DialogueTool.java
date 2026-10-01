package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.DialogueText;
import net.aerh.imagegenerator.tools.support.Heads;
import net.aerh.imagegenerator.tools.support.PackTheming;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Renders NPC dialogue as a transparent tooltip with an optional head in front of it. */
public final class DialogueTool {

    private final ResourcePackService packService;

    public DialogueTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorException if the dialogue is malformed or the render fails
     */
    public GeneratedObject render(DialogueRequest request, @Nullable GenerationContext context) throws IOException {
        String lore = request.mode() == DialogueRequest.Mode.SINGLE
            ? DialogueText.buildSingle(request.npcNames(), request.dialogue(), request.abiphone())
            : DialogueText.buildMulti(request.npcNames(), request.dialogue(), request.abiphone());

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .withItemLore(lore)
            .withAlpha(0)
            .withPadding(MinecraftTooltip.DEFAULT_PADDING)
            .hasFirstLinePadding(false)
            .withMaxLineLength(request.maxLineLength())
            .withRenderBorder(request.renderBackground())
            .bypassMaxLineLength(true);
        PackTheming.applyPackTheme(packService, tooltipGenerator, request.packId(), null, null);

        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context)
            .addGenerator(tooltipGenerator.build());

        if (request.skinValue() != null) {
            generatorImageBuilder.addGenerator(0, Heads.besideTooltip(request.skinValue()));
        }

        return generatorImageBuilder.build();
    }
}
