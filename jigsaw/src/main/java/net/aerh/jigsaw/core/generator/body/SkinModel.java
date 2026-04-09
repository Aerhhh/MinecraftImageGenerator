package net.aerh.jigsaw.core.generator.body;

/**
 * The two Minecraft player skin models, differing only in arm width.
 *
 * <ul>
 *   <li>{@link #CLASSIC} - 4-pixel wide arms (Steve model)</li>
 *   <li>{@link #SLIM} - 3-pixel wide arms (Alex model)</li>
 * </ul>
 */
public enum SkinModel {

    /** Classic (Steve) model with 4-pixel wide arms. */
    CLASSIC(4),

    /** Slim (Alex) model with 3-pixel wide arms. */
    SLIM(3);

    private final int armWidth;

    SkinModel(int armWidth) {
        this.armWidth = armWidth;
    }

    /**
     * Returns the arm width in skin pixels for this model.
     *
     * @return 4 for classic, 3 for slim
     */
    public int armWidth() {
        return armWidth;
    }
}
