package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.impl.MinecraftInventoryGenerator;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.text.TextColorRemap;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import org.jetbrains.annotations.Nullable;

/** Applies a resolved pack, and its configured theming, to the generator builders. */
public final class PackTheming {

    private PackTheming() {
    }

    /** Pack selection and repository for an item builder; a null pack renders vanilla. */
    public static void applyItemPack(ResourcePackService packService, MinecraftItemGenerator.Builder builder, @Nullable PackId packId) {
        builder.withPack(packId)
            .withPackRepository(packService.packRepository());
    }

    /** Pack selection and repository for an inventory builder, returned so it can be chained. */
    public static MinecraftInventoryGenerator.Builder applyInventoryPack(ResourcePackService packService, MinecraftInventoryGenerator.Builder builder, @Nullable PackId packId) {
        return builder.withPack(packId)
            .withPackRepository(packService.packRepository());
    }

    /**
     * Rejects an explicit tooltip_style when the border is disabled: the library only draws theme
     * sprites when the border is rendered, so the style would silently do nothing.
     *
     * @throws GeneratorException If a tooltip style is given while the border is disabled
     */
    public static void requireBorderForTooltipStyle(@Nullable String tooltipStyle, boolean renderBorder) {
        if (tooltipStyle != null && !tooltipStyle.isBlank() && !renderBorder) {
            throw new GeneratorException("The tooltip_style option only renders with the border; set render_border: true too!");
        }
    }

    /**
     * Applies pack theming to a tooltip builder. Precedence for the style: the explicit style, then
     * the pack's configured style for the rarity, then nothing (the pack's own default tooltip
     * override, if any, then applies inside the library). The pack's text color remap always
     * applies when one is configured.
     *
     * @throws GeneratorException If an explicit style is given without a pack
     */
    public static void applyPackTheme(ResourcePackService packService, MinecraftTooltipGenerator.Builder builder,
                                      @Nullable PackId packId, @Nullable String explicitStyle, @Nullable Rarity rarity) {
        boolean hasExplicitStyle = explicitStyle != null && !explicitStyle.isBlank();

        if (packId == null) {
            if (hasExplicitStyle) {
                throw new GeneratorException("The tooltip_style option needs a resource pack; set the pack option too!");
            }
            return;
        }

        builder.withPack(packId).withPackRepository(packService.packRepository());

        String style = hasExplicitStyle ? explicitStyle.trim() : packService.tooltipStyleFor(packId, rarity);
        if (style != null) {
            builder.withTooltipStyle(style);
        }

        TextColorRemap textColorRemap = packService.textColorRemapFor(packId);
        if (textColorRemap != null) {
            builder.withTextColorRemap(textColorRemap);
        }
    }
}
