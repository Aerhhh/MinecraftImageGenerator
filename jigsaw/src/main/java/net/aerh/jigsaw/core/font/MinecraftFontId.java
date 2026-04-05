package net.aerh.jigsaw.core.font;

/**
 * Well-known Minecraft font identifiers used to look up fonts in a {@link net.aerh.jigsaw.api.font.FontRegistry}.
 */
public final class MinecraftFontId {

    /** The standard Minecraft font used for most text. */
    public static final String DEFAULT = "minecraft:default";

    /** The Standard Galactic Alphabet (SGA) font used for enchanting table text. */
    public static final String GALACTIC = "minecraft:alt";

    /** The Illager Alt font. */
    public static final String ILLAGERALT = "minecraft:illageralt";

    private MinecraftFontId() {
        // constants-only class
    }
}
