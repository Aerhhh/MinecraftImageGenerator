package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.impl.MinecraftPlayerHeadGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.ItemAddressing;
import net.aerh.imagegenerator.tools.support.PackTheming;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Renders a single item, or a player head when the item is {@code player_head} with a skin value. */
public final class DisplayTool {

    private final ResourcePackService packService;

    public DisplayTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorException if the request names both or neither item address, or the render fails
     */
    public GeneratedObject render(DisplayRequest request, @Nullable GenerationContext context) throws IOException {
        ItemAddressing.requireSingleItemAddress(request.itemId(), request.itemModel());
        ItemAddressing.requireAnItemAddress(request.itemId(), request.itemModel());

        GeneratorImageBuilder item = new GeneratorImageBuilder().withContext(context);

        if (request.itemModel() == null && request.itemId().equalsIgnoreCase("player_head") && request.skinValue() != null) {
            item.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                .withSkin(request.skinValue())
                .build());
        } else {
            MinecraftItemGenerator.Builder itemBuilder = ItemAddressing.addressItem(new MinecraftItemGenerator.Builder(), request.itemId(), request.itemModel())
                .withData(request.data())
                .withColor(request.color())
                .isEnchanted(request.enchanted())
                .withHoverEffect(request.hoverEffect());
            PackTheming.applyItemPack(packService, itemBuilder, request.packId());
            itemBuilder.withDurability(request.durability());

            item.addGenerator(itemBuilder.build());
        }

        return item.build();
    }
}
