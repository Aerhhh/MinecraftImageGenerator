package net.aerh.imagegenerator.pack;

/**
 * A model's {@code display.gui} transform as declared in JSON: rotation in degrees, translation
 * in model units (16 units per slot; vanilla clamps to +-80 at render), scale factors (vanilla
 * clamps to +-4 at render; negative components mirror).
 *
 * <p>Vanilla applies the display transform about the slot center as scale, then rotation, then
 * translation. Only rotations equivalent to identity (|y| &lt;= 5 degrees, x = z = 0) or a
 * horizontal mirror (|y| within 5 degrees of 180, x = z = 0) are renderable this wave; anything
 * else fails loudly at render time.
 */
record GuiTransform(float rotationX, float rotationY, float rotationZ,
                    float translationX, float translationY, float translationZ,
                    float scaleX, float scaleY, float scaleZ) {

    static final GuiTransform IDENTITY = new GuiTransform(0, 0, 0, 0, 0, 0, 1, 1, 1);
}
