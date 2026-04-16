package net.aerh.jigsaw.core.font;

/**
 * Well-known Minecraft font identifiers used to look up fonts in a {@link net.aerh.jigsaw.api.font.FontRegistry}.
 */
public final class MinecraftFontId {

    /** The standard Minecraft font used for most text. */
    public static final String DEFAULT = "minecraft:default";

    /** The bold variant of the standard Minecraft font. */
    public static final String DEFAULT_BOLD = "minecraft:default_bold";

    /** The italic variant of the standard Minecraft font. */
    public static final String DEFAULT_ITALIC = "minecraft:default_italic";

    /** The bold-italic variant of the standard Minecraft font. */
    public static final String DEFAULT_BOLD_ITALIC = "minecraft:default_bold_italic";

    /** The Standard Galactic Alphabet (SGA) font used for enchanting table text. */
    public static final String GALACTIC = "minecraft:alt";

    /** The Illager Alt font. */
    public static final String ILLAGERALT = "minecraft:illageralt";

    /** Unifont - fallback for most Unicode characters. */
    public static final String UNIFONT = "minecraft:unifont";

    /** Unifont Upper - fallback for characters above U+FFFF (emoji etc.). */
    public static final String UNIFONT_UPPER = "minecraft:unifont_upper";

    private MinecraftFontId() {
        // constants-only class
    }
}
