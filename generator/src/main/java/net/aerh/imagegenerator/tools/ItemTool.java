package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.impl.MinecraftInventoryGenerator;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.Heads;
import net.aerh.imagegenerator.tools.support.ItemAddressing;
import net.aerh.imagegenerator.tools.support.PackTheming;
import net.aerh.imagegenerator.tools.support.SlashCommandText;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Renders a full item image: tooltip, optional item or head beside it, optional 3x3 recipe grid. */
public final class ItemTool {

    private final ResourcePackService packService;

    public ItemTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorException if the request is invalid or the render fails
     */
    public GeneratedObject render(ItemRequest request, @Nullable GenerationContext context) throws IOException {
        ItemAddressing.requireSingleItemAddress(request.itemId(), request.itemModel());

        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);
        MinecraftTooltipGenerator tooltipGenerator = tooltipBuilder(request).build();

        if (request.itemId() != null || request.itemModel() != null) {
            if (request.itemModel() == null && request.itemId().equalsIgnoreCase("player_head")) {
                generatorImageBuilder.addGenerator(Heads.besideTooltip(request.skinValue()));
            } else {
                MinecraftItemGenerator.Builder itemBuilder = ItemAddressing.addressItem(new MinecraftItemGenerator.Builder(), request.itemId(), request.itemModel())
                    .withColor(request.color())
                    .isEnchanted(request.enchanted());
                PackTheming.applyItemPack(packService, itemBuilder, request.packId());
                itemBuilder.withDurability(request.durability());

                generatorImageBuilder.addGenerator(itemBuilder.build());
            }
        }

        if (request.recipe() != null && !request.recipe().isBlank()) {
            generatorImageBuilder.addGenerator(0, PackTheming.applyInventoryPack(packService, new MinecraftInventoryGenerator.Builder(), request.packId())
                .withRows(3)
                .withSlotsPerRow(3)
                .drawBorder(request.renderBorder())
                .withInventoryString(request.recipe())
                .build());
        }

        if (request.tooltipSide() == MinecraftTooltipGenerator.TooltipSide.LEFT) {
            generatorImageBuilder.addGenerator(0, tooltipGenerator);
        } else {
            generatorImageBuilder.addGenerator(tooltipGenerator);
        }

        return generatorImageBuilder.build();
    }

    /** The {@code /gen item} command that reproduces this request. */
    public String slashCommand(ItemRequest request) {
        String command = tooltipBuilder(request).buildSlashCommand();
        return SlashCommandText.appendParsedItemOptions(command, request.itemId(), request.itemModel(), request.skinValue(), request.enchanted());
    }

    private MinecraftTooltipGenerator.Builder tooltipBuilder(ItemRequest request) {
        Rarity itemRarity = Rarity.byName(request.rarity());
        MinecraftTooltipGenerator.Builder tooltipBuilder = new MinecraftTooltipGenerator.Builder()
            .withName(request.itemName())
            .withRarity(itemRarity)
            .withItemLore(TextWrapper.stripActualNewlines(request.itemLore()))
            .withType(request.type())
            .withAlpha(request.alpha())
            .withPadding(request.padding())
            .withMaxLineLength(request.maxLineLength())
            .isTextCentered(request.centered())
            .hasFirstLinePadding(request.firstLinePadding())
            .withRenderBorder(request.renderBorder());
        PackTheming.requireBorderForTooltipStyle(request.tooltipStyle(), request.renderBorder());
        PackTheming.applyPackTheme(packService, tooltipBuilder, request.packId(), request.tooltipStyle(), itemRarity);
        return tooltipBuilder;
    }
}
