package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One cuboid of an item model's {@code elements} list, in model units (a slot interior is 16
 * units; coordinates outside 0..16 are legal within the vanilla -16..32 bounds).
 *
 * @param rotation the element's rotation entry, or null when absent (an explicit entry with
 *                 angle 0 parses to a {@link Rotation} that renders as a no-op)
 * @param shade    vanilla {@code shade} flag, default true; false exempts the element from
 *                 {@code gui_light: side} face shading
 * @param faces    faces by direction; every declared face rasterizes through the orthographic
 *                 GUI projection when it survives back-face culling
 */
record ModelElement(float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                    @Nullable Rotation rotation, boolean shade, Map<Direction, Face> faces) {

    enum Direction {
        NORTH, SOUTH, EAST, WEST, UP, DOWN
    }

    /** The rotation axis of an element rotation entry. */
    enum Axis {
        X, Y, Z
    }

    /**
     * A vanilla element rotation: a right-handed rotation of {@code angle} degrees about
     * {@code axis} through {@code origin} (model units). Modern vanilla (1.21.6+) accepts any
     * angle; older clients restricted it to 22.5-degree steps between -45 and 45.
     * {@code rescale} scales the two axes perpendicular to the rotation axis by
     * {@code 1 / cos(angle)} so a rotated full-size element keeps covering its block face
     * (vanilla {@code FaceBakery.applyElementRotation} semantics).
     */
    record Rotation(float angle, Axis axis, float originX, float originY, float originZ, boolean rescale) {

        /** True when the entry actually moves geometry (a declared angle-0 entry is a no-op). */
        boolean isActive() {
            return angle != 0;
        }
    }

    /** True when the element declares a rotation that actually moves geometry. */
    boolean hasActiveRotation() {
        return rotation != null && rotation.isActive();
    }

    /**
     * One face of an element.
     *
     * @param uv         explicit texture box in 16-based texture units, or null to derive the
     *                   vanilla default projection of the element bounds; reversed coordinates
     *                   (u1 &gt; u2 or v1 &gt; v2) mirror the sampled texture
     * @param textureRef the raw texture reference: {@code #key} into the model's texture map, or
     *                   a direct resource location
     * @param rotation   texture rotation within the face, clockwise degrees: 0, 90, 180 or 270
     * @param tintIndex  index into the item definition leaf's tint list, or -1 for untinted
     */
    record Face(@Nullable FaceUv uv, String textureRef, int rotation, int tintIndex) {
    }

    /** An explicit face uv box, 16-based texture units regardless of texture pixel size. */
    record FaceUv(float u1, float v1, float u2, float v2) {
    }
}
