package net.aerh.jigsaw.core.generator.skull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Renders an isometric 3D player head from a Minecraft skin texture.
 *
 * <p>Projects the head and hat layer cubes using rotation matrices, then draws each
 * face pixel-by-pixel with appropriate shadow values. Faces are rendered back-to-front
 * using a depth-sorted order.
 *
 * <p>The output is a high-resolution image that is then downscaled for anti-aliasing.
 */
public final class IsometricSkullRenderer {

    private static final double[][] DEFAULT_VERTEX_COORDINATES;
    private static final double[][] DEFAULT_FACE_ORDER;

    static {
        DEFAULT_VERTEX_COORDINATES = rotateVerticesAroundAxis(
            PlayerSkullSettings.DEFAULT_X_ROTATION,
            PlayerSkullSettings.DEFAULT_Y_ROTATION,
            PlayerSkullSettings.DEFAULT_Z_ROTATION,
            PlayerSkullSettings.DEFAULT_RENDER_SCALE,
            PlayerSkullSettings.DEFAULT_WIDTH,
            PlayerSkullSettings.DEFAULT_HEIGHT
        );
        DEFAULT_FACE_ORDER = calculateRenderOrder(DEFAULT_VERTEX_COORDINATES);
    }

    private IsometricSkullRenderer() {}

