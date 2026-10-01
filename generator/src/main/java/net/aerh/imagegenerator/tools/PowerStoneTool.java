package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.Heads;
import net.aerh.imagegenerator.tools.support.PackTheming;
import net.aerh.imagegenerator.tools.support.PowerStoneLore;
import net.aerh.imagegenerator.tools.support.SlashCommandText;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Renders a Power Stone tooltip with computed stats and an optional item or head beside it. */
public final class PowerStoneTool {

    private final ResourcePackService packService;

    public PowerStoneTool(ResourcePackService packService) {
        this.packService = Objects.requireNonNull(packService, "packService");
    }

    /**
     * @throws GeneratorException if a stat is unknown or malformed, or the render fails
     */
    public GeneratedObject render(PowerStoneRequest request, @Nullable GenerationContext context) throws IOException {
        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);
        MinecraftTooltipGenerator.Builder tooltipGenerator = tooltipBuilder(request);

        if (request.itemId() != null) {
            if (request.itemId().equalsIgnoreCase("player_head")) {
                generatorImageBuilder.addGenerator(Heads.besideTooltip(request.skinValue()));
            } else {
                MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
                    .withItem(request.itemId())
                    .withColor(request.color())
                    .isEnchanted(request.enchanted());
                PackTheming.applyItemPack(packService, itemBuilder, request.packId());

                generatorImageBuilder.addGenerator(itemBuilder.build());
            }
        }

        generatorImageBuilder.addGenerator(tooltipGenerator.build());
        return generatorImageBuilder.build();
    }

    /** The {@code /gen item} command that reproduces this stone's tooltip and item. */
    public String slashCommand(PowerStoneRequest request) {
        return SlashCommandText.appendPowerStoneItemOptions(tooltipBuilder(request).buildSlashCommand(), request.itemId(), request.enchanted());
    }

    private MinecraftTooltipGenerator.Builder tooltipBuilder(PowerStoneRequest request) {
        String itemLore = PowerStoneLore.build(request.powerStrength(), request.magicalPower(),
            request.scalingStats(), request.uniqueBonus(), request.selected());

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .withName("&a" + request.powerName())
            .withRarity(Rarity.byName("none"))
            .withItemLore(itemLore)
            .withAlpha(request.alpha())
            .withPadding(request.padding())
            .isTextCentered(false)
            .hasFirstLinePadding(true)
            .withRenderBorder(true);
        PackTheming.applyPackTheme(packService, tooltipGenerator, request.packId(), null, Rarity.byName("none"));
        return tooltipGenerator;
    }
}
