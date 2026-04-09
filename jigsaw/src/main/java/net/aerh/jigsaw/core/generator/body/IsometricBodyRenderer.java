package net.aerh.jigsaw.core.generator.body;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Renders an isometric 3D player body from a Minecraft skin texture.
 *
 * <p>Generalizes the head-only {@link net.aerh.jigsaw.core.generator.skull.IsometricSkullRenderer}
 * to render all six body parts (head, torso, arms, legs) with overlay layers and optional
 * 3D armor. Supports both classic (4px arms) and slim/Alex (3px arms) skin models.
 *
 * <p>Each body part is a 3D cuboid with its own dimensions and position. All cuboids are
 * projected using rotation matrices, depth-sorted globally, and rendered back-to-front
 * with per-face shading. The output is downscaled for anti-aliasing.
 */
public final class IsometricBodyRenderer {

    // Shadow intensity per face direction (0-255, higher = brighter)
    private static final int SHADOW_FRONT = 111;
    private static final int SHADOW_RIGHT = 156;
    private static final int SHADOW_BACK = 156;
    private static final int SHADOW_LEFT = 162;
    private static final int SHADOW_TOP = 255;
    private static final int SHADOW_BOTTOM = 111;

    private IsometricBodyRenderer() {}

    /**
     * Renders an isometric 3D player body with default rotation angles.
     *
     * @param skin       the 64x64 Minecraft skin texture
     * @param model      the skin model (classic or slim)
     * @param armorPieces armor pieces to render on top of the body (may be empty)
     * @return the rendered isometric body image, downscaled for anti-aliasing
     */
    public static BufferedImage render(BufferedImage skin, SkinModel model, List<ArmorPiece> armorPieces) {
        return render(skin, model,
                PlayerBodySettings.DEFAULT_X_ROTATION,
                PlayerBodySettings.DEFAULT_Y_ROTATION,
                PlayerBodySettings.DEFAULT_Z_ROTATION,
                armorPieces);
    }

