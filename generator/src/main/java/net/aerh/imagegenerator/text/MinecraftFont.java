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

    DEFAULT("minecraft:default"),
    GALACTIC("minecraft:alt"),
    ILLAGERALT("minecraft:illageralt");

    private final String resourceLocation;

    MinecraftFont(String resourceLocation) {
        this.resourceLocation = resourceLocation;
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
        MinecraftFont font = fromResourceLocationOrNull(resourceLocation);
        return font != null ? font : DEFAULT;
    }

    /**
     * Resolves a font from a Minecraft resource location string, distinguishing unknown ids from
     * the default font: a null or empty location IS the default, but an unrecognized location
     * (e.g. a resource-pack font like {@code "mypack:chat"}) returns null so callers can keep the
     * raw id instead of collapsing it to {@link #DEFAULT}.
     *
     * @param resourceLocation The resource location (e.g. {@code "minecraft:alt"}, {@code "alt"})
     *
     * @return The matching font, {@link #DEFAULT} for null/empty input, or null if not recognized
     */
    @Nullable
    public static MinecraftFont fromResourceLocationOrNull(@Nullable String resourceLocation) {
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

        return null;
    }
}