package net.aerh.jigsaw.core.generator.player;

/**
 * Maps (BodyPart, CuboidFace) pairs to UV coordinates on skin and armor textures.
 *
 * <p>Derives per-face UV regions from a body part's cross-pattern UV origin using
 * the standard Minecraft texture layout:
 * <pre>
 *              [top]          [bottom]
 *              (u+d, v)       (u+d+w, v)
 *               w x d          w x d
 *
 *   [left]     [front]        [right]        [back]
 *   (u, v+d)   (u+d, v+d)    (u+d+w, v+d)   (u+2d+w, v+d)
 *    d x h      w x h          d x h          w x h
 * </pre>
 *
 * <p>"Left" in the cross layout is the character's right side (-X), and "right" is
 * the character's left side (+X).
 *
 * <p>Handles the 64x64 skin format. For legacy 64x32 skins, left arm/leg UV regions
 * will fall outside the texture bounds and the renderer will skip those pixels.
 */
public final class SkinTextureMapper {

    private SkinTextureMapper() {}

    /**
     * Returns the UV region for a body part's base skin layer face.
     *
     * @param part the body part
     * @param face the cuboid face direction
     * @param slim true for slim (3px) arms, false for classic (4px)
     * @return the UV region on the skin texture
     */
    public static UvRegion baseUv(BodyPart part, CuboidFace face, boolean slim) {
        return computeUv(part.baseUvX(), part.baseUvY(),
                part.width(slim), part.height(), part.depth(), face);
    }

    /**
     * Returns the UV region for a body part's overlay layer face (hat/jacket/sleeve/pants).
     *
     * @param part the body part
     * @param face the cuboid face direction
     * @param slim true for slim arms
     * @return the UV region on the skin texture
     */
    public static UvRegion overlayUv(BodyPart part, CuboidFace face, boolean slim) {
        return computeUv(part.overlayUvX(), part.overlayUvY(),
                part.width(slim), part.height(), part.depth(), face);
    }

    /**
     * Returns the UV region for an armor texture face.
     *
     * <p>Armor textures use the same cross-pattern UV layout as skin textures, with
     * identical UV origins per body region. Left arm/leg map to the right arm/leg UV
     * region because armor textures (64x32) have no separate left-side sections.
     * Armor arms are always 4px wide regardless of the slim model setting.
     *
     * @param part the body part being covered
     * @param face the cuboid face direction
     * @return the UV region on the armor texture
     */
    public static UvRegion armorUv(BodyPart part, CuboidFace face) {
        BodyPart uvSource = switch (part) {
            case LEFT_ARM -> BodyPart.RIGHT_ARM;
            case LEFT_LEG -> BodyPart.RIGHT_LEG;
            default -> part;
        };
        return computeUv(uvSource.baseUvX(), uvSource.baseUvY(),
                part.width(false), part.height(), part.depth(), face);
    }

    /**
     * Computes the UV region for a specific face of a cuboid given its cross-pattern
     * origin and dimensions.
     */
    private static UvRegion computeUv(int u, int v, int w, int h, int d, CuboidFace face) {
        return switch (face) {
            case FRONT  -> new UvRegion(u + d,         v + d, w, h);
            case BACK   -> new UvRegion(u + 2 * d + w, v + d, w, h);
            case LEFT   -> new UvRegion(u,             v + d, d, h);
            case RIGHT  -> new UvRegion(u + d + w,     v + d, d, h);
            case TOP    -> new UvRegion(u + d,         v,     w, d);
            case BOTTOM -> new UvRegion(u + d + w,     v,     w, d);
        };
    }

    /**
     * A rectangular region on a texture.
     *
     * @param u      top-left X coordinate on the texture
     * @param v      top-left Y coordinate on the texture
     * @param width  region width in pixels
     * @param height region height in pixels
     */
    public record UvRegion(int u, int v, int width, int height) {}
}
