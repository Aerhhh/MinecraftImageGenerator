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
 * Rasterizes elements-based item models for the gui display context. The viewer looks along -z
 * (model x runs right, model y runs UP and is flipped to screen y); larger transformed z is
 * nearer to the viewer.
 *
 * <p><b>Transform:</b> vanilla anchors the model-space [0, 16] box on the slot center and
 * applies the display transform about that center as scale, then rotation, then translation,
 * with translation clamped to +-80 model units and scale to +-4. One model unit covers one GUI
 * px at scale 1. Rotation semantics (verified against the decompiled vanilla client):
 * {@code ItemTransform.apply} builds the rotation with JOML
 * {@code Quaternionf.rotationXYZ(x, y, z)} - the matrix product Rx * Ry * Rz, so a vertex is
 * rotated about z first, then y, then x, each a right-handed rotation in degrees - and the
 * PoseStack receives translate, rotate, scale in that order, which applies to vertices as
 * scale, then rotation, then translation. Blockbench, the tool these packs are authored in,
 * applies the identical order (THREE.js Euler 'XYZ' with the same left-hand-only sign flips),
 * so previews and this renderer agree. Under the block-model staple [30, 225, 0] the up, north
 * and east faces are the visible three, north on the lower right.
 *
 * <p><b>Rotation classification:</b> rotations with x = z = 0 and |y| within 5 degrees of 0
 * snap to the identity, and within 5 degrees of 180 to the horizontal mirror (absorbing MCC's
 * decorative 2-degree tilts; flat art stays pixel-crisp). Models classified this way whose
 * elements carry no active rotation render through the exact flat fast paths, whose pixel
 * output is pinned by tests and must never change. Any other gui rotation renders through the
 * true orthographic pipeline when the render opts in via {@code fullRotations}, and throws
 * {@link PackResolveException} otherwise. Classified rotations keep their snapped angles even
 * when element rotations force them through the orthographic pipeline.
 *
 * <p><b>Orthographic pipeline:</b> each element's faces (all six orientations) transform as 3D
 * quads and project by dropping z. Because every transform here is affine, a projected face is
 * always a parallelogram; destination pixels map back through the face's inverse affine
 * transform to fractional face coordinates (half-open [0, 1) coverage at pixel centers, the
 * same center-sampling convention as the flat paths) and sample the texture nearest-neighbor,
 * so pixels stay crisp at any target scale. Back-face culling treats each element as a solid
 * cuboid: a face survives when its projected winding, corrected by the sign of the display
 * scale's determinant, faces the viewer - this matches the flat paths' pinned behavior where a
 * negative z scale shows the north face. Edge-on faces (zero projected area) never paint.
 *
 * <p><b>Painting order:</b> faces paint back-to-front by projected depth at the face center
 * across ALL models of a composite (vanilla depth-tests the whole item into one buffer), ties
 * preserving composite order, then element JSON order, then a fixed face order - the sort is
 * stable. For non-intersecting geometry (flat stacks, convex solids) this painter's algorithm
 * matches a depth test; mutually intersecting faces are approximated by their center depth.
 *
 * <p><b>Element rotations:</b> vanilla single-axis element rotations (any angle about x, y or z
 * through an origin - modern vanilla lifted the legacy 22.5-degree-step restriction in 1.21.6)
 * rotate the element's corners before the display transform; {@code rescale: true} scales the
 * two perpendicular axes by 1/cos(angle) after rotation, exactly like vanilla's
 * {@code FaceBakery.applyElementRotation}.
 *
 * <p><b>Shading:</b> {@code gui_light: front} renders unshaded. {@code gui_light: side} shades
 * each face in the orthographic pipeline by the vanilla per-orientation block-light constants -
 * up 1.0, north/south 0.8, east/west 0.6, down 0.5 ({@code Level.getShade}), the accepted flat
 * approximation of the two directional GL lights vanilla aims at side-lit GUI items. The shade
 * direction is the nearest axis of the element-rotated outward normal, with the dot products
 * compared in float precision like the client, so exactly-45-degree rotations reach the tie
 * (broken in vanilla's {@code Direction.getNearest} order: down, up, north, south, west, east);
 * the display rotation deliberately does not re-key it, matching vanilla world-shade semantics.
 * Elements declaring {@code shade: false} stay unshaded. The flat fast paths never shade
 * regardless of {@code gui_light} - their pixel output predates shading and is pinned.
 *
 * <p><b>Sampling:</b> faces sample their texture nearest-neighbor at pixel centers directly at
 * the target resolution, so sub-GUI-px geometry (0.75-unit quads) survives at any scale; face
 * rotation turns the texture clockwise in 90 degree steps, reversed uv coordinates mirror it,
 * and {@code tintindex} faces multiply by the evaluated tint (absent tint entries multiply by
 * white, a no-op).
 */
@Slf4j
@UtilityClass
class ElementModelRenderer {

    /**
     * Maximum elements one model may contribute to a GUI render. Real UI packs stack dozens of
     * quads per model (Wynncraft-class item skins run to a few hundred); 4096 leaves generous
     * headroom while bounding the per-face work a crafted many-element model can demand.
     * Renders over the cap fail with a loud {@link PackResolveException}.
     */
    static final int MAX_ELEMENTS_PER_MODEL = 4_096;

    /**
     * Maximum total pixel area one render may touch, in canvas px: the canvas allocation plus
     * every face's clipped paint rect (for orthographic faces, the clipped bounding box of the
     * projected parallelogram). Legitimate renders stay far below this - a fully oversized
     * 64-GUI-px canvas at 32 px per GUI px is about 4.2M px - while a crafted model (huge
     * element coordinates exploding the oversized canvas, or thousands of full-canvas faces)
     * fails with a loud {@link PackResolveException} instead of exhausting memory or CPU.
     * Checked BEFORE the canvas is allocated.
     */
    static final long MAX_PAINTED_AREA_PX = 64L * 1024 * 1024;

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
     * The model's {@code gui_light} mode: {@code FRONT} renders unshaded, {@code SIDE} applies
     * the fixed per-orientation shading in the orthographic pipeline (vanilla defaults absent
     * {@code gui_light} to side).
     */
    enum GuiLight {
        FRONT, SIDE
    }

    /**
     * One resolved model to paint: elements plus the merged texture map, the inherited gui
     * transform, the item definition leaf's evaluated tints and the inherited gui light mode.
     */
    record ModelInstance(List<ModelElement> elements, Map<String, String> textures,
                         GuiTransform transform, List<Integer> tints, GuiLight guiLight) {

        /** Convenience for flat renders and tests: {@code gui_light: front} (unshaded). */
        ModelInstance(List<ModelElement> elements, Map<String, String> textures,
                      GuiTransform transform, List<Integer> tints) {
            this(elements, textures, transform, tints, GuiLight.FRONT);
        }
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

    /**
     * A face ready to paint. {@code depth} is the painter sort key (larger is nearer); the gui
     * bounds feed oversized canvas sizing and clipped-coverage math.
     */
    private sealed interface Paintable permits FlatFace, QuadFace {

        double depth();

        double leftGui();

        double topGui();

        double rightGui();

        double bottomGui();
    }

    /**
     * A flat fast-path face: screen-space gui rect and axis-aligned sampling parameters. The
     * arithmetic consuming this record is pinned - it must stay byte-identical to the
     * pre-orthographic renderer.
     */
    private record FlatFace(double leftGui, double topGui, double rightGui, double bottomGui,
                            double guiXAtFromX, double guiXAtToX, double guiYAtFromY, double guiYAtToY,
                            double z, ModelElement.Direction direction, ModelElement.Face face,
                            ModelElement element, ModelInstance model) implements Paintable {

        @Override
        public double depth() {
            return z;
        }
    }

    /**
     * An orthographic face: the projected parallelogram (origin corner plus the projected U and
     * V edge vectors, U along the face's p axis and V along its q axis) and sampling
     * parameters. {@code shade} is the resolved gui-light multiplier (1 when unshaded).
     */
    private record QuadFace(double leftGui, double topGui, double rightGui, double bottomGui,
                            double originGuiX, double originGuiY,
                            double uGuiX, double uGuiY, double vGuiX, double vGuiY,
                            double depth, float shade,
                            ModelElement.Direction direction, ModelElement.Face face,
                            ModelElement element, ModelInstance model) implements Paintable {
    }

    /** A face's clipped pixel coverage on the canvas, half-open, in canvas-space px. */
    private record PaintRect(int startX, int startY, int endX, int endY) {

        long area() {
            return (long) (endX - startX) * (endY - startY);
        }
    }

    /** How a model's gui rotation renders. */
    private enum ViewClass {
        /** Identity (within tolerance): the exact flat front fast path. */
        FRONT,
        /** (0, 180, 0) (within tolerance): the exact flat mirror fast path. */
        MIRROR,
        /** Anything else: the orthographic pipeline (requires {@code fullRotations}). */
        FULL
    }

    /**
     * Renders the models into one canvas at {@code pixelsPerGuiPx} with strict rotation
     * handling (gui rotations beyond identity and the mirror throw). See the six-argument
     * overload for the full-rotation variant.
     */
    static Raster render(List<ModelInstance> models, int pixelsPerGuiPx, boolean oversized,
                         TextureLookup textures, String context) {
        return render(models, pixelsPerGuiPx, oversized, false, textures, context);
    }

    /**
     * Renders the models into one canvas at {@code pixelsPerGuiPx}.
     *
     * @param models         resolved models; faces depth-sort across the WHOLE composite
     *                       (vanilla depth-tests all models of an item into one buffer), ties
     *                       keeping composite-then-JSON order
     * @param pixelsPerGuiPx target resolution, canvas px per GUI px (and per model unit)
     * @param oversized      when false the canvas is the clipped 16-GUI-px slot box; when true
     *                       it expands to the union of the slot box and the art's extent
     *                       (rotated bounds included)
     * @param fullRotations  when true, gui rotations beyond identity and the mirror render
     *                       through the true orthographic projection instead of throwing (see
     *                       the class javadoc)
     * @param textures       texture loader (item-capped decode path)
     * @param context        item ref and pack id for error messages
     * @throws PackResolveException on an unsupported gui rotation (unless {@code fullRotations}
     *                              is set), an unresolvable face texture reference, a model over
     *                              {@link #MAX_ELEMENTS_PER_MODEL} or a render over
     *                              {@link #MAX_PAINTED_AREA_PX}
     */
    static Raster render(List<ModelInstance> models, int pixelsPerGuiPx, boolean oversized,
                         boolean fullRotations, TextureLookup textures, String context) {
        if (pixelsPerGuiPx < 1) {
            throw new IllegalArgumentException("pixelsPerGuiPx must be at least 1, got: " + pixelsPerGuiPx);
        }
        List<Paintable> faces = new ArrayList<>();
        for (ModelInstance model : models) {
            if (model.elements().size() > MAX_ELEMENTS_PER_MODEL) {
                throw new PackResolveException(
                    "Model declares %s elements, exceeding the %s-element GUI render cap (%s)",
                    String.valueOf(model.elements().size()), String.valueOf(MAX_ELEMENTS_PER_MODEL), context);
            }
            collectFaces(model, fullRotations, context, faces);
        }
        // Back-to-front by transformed depth across the whole composite: vanilla renders every
        // model of an item into one depth-tested buffer, so a nearer quad from an earlier
        // composite entry must still paint over a farther quad from a later one. List.sort is
        // stable, so composite order then JSON order breaks ties exactly like vanilla draw
        // order.
        faces.sort(Comparator.comparingDouble(Paintable::depth));
        double minGuiX = 0;
        double minGuiY = 0;
        double maxGuiX = SLOT_UNITS;
        double maxGuiY = SLOT_UNITS;
        if (oversized) {
            for (Paintable face : faces) {
                minGuiX = Math.min(minGuiX, face.leftGui());
                minGuiY = Math.min(minGuiY, face.topGui());
                maxGuiX = Math.max(maxGuiX, face.rightGui());
                maxGuiY = Math.max(maxGuiY, face.bottomGui());
            }
        }

        // Extreme extents saturate the int casts; the area cap below rejects them before any
        // allocation happens, so saturation never silently distorts a render that survives.
        int leftPx = oversized ? (int) Math.floor(minGuiX * pixelsPerGuiPx) : 0;
        int topPx = oversized ? (int) Math.floor(minGuiY * pixelsPerGuiPx) : 0;
        int rightPx = oversized ? (int) Math.ceil(maxGuiX * pixelsPerGuiPx) : SLOT_UNITS * pixelsPerGuiPx;
        int bottomPx = oversized ? (int) Math.ceil(maxGuiY * pixelsPerGuiPx) : SLOT_UNITS * pixelsPerGuiPx;

        // Total-area cap, checked BEFORE the canvas allocation: the canvas itself plus every
        // face's clipped paint rect. Width and height are bounded individually first so the
        // area products below cannot overflow long.
        long canvasWidth = (long) rightPx - leftPx;
        long canvasHeight = (long) bottomPx - topPx;
        if (canvasWidth > MAX_PAINTED_AREA_PX || canvasHeight > MAX_PAINTED_AREA_PX) {
            // Checked before the product so a saturated-int extent cannot overflow long.
            throw paintedAreaExceeded(context);
        }
        long paintedArea = canvasWidth * canvasHeight;
        if (paintedArea > MAX_PAINTED_AREA_PX) {
            throw paintedAreaExceeded(context);
        }
        List<PaintRect> rects = new ArrayList<>(faces.size());
        for (Paintable face : faces) {
            PaintRect rect = clippedCoverage(face, pixelsPerGuiPx, leftPx, topPx, rightPx, bottomPx);
            rects.add(rect);
            if (rect != null) {
                paintedArea += rect.area();
                if (paintedArea > MAX_PAINTED_AREA_PX) {
                    throw paintedAreaExceeded(context);
                }
            }
        }

        BufferedImage canvas = new BufferedImage(rightPx - leftPx, bottomPx - topPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            for (int i = 0; i < faces.size(); i++) {
                if (rects.get(i) == null) {
                    continue;
                }
                switch (faces.get(i)) {
                    case FlatFace flat ->
                        paintFlatFace(graphics, flat, rects.get(i), pixelsPerGuiPx, leftPx, topPx, textures, context);
                    case QuadFace quad ->
                        paintQuadFace(graphics, quad, rects.get(i), pixelsPerGuiPx, leftPx, topPx, textures, context);
                }
            }
        } finally {
            graphics.dispose();
        }
        return new Raster(canvas, leftPx, topPx);
    }

    private static PackResolveException paintedAreaExceeded(String context) {
        return new PackResolveException(
            "Rendering would paint more than %s canvas px; refusing the render (%s)",
            String.valueOf(MAX_PAINTED_AREA_PX), context);
    }

    /**
     * The face's clipped pixel coverage on the canvas, null when it covers no pixel. Half-open
     * pixel coverage by centers: pixel px is covered when
     * {@code left <= (px + 0.5) / scale < right}, so adjacent faces sharing an edge never
     * double-paint a pixel column. Orthographic faces clip to the bounding box of their
     * projected parallelogram; the per-pixel inverse mapping rejects box pixels outside the
     * face itself.
     */
    private static PaintRect clippedCoverage(Paintable paint, int pixelsPerGuiPx,
                                             int canvasLeftPx, int canvasTopPx, int canvasRightPx, int canvasBottomPx) {
        int startX = (int) Math.ceil(paint.leftGui() * pixelsPerGuiPx - 0.5);
        int endX = (int) Math.ceil(paint.rightGui() * pixelsPerGuiPx - 0.5);
        int startY = (int) Math.ceil(paint.topGui() * pixelsPerGuiPx - 0.5);
        int endY = (int) Math.ceil(paint.bottomGui() * pixelsPerGuiPx - 0.5);
        startX = Math.max(startX, canvasLeftPx);
        endX = Math.min(endX, canvasRightPx);
        startY = Math.max(startY, canvasTopPx);
        endY = Math.min(endY, canvasBottomPx);
        if (startX >= endX || startY >= endY) {
            return null;
        }
        return new PaintRect(startX, startY, endX, endY);
    }

    /**
     * Collects the model's paintable faces: the exact flat fast path when the rotation
     * classifies as identity or mirror and no element carries an active rotation, the
     * orthographic pipeline otherwise. Classified rotations enter the orthographic pipeline
     * with their snapped canonical angles (and exact quarter-turn matrices, see
     * {@link #rotationMatrixAboutAxis}), so a snapped classification projects exactly the
     * geometry its flat classification would; sampling agrees with the flat fast paths except
     * at knife-edge alignments where a pixel center meets a texel boundary and the two
     * pipelines' last-ulp rounding may pick adjacent texels.
     */
    private static void collectFaces(ModelInstance model, boolean fullRotations, String context,
                                     List<Paintable> faces) {
        GuiTransform transform = model.transform();
        ViewClass view = classify(transform, fullRotations, context);
        boolean rotatedElements = model.elements().stream().anyMatch(ModelElement::hasActiveRotation);
        if (view != ViewClass.FULL && !rotatedElements) {
            collectFlatFaces(model, view == ViewClass.MIRROR, context, faces);
            return;
        }
        double rotationX = view == ViewClass.FULL ? transform.rotationX() : 0;
        double rotationY = view == ViewClass.FULL ? transform.rotationY() : (view == ViewClass.MIRROR ? 180 : 0);
        double rotationZ = view == ViewClass.FULL ? transform.rotationZ() : 0;
        collectOrthoFaces(model, rotationX, rotationY, rotationZ, faces);
    }

    /**
     * Classifies the gui rotation. Rotations within {@value #ROTATION_TOLERANCE_DEGREES}
     * degrees of 0 or 180 about y (with zero x and z) snap to the nearest flat view, absorbing
     * MCC's decorative tilts. Anything else renders orthographically under
     * {@code fullRotations} and throws without it.
     */
    private static ViewClass classify(GuiTransform transform, boolean fullRotations, String context) {
        if (transform.rotationX() == 0 && transform.rotationZ() == 0) {
            double y = Math.abs(transform.rotationY());
            if (y <= ROTATION_TOLERANCE_DEGREES) {
                return ViewClass.FRONT;
            }
            if (Math.abs(y - 180) <= ROTATION_TOLERANCE_DEGREES) {
                return ViewClass.MIRROR;
            }
        }
        if (fullRotations) {
            return ViewClass.FULL;
        }
        throw new PackResolveException(
            "Unsupported gui rotation [%s, %s, %s]: only identity and the (0, 180, 0) mirror render by default; opt into full gui rotations for the orthographic projection (%s)",
            String.valueOf(transform.rotationX()), String.valueOf(transform.rotationY()),
            String.valueOf(transform.rotationZ()), context);
    }

    /**
     * The display transform's translation and scale clamped to the vanilla limits, shared by
     * the flat and orthographic collectors so the two paths' clamping can never drift.
     */
    private record ClampedDisplay(double translationX, double translationY, double translationZ,
                                  double scaleX, double scaleY, double scaleZ) {

        static ClampedDisplay of(GuiTransform transform) {
            return new ClampedDisplay(
                clamp(transform.translationX(), TRANSLATION_LIMIT),
                clamp(transform.translationY(), TRANSLATION_LIMIT),
                clamp(transform.translationZ(), TRANSLATION_LIMIT),
                clamp(transform.scaleX(), SCALE_LIMIT),
                clamp(transform.scaleY(), SCALE_LIMIT),
                clamp(transform.scaleZ(), SCALE_LIMIT));
        }
    }

    /**
     * The exact flat projection of the identity and mirror fast paths. This arithmetic is
     * pinned by tests and goldens; it must not change.
     */
    private static void collectFlatFaces(ModelInstance model, boolean mirrored, String context,
                                         List<Paintable> faces) {
        ClampedDisplay display = ClampedDisplay.of(model.transform());
        double translationX = display.translationX();
        double translationY = display.translationY();
        double translationZ = display.translationZ();
        double scaleX = display.scaleX();
        double scaleY = display.scaleY();
        double scaleZ = display.scaleZ();
        // The (0,180,0) mirror negates model x and z on the way to the screen.
        double xSign = mirrored ? -1 : 1;
        double zSign = mirrored ? -1 : 1;

        for (ModelElement element : model.elements()) {
            // The face whose outward normal points toward the viewer (+z) after the transform:
            // south (+z normal) unless the transform's z direction is negated by the mirror or a
            // negative z scale; a degenerate zero z scale keeps south.
            double normalSign = zSign * Math.signum(scaleZ);
            ModelElement.Direction visible = normalSign < 0 ? ModelElement.Direction.NORTH : ModelElement.Direction.SOUTH;
            ModelElement.Face face = element.faces().get(visible);
            if (face == null) {
                if (!element.faces().containsKey(ModelElement.Direction.NORTH)
                    && !element.faces().containsKey(ModelElement.Direction.SOUTH)) {
                    log.warn("Element with neither north nor south face contributes nothing to the flat GUI projection ({})",
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
            faces.add(new FlatFace(
                Math.min(guiXAtFromX, guiXAtToX), Math.min(guiYAtFromY, guiYAtToY),
                Math.max(guiXAtFromX, guiXAtToX), Math.max(guiYAtFromY, guiYAtToY),
                guiXAtFromX, guiXAtToX, guiYAtFromY, guiYAtToY,
                z, visible, face, element, model));
        }
    }

    /**
     * The orthographic pipeline: transforms every declared face of every element (element
     * rotation, then display scale-rotate-translate about the model center), projects to the
     * slot plane, culls back faces and emits {@link QuadFace}s ready for inverse-mapped
     * rasterization.
     */
    private static void collectOrthoFaces(ModelInstance model, double rotationXDeg, double rotationYDeg,
                                          double rotationZDeg, List<Paintable> faces) {
        ClampedDisplay display = ClampedDisplay.of(model.transform());
        double translationX = display.translationX();
        double translationY = display.translationY();
        double translationZ = display.translationZ();
        double scaleX = display.scaleX();
        double scaleY = display.scaleY();
        double scaleZ = display.scaleZ();
        double[][] rotation = rotationMatrixXyz(rotationXDeg, rotationYDeg, rotationZDeg);
        // Solid-cuboid culling under mirroring scales: a negative-determinant scale flips every
        // projected winding, so the winding test flips with it (a zero determinant keeps the
        // raw winding - the flat paths' "degenerate zero z scale keeps south" convention).
        double detSign = scaleX * scaleY * scaleZ < 0 ? -1 : 1;
        boolean sideLit = model.guiLight() == GuiLight.SIDE;

        for (ModelElement element : model.elements()) {
            ModelElement.Rotation elementRotation = element.hasActiveRotation() ? element.rotation() : null;
            double[][] elementMatrix = elementRotation == null ? null
                : rotationMatrixAboutAxis(elementRotation.axis(), elementRotation.angle());
            for (ModelElement.Direction direction : ModelElement.Direction.values()) {
                ModelElement.Face face = element.faces().get(direction);
                if (face == null) {
                    continue;
                }
                // The face rectangle's origin corner (p = q = 0) and its p/q edge corners.
                double[][] corners = faceCorners(direction, element);
                double[][] projected = new double[3][];
                for (int i = 0; i < 3; i++) {
                    double[] point = corners[i];
                    if (elementRotation != null) {
                        point = applyElementRotation(point, elementRotation, elementMatrix);
                    }
                    projected[i] = projectDisplay(point, scaleX, scaleY, scaleZ, rotation,
                        translationX, translationY, translationZ);
                }
                double uGuiX = projected[1][0] - projected[0][0];
                double uGuiY = projected[1][1] - projected[0][1];
                double vGuiX = projected[2][0] - projected[0][0];
                double vGuiY = projected[2][1] - projected[0][1];
                double cross = uGuiX * vGuiY - uGuiY * vGuiX;
                if (cross * detSign <= 0) {
                    // Back-facing (the solid cuboid's outward side turns away) or edge-on.
                    continue;
                }
                double depth = projected[0][2]
                    + 0.5 * (projected[1][2] - projected[0][2])
                    + 0.5 * (projected[2][2] - projected[0][2]);
                float shade = !sideLit || !element.shade() ? 1.0f
                    : shadeFor(direction, elementMatrix);
                double farGuiX = projected[0][0] + uGuiX + vGuiX;
                double farGuiY = projected[0][1] + uGuiY + vGuiY;
                faces.add(new QuadFace(
                    min(projected[0][0], projected[1][0], projected[2][0], farGuiX),
                    min(projected[0][1], projected[1][1], projected[2][1], farGuiY),
                    max(projected[0][0], projected[1][0], projected[2][0], farGuiX),
                    max(projected[0][1], projected[1][1], projected[2][1], farGuiY),
                    projected[0][0], projected[0][1], uGuiX, uGuiY, vGuiX, vGuiY,
                    depth, shade, direction, face, element, model));
            }
        }
    }

    /**
     * The face rectangle in model space as three corners: the origin corner ({@code p = q = 0}
     * in face coordinates, where the default uv starts), the corner at {@code p = 1} and the
     * corner at {@code q = 1}. The per-direction axes follow vanilla's face enumeration (u
     * along the direction the default uv increases, v downward on the face as viewed from
     * outside the cuboid) so explicit and default uvs sample identically to the flat paths.
     */
    private static double[][] faceCorners(ModelElement.Direction direction, ModelElement element) {
        double fx = element.fromX();
        double fy = element.fromY();
        double fz = element.fromZ();
        double tx = element.toX();
        double ty = element.toY();
        double tz = element.toZ();
        return switch (direction) {
            case SOUTH -> new double[][]{{fx, ty, tz}, {tx, ty, tz}, {fx, fy, tz}};
            case NORTH -> new double[][]{{tx, ty, fz}, {fx, ty, fz}, {tx, fy, fz}};
            case EAST -> new double[][]{{tx, ty, tz}, {tx, ty, fz}, {tx, fy, tz}};
            case WEST -> new double[][]{{fx, ty, fz}, {fx, ty, tz}, {fx, fy, fz}};
            case UP -> new double[][]{{fx, ty, fz}, {tx, ty, fz}, {fx, ty, tz}};
            case DOWN -> new double[][]{{fx, fy, tz}, {tx, fy, tz}, {fx, fy, fz}};
        };
    }

    /** The face's outward unit normal in model space. */
    private static double[] outwardNormal(ModelElement.Direction direction) {
        return switch (direction) {
            case SOUTH -> new double[]{0, 0, 1};
            case NORTH -> new double[]{0, 0, -1};
            case EAST -> new double[]{1, 0, 0};
            case WEST -> new double[]{-1, 0, 0};
            case UP -> new double[]{0, 1, 0};
            case DOWN -> new double[]{0, -1, 0};
        };
    }

    /**
     * Applies the element rotation to a model-space point: rotate the offset from the rotation
     * origin, then scale the two perpendicular axes by 1/cos(angle) when {@code rescale} is set
     * (the scale is uniform in the plane perpendicular to the axis, so applying it after the
     * rotation matches vanilla's {@code FaceBakery} exactly).
     */
    private static double[] applyElementRotation(double[] point, ModelElement.Rotation rotation,
                                                 double[][] matrix) {
        double x = point[0] - rotation.originX();
        double y = point[1] - rotation.originY();
        double z = point[2] - rotation.originZ();
        double rx = matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z;
        double ry = matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z;
        double rz = matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z;
        if (rotation.rescale()) {
            double factor = 1.0 / Math.cos(Math.toRadians(rotation.angle()));
            switch (rotation.axis()) {
                case X -> {
                    ry *= factor;
                    rz *= factor;
                }
                case Y -> {
                    rx *= factor;
                    rz *= factor;
                }
                case Z -> {
                    rx *= factor;
                    ry *= factor;
                }
            }
        }
        return new double[]{rotation.originX() + rx, rotation.originY() + ry, rotation.originZ() + rz};
    }

    /**
     * Applies the display transform (scale, then rotation, then translation, about the model
     * center) and projects to the slot plane: gui x right, gui y down (model y flipped), the
     * returned third component the depth (larger is nearer).
     */
    private static double[] projectDisplay(double[] point, double scaleX, double scaleY, double scaleZ,
                                           double[][] rotation, double translationX, double translationY,
                                           double translationZ) {
        double x = scaleX * (point[0] - CENTER);
        double y = scaleY * (point[1] - CENTER);
        double z = scaleZ * (point[2] - CENTER);
        double rx = rotation[0][0] * x + rotation[0][1] * y + rotation[0][2] * z;
        double ry = rotation[1][0] * x + rotation[1][1] * y + rotation[1][2] * z;
        double rz = rotation[2][0] * x + rotation[2][1] * y + rotation[2][2] * z;
        return new double[]{CENTER + rx + translationX, CENTER - (ry + translationY), rz + translationZ};
    }

    /**
     * The display rotation matrix Rx(x) * Ry(y) * Rz(z): a vertex is rotated about z first,
     * then y, then x - the vanilla {@code Quaternionf.rotationXYZ} order (see the class
     * javadoc for the evidence).
     */
    private static double[][] rotationMatrixXyz(double xDeg, double yDeg, double zDeg) {
        return multiply(rotationMatrixAboutAxis(ModelElement.Axis.X, xDeg),
            multiply(rotationMatrixAboutAxis(ModelElement.Axis.Y, yDeg),
                rotationMatrixAboutAxis(ModelElement.Axis.Z, zDeg)));
    }

    /**
     * A right-handed rotation matrix about one model axis, angle in degrees. Exact multiples
     * of 90 degrees snap their sine and cosine to exact 0 and +-1: {@code Math.sin(Math.PI)}
     * is 1.2246e-16, and without the snap that residue skews mirror-classified models forced
     * through the orthographic pipeline by ~1e-15 gui units - enough to report a phantom
     * extra canvas column on an oversized render (the floor of a barely-negative extent).
     */
    private static double[][] rotationMatrixAboutAxis(ModelElement.Axis axis, double degrees) {
        double cos = Math.cos(Math.toRadians(degrees));
        double sin = Math.sin(Math.toRadians(degrees));
        if (degrees % 90 == 0) {
            // The residues are strictly below 1e-15, so rounding recovers the exact values.
            cos = Math.round(cos);
            sin = Math.round(sin);
        }
        return switch (axis) {
            case X -> new double[][]{{1, 0, 0}, {0, cos, -sin}, {0, sin, cos}};
            case Y -> new double[][]{{cos, 0, sin}, {0, 1, 0}, {-sin, 0, cos}};
            case Z -> new double[][]{{cos, -sin, 0}, {sin, cos, 0}, {0, 0, 1}};
        };
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] result = new double[3][3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                result[row][col] = a[row][0] * b[0][col] + a[row][1] * b[1][col] + a[row][2] * b[2][col];
            }
        }
        return result;
    }

    /**
     * The {@code gui_light: side} shade for a face: the vanilla per-orientation block-light
     * constant of the element-rotated outward normal's nearest axis (up 1.0, north/south 0.8,
     * east/west 0.6, down 0.5). Dot products compare in FLOAT precision like vanilla's
     * {@code Direction.getNearest}, so ties at exactly 45 degrees are reachable and break in
     * vanilla's iteration order: down, up, north, south, west, east.
     */
    private static float shadeFor(ModelElement.Direction direction, double[][] elementMatrix) {
        double[] normal = outwardNormal(direction);
        if (elementMatrix != null) {
            normal = new double[]{
                elementMatrix[0][0] * normal[0] + elementMatrix[0][1] * normal[1] + elementMatrix[0][2] * normal[2],
                elementMatrix[1][0] * normal[0] + elementMatrix[1][1] * normal[1] + elementMatrix[1][2] * normal[2],
                elementMatrix[2][0] * normal[0] + elementMatrix[2][1] * normal[1] + elementMatrix[2][2] * normal[2]};
        }
        // Vanilla Direction.getNearest: iterate down, up, north, south, west, east keeping the
        // first strictly-greatest dot product. The float cast is load-bearing: (float) cos(45
        // degrees) equals (float) sin(45 degrees) while the doubles differ by one ulp, so an
        // exactly-45-degree normal ties two axes and the iteration order decides, exactly like
        // the client's float math.
        double[][] axes = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        float[] shades = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};
        int best = 0;
        float bestDot = -Float.MAX_VALUE;
        for (int i = 0; i < axes.length; i++) {
            float dot = (float) (axes[i][0] * normal[0] + axes[i][1] * normal[1] + axes[i][2] * normal[2]);
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        return shades[best];
    }

    /** The flat fast-path rasterizer; byte-identical to the pre-orthographic renderer. */
    private static void paintFlatFace(Graphics2D graphics, FlatFace paint, PaintRect rect, int pixelsPerGuiPx,
                                      int canvasLeftPx, int canvasTopPx,
                                      TextureLookup textures, String context) {
        int startX = rect.startX();
        int endX = rect.endX();
        int startY = rect.startY();
        int endY = rect.endY();

        BufferedImage texture = textures.load(resolveTextureRef(paint.face().textureRef(), paint.model().textures(), context));
        ModelElement element = paint.element();
        ModelElement.FaceUv uv = paint.face().uv() != null ? paint.face().uv()
            : defaultUv(paint.direction(), element);
        int tint = tintFor(paint.face(), paint.model());

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
                // The face's intrinsic axes: P across the face as vanilla enumerates it (along
                // +x for south, -x for north), Q top-down.
                double faceP = paint.direction() == ModelElement.Direction.SOUTH ? a : 1 - a;
                double faceQ = 1 - b;
                int argb = sampleTexel(texture, uv, paint.face().rotation(), faceP, faceQ);
                if ((argb >>> 24) != 0) {
                    faceRaster.setRGB(px - startX, py - startY, tinted(argb, tint));
                }
            }
        }
        graphics.drawImage(faceRaster, startX - canvasLeftPx, startY - canvasTopPx, null);
    }

    /**
     * The orthographic rasterizer: scans the face's clipped bounding box and inverse-maps each
     * pixel center through the projected parallelogram's affine transform to face coordinates
     * (a along the p axis, b along the q axis), rejecting pixels outside the half-open
     * [0, 1) x [0, 1) face interior. Inside pixels sample nearest-neighbor and apply tint and
     * gui-light shade.
     */
    private static void paintQuadFace(Graphics2D graphics, QuadFace paint, PaintRect rect, int pixelsPerGuiPx,
                                      int canvasLeftPx, int canvasTopPx,
                                      TextureLookup textures, String context) {
        int startX = rect.startX();
        int endX = rect.endX();
        int startY = rect.startY();
        int endY = rect.endY();

        BufferedImage texture = textures.load(resolveTextureRef(paint.face().textureRef(), paint.model().textures(), context));
        ModelElement.FaceUv uv = paint.face().uv() != null ? paint.face().uv()
            : defaultUv(paint.direction(), paint.element());
        int tint = tintFor(paint.face(), paint.model());
        // Culling guarantees a nonzero cross product, so the inverse always exists.
        double invCross = 1.0 / (paint.uGuiX() * paint.vGuiY() - paint.uGuiY() * paint.vGuiX());

        BufferedImage faceRaster = new BufferedImage(endX - startX, endY - startY, BufferedImage.TYPE_INT_ARGB);
        for (int py = startY; py < endY; py++) {
            double offsetY = (py + 0.5) / pixelsPerGuiPx - paint.originGuiY();
            for (int px = startX; px < endX; px++) {
                double offsetX = (px + 0.5) / pixelsPerGuiPx - paint.originGuiX();
                double a = (offsetX * paint.vGuiY() - offsetY * paint.vGuiX()) * invCross;
                if (a < 0 || a >= 1) {
                    continue;
                }
                double b = (paint.uGuiX() * offsetY - paint.uGuiY() * offsetX) * invCross;
                if (b < 0 || b >= 1) {
                    continue;
                }
                int argb = sampleTexel(texture, uv, paint.face().rotation(), a, b);
                if ((argb >>> 24) != 0) {
                    faceRaster.setRGB(px - startX, py - startY, shadedTint(argb, tint, paint.shade()));
                }
            }
        }
        graphics.drawImage(faceRaster, startX - canvasLeftPx, startY - canvasTopPx, null);
    }

    /**
     * Samples the face texture nearest-neighbor at face coordinates ({@code faceP} along the
     * face's p axis, {@code faceQ} along its q axis, both 0..1 across the face as vanilla
     * enumerates it). Face rotation turns the texture clockwise in 90 degree steps by
     * remapping (P, Q) to uv fractions. Out-of-range uv values clamp to the texture's edge
     * texels.
     */
    private static int sampleTexel(BufferedImage texture, ModelElement.FaceUv uv,
                                   int rotation, double faceP, double faceQ) {
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
     * The vanilla default uv projection of the element bounds per face direction (vanilla
     * {@code BlockElement}'s uvs-by-face): texture v runs opposite model y on the four side
     * faces (v increases downward), u runs along each face's enumeration direction, and the
     * up/down faces project onto the x-z plane.
     */
    private static ModelElement.FaceUv defaultUv(ModelElement.Direction direction, ModelElement element) {
        return switch (direction) {
            case SOUTH -> new ModelElement.FaceUv(element.fromX(), SLOT_UNITS - element.toY(),
                element.toX(), SLOT_UNITS - element.fromY());
            case NORTH -> new ModelElement.FaceUv(SLOT_UNITS - element.toX(), SLOT_UNITS - element.toY(),
                SLOT_UNITS - element.fromX(), SLOT_UNITS - element.fromY());
            case EAST -> new ModelElement.FaceUv(SLOT_UNITS - element.toZ(), SLOT_UNITS - element.toY(),
                SLOT_UNITS - element.fromZ(), SLOT_UNITS - element.fromY());
            case WEST -> new ModelElement.FaceUv(element.fromZ(), SLOT_UNITS - element.toY(),
                element.toZ(), SLOT_UNITS - element.fromY());
            case UP -> new ModelElement.FaceUv(element.fromX(), element.fromZ(),
                element.toX(), element.toZ());
            case DOWN -> new ModelElement.FaceUv(element.fromX(), SLOT_UNITS - element.toZ(),
                element.toX(), SLOT_UNITS - element.fromZ());
        };
    }

    /** The evaluated tint for the face's tintindex; indexes past the tint list are white. */
    private static int tintFor(ModelElement.Face face, ModelInstance model) {
        int tintIndex = face.tintIndex();
        List<Integer> tints = model.tints();
        if (tintIndex < 0 || tintIndex >= tints.size()) {
            return GuiModelResolver.WHITE;
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
        // Multiplying by shade 1.0f is exact, so delegating is bit-identical to a standalone
        // per-channel multiply - one copy of the rounding arithmetic serves both paths.
        return shadedTint(argb, tint, 1.0f);
    }

    /**
     * {@link #tinted(int, int)} with the gui-light shade folded into the same rounded
     * multiply: {@code round(channel * tintChannel / 255 * shade)}, alpha kept.
     */
    private static int shadedTint(int argb, int tint, float shade) {
        if (tint == GuiModelResolver.WHITE && shade == 1.0f) {
            return argb;
        }
        int red = Math.round(((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255.0f * shade);
        int green = Math.round(((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255.0f * shade);
        int blue = Math.round((argb & 0xFF) * (tint & 0xFF) / 255.0f * shade);
        return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Resolves a texture reference against a model's merged texture map: {@code #key}
     * indirections follow the map (values may chain to further {@code #key}s); anything else
     * is already concrete. Shared by element faces and the generated {@code layerN} path so
     * the two can never disagree on reference semantics.
     */
    static String resolveTextureRef(String textureRef, Map<String, String> textures, String context) {
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
                    "Texture reference '#%s' is not defined in the model's texture map (%s)", key, context);
            }
            current = next;
        }
        throw new PackResolveException(
            "Texture reference '%s' forms a cycle in the texture map (%s)", textureRef, context);
    }

    private static double clamp(float value, float limit) {
        return Math.clamp(value, -limit, limit);
    }

    private static double min(double a, double b, double c, double d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static double max(double a, double b, double c, double d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
}
