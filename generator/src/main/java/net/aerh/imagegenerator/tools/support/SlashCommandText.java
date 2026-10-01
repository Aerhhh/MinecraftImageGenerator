package net.aerh.imagegenerator.tools.support;

import org.jetbrains.annotations.Nullable;

/**
 * Completes the {@code /gen item} command string that a tooltip builder reconstructs with the
 * options that belong to the sibling item generator, so a re-run reproduces the same render.
 */
public final class SlashCommandText {

    private SlashCommandText() {
    }

    /** Appends the Power Stone's item id and enchanted flag. */
    public static String appendPowerStoneItemOptions(String slashCommand, @Nullable String itemId, boolean enchanted) {
        if (itemId != null && !itemId.isBlank()) {
            slashCommand += " item_id: " + itemId;
        }

        if (enchanted) {
            slashCommand += " enchanted: True";
        }

        return slashCommand;
    }

    /**
     * Appends the address of the item a parsed NBT payload rendered. An item with a
     * {@code minecraft:item_model} component is addressed by that model rather than by its id.
     */
    public static String appendParsedItemOptions(String slashCommand, @Nullable String itemId, @Nullable String itemModel,
                                                 @Nullable String skinValue, boolean enchanted) {
        if (itemModel != null && !itemModel.isBlank()) {
            slashCommand += " item_model: " + itemModel;
        } else if (itemId != null && !itemId.isBlank()) {
            slashCommand += " item_id: " + ItemAddressing.stripMinecraftNamespace(itemId);
        }

        if (skinValue != null && !skinValue.isBlank()) {
            slashCommand += " skin_value: " + skinValue;
        }

        if (enchanted) {
            slashCommand += " enchanted: True";
        }

        return slashCommand;
    }

    /** Replaces real newlines with the two characters {@code \n} so the command stays on one line. */
    public static String escapeNewlines(String slashCommand) {
        return slashCommand.replace("\n", "\\n");
    }
}
