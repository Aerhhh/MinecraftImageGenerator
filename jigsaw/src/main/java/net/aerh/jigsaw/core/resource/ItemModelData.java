package net.aerh.jigsaw.core.resource;

import java.util.Map;

/**
 * The resolved texture data from an item model, after following the parent
 * inheritance chain and merging all texture references.
 *
 * @param textures   merged texture map (e.g. "layer0" -> "minecraft:item/diamond_sword")
 * @param parentType the terminal parent type (e.g. "builtin/generated", "item/generated")
 */
public record ItemModelData(
        Map<String, String> textures,
        String parentType
) {
    public ItemModelData {
        textures = Map.copyOf(textures);
        if (parentType == null) {
            parentType = "unknown";
        }
    }
}
