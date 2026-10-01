package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.impl.MinecraftInventoryGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.PackTheming;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Renders a 3x3 crafting grid without a border. */
public final class RecipeTool {

    private final ResourcePackService packService;

    public RecipeTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorException if the recipe string is invalid or the render fails
     */
    public GeneratedObject render(RecipeRequest request, @Nullable GenerationContext context) throws IOException {
        return new GeneratorImageBuilder().withContext(context)
            .addGenerator(PackTheming.applyInventoryPack(packService, new MinecraftInventoryGenerator.Builder(), request.packId())
                .withRows(3)
                .withSlotsPerRow(3)
                .drawBorder(false)
                .drawBackground(request.renderBackground())
                .withInventoryString(request.recipe())
                .build())
            .build();
    }
}
