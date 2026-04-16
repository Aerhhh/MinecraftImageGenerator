package net.aerh.jigsaw.core.generator.player;

/**
 * Constants for the isometric 3D player model renderer, defining canvas dimensions,
 * rotation angles, scale factors, and layer inflation values.
 *
 * <p>The renderer works at high resolution ({@value #CANVAS_WIDTH}x{@value #CANVAS_HEIGHT})
 * then downscales by a factor of {@value #DOWNSCALE_FACTOR} for anti-aliasing.
 */
public final class PlayerModelSettings {

    /** High-resolution canvas width (before downscaling). */
    public static final int CANVAS_WIDTH = 4800;

    /** High-resolution canvas height (before downscaling). */
    public static final int CANVAS_HEIGHT = 4800;

    /** Scale factor mapping renderer units to canvas pixels. */
    public static final int RENDER_SCALE = 400;

    /** Default X rotation (pitch) in radians - 30 degrees. */
    public static final double DEFAULT_X_ROTATION = Math.PI / 6;

    /** Default Y rotation (yaw) in radians - negative 45 degrees. */
    public static final double DEFAULT_Y_ROTATION = -Math.PI / 4;

    /** Default Z rotation (roll) in radians. */
    public static final double DEFAULT_Z_ROTATION = 0;

    /** Anti-aliasing downscale factor applied to the final image. */
    public static final int DOWNSCALE_FACTOR = 3;

    /** Overlay layer inflation (hat/jacket/sleeve/pants) in renderer units. */
    public static final double OVERLAY_INFLATION = 0.0625;

    /** Outer armor inflation (helmet, chestplate, boots) - 1 pixel per side. */
    public static final double OUTER_ARMOR_INFLATION = 0.25;

    /** Inner armor inflation (leggings) - 0.5 pixel per side. */
    public static final double INNER_ARMOR_INFLATION = 0.125;

    /** Small Y overlap to close sub-pixel seams between adjacent body parts. */
    public static final double SEAM_OVERLAP = 0.03;

    private PlayerModelSettings() {}
}
