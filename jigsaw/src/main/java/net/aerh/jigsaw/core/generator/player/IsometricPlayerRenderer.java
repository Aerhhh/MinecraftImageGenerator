package net.aerh.jigsaw.core.generator.player;

import net.aerh.jigsaw.core.util.ColorUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Renders an isometric 3D player model from a Minecraft skin texture.
 *
 * <p>Builds cuboid geometry for all six body parts (head, body, both arms, both legs),
 * applies overlay layers (hat, jacket, sleeves, pants), renders optional armor on top,
 * then projects everything using rotation matrices, depth-sorts all faces globally,
 * and paints them back-to-front with per-face directional shading.
 *
 * <p>Supports both classic (4px-wide arms) and slim/Alex (3px-wide arms) skin models.
 * Armor pieces can be tinted for leather dye support, with a separate overlay texture
 * composited on top for non-dyeable trim details.
 *
 * <p>Rendering pipeline:
 * <ol>
 *   <li>Handle legacy transparency markers in the skin texture</li>
 *   <li>Build cuboid vertices and faces for each body part (base skin + overlay)</li>
 *   <li>Build armor cuboids if equipped (with appropriate inflation)</li>
 *   <li>Project all vertices through the isometric transformation pipeline</li>
 *   <li>Depth-sort all faces globally by average projected Z</li>
 *   <li>Render each face as a grid of textured quads with directional shading</li>
 *   <li>Downscale for anti-aliasing and autocrop to content bounds</li>
 * </ol>
 */
public final class IsometricPlayerRenderer {

    private IsometricPlayerRenderer() {}

    /**
     * Renders the player model with no armor.
     *
     * @param skin 64x64 (or 64x32) Minecraft skin texture
     * @param slim true for slim (Alex) arms, false for classic (Steve)
     * @return rendered isometric image, downscaled and autocropped
     */
    public static BufferedImage render(BufferedImage skin, boolean slim) {
        return render(skin, slim, null, null);
    }

