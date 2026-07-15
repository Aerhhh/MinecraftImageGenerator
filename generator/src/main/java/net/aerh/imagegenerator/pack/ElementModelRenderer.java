package net.aerh.imagegenerator.pack;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.PackResolveException;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rasterizes elements-based item models for the gui display context as a flat front projection
 * (the viewer looks along -z; model x runs right, model y runs UP and is flipped to screen y).
 *
 * <p><b>Transform:</b> vanilla anchors the model-space [0, 16] box on the slot center and
 * applies the display transform about that center as scale, then rotation, then translation
 * (translation last), with translation clamped to +-80 model units and scale to +-4. One model
 * unit covers one GUI px at scale 1. Supported gui rotations: identity (|y| &lt;= 5 degrees with
 * x = z = 0, absorbing MCC's decorative 2-degree tilts) and the horizontal mirror (|y| within 5
 * degrees of 180 with x = z = 0). Anything else throws {@link PackResolveException}.
 *
 * <p><b>Painting:</b> each element contributes the face pointing toward the viewer after the
 * transform (south normally; north under a mirror or a negative z scale; an element with
 * neither renders nothing). Elements paint back-to-front by their transformed visible-face z
 * across ALL models of a composite (vanilla depth-tests the whole item into one buffer), ties
 * preserving composite-then-JSON order like vanilla draw order. Faces sample their texture
 * nearest-neighbor at pixel centers directly at the target resolution, so sub-GUI-px geometry
 * (0.75-unit quads) survives at any scale; face rotation turns the texture clockwise in 90
 * degree steps, reversed uv coordinates mirror it, and {@code tintindex} faces multiply by the
 * evaluated tint (absent tint entries multiply by white, a no-op).
 *
 * <p><b>Fidelity note:</b> deep 3D models render as layered front-projected quads with no
 * perspective or dimetric projection: acceptable for UI-flat packs (MCC interface quads,
 * Wynncraft near-flat stacks), wrong-but-defined for true 3D models. East/west/up/down faces
 * never render. {@code gui_light} is accepted but never shades: "front" is unshaded by
 * definition and viewer-facing flat faces receive full brightness under "side" as well.
 */
@Slf4j
@UtilityClass
class ElementModelRenderer {

    /** GUI px per slot box side; also model units per slot at display scale 1. */
    private static final int SLOT_UNITS = 16;
    /** Model-space center the display transform is applied about. */
    private static final double CENTER = 8.0;
    /** Face uv coordinates span 0..16 regardless of the texture's pixel size. */
    private static final double UV_UNITS = 16.0;
    private static final float TRANSLATION_LIMIT = 80.0f;
    private static final float SCALE_LIMIT = 4.0f;
    private static final double ROTATION_TOLERANCE_DEGREES = 5.0;

    /**
     * One resolved model to paint: elements plus the merged texture map, the inherited gui
     * transform and the item definition leaf's evaluated tints.
     */
    record ModelInstance(List<ModelElement> elements, Map<String, String> textures,
                         GuiTransform transform, List<Integer> tints) {
    }

    /** Loads a concrete (non-{@code #key}) texture reference; must fail loudly when missing. */
    interface TextureLookup {
        BufferedImage load(String textureRef);
    }

    /**
     * The rendered raster. Offsets position the image's top-left corner relative to the slot
     * box's top-left corner in canvas px; clipped renders are exactly the slot box at (0, 0).
     */
    record Raster(BufferedImage image, int offsetX, int offsetY) {
    }

    /** A face ready to paint: screen-space gui rect, z sort key and sampling parameters. */
    private record PaintFace(double leftGui, double topGui, double rightGui, double bottomGui,
                             double guiXAtFromX, double guiXAtToX, double guiYAtFromY, double guiYAtToY,
                             double z, ModelElement.Direction direction, ModelElement.Face face,
                             ModelElement element, ModelInstance model) {
    }

