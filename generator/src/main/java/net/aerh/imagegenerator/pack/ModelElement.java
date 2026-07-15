package net.aerh.imagegenerator.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One cuboid of an item model's {@code elements} list, in model units (a slot interior is 16
 * units; coordinates outside 0..16 are legal within the vanilla -16..32 bounds).
 *
 * <p>{@code rotationAngle} carries the element's declared rotation angle: 0 (including an
 * explicit no-op rotation entry) renders normally; any non-zero angle is unsupported this wave
 * and fails loudly at render time.
 *
 * @param rotationAngle the element rotation angle in degrees, 0 when absent or a declared no-op
 * @param faces         faces by direction; only NORTH and SOUTH ever rasterize (front projection)
 */
record ModelElement(float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                    float rotationAngle, Map<Direction, Face> faces) {

    enum Direction {
        NORTH, SOUTH, EAST, WEST, UP, DOWN
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
