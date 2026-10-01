package net.aerh.imagegenerator.tools.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Building the shareable {@code /gen powerstone} and {@code /gen item} command strings from the
 * item and enchanted options, and escaping newlines so the command stays on one line.
 */
class SlashCommandTextTest {

    private static final String BASE = "/gen item item_name: &fSafari Belt";

    @Test
    void appendsItemIdWhenPresent() {
        assertEquals("/gen powerstone item_id: stick",
            SlashCommandText.appendPowerStoneItemOptions("/gen powerstone", "stick", false));
    }

    @Test
    void omitsItemIdWhenNullOrBlank() {
        assertEquals("/gen powerstone", SlashCommandText.appendPowerStoneItemOptions("/gen powerstone", null, false));
        assertEquals("/gen powerstone", SlashCommandText.appendPowerStoneItemOptions("/gen powerstone", "  ", false));
    }

    @Test
    void appendsEnchantedWhenTrue() {
        assertEquals("/gen powerstone item_id: stick enchanted: True",
            SlashCommandText.appendPowerStoneItemOptions("/gen powerstone", "stick", true));
    }

    @Test
    void appendsItemIdWhenTheItemHasNoModel() {
        assertEquals(BASE + " item_id: paper",
            SlashCommandText.appendParsedItemOptions(BASE, "paper", null, null, false));
    }

    @Test
    void appendsItemModelInsteadOfItemIdWhenPresent() {
        assertEquals(BASE + " item_model: hypixel_skyblock:item/island_relevant/safari/safari_belt",
            SlashCommandText.appendParsedItemOptions(BASE, "paper",
                "hypixel_skyblock:item/island_relevant/safari/safari_belt", null, false));
    }

    @Test
    void stripsTheMinecraftNamespaceFromTheItemIdOnly() {
        assertEquals(BASE + " item_id: paper",
            SlashCommandText.appendParsedItemOptions(BASE, "minecraft:paper", null, null, false));
        assertEquals(BASE + " item_model: minecraft:item/diamond_sword",
            SlashCommandText.appendParsedItemOptions(BASE, "minecraft:paper", "minecraft:item/diamond_sword", null, false));
    }

    @Test
    void omitsBothWhenNeitherIsPresent() {
        assertEquals(BASE, SlashCommandText.appendParsedItemOptions(BASE, null, null, null, false));
        assertEquals(BASE, SlashCommandText.appendParsedItemOptions(BASE, "  ", "  ", null, false));
    }

    @Test
    void appendsSkinValueAlongsideAnItemModel() {
        assertEquals(BASE + " item_model: hypixel_skyblock:item/belt skin_value: abc123",
            SlashCommandText.appendParsedItemOptions(BASE, "player_head", "hypixel_skyblock:item/belt", "abc123", false));
    }

    @Test
    void appendsEnchantedLast() {
        assertEquals(BASE + " item_model: hypixel_skyblock:item/belt enchanted: True",
            SlashCommandText.appendParsedItemOptions(BASE, "paper", "hypixel_skyblock:item/belt", null, true));
    }

    @Test
    void escapeNewlinesKeepsCommandOnOneLine() {
        assertEquals("/gen item item_lore: a\\nb", SlashCommandText.escapeNewlines("/gen item item_lore: a\nb"));
        assertEquals("unchanged", SlashCommandText.escapeNewlines("unchanged"));
    }
}