    /**
     * Renders an isometric 3D player body with custom rotation angles.
     *
     * @param skin       the 64x64 Minecraft skin texture
     * @param model      the skin model (classic or slim)
     * @param xRotation  rotation around the X axis (pitch) in radians
     * @param yRotation  rotation around the Y axis (yaw) in radians
     * @param zRotation  rotation around the Z axis (roll) in radians
     * @param armorPieces armor pieces to render on top of the body (may be empty)
     * @return the rendered isometric body image, downscaled for anti-aliasing
     */
    public static BufferedImage render(
            BufferedImage skin, SkinModel model,
            double xRotation, double yRotation, double zRotation,
            List<ArmorPiece> armorPieces) {

        skin = handleTransparency(skin);

        // Build all vertices and faces
        List<double[]> vertexList = new ArrayList<>();
        List<RenderFace> faceList = new ArrayList<>();

        List<BodyPart.PartWithGeometry> parts = BodyPart.allParts(model);

        for (BodyPart.PartWithGeometry pwg : parts) {
            BodyPart.Geometry geom = pwg.geometry();

            // Base layer
            addCuboid(vertexList, faceList, geom, 0, skin,
                    geom.baseUvX(), geom.baseUvY(),
                    geom.pixelWidth(), geom.pixelHeight(), geom.pixelDepth(), false);

            // Overlay layer (slightly inflated)
            addCuboid(vertexList, faceList, geom, PlayerBodySettings.OVERLAY_INFLATION, skin,
                    geom.overlayUvX(), geom.overlayUvY(),
                    geom.pixelWidth(), geom.pixelHeight(), geom.pixelDepth(), false);
        }

        // Armor layers
        for (ArmorPiece armor : armorPieces) {
            List<ArmorLayer.ArmorMapping> mappings = ArmorLayer.mappingsFor(armor.slot(), model);
            for (ArmorLayer.ArmorMapping mapping : mappings) {
                BodyPart.Geometry bodyGeom = mapping.bodyPart().geometry(model);
                addCuboid(vertexList, faceList, bodyGeom, mapping.inflation(), armor.armorTexture(),
                        mapping.uvX(), mapping.uvY(),
                        mapping.pixelWidth(), mapping.pixelHeight(), mapping.pixelDepth(),
                        mapping.mirrored());
            }
        }

        // Transform vertices
        double[][] vertices = vertexList.toArray(new double[0][]);
        double[][] transformed = rotateVertices(vertices,
                xRotation, yRotation, zRotation,
                PlayerBodySettings.DEFAULT_RENDER_SCALE,
                PlayerBodySettings.DEFAULT_WIDTH, PlayerBodySettings.DEFAULT_HEIGHT);

        // Depth-sort faces
        int[][] faceOrder = calculateRenderOrder(transformed, faceList);

        // Render
        BufferedImage image = new BufferedImage(
                PlayerBodySettings.DEFAULT_WIDTH,
                PlayerBodySettings.DEFAULT_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        drawFaces(g2d, transformed, faceList, faceOrder);

        g2d.dispose();

        return downscale(image);
    }

    // -------------------------------------------------------------------------
    // Cuboid building
    // -------------------------------------------------------------------------

    /**
     * Adds 8 vertices and 6 faces for a cuboid to the vertex/face lists.
     *
     * @param vertices   accumulating vertex list
     * @param faces      accumulating face list
     * @param geom       body part geometry (provides position offset)
     * @param inflation  amount to inflate each half-extent (0 for base layer)
     * @param texture    the texture to sample from
     * @param uvOriginX  UV origin x on the texture
     * @param uvOriginY  UV origin y on the texture
     * @param pixelW     cuboid width in pixels (for UV computation)
     * @param pixelH     cuboid height in pixels
     * @param pixelD     cuboid depth in pixels
     * @param mirrored   whether to mirror the texture horizontally
     */
    private static void addCuboid(
            List<double[]> vertices, List<RenderFace> faces,
            BodyPart.Geometry geom, double inflation, BufferedImage texture,
            int uvOriginX, int uvOriginY, int pixelW, int pixelH, int pixelD,
            boolean mirrored) {

        int baseIndex = vertices.size();

        double hx = geom.halfExtentX() + inflation;
        double hy = geom.halfExtentY() + inflation;
        double hz = geom.halfExtentZ() + inflation;
        double ox = geom.offsetX();
        double oy = geom.offsetY();
        double oz = geom.offsetZ();

        // 8 vertices in the same order as PlayerSkullSettings.COORDINATES
        vertices.add(new double[]{ox + hx, oy + hy, oz - hz, 1});  // 0: right, bottom, front
        vertices.add(new double[]{ox + hx, oy + hy, oz + hz, 1});  // 1: right, bottom, back
        vertices.add(new double[]{ox - hx, oy + hy, oz + hz, 1});  // 2: left, bottom, back
        vertices.add(new double[]{ox - hx, oy + hy, oz - hz, 1});  // 3: left, bottom, front
        vertices.add(new double[]{ox + hx, oy - hy, oz - hz, 1});  // 4: right, top, front
        vertices.add(new double[]{ox + hx, oy - hy, oz + hz, 1});  // 5: right, top, back
        vertices.add(new double[]{ox - hx, oy - hy, oz + hz, 1});  // 6: left, top, back
        vertices.add(new double[]{ox - hx, oy - hy, oz - hz, 1});  // 7: left, top, front

        int b = baseIndex;
        int w = pixelW;
        int h = pixelH;
        int d = pixelD;

        // UV origins for each face (derived from the standard Minecraft skin layout)
        // Front: (uvOriginX + d, uvOriginY + d), size: w x h
        // Right: (uvOriginX + d + w, uvOriginY + d), size: d x h
        // Back:  (uvOriginX + d + w + d, uvOriginY + d), size: w x h
        // Left:  (uvOriginX, uvOriginY + d), size: d x h
        // Top:   (uvOriginX + d, uvOriginY), size: w x d
        // Bottom:(uvOriginX + d + w, uvOriginY), size: w x d

        if (mirrored) {
            // For mirrored (left-side) pieces, swap left/right faces and flip UV x within each face
            faces.add(new RenderFace(new int[]{b + 7, b + 4, b + 0, b + 3}, SHADOW_FRONT,
                    uvOriginX + d, uvOriginY + d, w, h, texture, true));
            faces.add(new RenderFace(new int[]{b + 4, b + 5, b + 1, b + 0}, SHADOW_RIGHT,
                    uvOriginX, uvOriginY + d, d, h, texture, true));
            faces.add(new RenderFace(new int[]{b + 5, b + 6, b + 2, b + 1}, SHADOW_BACK,
                    uvOriginX + d + w + d, uvOriginY + d, w, h, texture, true));
            faces.add(new RenderFace(new int[]{b + 6, b + 7, b + 3, b + 2}, SHADOW_LEFT,
                    uvOriginX + d + w, uvOriginY + d, d, h, texture, true));
            faces.add(new RenderFace(new int[]{b + 6, b + 5, b + 4, b + 7}, SHADOW_TOP,
                    uvOriginX + d, uvOriginY, w, d, texture, true));
            faces.add(new RenderFace(new int[]{b + 2, b + 1, b + 0, b + 3}, SHADOW_BOTTOM,
                    uvOriginX + d + w, uvOriginY, w, d, texture, true));
        } else {
            faces.add(new RenderFace(new int[]{b + 7, b + 4, b + 0, b + 3}, SHADOW_FRONT,
                    uvOriginX + d, uvOriginY + d, w, h, texture, false));
            faces.add(new RenderFace(new int[]{b + 4, b + 5, b + 1, b + 0}, SHADOW_RIGHT,
                    uvOriginX + d + w, uvOriginY + d, d, h, texture, false));
            faces.add(new RenderFace(new int[]{b + 5, b + 6, b + 2, b + 1}, SHADOW_BACK,
                    uvOriginX + d + w + d, uvOriginY + d, w, h, texture, false));
            faces.add(new RenderFace(new int[]{b + 6, b + 7, b + 3, b + 2}, SHADOW_LEFT,
                    uvOriginX, uvOriginY + d, d, h, texture, false));
            faces.add(new RenderFace(new int[]{b + 6, b + 5, b + 4, b + 7}, SHADOW_TOP,
                    uvOriginX + d, uvOriginY, w, d, texture, false));
            faces.add(new RenderFace(new int[]{b + 2, b + 1, b + 0, b + 3}, SHADOW_BOTTOM,
                    uvOriginX + d + w, uvOriginY, w, d, texture, false));
        }
    }

    // -------------------------------------------------------------------------
    // 3D projection
    // -------------------------------------------------------------------------

    private static double[][] rotateVertices(
            double[][] vertices,
            double xRotation, double yRotation, double zRotation,
            int renderScale, int imageWidth, int imageHeight) {

        double[][] zRot = {
                {Math.cos(zRotation), -Math.sin(zRotation), 0, 0},
                {Math.sin(zRotation),  Math.cos(zRotation), 0, 0},
                {0, 0, 1, 0},
                {0, 0, 0, 1}
        };
        double[][] yRot = {
                { Math.cos(yRotation), 0, Math.sin(yRotation), 0},
                {0, 1, 0, 0},
                {-Math.sin(yRotation), 0, Math.cos(yRotation), 0},
                {0, 0, 0, 1}
        };
        double[][] xRot = {
                {1, 0, 0, 0},
                {0, Math.cos(xRotation), -Math.sin(xRotation), 0},
                {0, Math.sin(xRotation),  Math.cos(xRotation), 0},
                {0, 0, 0, 1}
        };
        double[][] scale = {
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

        double[][] result = new double[vertices.length][4];
        for (int i = 0; i < vertices.length; i++) {
            result[i] = multiplyMatrix(zRot, vertices[i]);
            result[i] = multiplyMatrix(yRot, result[i]);
            result[i] = multiplyMatrix(xRot, result[i]);
            result[i] = multiplyMatrix(scale, result[i]);
            result[i] = multiplyMatrix(offset, result[i]);
        }
        return result;
    }

    private static int[][] calculateRenderOrder(double[][] vertices, List<RenderFace> faces) {
        int[][] order = new int[faces.size()][1];
        double[] depths = new double[faces.size()];

        for (int i = 0; i < faces.size(); i++) {
            order[i][0] = i;
            double average = 0;
            int[] verts = faces.get(i).vertexIndices();
            for (int point : verts) {
                average += vertices[point][2];
            }
            depths[i] = average / 4.0;
        }

        // Sort back-to-front (highest Z first since further away = higher Z)
        Integer[] indices = new Integer[faces.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(depths[b], depths[a]));

        int[][] sorted = new int[faces.size()][1];
        for (int i = 0; i < indices.length; i++) {
            sorted[i][0] = indices[i];
        }
        return sorted;
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

    private static void drawFaces(Graphics2D g2d, double[][] vertices,
                                   List<RenderFace> faces, int[][] faceOrder) {
        for (int[] order : faceOrder) {
            int faceIndex = order[0];
            RenderFace face = faces.get(faceIndex);

            int[] verts = face.vertexIndices();
            double[] v1 = vertices[verts[0]];
            double[] v2 = vertices[verts[1]];
            double[] v3 = vertices[verts[2]];
            double[] v4 = vertices[verts[3]];

            int faceW = face.uvWidth();
            int faceH = face.uvHeight();

            if (faceW == 0 || faceH == 0) continue;

            double[][] displacement = {
                    {(v1[0] - v2[0]) / faceW, (v1[1] - v2[1]) / faceW},
                    {(v2[0] - v3[0]) / faceH, (v2[1] - v3[1]) / faceH},
                    {(v3[0] - v4[0]) / faceW, (v3[1] - v4[1]) / faceW}
            };
            double xStep = (v4[0] - v1[0]) / faceH;

            int uvFaceX = face.uvX();
            int uvFaceY = face.uvY();
            float shadow = face.shadow() / 255f;
            BufferedImage texture = face.texture();
            boolean mirrored = face.mirrored();

            for (int y = 0; y < faceH; y++) {
                for (int x = 0; x < faceW; x++) {
                    int texX = mirrored ? (faceW - 1 - x) + uvFaceX : x + uvFaceX;
                    int texY = y + uvFaceY;

                    // Bounds check for safety
                    if (texX < 0 || texX >= texture.getWidth() || texY < 0 || texY >= texture.getHeight()) {
                        continue;
                    }

                    int color = texture.getRGB(texX, texY);
                    int alpha = (color >> 24) & 0xFF;
                    if (alpha == 0) continue;

                    int red = Math.round(((color >> 16) & 0xFF) * shadow);
                    int green = Math.round(((color >> 8) & 0xFF) * shadow);
                    int blue = Math.round((color & 0xFF) * shadow);
                    g2d.setColor(new Color(red, green, blue, alpha));

                    double xCoord = v1[0] - displacement[0][0] * x + xStep * y;
                    double yCoord = v1[1] - displacement[0][1] * x - displacement[1][1] * y;

                    int[] pointsX = new int[4];
                    int[] pointsY = new int[4];

                    pointsX[0] = (int) Math.round(xCoord);
                    pointsY[0] = (int) Math.round(yCoord);
                    for (int i = 0; i < 3; i++) {
                        xCoord -= displacement[i][0];
                        yCoord -= displacement[i][1];
                        pointsX[i + 1] = (int) Math.round(xCoord);
                        pointsY[i + 1] = (int) Math.round(yCoord);
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
            BufferedImage copy = new BufferedImage(skin.getWidth(), skin.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(skin, 0, 0, null);
            g.dispose();

            for (int y = 0; y < skin.getHeight(); y++) {
                for (int x = 0; x < skin.getWidth(); x++) {
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
        int newWidth = image.getWidth() / PlayerBodySettings.BODY_SCALE_DOWN;
        int newHeight = image.getHeight() / PlayerBodySettings.BODY_SCALE_DOWN;
        Image rescaled = image.getScaledInstance(newWidth, newHeight, Image.SCALE_AREA_AVERAGING);

        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(rescaled, 0, 0, null);
        g.dispose();
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal data
    // -------------------------------------------------------------------------

    /**
     * Internal record representing a face to render, with texture and UV mapping information.
     */
    private record RenderFace(
            int[] vertexIndices,
            int shadow,
            int uvX, int uvY,
            int uvWidth, int uvHeight,
            BufferedImage texture,
            boolean mirrored
    ) {}
}