    /**
     * Renders an isometric 3D head from the given skin texture.
     *
     * <p>Handles transparency in the hat layer by checking for skin-specific transparency
     * colors, then renders all faces with shading and composites the result.
     *
     * @param skin the 64x64 (or 64x32) Minecraft skin texture
     * @return the rendered isometric head image, downscaled for anti-aliasing
     */
    public static BufferedImage render(BufferedImage skin) {
        // Handle transparency: some skins use a specific color at (32,0) for transparency
        skin = handleTransparency(skin);

        BufferedImage image = new BufferedImage(
            PlayerSkullSettings.DEFAULT_WIDTH,
            PlayerSkullSettings.DEFAULT_HEIGHT,
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g2d = image.createGraphics();

        drawHead(g2d, skin, DEFAULT_VERTEX_COORDINATES, DEFAULT_FACE_ORDER);

        g2d.dispose();

        return downscale(image);
    }

    // -------------------------------------------------------------------------
    // 3D projection
    // -------------------------------------------------------------------------

    private static double[][] rotateVerticesAroundAxis(
            double xRotation, double yRotation, double zRotation,
            int renderScale, int imageWidth, int imageHeight) {

        double[][] zRotations = {
            {Math.cos(zRotation), -Math.sin(zRotation), 0, 0},
            {Math.sin(zRotation),  Math.cos(zRotation), 0, 0},
            {0, 0, 1, 0},
            {0, 0, 0, 1}
        };
        double[][] yRotations = {
            { Math.cos(yRotation), 0, Math.sin(yRotation), 0},
            {0, 1, 0, 0},
            {-Math.sin(yRotation), 0, Math.cos(yRotation), 0},
            {0, 0, 0, 1}
        };
        double[][] xRotations = {
            {1, 0, 0, 0},
            {0, Math.cos(xRotation), -Math.sin(xRotation), 0},
            {0, Math.sin(xRotation),  Math.cos(xRotation), 0},
            {0, 0, 0, 1}
        };
        double[][] scaleFactor = {
            {renderScale, 0, 0, 0},
            {0, renderScale, 0, 0},
            {0, 0, 1, 0},
            {0, 0, 0, 1}
        };
        double[][] offset = {
            {1, 0, 0, imageWidth / 2.0},
            {0, 1, 0, imageHeight / 2.0},
            {0, 0, 1, 0},
            {0, 0, 0, 0}
        };

        double[][] shapeVertices = PlayerSkullSettings.COORDINATES;
        double[][] result = new double[shapeVertices.length][4];
        for (int i = 0; i < shapeVertices.length; i++) {
            result[i] = multiplyMatrix(zRotations, shapeVertices[i]);
            result[i] = multiplyMatrix(yRotations, result[i]);
            result[i] = multiplyMatrix(xRotations, result[i]);
            result[i] = multiplyMatrix(scaleFactor, result[i]);
            result[i] = multiplyMatrix(offset, result[i]);
        }

        return result;
    }

    private static double[][] calculateRenderOrder(double[][] vertices) {
        Face[] faces = PlayerSkullSettings.FACES;
        double[][] order = new double[faces.length][2];

        for (int i = 0; i < faces.length; i++) {
            double average = 0;
            Face currentFace = faces[i];
            for (int point : currentFace.faceVertices()) {
                average += vertices[point][2];
            }
            order[i] = new double[]{i, average / 4.0};
        }

        Arrays.sort(order, (face1, face2) -> Double.compare(face2[1], face1[1]));
        return order;
    }

    private static double[] multiplyMatrix(double[][] matrix, double[] vertexPos) {
        double[] result = new double[4];
        for (int row = 0; row < matrix.length; row++) {
            double cell = 0;
            for (int col = 0; col < vertexPos.length; col++) {
                cell += matrix[row][col] * vertexPos[col];
            }
            result[row] = cell;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Face rendering
    // -------------------------------------------------------------------------

    private static void drawHead(Graphics2D g2d, BufferedImage skin,
                                  double[][] vertexCoordinates, double[][] faceOrder) {
        for (int face = 0; face < PlayerSkullSettings.FACES.length; face++) {
            int faceIndex = (int) faceOrder[face][0];
            Face currentFace = PlayerSkullSettings.FACES[faceIndex];

            int[] facePoints = currentFace.faceVertices();
            double[] vertex1 = vertexCoordinates[facePoints[0]];
            double[] vertex2 = vertexCoordinates[facePoints[1]];
            double[] vertex3 = vertexCoordinates[facePoints[2]];
            double[] vertex4 = vertexCoordinates[facePoints[3]];

            double[][] startPointDisplacement = {
                {(vertex1[0] - vertex2[0]) / 8, (vertex1[1] - vertex2[1]) / 8},
                {(vertex2[0] - vertex3[0]) / 8, (vertex2[1] - vertex3[1]) / 8},
                {(vertex3[0] - vertex4[0]) / 8, (vertex3[1] - vertex4[1]) / 8}
            };
            double xOffset = (vertex4[0] - vertex1[0]) / 8;

            int uvFaceX = currentFace.startX();
            int uvFaceY = currentFace.startY();
            float shadow = currentFace.shadow() / 255f;

            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int color = skin.getRGB(x + uvFaceX, y + uvFaceY);
                    int alpha = (color >> 24) & 0xFF;
                    if (alpha == 0) {
                        continue;
                    }

                    int red = Math.round(((color >> 16) & 0xFF) * shadow);
                    int green = Math.round(((color >> 8) & 0xFF) * shadow);
                    int blue = Math.round((color & 0xFF) * shadow);
                    g2d.setColor(new Color((alpha << 24) | (red << 16) | (green << 8) | blue, true));

                    double xCoordinate = vertex1[0] - startPointDisplacement[0][0] * x + xOffset * y;
                    double yCoordinate = vertex1[1] - startPointDisplacement[0][1] * x - startPointDisplacement[1][1] * y;

                    int[] pointsX = new int[4];
                    int[] pointsY = new int[4];

                    pointsX[0] = (int) Math.round(xCoordinate);
                    pointsY[0] = (int) Math.round(yCoordinate);
                    for (int i = 0; i < 3; i++) {
                        xCoordinate -= startPointDisplacement[i][0];
                        yCoordinate -= startPointDisplacement[i][1];
                        pointsX[i + 1] = (int) Math.round(xCoordinate);
                        pointsY[i + 1] = (int) Math.round(yCoordinate);
                    }

                    g2d.drawPolygon(pointsX, pointsY, 4);
                    g2d.fillPolygon(pointsX, pointsY, 4);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static BufferedImage handleTransparency(BufferedImage skin) {
        int invisibilityColor = skin.getRGB(32, 0);
        int shifted = invisibilityColor << 8;

        if (shifted != 0) {
            // Copy to avoid mutating the original
            BufferedImage copy = new BufferedImage(skin.getWidth(), skin.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(skin, 0, 0, null);
            g.dispose();

            for (int y = 0; y < 32; y++) {
                for (int x = 32; x < 64; x++) {
                    if (invisibilityColor == copy.getRGB(x, y)) {
                        copy.setRGB(x, y, 0);
                    }
                }
            }
            return copy;
        }
        return skin;
    }

    private static BufferedImage downscale(BufferedImage image) {
        int newWidth = image.getWidth() / PlayerSkullSettings.HEAD_SCALE_DOWN;
        int newHeight = image.getHeight() / PlayerSkullSettings.HEAD_SCALE_DOWN;

        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return result;
    }
}
