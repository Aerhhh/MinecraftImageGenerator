package net.aerh.imagegenerator.pack;

/**
 * A model's {@code display.gui} transform as declared in JSON: rotation in degrees, translation
 * in model units (16 units per slot; vanilla clamps to +-80 at render), scale factors (vanilla
 * clamps to +-4 at render; negative components mirror).
 *
 * <p>Vanilla applies the display transform about the slot center as scale, then rotation (a
 * vertex rotates about z, then y, then x - see {@link ElementModelRenderer}), then translation.
 * Rotations equivalent to identity (|y| &lt;= 5 degrees, x = z = 0) or a horizontal mirror
 * (|y| within 5 degrees of 180, x = z = 0) render through exact flat fast paths; anything else
 * renders through the orthographic pipeline when the render opts into full gui rotations and
 * fails loudly otherwise.
 */
record GuiTransform(float rotationX, float rotationY, float rotationZ,
                    float translationX, float translationY, float translationZ,
                    float scaleX, float scaleY, float scaleZ) {

    static final GuiTransform IDENTITY = new GuiTransform(0, 0, 0, 0, 0, 0, 1, 1, 1);
}
