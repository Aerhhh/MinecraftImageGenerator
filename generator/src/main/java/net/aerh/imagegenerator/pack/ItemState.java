package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The item component values an item model definition evaluates against, the subset of a
 * vanilla item stack this library renders from.
 *
 * @param customModelData the {@code minecraft:custom_model_data} component, read by model
 *                        dispatch and {@code custom_model_data} tint sources
 * @param dyedColor       the {@code minecraft:dyed_color} component as packed {@code 0xRRGGBB},
 *                        read by {@code dye} tint sources; null when the item is not dyed, so
 *                        those sources use their declared default
 */
public record ItemState(CustomModelData customModelData, @Nullable Integer dyedColor) {

    /** No component data: every lookup evaluates to its default. */
    public static final ItemState EMPTY = new ItemState(CustomModelData.EMPTY, null);

    /**
     * @throws IllegalArgumentException when {@code dyedColor} is outside {@code 0..0xFFFFFF}
     */
    public ItemState {
        Objects.requireNonNull(customModelData, "customModelData");
        if (dyedColor != null && (dyedColor < 0 || dyedColor > 0xFFFFFF)) {
            throw new IllegalArgumentException(
                "dyedColor must be a packed 0xRRGGBB value, got 0x" + Integer.toHexString(dyedColor));
        }
    }

    /** An undyed item carrying {@code customModelData}. */
    public static ItemState of(CustomModelData customModelData) {
        return new ItemState(customModelData, null);
    }
}