    /**
     * Renders the player model with optional armor.
     *
     * @param skin          64x64 (or 64x32) Minecraft skin texture
     * @param slim          true for slim (Alex) arms, false for classic (Steve)
     * @param armor         armor set to render, or null for no armor
     * @param armorTextures armor texture loader, required if armor is non-null
     * @return rendered isometric image, downscaled and autocropped
     */
    public static BufferedImage render(BufferedImage skin, boolean slim,
                                       ArmorSet armor, ArmorTexture armorTextures) {
        skin = handleTransparency(skin);

        List<double[]> allVertices = new ArrayList<>();
        List<FaceData> allFaces = new ArrayList<>();

        // -- Base skin + overlay layers --
        for (BodyPart part : BodyPart.values()) {
            buildCuboid(allVertices, allFaces, part, slim, 0,
                    skin, part.baseUvX(), part.baseUvY(),
                    part.width(slim), part.height(), part.depth(),
                    false, -1);

            buildCuboid(allVertices, allFaces, part, slim,
                    PlayerModelSettings.OVERLAY_INFLATION,
                    skin, part.overlayUvX(), part.overlayUvY(),
                    part.width(slim), part.height(), part.depth(),
                    false, -1);
        }

        // -- Armor layers --
        if (armor != null && armorTextures != null) {
            buildArmorCuboids(allVertices, allFaces, armor, armorTextures, slim);
        }

        // -- Project --
        double[][] projected = IsometricProjection.project(
                allVertices.toArray(new double[0][]),
                PlayerModelSettings.DEFAULT_X_ROTATION,
                PlayerModelSettings.DEFAULT_Y_ROTATION,
                PlayerModelSettings.DEFAULT_Z_ROTATION,
                PlayerModelSettings.RENDER_SCALE,
                PlayerModelSettings.CANVAS_WIDTH,
                PlayerModelSettings.CANVAS_HEIGHT);

        // -- Depth sort --
        int[][] faceVerts = allFaces.stream()
                .map(FaceData::vertexIndices)
                .toArray(int[][]::new);
        int[] order = IsometricProjection.depthSort(projected, faceVerts);

        // -- Render --
        BufferedImage canvas = new BufferedImage(
                PlayerModelSettings.CANVAS_WIDTH,
                PlayerModelSettings.CANVAS_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();
        paintFaces(g2d, projected, allFaces, order);
        g2d.dispose();

        return autocrop(downscale(canvas));
    }

    // =========================================================================
    // Armor
    // =========================================================================

    private static void buildArmorCuboids(List<double[]> vertices, List<FaceData> faces,
                                           ArmorSet armor, ArmorTexture armorTextures,
                                           boolean slim) {
        for (ArmorPiece piece : ArmorPiece.values()) {
            Optional<String> material = armor.materialFor(piece);
            if (material.isEmpty()) continue;

            int layer = piece.textureLayer();
            Optional<BufferedImage> texture = armorTextures.loadComposited(
                    material.get(), layer, armor.leatherDyeColor());
            if (texture.isEmpty()) continue;

            BufferedImage tex = texture.get();

            for (BodyPart part : piece.coveredParts()) {
                boolean mirrored = piece.isMirrored(part);
                // Armor always uses 4px arm width (non-slim) for UV computation
                int armorW = part.width(false);

                buildCuboid(vertices, faces, part, slim, piece.inflation(),
                        tex, part.baseUvX(), part.baseUvY(),
                        armorW, part.height(), part.depth(),
                        mirrored, -1);
            }
        }
    }

    // =========================================================================
    // Cuboid construction
    // =========================================================================

    /**
     * Adds 8 vertices and 6 faces for a textured cuboid.
     *
     * <p>Vertex layout (Y-down, -Z toward viewer):
     * <pre>
     *     6--------7
     *    /|       /|        +X = right
     *   5--------4 |        +Y = down
     *   | 2------|-3        -Z = toward viewer (front)
     *   |/       |/
     *   1--------0
     * </pre>
     */
    private static void buildCuboid(
            List<double[]> vertices, List<FaceData> faces,
            BodyPart part, boolean slim, double inflation,
            BufferedImage texture, int uvX, int uvY,
            int w, int h, int d,
            boolean mirrored, int tint) {

        int base = vertices.size();

        double hx = part.halfExtentX(slim) + inflation;
        double hy = part.halfExtentY() + inflation;
        double hz = part.halfExtentZ() + inflation;
        double ox = part.offsetX(slim);
        double oy = part.offsetY();
        double oz = part.offsetZ();

        // 8 vertices in homogeneous coordinates [x, y, z, 1]
        vertices.add(new double[]{ox + hx, oy + hy, oz - hz, 1}); // 0: right, bottom, front
        vertices.add(new double[]{ox + hx, oy + hy, oz + hz, 1}); // 1: right, bottom, back
        vertices.add(new double[]{ox - hx, oy + hy, oz + hz, 1}); // 2: left,  bottom, back
        vertices.add(new double[]{ox - hx, oy + hy, oz - hz, 1}); // 3: left,  bottom, front
        vertices.add(new double[]{ox + hx, oy - hy, oz - hz, 1}); // 4: right, top,    front
        vertices.add(new double[]{ox + hx, oy - hy, oz + hz, 1}); // 5: right, top,    back
        vertices.add(new double[]{ox - hx, oy - hy, oz + hz, 1}); // 6: left,  top,    back
        vertices.add(new double[]{ox - hx, oy - hy, oz - hz, 1}); // 7: left,  top,    front

        int b = base;

        // For mirrored pieces (left-side armor): swap left/right UV assignments
        // and enable horizontal flip during texture sampling
        if (mirrored) {
            addFace(faces, b, CuboidFace.FRONT, uvX + d, uvY + d, w, h, texture, true, tint);
            addFace(faces, b, CuboidFace.RIGHT, uvX, uvY + d, d, h, texture, true, tint);
            addFace(faces, b, CuboidFace.BACK, uvX + d + w + d, uvY + d, w, h, texture, true, tint);
            addFace(faces, b, CuboidFace.LEFT, uvX + d + w, uvY + d, d, h, texture, true, tint);
            addFace(faces, b, CuboidFace.TOP, uvX + d, uvY, w, d, texture, true, tint);
            addFace(faces, b, CuboidFace.BOTTOM, uvX + d + w, uvY, w, d, texture, true, tint);
        } else {
            addFace(faces, b, CuboidFace.FRONT, uvX + d, uvY + d, w, h, texture, false, tint);
            addFace(faces, b, CuboidFace.RIGHT, uvX + d + w, uvY + d, d, h, texture, false, tint);
            addFace(faces, b, CuboidFace.BACK, uvX + d + w + d, uvY + d, w, h, texture, false, tint);
            addFace(faces, b, CuboidFace.LEFT, uvX, uvY + d, d, h, texture, false, tint);
            addFace(faces, b, CuboidFace.TOP, uvX + d, uvY, w, d, texture, false, tint);
            addFace(faces, b, CuboidFace.BOTTOM, uvX + d + w, uvY, w, d, texture, false, tint);
        }
    }

    /** Returns the 4 vertex indices for a given face of a cuboid starting at base index. */
    private static int[] faceVertexIndices(int b, CuboidFace face) {
        return switch (face) {
            case FRONT  -> new int[]{b + 7, b + 4, b + 0, b + 3};
            case RIGHT  -> new int[]{b + 4, b + 5, b + 1, b + 0};
            case BACK   -> new int[]{b + 5, b + 6, b + 2, b + 1};
            case LEFT   -> new int[]{b + 6, b + 7, b + 3, b + 2};
            case TOP    -> new int[]{b + 6, b + 5, b + 4, b + 7};
            case BOTTOM -> new int[]{b + 2, b + 1, b + 0, b + 3};
        };
    }

    private static void addFace(List<FaceData> faces, int base, CuboidFace face,
                                 int uvX, int uvY, int uvW, int uvH,
                                 BufferedImage texture, boolean mirrored, int tint) {
        faces.add(new FaceData(
                faceVertexIndices(base, face),
                face.shadowIntensity(),
                uvX, uvY, uvW, uvH,
                texture, mirrored, tint));
    }

    // =========================================================================
    // Face rendering
    // =========================================================================

    /**
     * Renders all faces in depth-sorted order using textured quad rasterization.
     *
     * <p>Each face's projected quadrilateral is subdivided into a grid of texel-sized
     * quads. For each texel, the color is sampled from the face's texture, modulated
     * by directional shading and optional dye tint, then drawn as a filled polygon.
     */
    private static void paintFaces(Graphics2D g2d, double[][] projected,
                                    List<FaceData> faces, int[] order) {
        for (int idx : order) {
            FaceData face = faces.get(idx);
            int[] verts = face.vertexIndices();

            double[] v1 = projected[verts[0]];
            double[] v2 = projected[verts[1]];
            double[] v3 = projected[verts[2]];
            double[] v4 = projected[verts[3]];

            int faceW = face.uvW();
            int faceH = face.uvH();
            if (faceW == 0 || faceH == 0) continue;

            // Edge displacement vectors for texel quad interpolation
            double d0x = (v1[0] - v2[0]) / faceW;
            double d0y = (v1[1] - v2[1]) / faceW;
            double d1x = (v2[0] - v3[0]) / faceH;
            double d1y = (v2[1] - v3[1]) / faceH;
            double d2x = (v3[0] - v4[0]) / faceW;
            double d2y = (v3[1] - v4[1]) / faceW;
            double rowStepX = (v4[0] - v1[0]) / faceH;

            float shadow = face.shade() / 255f;
            BufferedImage texture = face.texture();
            boolean mirrored = face.mirrored();
            int tintColor = face.tint();

            float tintR = 1f, tintG = 1f, tintB = 1f;
            if (tintColor != -1) {
                float[] rgb = ColorUtil.extractTintRgb(tintColor);
                tintR = rgb[0];
                tintG = rgb[1];
                tintB = rgb[2];
            }

            int uvBaseX = face.uvX();
            int uvBaseY = face.uvY();

            for (int ty = 0; ty < faceH; ty++) {
                for (int tx = 0; tx < faceW; tx++) {
                    int texX = mirrored ? uvBaseX + (faceW - 1 - tx) : uvBaseX + tx;
                    int texY = uvBaseY + ty;

                    if (texX < 0 || texX >= texture.getWidth()
                            || texY < 0 || texY >= texture.getHeight()) {
                        continue;
                    }

                    int pixel = texture.getRGB(texX, texY);
                    int alpha = (pixel >> 24) & 0xFF;
                    if (alpha == 0) continue;

                    int r = ColorUtil.clamp(Math.round(((pixel >> 16) & 0xFF) * tintR * shadow));
                    int g = ColorUtil.clamp(Math.round(((pixel >>  8) & 0xFF) * tintG * shadow));
                    int b = ColorUtil.clamp(Math.round(( pixel        & 0xFF) * tintB * shadow));
                    g2d.setColor(new Color(r, g, b, alpha));

                    double px = v1[0] - d0x * tx + rowStepX * ty;
                    double py = v1[1] - d0y * tx - d1y * ty;

                    int[] polyX = new int[4];
                    int[] polyY = new int[4];

                    polyX[0] = (int) Math.round(px);
                    polyY[0] = (int) Math.round(py);
                    px -= d0x; py -= d0y;
                    polyX[1] = (int) Math.round(px);
                    polyY[1] = (int) Math.round(py);
                    px -= d1x; py -= d1y;
                    polyX[2] = (int) Math.round(px);
                    polyY[2] = (int) Math.round(py);
                    px -= d2x; py -= d2y;
                    polyX[3] = (int) Math.round(px);
                    polyY[3] = (int) Math.round(py);

                    g2d.drawPolygon(polyX, polyY, 4);
                    g2d.fillPolygon(polyX, polyY, 4);
                }
            }
        }
    }

    // =========================================================================
    // Post-processing
    // =========================================================================

    /** Handles legacy skin transparency markers at pixel (32,0). */
    private static BufferedImage handleTransparency(BufferedImage skin) {
        int marker = skin.getRGB(32, 0);
        if ((marker << 8) == 0) return skin;

        BufferedImage copy = new BufferedImage(
                skin.getWidth(), skin.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(skin, 0, 0, null);
        g.dispose();

        for (int y = 0; y < copy.getHeight(); y++) {
            for (int x = 0; x < copy.getWidth(); x++) {
                if (copy.getRGB(x, y) == marker) {
                    copy.setRGB(x, y, 0);
                }
            }
        }
        return copy;
    }

    /** Autocrop to bounding box of non-transparent pixels with 4px padding. */
    private static BufferedImage autocrop(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int minX = w, minY = h, maxX = 0, maxY = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((image.getRGB(x, y) >> 24) & 0xFF) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX) return image;

        int pad = 4;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad);
        maxY = Math.min(h - 1, maxY + pad);

        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Downscale by configured factor using area averaging for anti-aliasing. */
    private static BufferedImage downscale(BufferedImage image) {
        int f = PlayerModelSettings.DOWNSCALE_FACTOR;
        int nw = image.getWidth() / f;
        int nh = image.getHeight() / f;
        Image scaled = image.getScaledInstance(nw, nh, Image.SCALE_AREA_AVERAGING);

        BufferedImage result = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return result;
    }

    // =========================================================================
    // Internal data
    // =========================================================================

    private record FaceData(
            int[] vertexIndices,
            int shade,
            int uvX, int uvY,
            int uvW, int uvH,
            BufferedImage texture,
            boolean mirrored,
            int tint
    ) {}
}
