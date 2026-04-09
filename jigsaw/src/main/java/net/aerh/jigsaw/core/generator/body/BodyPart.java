package net.aerh.jigsaw.core.generator.body;

import java.util.List;

/**
 * Defines the six body parts of a Minecraft player model, each with its pixel dimensions,
 * UV mapping origins on the 64x64 skin texture, and 3D position offset.
 *
 * <p>Dimensions and UV origins follow the standard Minecraft skin texture layout where each
 * body part's faces are arranged in a cross pattern:
 * <pre>
 *          [top]     [bottom]
 *          (u+d,v)   (u+d+w,v)
 *
 * [right]  [front]   [left]    [back]
 * (u,v+d)  (u+d,v+d) (u+d+w,v+d) (u+d+w+d,v+d)
 * </pre>
 *
 * <p>Position offsets are in renderer units (1 skin pixel = 0.25 units) relative to the
 * body center, with Y increasing downward.
 */
public enum BodyPart {

    HEAD,
    BODY,
    RIGHT_ARM,
    LEFT_ARM,
    RIGHT_LEG,
    LEFT_LEG;

    /**
     * Returns the geometry for this body part given the skin model.
     * The skin model only affects arm dimensions and positions.
     *
     * @param model the skin model (classic or slim)
     * @return the geometry for this part
     */
    public Geometry geometry(SkinModel model) {
        return switch (this) {
            case HEAD -> new Geometry(8, 8, 8, 0, 0, 32, 0, 0, -3, 0);
            case BODY -> new Geometry(8, 12, 4, 16, 16, 16, 32, 0, -0.5, 0);
            case RIGHT_ARM -> {
                int w = model.armWidth();
                double ox = -(1.0 + w / 8.0);
                yield new Geometry(w, 12, 4, 40, 16, 40, 32, ox, -0.5, 0);
            }
            case LEFT_ARM -> {
                int w = model.armWidth();
                double ox = 1.0 + w / 8.0;
                yield new Geometry(w, 12, 4, 32, 48, 48, 48, ox, -0.5, 0);
            }
            case RIGHT_LEG -> new Geometry(4, 12, 4, 0, 16, 0, 32, -0.5, 2.5, 0);
            case LEFT_LEG -> new Geometry(4, 12, 4, 16, 48, 0, 48, 0.5, 2.5, 0);
        };
    }

    /**
     * Returns the list of all body parts with their geometry for the given skin model.
     *
     * @param model the skin model
     * @return list of (BodyPart, Geometry) pairs for all six body parts
     */
    public static List<PartWithGeometry> allParts(SkinModel model) {
        BodyPart[] parts = values();
        return List.of(
                new PartWithGeometry(parts[0], parts[0].geometry(model)),
                new PartWithGeometry(parts[1], parts[1].geometry(model)),
                new PartWithGeometry(parts[2], parts[2].geometry(model)),
                new PartWithGeometry(parts[3], parts[3].geometry(model)),
                new PartWithGeometry(parts[4], parts[4].geometry(model)),
                new PartWithGeometry(parts[5], parts[5].geometry(model))
        );
    }

    /**
     * Geometry data for a single body part.
     *
     * @param pixelWidth   width in skin pixels
     * @param pixelHeight  height in skin pixels
     * @param pixelDepth   depth in skin pixels
     * @param baseUvX      UV origin x for the base skin layer
     * @param baseUvY      UV origin y for the base skin layer
     * @param overlayUvX   UV origin x for the overlay layer (hat/jacket/sleeve/pants)
     * @param overlayUvY   UV origin y for the overlay layer
     * @param offsetX      3D position offset x (in renderer units, 1px = 0.25 units)
     * @param offsetY      3D position offset y (in renderer units, Y-down)
     * @param offsetZ      3D position offset z (in renderer units)
     */
    public record Geometry(
            int pixelWidth, int pixelHeight, int pixelDepth,
            int baseUvX, int baseUvY,
            int overlayUvX, int overlayUvY,
            double offsetX, double offsetY, double offsetZ
    ) {

        /** Half-extent in x (renderer units). */
        public double halfExtentX() { return pixelWidth / 8.0; }

        /** Half-extent in y (renderer units). */
        public double halfExtentY() { return pixelHeight / 8.0; }

        /** Half-extent in z (renderer units). */
        public double halfExtentZ() { return pixelDepth / 8.0; }
    }

    /**
     * Pairs a body part with its resolved geometry.
     */
    public record PartWithGeometry(BodyPart part, Geometry geometry) {}
}
