package net.aerh.jigsaw.core.generator.player;

/**
 * The six body parts of a Minecraft player model with their pixel dimensions,
 * UV mapping origins on the 64x64 skin texture, and 3D position offsets.
 *
 * <p>Dimensions follow Minecraft's player model specification:
 * <ul>
 *   <li>Head: 8x8x8 pixels</li>
 *   <li>Body: 8x12x4 pixels</li>
 *   <li>Arms: 4x12x4 (classic/Steve) or 3x12x4 (slim/Alex)</li>
 *   <li>Legs: 4x12x4 pixels</li>
 * </ul>
 *
 * <p>UV origins point to the top-left of the standard Minecraft cross-pattern for each
 * body part on the skin texture. Position offsets use renderer units where
 * {@code halfExtent = pixelDim / 8.0} (an 8px cube has half-extent 1.0). Y is down.
 *
 * <p>Reference: <a href="https://minecraft.wiki/w/Skin#File_format">Minecraft Wiki - Skin</a>
 */
public enum BodyPart {

    /** Head: 8x8x8, base UV at (0,0), overlay (hat) UV at (32,0). */
    HEAD(8, 8, 8, 0, 0, 32, 0),

    /** Body/torso: 8x12x4, base UV at (16,16), overlay (jacket) UV at (16,32). */
    BODY(8, 12, 4, 16, 16, 16, 32),

    /** Right arm: 4x12x4 (classic) or 3x12x4 (slim), base UV at (40,16), overlay at (40,32). */
    RIGHT_ARM(4, 12, 4, 40, 16, 40, 32),

    /** Left arm: own UV section in 64x64 format, base UV at (32,48), overlay at (48,48). */
    LEFT_ARM(4, 12, 4, 32, 48, 48, 48),

    /** Right leg: 4x12x4, base UV at (0,16), overlay at (0,32). */
    RIGHT_LEG(4, 12, 4, 0, 16, 0, 32),

    /** Left leg: own UV section in 64x64 format, base UV at (16,48), overlay at (0,48). */
    LEFT_LEG(4, 12, 4, 16, 48, 0, 48);

    private final int defaultWidth;
    private final int height;
    private final int depth;
    private final int baseUvX;
    private final int baseUvY;
    private final int overlayUvX;
    private final int overlayUvY;

    BodyPart(int defaultWidth, int height, int depth,
             int baseUvX, int baseUvY, int overlayUvX, int overlayUvY) {
        this.defaultWidth = defaultWidth;
        this.height = height;
        this.depth = depth;
        this.baseUvX = baseUvX;
        this.baseUvY = baseUvY;
        this.overlayUvX = overlayUvX;
        this.overlayUvY = overlayUvY;
    }

    /** Width in pixels, accounting for slim arm model (3px instead of 4px). */
    public int width(boolean slim) {
        if (slim && (this == RIGHT_ARM || this == LEFT_ARM)) {
            return 3;
        }
        return defaultWidth;
    }

    /** Height in pixels. */
    public int height() { return height; }

    /** Depth in pixels. */
    public int depth() { return depth; }

    /** UV origin X for the base skin layer cross pattern. */
    public int baseUvX() { return baseUvX; }

    /** UV origin Y for the base skin layer cross pattern. */
    public int baseUvY() { return baseUvY; }

    /** UV origin X for the overlay layer (hat/jacket/sleeve/pants) cross pattern. */
    public int overlayUvX() { return overlayUvX; }

    /** UV origin Y for the overlay layer cross pattern. */
    public int overlayUvY() { return overlayUvY; }

    /** Half-extent in X (renderer units). */
    public double halfExtentX(boolean slim) { return width(slim) / 8.0; }

    /** Half-extent in Y (renderer units). */
    public double halfExtentY() { return height / 8.0; }

    /** Half-extent in Z (renderer units). */
    public double halfExtentZ() { return depth / 8.0; }

    /** 3D position offset X in renderer units. */
    public double offsetX(boolean slim) {
        return switch (this) {
            case HEAD, BODY -> 0;
            case RIGHT_ARM -> {
                int w = width(slim);
                yield -(1.0 + w / 8.0) - PlayerModelSettings.SEAM_OVERLAP;
            }
            case LEFT_ARM -> {
                int w = width(slim);
                yield (1.0 + w / 8.0) + PlayerModelSettings.SEAM_OVERLAP;
            }
            case RIGHT_LEG -> -0.5;
            case LEFT_LEG -> 0.5;
        };
    }

    /** 3D position offset Y in renderer units (positive = down). */
    public double offsetY() {
        return switch (this) {
            case HEAD -> -3.0;
            case BODY, RIGHT_ARM, LEFT_ARM -> -0.5 - PlayerModelSettings.SEAM_OVERLAP;
            case RIGHT_LEG, LEFT_LEG -> 2.5 - PlayerModelSettings.SEAM_OVERLAP;
        };
    }

    /** 3D position offset Z in renderer units. */
    public double offsetZ() { return 0; }
}
