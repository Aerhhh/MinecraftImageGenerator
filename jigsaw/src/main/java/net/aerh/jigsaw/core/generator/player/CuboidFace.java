package net.aerh.jigsaw.core.generator.player;

/**
 * The six faces of a cuboid, each with a directional shadow intensity that
 * simulates lighting on the isometric model.
 *
 * <p>Shadow values range from 0 (fully dark) to 255 (fully lit). They simulate
 * a light source positioned above and slightly to the left of the isometric camera,
 * matching Minecraft's typical inventory rendering aesthetic.
 */
public enum CuboidFace {

    /** Front face (-Z normal), moderately shadowed. */
    FRONT(111),

    /** Back face (+Z normal). */
    BACK(156),

    /** Left face (-X normal, character's right side), brightest side face. */
    LEFT(162),

    /** Right face (+X normal, character's left side). */
    RIGHT(156),

    /** Top face (-Y normal), fully lit from above. */
    TOP(255),

    /** Bottom face (+Y normal), in shadow. */
    BOTTOM(111);

    private final int shadowIntensity;

    CuboidFace(int shadowIntensity) {
        this.shadowIntensity = shadowIntensity;
    }

    /**
     * Returns the shadow intensity for this face direction.
     *
     * @return intensity in the range [0, 255]
     */
    public int shadowIntensity() {
        return shadowIntensity;
    }
}
