package net.aerh.jigsaw.core.generator.skull;

/**
 * Constants for the isometric 3D player skull renderer, defining vertex coordinates,
 * rotation angles, render dimensions, and hat layer offset.
 */
public final class PlayerSkullSettings {

    public static final Face[] FACES = Face.values();
    public static final double HAT_LAYER_OFFSET = 1.06;

    /** Vertex coordinates for the head cube (indices 0-7) and hat layer cube (indices 8-15). */
    public static final double[][] COORDINATES = {
        { 1,  1, -1, 1},
        { 1,  1,  1, 1},
        {-1,  1,  1, 1},
        {-1,  1, -1, 1},
        { 1, -1, -1, 1},
        { 1, -1,  1, 1},
        {-1, -1,  1, 1},
        {-1, -1, -1, 1},
        { HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1},
        { HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1},
        { HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1},
        { HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET,  HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1}
    };

    public static final int DEFAULT_WIDTH = 1250;
    public static final int DEFAULT_HEIGHT = 1250;
    public static final int DEFAULT_RENDER_SCALE = Math.round(Math.min(DEFAULT_WIDTH, DEFAULT_HEIGHT) / 4f);
    public static final double DEFAULT_X_ROTATION = Math.PI / 6;
    public static final double DEFAULT_Y_ROTATION = -Math.PI / 4;
    public static final double DEFAULT_Z_ROTATION = 0;
    public static final int HEAD_SCALE_DOWN = 3;

    private PlayerSkullSettings() {}
}
