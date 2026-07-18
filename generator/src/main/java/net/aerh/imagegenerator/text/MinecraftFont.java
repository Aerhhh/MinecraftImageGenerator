package net.aerh.imagegenerator.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the available font families in Minecraft's text rendering system.
 * <p>
 * In vanilla Minecraft, the font is specified via the {@code "font"} property in JSON text components
 * using resource location format (e.g. {@code "minecraft:default"}, {@code "minecraft:alt"}).
 */
public enum MinecraftFont {

    DEFAULT("minecraft:default", '\0'),
    GALACTIC("minecraft:alt", 'g'),
    ILLAGERALT("minecraft:illageralt", 'h');

    private final String resourceLocation;
    private final char fontCode;

    MinecraftFont(String resourceLocation, char fontCode) {
        this.resourceLocation = resourceLocation;
        this.fontCode = fontCode;
    }

    /**
     * Returns the legacy in-band font code for this font ({@code g} for {@link #GALACTIC},
     * {@code h} for {@link #ILLAGERALT}), or {@code '\0'} for {@link #DEFAULT} which has no code.
     *
     * @return the font code character
     */
    public char getFontCode() {
        return fontCode;
    }

    /**
     * Resolves the alternate font selected by a legacy font code ({@code g}/{@code h}). Any other
     * character (including {@code '\0'}) resolves to {@link #DEFAULT}.
     *
     * @param code the font code character
     *
     * @return the matching alternate font, or {@link #DEFAULT} when the code is not a font code
     */
    @NotNull
    public static MinecraftFont fromFontCode(char code) {
        for (MinecraftFont font : values()) {
            if (font != DEFAULT && font.fontCode == code) {
                return font;
            }
        }

        return DEFAULT;
    }

    /**
     * Whether {@code code} is a legacy font code ({@code g}/{@code h}) selecting an alternate font.
     *
     * @param code the code character
     *
     * @return true if the code selects an alternate font
     */
    public static boolean isFontCode(char code) {
        return fromFontCode(code) != DEFAULT;
    }

    /**
     * Returns the Minecraft resource location string for this font (e.g. {@code "minecraft:default"}).
     *
     * @return The resource location
     */
    public String getResourceLocation() {
        return resourceLocation;
    }

    /**
     * Resolves a font from a Minecraft resource location string.
     *
     * @param resourceLocation The resource location (e.g. {@code "minecraft:alt"}, {@code "alt"})
     *
     * @return The matching font, or {@link #DEFAULT} if not recognized
     */
    @NotNull
    public static MinecraftFont fromResourceLocation(@Nullable String resourceLocation) {
        if (resourceLocation == null || resourceLocation.isEmpty()) {
            return DEFAULT;
        }

        for (MinecraftFont font : values()) {
            if (font.resourceLocation.equals(resourceLocation)) {
                return font;
            }
        }

        // Also match without the "minecraft:" prefix
        for (MinecraftFont font : values()) {
            String shortName = font.resourceLocation.substring(font.resourceLocation.indexOf(':') + 1);
            if (shortName.equals(resourceLocation)) {
                return font;
            }
        }

        return DEFAULT;
    }
}