    /**
     * Renders the models into one canvas at {@code pixelsPerGuiPx}.
     *
     * @param models         resolved models; faces z-sort across the WHOLE composite (vanilla
     *                       depth-tests all models of an item into one buffer), ties keeping
     *                       composite-then-JSON order
     * @param pixelsPerGuiPx target resolution, canvas px per GUI px (and per model unit)
     * @param oversized      when false the canvas is the clipped 16-GUI-px slot box; when true
     *                       it expands to the union of the slot box and the art's extent
     * @param textures       texture loader (item-capped decode path)
     * @param context        item ref and pack id for error messages
     * @throws PackResolveException on a non-zero element rotation, an unsupported gui rotation,
     *                              or an unresolvable face texture reference
     */
    static Raster render(List<ModelInstance> models, int pixelsPerGuiPx, boolean oversized,
                         TextureLookup textures, String context) {
        if (pixelsPerGuiPx < 1) {
            throw new IllegalArgumentException("pixelsPerGuiPx must be at least 1, got: " + pixelsPerGuiPx);
        }
        List<PaintFace> faces = new ArrayList<>();
        for (ModelInstance model : models) {
            faces.addAll(collectPaintFaces(model, context));
        }
        // Back-to-front by transformed visible-face z across the whole composite: vanilla
        // renders every model of an item into one depth-tested buffer, so a nearer quad from an
        // earlier composite entry must still paint over a farther quad from a later one.
        // List.sort is stable, so composite order then JSON order breaks ties exactly like
        // vanilla draw order.
        faces.sort(Comparator.comparingDouble(PaintFace::z));
        double minGuiX = 0;
        double minGuiY = 0;
        double maxGuiX = SLOT_UNITS;
        double maxGuiY = SLOT_UNITS;
        if (oversized) {
            for (PaintFace face : faces) {
                minGuiX = Math.min(minGuiX, face.leftGui());
                minGuiY = Math.min(minGuiY, face.topGui());
                maxGuiX = Math.max(maxGuiX, face.rightGui());
                maxGuiY = Math.max(maxGuiY, face.bottomGui());
            }
        }

        int leftPx = oversized ? (int) Math.floor(minGuiX * pixelsPerGuiPx) : 0;
        int topPx = oversized ? (int) Math.floor(minGuiY * pixelsPerGuiPx) : 0;
        int rightPx = oversized ? (int) Math.ceil(maxGuiX * pixelsPerGuiPx) : SLOT_UNITS * pixelsPerGuiPx;
        int bottomPx = oversized ? (int) Math.ceil(maxGuiY * pixelsPerGuiPx) : SLOT_UNITS * pixelsPerGuiPx;

        BufferedImage canvas = new BufferedImage(rightPx - leftPx, bottomPx - topPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            for (PaintFace face : faces) {
                paintFace(graphics, face, pixelsPerGuiPx, leftPx, topPx, rightPx, bottomPx, textures, context);
            }
        } finally {
            graphics.dispose();
        }
        return new Raster(canvas, leftPx, topPx);
    }

    private static List<PaintFace> collectPaintFaces(ModelInstance model, String context) {
        GuiTransform transform = model.transform();
        boolean mirrored = isMirrored(transform, context);
        double translationX = clamp(transform.translationX(), TRANSLATION_LIMIT);
        double translationY = clamp(transform.translationY(), TRANSLATION_LIMIT);
        double translationZ = clamp(transform.translationZ(), TRANSLATION_LIMIT);
        double scaleX = clamp(transform.scaleX(), SCALE_LIMIT);
        double scaleY = clamp(transform.scaleY(), SCALE_LIMIT);
        double scaleZ = clamp(transform.scaleZ(), SCALE_LIMIT);
        // The (0,180,0) mirror negates model x and z on the way to the screen.
        double xSign = mirrored ? -1 : 1;
        double zSign = mirrored ? -1 : 1;

        List<PaintFace> faces = new ArrayList<>();
        for (ModelElement element : model.elements()) {
            if (element.rotationAngle() != 0) {
                throw new PackResolveException(
                    "Model element uses a non-zero rotation angle (%s degrees), which is unsupported for GUI rendering (%s)",
                    String.valueOf(element.rotationAngle()), context);
            }
            // The face whose outward normal points toward the viewer (+z) after the transform:
            // south (+z normal) unless the transform's z direction is negated by the mirror or a
            // negative z scale; a degenerate zero z scale keeps south.
            double normalSign = zSign * Math.signum(scaleZ);
            ModelElement.Direction visible = normalSign < 0 ? ModelElement.Direction.NORTH : ModelElement.Direction.SOUTH;
            ModelElement.Face face = element.faces().get(visible);
            if (face == null) {
                if (!element.faces().containsKey(ModelElement.Direction.NORTH)
                    && !element.faces().containsKey(ModelElement.Direction.SOUTH)) {
                    log.warn("Element with neither north nor south face contributes nothing to the GUI projection ({})",
                        context);
                }
                continue;
            }
            double guiXAtFromX = CENTER + translationX + xSign * scaleX * (element.fromX() - CENTER);
            double guiXAtToX = CENTER + translationX + xSign * scaleX * (element.toX() - CENTER);
            // Model y runs up; screen y runs down.
            double guiYAtFromY = CENTER - translationY - scaleY * (element.fromY() - CENTER);
            double guiYAtToY = CENTER - translationY - scaleY * (element.toY() - CENTER);
            double planeZ = visible == ModelElement.Direction.SOUTH ? element.toZ() : element.fromZ();
            double z = translationZ + zSign * scaleZ * (planeZ - CENTER);
            faces.add(new PaintFace(
                Math.min(guiXAtFromX, guiXAtToX), Math.min(guiYAtFromY, guiYAtToY),
                Math.max(guiXAtFromX, guiXAtToX), Math.max(guiYAtFromY, guiYAtToY),
                guiXAtFromX, guiXAtToX, guiYAtFromY, guiYAtToY,
                z, visible, face, element, model));
        }
        return faces;
    }

