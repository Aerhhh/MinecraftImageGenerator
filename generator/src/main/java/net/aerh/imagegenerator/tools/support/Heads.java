package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.impl.MinecraftPlayerHeadGenerator;
import org.jetbrains.annotations.Nullable;

/** Player head setup shared by the tools. */
public final class Heads {

    /** The scale heads use beside a tooltip, matching the Discord commands. */
    private static final int BESIDE_TOOLTIP_SCALE = -2;

    private Heads() {
    }

    /**
     * A player head sized to sit beside a tooltip.
     *
     * @param skinValue the base64 skin texture, or null for the default head
     */
    public static MinecraftPlayerHeadGenerator besideTooltip(@Nullable String skinValue) {
        MinecraftPlayerHeadGenerator.Builder builder = new MinecraftPlayerHeadGenerator.Builder()
            .withScale(BESIDE_TOOLTIP_SCALE);

        if (skinValue != null) {
            builder.withSkin(skinValue);
        }

        return builder.build();
    }
}
