package net.aerh.jigsaw.core.generator.body;

/**
 * Constants for the isometric 3D player body renderer, defining render dimensions,
 * rotation angles, and layer inflation values.
 */
public final class PlayerBodySettings {

    /** Canvas width for the high-resolution render (before downscaling). */
    public static final int DEFAULT_WIDTH = 2700;

    /** Canvas height for the high-resolution render (before downscaling). */
    public static final int DEFAULT_HEIGHT = 2700;

    /** Scale factor mapping renderer units to pixels on the canvas. */
    public static final int DEFAULT_RENDER_SCALE = 180;

    /** Default X rotation (pitch) in radians - 30 degrees. */
    public static final double DEFAULT_X_ROTATION = Math.PI / 6;

    /** Default Y rotation (yaw) in radians - negative 45 degrees. */
    public static final double DEFAULT_Y_ROTATION = -Math.PI / 4;

    /** Default Z rotation (roll) in radians. */
    public static final double DEFAULT_Z_ROTATION = 0;

    /** Anti-aliasing downscale factor applied to the final image. */
    public static final int BODY_SCALE_DOWN = 3;

    /** Inflation added to each half-extent for overlay layers (hat/jacket/sleeve/pants). */
    public static final double OVERLAY_INFLATION = 0.0625;

    private PlayerBodySettings() {}
}
