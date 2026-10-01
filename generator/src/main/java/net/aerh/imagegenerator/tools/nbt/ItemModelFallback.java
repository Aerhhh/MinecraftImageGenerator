package net.aerh.imagegenerator.tools.nbt;

/** What a {@code minecraft:item_model} render degrades to when the pack cannot produce it. */
public enum ItemModelFallback {
    /** The model resolved, or none was asked for: render it as addressed. */
    NONE,
    /** Render the item's player head texture instead. */
    PLAYER_HEAD,
    /** Render the base item the model sits on instead. */
    ITEM_ID
}
