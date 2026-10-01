package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import org.jetbrains.annotations.Nullable;

/** Validation and builder wiring for the two ways a request can name an item. */
public final class ItemAddressing {

    private static final String MINECRAFT_NAMESPACE = "minecraft:";

    private ItemAddressing() {
    }

    /**
     * Rejects addressing one item both ways at once.
     *
     * @throws GeneratorException If both options were supplied
     */
    public static void requireSingleItemAddress(@Nullable String itemId, @Nullable String itemModel) {
        if (itemId != null && !itemId.isBlank() && itemModel != null && !itemModel.isBlank()) {
            throw new GeneratorException("The item_id and item_model options are mutually exclusive; use one or the other!");
        }
    }

    /**
     * Rejects a render that names neither an item id nor an item model, for the tools whose whole
     * output is the item.
     *
     * @throws GeneratorException If neither option was supplied
     */
    public static void requireAnItemAddress(@Nullable String itemId, @Nullable String itemModel) {
        if ((itemId == null || itemId.isBlank()) && (itemModel == null || itemModel.isBlank())) {
            throw new GeneratorException("Set either the item_id or the item_model option to pick what to render!");
        }
    }

    /** Points an item builder at whichever address was given; callers validate the pair first. */
    public static MinecraftItemGenerator.Builder addressItem(MinecraftItemGenerator.Builder builder, @Nullable String itemId, @Nullable String itemModel) {
        return itemModel != null && !itemModel.isBlank()
            ? builder.withItemModel(itemModel)
            : builder.withItem(itemId);
    }

    /** The item id without its default namespace. Item model refs keep their namespace. */
    public static String stripMinecraftNamespace(String itemId) {
        return itemId.startsWith(MINECRAFT_NAMESPACE) ? itemId.substring(MINECRAFT_NAMESPACE.length()) : itemId;
    }
}
