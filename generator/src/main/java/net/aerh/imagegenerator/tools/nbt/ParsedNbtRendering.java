package net.aerh.imagegenerator.tools.nbt;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.impl.MinecraftNbtParser;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.Heads;
import net.aerh.imagegenerator.tools.support.ItemAddressing;
import net.aerh.imagegenerator.tools.support.PackTheming;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Renders parsed NBT, degrading gracefully when an addressed item model cannot be produced by
 * the pack. Whether a model can render is only knowable by rendering it, so the model is
 * attempted as addressed and only a failure triggers the stand-in: the head texture where the
 * item has one, otherwise the base item the model sits on. A failure with no item model involved
 * is rethrown untouched.
 */
@Slf4j
public final class ParsedNbtRendering {

    private ParsedNbtRendering() {
    }

    /**
     * @param packService The pack service backing the render
     * @param nbtInput    The raw NBT, re-parsed for the retry so builders start clean
     * @param packId      The resolved pack, or null for vanilla
     * @param context     The generation context, may be null
     *
     * @throws GeneratorException If the render fails for any reason other than an item model the
     *                            pack cannot produce
     */
    public static ParsedRender renderWithFallback(ResourcePackService packService, String nbtInput,
                                                  @Nullable PackId packId, @Nullable GenerationContext context) throws IOException {
        MinecraftNbtParser.ParsedNbt parsedNbt = MinecraftNbtParser.parse(nbtInput);

        try {
            return new ParsedRender(render(packService, parsedNbt, packId, context, ItemModelFallback.NONE),
                parsedNbt, ItemModelFallback.NONE);
        } catch (GeneratorException exception) {
            ItemModelFallback fallback = fallbackFor(parsedNbt.getParsedItemModel(), parsedNbt.getBase64Texture());
            if (fallback == ItemModelFallback.NONE) {
                throw exception;
            }

            log.info("Could not render item model '{}' from pack '{}', falling back to {}",
                parsedNbt.getParsedItemModel(), packId, fallback, exception);
            MinecraftNbtParser.ParsedNbt retry = MinecraftNbtParser.parse(nbtInput);
            return new ParsedRender(render(packService, retry, packId, context, fallback), retry, fallback);
        }
    }

    private static GeneratedObject render(ResourcePackService packService, MinecraftNbtParser.ParsedNbt parsedNbt,
                                          @Nullable PackId packId, @Nullable GenerationContext context,
                                          ItemModelFallback fallback) throws IOException {
        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);

        for (ClassBuilder<? extends Generator> generator : parsedNbt.getGenerators()) {
            if (generator instanceof MinecraftTooltipGenerator.Builder tooltipBuilder) {
                PackTheming.applyPackTheme(packService, tooltipBuilder, packId, null, tooltipBuilder.getRarity());
            } else if (generator instanceof MinecraftItemGenerator.Builder itemBuilder) {
                if (fallback == ItemModelFallback.PLAYER_HEAD) {
                    generatorImageBuilder.addGenerator(Heads.besideTooltip(parsedNbt.getBase64Texture()));
                    continue;
                }

                if (fallback == ItemModelFallback.ITEM_ID) {
                    MinecraftItemGenerator.Builder baseItem = new MinecraftItemGenerator.Builder()
                        .withItem(parsedNbt.getParsedItemId())
                        .isEnchanted(parsedNbt.isEnchanted());
                    PackTheming.applyItemPack(packService, baseItem, packId);
                    generatorImageBuilder.addGenerator(baseItem.build());
                    continue;
                }

                PackTheming.applyItemPack(packService, itemBuilder, packId);
            }

            generatorImageBuilder.addGenerator(generator.build());
        }

        return generatorImageBuilder.build();
    }

    /**
     * Picks what to render after a render failed with an item model addressed: the head where a
     * texture exists, otherwise the base item. {@link ItemModelFallback#NONE} when no model was
     * addressed, because that failure is about the user's input, not the pack.
     */
    public static ItemModelFallback fallbackFor(@Nullable String itemModel, @Nullable String base64Texture) {
        if (itemModel == null || itemModel.isBlank()) {
            return ItemModelFallback.NONE;
        }

        return base64Texture != null && !base64Texture.isBlank()
            ? ItemModelFallback.PLAYER_HEAD
            : ItemModelFallback.ITEM_ID;
    }

    /**
     * The line telling the user their preview is not what the NBT asked for.
     *
     * @return The notice, or null when nothing was substituted
     */
    @Nullable
    public static String fallbackNotice(ItemModelFallback fallback, @Nullable String packId, String itemModel, String itemId) {
        if (fallback == ItemModelFallback.NONE) {
            return null;
        }

        String source = packId == null ? "Vanilla" : "The `" + packId + "` pack";
        String rendered = fallback == ItemModelFallback.PLAYER_HEAD
            ? "the item's player head texture"
            : "the base item `" + ItemAddressing.stripMinecraftNamespace(itemId) + "`";

        return source + " could not resolve item model `" + itemModel + "`, so " + rendered
            + " was rendered instead. The pack is likely older than this item.";
    }
}