    /**
     * Classifies the gui rotation as identity or horizontal mirror, throwing on anything else.
     * Rotations within {@value #ROTATION_TOLERANCE_DEGREES} degrees of 0 or 180 about y (with
     * zero x and z) snap to the nearest supported case, absorbing MCC's decorative tilts.
     */
    private static boolean isMirrored(GuiTransform transform, String context) {
        if (transform.rotationX() == 0 && transform.rotationZ() == 0) {
            double y = Math.abs(transform.rotationY());
            if (y <= ROTATION_TOLERANCE_DEGREES) {
                return false;
            }
            if (Math.abs(y - 180) <= ROTATION_TOLERANCE_DEGREES) {
                return true;
            }
        }
        throw new PackResolveException(
            "Unsupported gui rotation [%s, %s, %s]: only identity and the (0, 180, 0) mirror render in the flat GUI projection (%s)",
            String.valueOf(transform.rotationX()), String.valueOf(transform.rotationY()),
            String.valueOf(transform.rotationZ()), context);
    }

    private static void paintFace(Graphics2D graphics, PaintFace paint, int pixelsPerGuiPx,
                                  int canvasLeftPx, int canvasTopPx, int canvasRightPx, int canvasBottomPx,
                                  TextureLookup textures, String context) {
        // Half-open pixel coverage by centers: pixel px is covered when left <= (px + 0.5) / s < right,
        // so adjacent faces sharing an edge never double-paint a pixel column.
        int startX = (int) Math.ceil(paint.leftGui() * pixelsPerGuiPx - 0.5);
        int endX = (int) Math.ceil(paint.rightGui() * pixelsPerGuiPx - 0.5);
        int startY = (int) Math.ceil(paint.topGui() * pixelsPerGuiPx - 0.5);
        int endY = (int) Math.ceil(paint.bottomGui() * pixelsPerGuiPx - 0.5);
        startX = Math.max(startX, canvasLeftPx);
        endX = Math.min(endX, canvasRightPx);
        startY = Math.max(startY, canvasTopPx);
        endY = Math.min(endY, canvasBottomPx);
        if (startX >= endX || startY >= endY) {
            return;
        }

        BufferedImage texture = textures.load(resolveTextureRef(paint.face().textureRef(), paint.model().textures(), context));
        ModelElement element = paint.element();
        ModelElement.FaceUv uv = paint.face().uv() != null ? paint.face().uv()
            : defaultUv(paint.direction(), element);
        int tint = tintFor(paint);

        // Signed spans: mirrored or reversed geometry simply flips the sign, and the fraction
        // math below stays correct.
        double guiSpanX = paint.guiXAtToX() - paint.guiXAtFromX();
        double guiSpanY = paint.guiYAtToY() - paint.guiYAtFromY();
        if (guiSpanX == 0 || guiSpanY == 0) {
            return;
        }

        BufferedImage faceRaster = new BufferedImage(endX - startX, endY - startY, BufferedImage.TYPE_INT_ARGB);
        for (int py = startY; py < endY; py++) {
            double centerGuiY = (py + 0.5) / pixelsPerGuiPx;
            // Fraction along model y from fromY to toY (bottom to top of the element).
            double b = (centerGuiY - paint.guiYAtFromY()) / guiSpanY;
            for (int px = startX; px < endX; px++) {
                double centerGuiX = (px + 0.5) / pixelsPerGuiPx;
                // Fraction along model x from fromX to toX.
                double a = (centerGuiX - paint.guiXAtFromX()) / guiSpanX;
                int argb = sampleTexel(texture, uv, paint.direction(), paint.face().rotation(), a, b);
                if ((argb >>> 24) != 0) {
                    faceRaster.setRGB(px - startX, py - startY, tinted(argb, tint));
                }
            }
        }
        graphics.drawImage(faceRaster, startX - canvasLeftPx, startY - canvasTopPx, null);
    }

