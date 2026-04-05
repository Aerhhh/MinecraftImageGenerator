package net.aerh.jigsaw.api.nbt;

import java.util.List;
import java.util.Optional;

/**
 * The result of parsing an item NBT string, containing all data needed to render the item.
 *
 * @param itemId        The Minecraft item ID (e.g. {@code "minecraft:diamond_sword"}).
 * @param enchanted     Whether the item has the enchantment glint.
 * @param base64Texture Optional Base64-encoded skull texture for player head items.
 * @param lore          The item's lore lines (may be empty).
 * @param displayName   The item's custom display name, or empty if not set.
 * @param dyeColor      The packed RGB dye color for leather armor or similar items, or empty if not dyed.
 */
public record ParsedItem(
        String itemId,
        boolean enchanted,
        Optional<String> base64Texture,
        List<String> lore,
        Optional<String> displayName,
        Optional<Integer> dyeColor
) {

    public ParsedItem {
        lore = List.copyOf(lore);
    }
}
