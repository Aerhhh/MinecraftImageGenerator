package net.aerh.jigsaw.core.generator.player;

import java.util.Arrays;

/**
 * 3D projection mathematics for the isometric player model renderer.
 *
 * <p>Projects 3D model-space vertices through a rotation, scale, and translation
 * pipeline to produce 2D screen coordinates suitable for rendering. Also provides
 * depth sorting for back-to-front face rendering.
 *
 * <p>The transformation pipeline applies five 4x4 homogeneous matrices in sequence:
 * <ol>
 *   <li>Z-axis rotation (roll)</li>
 *   <li>Y-axis rotation (yaw) - primary view angle</li>
 *   <li>X-axis rotation (pitch) - vertical tilt</li>
 *   <li>Scale to canvas pixel space</li>
 *   <li>Translation to canvas center</li>
 * </ol>
 *
 * <p>The Z coordinate is preserved (not flattened) so it can be used for depth sorting.
 */
public final class IsometricProjection {

    private IsometricProjection() {}

    /**
     * Projects all vertices through the isometric transformation pipeline.
     *
     * @param vertices array of [x, y, z, 1] positions in model space
     * @param xRot     pitch angle in radians
     * @param yRot     yaw angle in radians
     * @param zRot     roll angle in radians
     * @param scale    pixels per renderer unit
     * @param canvasW  canvas width in pixels (for centering)
     * @param canvasH  canvas height in pixels (for centering)
     * @return array of projected [screenX, screenY, depth, w] positions
     */
    public static double[][] project(double[][] vertices,
                                      double xRot, double yRot, double zRot,
                                      int scale, int canvasW, int canvasH) {
        double cz = Math.cos(zRot), sz = Math.sin(zRot);
        double cy = Math.cos(yRot), sy = Math.sin(yRot);
        double cx = Math.cos(xRot), sx = Math.sin(xRot);

        double[][] rotZ = {
                { cz, -sz, 0, 0},
                { sz,  cz, 0, 0},
                {  0,   0, 1, 0},
                {  0,   0, 0, 1}
        };
        double[][] rotY = {
                { cy, 0,  sy, 0},
                {  0, 1,   0, 0},
                {-sy, 0,  cy, 0},
                {  0, 0,   0, 1}
        };
        double[][] rotX = {
                {1,  0,   0, 0},
                {0, cx, -sx, 0},
                {0, sx,  cx, 0},
                {0,  0,   0, 1}
        };
        double[][] scaleM = {
                {scale, 0, 0, 0},
                {0, scale, 0, 0},
                {0, 0, 1, 0},
                {0, 0, 0, 1}
        };
        double[][] offsetM = {
                {1, 0, 0, canvasW / 2.0},
                {0, 1, 0, canvasH / 2.0},
                {0, 0, 1, 0},
                {0, 0, 0, 0}
        };

        double[][] result = new double[vertices.length][];
        for (int i = 0; i < vertices.length; i++) {
            double[] v = vertices[i];
            v = matMul(rotZ, v);
            v = matMul(rotY, v);
            v = matMul(rotX, v);
            v = matMul(scaleM, v);
            v = matMul(offsetM, v);
            result[i] = v;
        }
        return result;
    }

    /**
     * Returns face indices sorted back-to-front by average projected Z coordinate.
     *
     * <p>Faces with higher average Z are further from the camera and should be
     * rendered first so that closer faces correctly occlude them.
     *
     * @param projected    projected vertex positions
     * @param faceVertices array of face definitions, each containing 4 vertex indices
     * @return face indices sorted back-to-front
     */
    public static int[] depthSort(double[][] projected, int[][] faceVertices) {
        int n = faceVertices.length;
        double[] depths = new double[n];
        Integer[] indices = new Integer[n];

        for (int i = 0; i < n; i++) {
            indices[i] = i;
            int[] verts = faceVertices[i];
            depths[i] = (projected[verts[0]][2] + projected[verts[1]][2]
                        + projected[verts[2]][2] + projected[verts[3]][2]) / 4.0;
        }

        Arrays.sort(indices, (a, b) -> Double.compare(depths[b], depths[a]));

        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = indices[i];
        }
        return order;
    }

    /**
     * 4x4 matrix multiplied by a 4x1 column vector.
     */
    static double[] matMul(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2] + m[0][3] * v[3],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2] + m[1][3] * v[3],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2] + m[2][3] * v[3],
                m[3][0] * v[0] + m[3][1] * v[1] + m[3][2] * v[2] + m[3][3] * v[3]
        };
    }
}