    /**
     * Samples the face texture nearest-neighbor for the pixel at fractions ({@code a} along
     * model x from the element's fromX to toX, {@code b} along model y from fromY to toY).
     *
     * <p>The face's intrinsic screen axes are P (across the face as vanilla enumerates it: along
     * +x for south, along -x for north) and Q (top-down, so Q = 1 - b for both). Face rotation
     * then turns the texture clockwise in 90 degree steps by remapping (P, Q) to uv fractions.
     * Out-of-range uv values clamp to the texture's edge texels.
     */
    private static int sampleTexel(BufferedImage texture, ModelElement.FaceUv uv,
                                   ModelElement.Direction direction, int rotation, double a, double b) {
        double faceP = direction == ModelElement.Direction.SOUTH ? a : 1 - a;
        double faceQ = 1 - b;
        double p;
        double q;
        switch (rotation) {
            case 90 -> {
                p = faceQ;
                q = 1 - faceP;
            }
            case 180 -> {
                p = 1 - faceP;
                q = 1 - faceQ;
            }
            case 270 -> {
                p = 1 - faceQ;
                q = faceP;
            }
            default -> {
                p = faceP;
                q = faceQ;
            }
        }
        double u = uv.u1() + p * (uv.u2() - uv.u1());
        double v = uv.v1() + q * (uv.v2() - uv.v1());
        int texelX = Math.clamp((int) Math.floor(u / UV_UNITS * texture.getWidth()), 0, texture.getWidth() - 1);
        int texelY = Math.clamp((int) Math.floor(v / UV_UNITS * texture.getHeight()), 0, texture.getHeight() - 1);
        return texture.getRGB(texelX, texelY);
    }

    /**
     * The vanilla default uv projection of the element bounds: texture v runs opposite model y
     * (v increases downward), and the north face's u runs opposite model x (it is enumerated
     * from its own viewing direction).
     */
    private static ModelElement.FaceUv defaultUv(ModelElement.Direction direction, ModelElement element) {
        if (direction == ModelElement.Direction.SOUTH) {
            return new ModelElement.FaceUv(element.fromX(), SLOT_UNITS - element.toY(),
                element.toX(), SLOT_UNITS - element.fromY());
        }
        return new ModelElement.FaceUv(SLOT_UNITS - element.toX(), SLOT_UNITS - element.toY(),
            SLOT_UNITS - element.fromX(), SLOT_UNITS - element.fromY());
    }

    /** The evaluated tint for the face's tintindex; indexes past the tint list are white. */
    private static int tintFor(PaintFace paint) {
        int tintIndex = paint.face().tintIndex();
        List<Integer> tints = paint.model().tints();
        if (tintIndex < 0 || tintIndex >= tints.size()) {
            return 0xFFFFFF;
        }
        return tints.get(tintIndex);
    }

    /**
     * Multiplies the pixel's color channels by the tint, each product rounded to nearest
     * ({@code round(channel * tintChannel / 255)}), keeping alpha - the same multiplicative
     * convention as glyph tinting. Package-private so the flat sprite path tints layers with
     * identical arithmetic.
     */
    static int tinted(int argb, int tint) {
        if (tint == 0xFFFFFF) {
            return argb;
        }
        int red = Math.round(((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255.0f);
        int green = Math.round(((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255.0f);
        int blue = Math.round((argb & 0xFF) * (tint & 0xFF) / 255.0f);
        return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Resolves a face texture reference: {@code #key} indirections follow the merged texture
     * map (values may chain to further {@code #key}s); anything else is already concrete.
     */
    private static String resolveTextureRef(String textureRef, Map<String, String> textures, String context) {
        String current = textureRef;
        // The bound of map size + 1 hops makes reference cycles terminate loudly.
        for (int hops = 0; hops <= textures.size(); hops++) {
            if (!current.startsWith("#")) {
                return current;
            }
            String key = current.substring(1);
            String next = textures.get(key);
            if (next == null) {
                throw new PackResolveException(
                    "Element face references undefined texture key '#%s' (%s)", key, context);
            }
            current = next;
        }
        throw new PackResolveException(
            "Element face texture reference '%s' forms a cycle in the texture map (%s)", textureRef, context);
    }

    private static double clamp(float value, float limit) {
        return Math.clamp(value, -limit, limit);
    }
}
