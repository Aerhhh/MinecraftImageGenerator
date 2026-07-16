package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Precision tests for the GUI projection: transforms, sampling, ordering, clipping, and the
 * orthographic pipeline for full gui rotations and element rotations. Coordinates in comments
 * are GUI px relative to the slot box origin unless stated otherwise.
 *
 * <p>Orthographic expectations are hand-derived from the display rotation matrix
 * Rx * Ry * Rz. For [30, 225, 0] the matrix rows are approximately
 * [-0.7071, 0, -0.7071], [-0.3536, 0.8660, 0.3536], [0.6124, 0.5, -0.6124]: the up, north and
 * east faces keep a positive projected depth component (visible), with north's center at gui
 * (13.66, 10.83), east's at (2.34, 10.83) and up's at (8, 1.07).
 */
class ElementModelRendererTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int YELLOW = 0xFFFFFF00;
    private static final int MAGENTA = 0xFFFF00FF;
    private static final int CYAN = 0xFF00FFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final String CONTEXT = "test model";

    /** The classic block gui rotation: up, north and east faces visible. */
    private static final GuiTransform BLOCK_ANGLE = new GuiTransform(30, 225, 0, 0, 0, 0, 1, 1, 1);

    /** 2x2 texture with distinct quadrants: TL red, TR green, BL blue, BR yellow. */
    private static BufferedImage quadrants() {
        BufferedImage texture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, RED);
        texture.setRGB(1, 0, GREEN);
        texture.setRGB(0, 1, BLUE);
        texture.setRGB(1, 1, YELLOW);
        return texture;
    }

    /** 16x16 texture where every column x is a distinct opaque color 0xFF0000xx. */
    private static BufferedImage columns() {
        BufferedImage texture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                texture.setRGB(x, y, 0xFF000000 | x);
            }
        }
        return texture;
    }

    private static ElementModelRenderer.TextureLookup lookup(BufferedImage texture) {
        return ref -> {
            if (!"tex:main".equals(ref)) {
                throw new PackResolveException("Unknown texture %s", ref);
            }
            return texture;
        };
    }

    private static ModelElement fullQuad(ModelElement.Face south, ModelElement.Face north) {
        Map<ModelElement.Direction, ModelElement.Face> faces = new EnumMap<>(ModelElement.Direction.class);
        if (south != null) {
            faces.put(ModelElement.Direction.SOUTH, south);
        }
        if (north != null) {
            faces.put(ModelElement.Direction.NORTH, north);
        }
        return new ModelElement(0, 0, 0, 16, 16, 1, null, true, Map.copyOf(faces));
    }

    private static ModelElement.Face face() {
        return new ModelElement.Face(null, "#main", 0, -1);
    }

    private static ElementModelRenderer.ModelInstance instance(GuiTransform transform, ModelElement... elements) {
        return new ElementModelRenderer.ModelInstance(List.of(elements), Map.of("main", "tex:main"),
            transform, List.of());
    }

    private static ElementModelRenderer.Raster render(BufferedImage texture, GuiTransform transform,
                                                      int scale, boolean oversized, ModelElement... elements) {
        return ElementModelRenderer.render(List.of(instance(transform, elements)), scale, oversized,
            lookup(texture), CONTEXT);
    }

    @Test
    void fullQuadFillsTheSlotBox() {
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(face(), null));
        BufferedImage image = raster.image();
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertEquals(0, raster.offsetX());
        assertEquals(0, raster.offsetY());
        assertEquals(RED, image.getRGB(0, 0), "texture top-left at screen top-left");
        assertEquals(GREEN, image.getRGB(63, 0));
        assertEquals(BLUE, image.getRGB(0, 63));
        assertEquals(YELLOW, image.getRGB(63, 63));
    }

    @Test
    void yMirrorShowsTheNorthFaceUpright() {
        // Under (0,180,0) the south face turns away and the north face becomes visible. The
        // north face's default uv runs opposite model x, so through the mirrored projection its
        // texture reads upright: top-left texel at screen top-left.
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, false,
            fullQuad(null, face()));
        assertEquals(RED, raster.image().getRGB(0, 0));
        assertEquals(GREEN, raster.image().getRGB(63, 0));
    }

    @Test
    void yMirrorFlipsAnAsymmetricSouthOnlyModelToNothing() {
        // With only a south face, the mirror turns the sole face away from the viewer.
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, false,
            fullQuad(face(), null));
        assertEquals(0, raster.image().getRGB(32, 32), "back-facing faces are culled");
    }

    @Test
    void mirrorFlipsTheGeometryHorizontally() {
        // Two double-faced quads, red on the left half of the slot and blue on the right: the
        // (0,180,0) mirror swaps their screen sides (the art composition flips horizontally).
        BufferedImage red = solid(RED);
        BufferedImage blue = solid(BLUE);
        ModelElement left = new ModelElement(0, 0, 0, 8, 16, 1, null, true, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#l", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#l", 0, -1)));
        ModelElement right = new ModelElement(8, 0, 0, 16, 16, 1, null, true, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#r", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#r", 0, -1)));
        ElementModelRenderer.TextureLookup textures = ref -> "tex:red".equals(ref) ? red : blue;
        Map<String, String> map = Map.of("l", "tex:red", "r", "tex:blue");

        ElementModelRenderer.Raster identity = ElementModelRenderer.render(
            List.of(new ElementModelRenderer.ModelInstance(List.of(left, right), map,
                GuiTransform.IDENTITY, List.of())), 4, false, textures, CONTEXT);
        assertEquals(RED, identity.image().getRGB(16, 32));
        assertEquals(BLUE, identity.image().getRGB(48, 32));

        ElementModelRenderer.Raster mirrored = ElementModelRenderer.render(
            List.of(new ElementModelRenderer.ModelInstance(List.of(left, right), map,
                new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), List.of())), 4, false, textures, CONTEXT);
        assertEquals(BLUE, mirrored.image().getRGB(16, 32), "the mirror swaps the halves");
        assertEquals(RED, mirrored.image().getRGB(48, 32));
    }

    @Test
    void negativeXScaleMirrorsHorizontally() {
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 0, 0, 0, -1, 1, 1), 4, false,
            fullQuad(face(), null));
        assertEquals(GREEN, raster.image().getRGB(0, 0), "texture top-right lands at screen top-left");
        assertEquals(RED, raster.image().getRGB(63, 0));
    }

    @Test
    void negativeZScaleShowsTheNorthFace() {
        // A negative z scale flips the visible face to north WITHOUT mirroring x: the north
        // face's default uv runs opposite model x, so viewed from the front it reads
        // horizontally mirrored - texture top-RIGHT (green) lands at screen top-left.
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 0, 0, 0, 1, 1, -1), 4, false,
            fullQuad(null, face()));
        assertEquals(GREEN, raster.image().getRGB(0, 0));
        assertEquals(RED, raster.image().getRGB(63, 0));
        assertEquals(YELLOW, raster.image().getRGB(0, 63));
        assertEquals(BLUE, raster.image().getRGB(63, 63));
    }

    @Test
    void negativeYScaleMirrorsVertically() {
        // Scale (1, -1, 1): model y flips, so the texture's bottom row reaches the screen top.
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 0, 0, 0, 1, -1, 1), 4, false,
            fullQuad(face(), null));
        assertEquals(BLUE, raster.image().getRGB(0, 0), "texture bottom-left lands at screen top-left");
        assertEquals(YELLOW, raster.image().getRGB(63, 0));
        assertEquals(RED, raster.image().getRGB(0, 63));
        assertEquals(GREEN, raster.image().getRGB(63, 63));
    }

    @Test
    void translationMovesArtUpAndRight() {
        // Quad [0,4]x[0,4] translated (+2, +3): +y is up in model space, so the art lands at
        // gui x [2,6), y [9,13).
        ModelElement quad = new ModelElement(0, 0, 0, 4, 4, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 2, 3, 0, 1, 1, 1), 2, false, quad);
        BufferedImage image = raster.image();
        assertEquals(0, image.getRGB(3, 20), "left of the translated rect stays empty");
        assertTrue((image.getRGB(4, 18) >>> 24) != 0, "gui (2,9) is the translated top-left");
        assertTrue((image.getRGB(11, 25) >>> 24) != 0, "gui (6,13) exclusive bottom-right");
        assertEquals(0, image.getRGB(12, 18));
        assertEquals(0, image.getRGB(4, 26));
        assertEquals(0, image.getRGB(4, 31), "the untranslated position (y [12,16)) is vacated");
    }

    @Test
    void subPixelQuadSamplesFractionalUv() {
        // A 0.75-unit-wide quad with a 3-texel-wide uv box: at 4 px per GUI px the quad is
        // exactly 3 canvas px, and each pixel center must sample its own texel column - the
        // geometry would be destroyed by any 16 px intermediate raster.
        ModelElement sliver = new ModelElement(0, 0, 0, 0.75f, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH,
                new ModelElement.Face(new ModelElement.FaceUv(3, 0, 6, 16), "#main", 0, -1)));
        ElementModelRenderer.Raster raster = render(columns(), GuiTransform.IDENTITY, 4, false, sliver);
        BufferedImage image = raster.image();
        assertEquals(0xFF000003, image.getRGB(0, 32));
        assertEquals(0xFF000004, image.getRGB(1, 32));
        assertEquals(0xFF000005, image.getRGB(2, 32));
        assertEquals(0, image.getRGB(3, 32), "the quad is exactly 3 px wide");
    }

    @Test
    void elementsPaintBackToFrontByTransformedZ() {
        // The blue element sits closer to the viewer (higher z) but is listed FIRST; painting
        // must sort back-to-front, not JSON order.
        ModelElement front = new ModelElement(0, 0, 2, 16, 16, 3, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#front", 0, -1)));
        ModelElement back = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#back", 0, -1)));
        BufferedImage blue = solid(BLUE);
        BufferedImage red = solid(RED);
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(front, back), Map.of("front", "tex:blue", "back", "tex:red"),
            GuiTransform.IDENTITY, List.of());
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 2, false,
            ref -> "tex:blue".equals(ref) ? blue : red, CONTEXT);
        assertEquals(BLUE, raster.image().getRGB(16, 16), "the nearer element paints last");
    }

    @Test
    void mirrorReordersDepthAcrossElements() {
        // Two double-faced full quads: A behind (z 0..1), B in front (z 2..3). The (0,180,0)
        // mirror negates z, so A becomes the NEARER element and must paint over B - the exact
        // stack shape of Wynncraft's mirrored wN models.
        BufferedImage red = solid(RED);
        BufferedImage green = solid(GREEN);
        ModelElement back = new ModelElement(0, 0, 0, 16, 16, 1, null, true, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#a", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#a", 0, -1)));
        ModelElement front = new ModelElement(0, 0, 2, 16, 16, 3, null, true, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#b", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#b", 0, -1)));
        Map<String, String> map = Map.of("a", "tex:red", "b", "tex:green");
        ElementModelRenderer.TextureLookup textures = ref -> "tex:red".equals(ref) ? red : green;

        ElementModelRenderer.Raster identity = ElementModelRenderer.render(
            List.of(new ElementModelRenderer.ModelInstance(List.of(back, front), map,
                GuiTransform.IDENTITY, List.of())), 2, false, textures, CONTEXT);
        assertEquals(GREEN, identity.image().getRGB(16, 16), "under identity the front element wins");

        ElementModelRenderer.Raster mirrored = ElementModelRenderer.render(
            List.of(new ElementModelRenderer.ModelInstance(List.of(back, front), map,
                new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), List.of())), 2, false, textures, CONTEXT);
        assertEquals(RED, mirrored.image().getRGB(16, 16),
            "the mirror negates z, bringing the formerly-back element to the front");
    }

    @Test
    void depthSortUsesTheVisibleFacePlane() {
        // Nested elements: A spans z 0..3, B sits inside it at z 1..2, both double-faced.
        // Under identity the SOUTH planes compare (A's 3 beats B's 2); under the mirror the
        // NORTH planes compare (A's 0 mirrors to the nearest depth). A wins both times, but
        // only when the sort key tracks the VISIBLE face's plane - keying on the south plane
        // under the mirror would wrongly paint B on top.
        BufferedImage red = solid(RED);
        BufferedImage green = solid(GREEN);
        ModelElement outer = new ModelElement(0, 0, 0, 16, 16, 3, null, true, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#a", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#a", 0, -1)));
        ModelElement inner = new ModelElement(0, 0, 1, 16, 16, 2, null, true, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#b", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#b", 0, -1)));
        Map<String, String> map = Map.of("a", "tex:red", "b", "tex:green");
        ElementModelRenderer.TextureLookup textures = ref -> "tex:red".equals(ref) ? red : green;

        ElementModelRenderer.Raster identity = ElementModelRenderer.render(
            List.of(new ElementModelRenderer.ModelInstance(List.of(inner, outer), map,
                GuiTransform.IDENTITY, List.of())), 2, false, textures, CONTEXT);
        assertEquals(RED, identity.image().getRGB(16, 16), "the outer element's south plane is nearer");

        ElementModelRenderer.Raster mirrored = ElementModelRenderer.render(
            List.of(new ElementModelRenderer.ModelInstance(List.of(inner, outer), map,
                new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), List.of())), 2, false, textures, CONTEXT);
        assertEquals(RED, mirrored.image().getRGB(16, 16),
            "the outer element's north plane mirrors to the nearest depth");
    }

    @Test
    void compositeModelsInterleaveByTransformedZ() {
        // Composite [A at z 2..3, B at z 0..1]: vanilla depth-tests the whole item into one
        // buffer, so the NEARER quad from the EARLIER composite entry stays on top wherever
        // they overlap - sequential per-model painting would wrongly put B over A.
        BufferedImage red = solid(RED);
        BufferedImage green = solid(GREEN);
        ModelElement nearQuad = new ModelElement(0, 0, 2, 16, 16, 3, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, -1)));
        ModelElement farQuad = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, -1)));
        ElementModelRenderer.ModelInstance modelA = new ElementModelRenderer.ModelInstance(
            List.of(nearQuad), Map.of("main", "tex:red"), GuiTransform.IDENTITY, List.of());
        ElementModelRenderer.ModelInstance modelB = new ElementModelRenderer.ModelInstance(
            List.of(farQuad), Map.of("main", "tex:green"), GuiTransform.IDENTITY, List.of());
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(modelA, modelB),
            2, false, ref -> "tex:red".equals(ref) ? red : green, CONTEXT);
        assertEquals(RED, raster.image().getRGB(16, 16),
            "the nearer quad of the earlier composite model paints over the later model's farther quad");
    }

    @Test
    void compositeOrderBreaksZTiesAcrossModels() {
        BufferedImage red = solid(RED);
        BufferedImage green = solid(GREEN);
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, -1)));
        ElementModelRenderer.ModelInstance first = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("main", "tex:red"), GuiTransform.IDENTITY, List.of());
        ElementModelRenderer.ModelInstance second = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("main", "tex:green"), GuiTransform.IDENTITY, List.of());
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(first, second),
            2, false, ref -> "tex:red".equals(ref) ? red : green, CONTEXT);
        assertEquals(GREEN, raster.image().getRGB(16, 16),
            "equal z keeps composite order; the later model paints over");
    }

    @Test
    void jsonOrderBreaksZTies() {
        ModelElement first = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#a", 0, -1)));
        ModelElement second = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#b", 0, -1)));
        BufferedImage red = solid(RED);
        BufferedImage green = solid(GREEN);
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(first, second), Map.of("a", "tex:red", "b", "tex:green"),
            GuiTransform.IDENTITY, List.of());
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 2, false,
            ref -> "tex:red".equals(ref) ? red : green, CONTEXT);
        assertEquals(GREEN, raster.image().getRGB(16, 16), "equal z keeps JSON order; later paints over");
    }

    @Test
    void clippedRenderStaysTheSlotBox() {
        // Scale 2 doubles the quad to gui [-8, 24); without oversized_in_gui the canvas stays
        // the 16-GUI-px slot box showing the middle of the art.
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 0, 0, 0, 2, 2, 2), 4, false,
            fullQuad(face(), null));
        assertEquals(64, raster.image().getWidth());
        assertEquals(64, raster.image().getHeight());
        assertEquals(0, raster.offsetX());
        assertEquals(0, raster.offsetY());
        assertTrue((raster.image().getRGB(0, 0) >>> 24) != 0, "the slot box is fully covered");
    }

    @Test
    void oversizedRenderReportsTheFullExtent() {
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 0, 0, 0, 2, 2, 2), 4, true,
            fullQuad(face(), null));
        assertEquals(128, raster.image().getWidth(), "gui [-8, 24) at 4 px per GUI px");
        assertEquals(128, raster.image().getHeight());
        assertEquals(-32, raster.offsetX());
        assertEquals(-32, raster.offsetY());
        assertEquals(RED, raster.image().getRGB(0, 0));
        assertEquals(YELLOW, raster.image().getRGB(127, 127));
    }

    @Test
    void oversizedCanvasAlwaysCoversTheSlotBox() {
        // A small quad in the slot's top-left quarter: the oversized canvas still spans the
        // whole slot box so container anchoring math never needs a special case.
        ModelElement quad = new ModelElement(0, 12, 0, 4, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 2, true, quad);
        assertEquals(32, raster.image().getWidth());
        assertEquals(32, raster.image().getHeight());
        assertEquals(0, raster.offsetX());
        assertEquals(0, raster.offsetY());
    }

    @Test
    void unsupportedGuiRotationsThrowWithoutTheFullRotationOptIn() {
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(30, 225, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(0, 90, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(10, 0, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(0, 7, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
    }

    // ---------------------------------------------------------------------------------------
    // Orthographic pipeline: full gui rotations.
    // ---------------------------------------------------------------------------------------

    /** The render helper with full gui rotations enabled. */
    private static ElementModelRenderer.Raster renderFull(ElementModelRenderer.ModelInstance model,
                                                          ElementModelRenderer.TextureLookup textures,
                                                          int scale, boolean oversized) {
        return ElementModelRenderer.render(List.of(model), scale, oversized, true, textures, CONTEXT);
    }

    /** A 16-unit cube with all six faces on distinct texture keys. */
    private static ElementModelRenderer.ModelInstance cube(GuiTransform transform,
                                                           ElementModelRenderer.GuiLight guiLight,
                                                           boolean shade) {
        Map<ModelElement.Direction, ModelElement.Face> faces = new EnumMap<>(ModelElement.Direction.class);
        for (ModelElement.Direction direction : ModelElement.Direction.values()) {
            faces.put(direction, new ModelElement.Face(null, "#" + direction.name(), 0, -1));
        }
        ModelElement element = new ModelElement(0, 0, 0, 16, 16, 16, null, shade, Map.copyOf(faces));
        Map<String, String> textures = Map.of(
            "NORTH", "tex:NORTH", "SOUTH", "tex:SOUTH", "EAST", "tex:EAST",
            "WEST", "tex:WEST", "UP", "tex:UP", "DOWN", "tex:DOWN");
        return new ElementModelRenderer.ModelInstance(List.of(element), textures, transform,
            List.of(), guiLight);
    }

    /** Distinct solid color per cube face: up red, north green, east blue, south yellow, west magenta, down cyan. */
    private static ElementModelRenderer.TextureLookup cubeColors() {
        return ref -> solid(switch (ref) {
            case "tex:UP" -> RED;
            case "tex:NORTH" -> GREEN;
            case "tex:EAST" -> BLUE;
            case "tex:SOUTH" -> YELLOW;
            case "tex:WEST" -> MAGENTA;
            case "tex:DOWN" -> CYAN;
            default -> throw new PackResolveException("Unknown texture %s", ref);
        });
    }

    /** Every cube face plain white, for shading assertions. */
    private static ElementModelRenderer.TextureLookup cubeWhite() {
        return ref -> solid(WHITE);
    }

    /** The color of the pixel containing the gui point, honoring the raster's offsets. */
    private static int rgbAtGui(ElementModelRenderer.Raster raster, double guiX, double guiY, int scale) {
        int px = (int) Math.floor(guiX * scale) - raster.offsetX();
        int py = (int) Math.floor(guiY * scale) - raster.offsetY();
        return raster.image().getRGB(px, py);
    }

    /** The distinct colors present anywhere in the image. */
    private static Set<Integer> distinctColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getRGB(x, y));
            }
        }
        return colors;
    }

    /** Consecutive color runs down the full pixel column containing gui x. */
    private static List<Integer> columnColorRuns(ElementModelRenderer.Raster raster, double guiX, int scale) {
        int px = (int) Math.floor(guiX * scale) - raster.offsetX();
        List<Integer> runs = new ArrayList<>();
        for (int py = 0; py < raster.image().getHeight(); py++) {
            int color = raster.image().getRGB(px, py);
            if (runs.isEmpty() || runs.get(runs.size() - 1) != color) {
                runs.add(color);
            }
        }
        return runs;
    }

    @Test
    void cubeAtBlockAngleShowsExactlyThreeFaces() {
        // [30,225,0]: the rotated up, north and east normals keep a positive depth component;
        // south, west and down turn away. Face centers project to gui (8, 1.07) for up,
        // (13.66, 10.83) for north and (2.34, 10.83) for east.
        ElementModelRenderer.Raster raster = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.FRONT, true), cubeColors(), 4, true);
        assertEquals(RED, rgbAtGui(raster, 8, 1.07, 4), "up face on top");
        assertEquals(GREEN, rgbAtGui(raster, 13.66, 10.83, 4), "north face lower-right");
        assertEquals(BLUE, rgbAtGui(raster, 2.34, 10.83, 4), "east face lower-left");
        assertEquals(Set.of(0, RED, GREEN, BLUE), distinctColors(raster.image()),
            "exactly the up, north and east faces paint; south, west and down are culled");
    }

    @Test
    void cubeAtBlockAngleFacesAreAdjacentWithoutCracks() {
        // Down the column at gui x = 10 the silhouette reads: empty, up face (from y = -3.59),
        // north face (from the shared edge at y = 5.73 to 19.59), empty - one transition,
        // no transparent crack between the adjacent faces and no third face.
        ElementModelRenderer.Raster raster = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.FRONT, true), cubeColors(), 4, true);
        assertEquals(List.of(0, RED, GREEN, 0), columnColorRuns(raster, 10, 4));
    }

    @Test
    void cubeAtBlockAngleReportsRotatedOversizedExtents() {
        // Projected cube corners reach gui x in [-3.32, 19.32] and y in [-4.59, 20.59]; the
        // oversized canvas must cover the rotated bounds, not the axis-aligned model box.
        ElementModelRenderer.Raster raster = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.FRONT, true), cubeColors(), 4, true);
        assertEquals(-14, raster.offsetX(), "floor(-3.3137 * 4)");
        assertEquals(-19, raster.offsetY(), "floor(-4.5854 * 4)");
        assertEquals(92, raster.image().getWidth(), "ceil(19.3137 * 4) - floor(-3.3137 * 4)");
        assertEquals(102, raster.image().getHeight(), "ceil(20.5854 * 4) - floor(-4.5854 * 4)");
    }

    @Test
    void cubeAtLoweredAngleShowsDownSouthWest() {
        // [-30,45,0] tips the cube the other way: down, south and west face the viewer, with
        // down's center at gui (8, 14.93), south's at (13.66, 5.17) and west's at (2.34, 5.17).
        ElementModelRenderer.Raster raster = renderFull(
            cube(new GuiTransform(-30, 45, 0, 0, 0, 0, 1, 1, 1),
                ElementModelRenderer.GuiLight.FRONT, true), cubeColors(), 4, true);
        assertEquals(CYAN, rgbAtGui(raster, 8, 14.93, 4), "down face at the bottom");
        assertEquals(YELLOW, rgbAtGui(raster, 13.66, 5.17, 4), "south face upper-right");
        assertEquals(MAGENTA, rgbAtGui(raster, 2.34, 5.17, 4), "west face upper-left");
        assertEquals(Set.of(0, CYAN, YELLOW, MAGENTA), distinctColors(raster.image()));
    }

    @Test
    void sideGuiLightShadesFacesByOrientation() {
        // gui_light side: up 1.0, north/south 0.8, east/west 0.6, down 0.5 on white textures.
        ElementModelRenderer.Raster upper = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.SIDE, true), cubeWhite(), 4, true);
        assertEquals(WHITE, rgbAtGui(upper, 8, 1.07, 4), "up shades 1.0");
        assertEquals(0xFFCCCCCC, rgbAtGui(upper, 13.66, 10.83, 4), "north shades 0.8");
        assertEquals(0xFF999999, rgbAtGui(upper, 2.34, 10.83, 4), "east shades 0.6");

        ElementModelRenderer.Raster lower = renderFull(
            cube(new GuiTransform(-30, 45, 0, 0, 0, 0, 1, 1, 1),
                ElementModelRenderer.GuiLight.SIDE, true), cubeWhite(), 4, true);
        assertEquals(0xFF808080, rgbAtGui(lower, 8, 14.93, 4), "down shades 0.5 (round(127.5))");
        assertEquals(0xFFCCCCCC, rgbAtGui(lower, 13.66, 5.17, 4), "south shades 0.8");
        assertEquals(0xFF999999, rgbAtGui(lower, 2.34, 5.17, 4), "west shades 0.6");
    }

    @Test
    void shadeFalseElementsSkipSideShading() {
        ElementModelRenderer.Raster raster = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.SIDE, false), cubeWhite(), 4, true);
        assertEquals(WHITE, rgbAtGui(raster, 8, 1.07, 4));
        assertEquals(WHITE, rgbAtGui(raster, 13.66, 10.83, 4), "shade:false keeps full brightness");
        assertEquals(WHITE, rgbAtGui(raster, 2.34, 10.83, 4));
    }

    @Test
    void frontGuiLightNeverShades() {
        ElementModelRenderer.Raster raster = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.FRONT, true), cubeWhite(), 4, true);
        assertEquals(WHITE, rgbAtGui(raster, 13.66, 10.83, 4));
        assertEquals(WHITE, rgbAtGui(raster, 2.34, 10.83, 4));
    }

    @Test
    void backFacesAreCulledUnderFullRotations() {
        // A south-only quad at [30,225,0] turns its sole face away from the viewer: nothing
        // paints. The complement of the north-face visibility the block angle relies on.
        ElementModelRenderer.Raster raster = renderFull(instance(BLOCK_ANGLE, fullQuad(face(), null)),
            lookup(quadrants()), 4, false);
        assertEquals(Set.of(0), distinctColors(raster.image()), "the south face is back-facing");
    }

    @Test
    void edgeOnRotationsRenderNothing() {
        // [0,90,0] turns the north/south quad exactly edge-on: every projected face has zero
        // area and is culled - deterministic emptiness, not an arbitrary flat view.
        ElementModelRenderer.Raster raster = renderFull(instance(
                new GuiTransform(0, 90, 0, 0, 0, 0, 1, 1, 1), fullQuad(face(), face())),
            lookup(quadrants()), 4, false);
        assertEquals(Set.of(0), distinctColors(raster.image()));
    }

    @Test
    void painterOrderFollowsRotatedDepth() {
        // Two parallel north-faced quads: A at z 0..1, B at z 2..3, JSON order [A, B]. The
        // [30,225,0] rotation flips depth (like the mirror), so A's plane becomes the NEARER
        // one and must paint over B where they overlap - JSON order alone would show B.
        BufferedImage red = solid(RED);
        BufferedImage green = solid(GREEN);
        ModelElement quadA = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.NORTH, new ModelElement.Face(null, "#a", 0, -1)));
        ModelElement quadB = new ModelElement(0, 0, 2, 16, 16, 3, null, true,
            Map.of(ModelElement.Direction.NORTH, new ModelElement.Face(null, "#b", 0, -1)));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(quadA, quadB), Map.of("a", "tex:red", "b", "tex:green"), BLOCK_ANGLE, List.of());
        ElementModelRenderer.Raster raster = renderFull(model,
            ref -> "tex:red".equals(ref) ? red : green, 4, true);
        // A's projected face center (gui 13.66, 10.83) lies inside both projections.
        assertEquals(RED, rgbAtGui(raster, 13.66, 10.83, 4),
            "the quad rotated nearest paints last regardless of JSON order");
    }

    @Test
    void fullRotationRenderingIsDeterministic() {
        ElementModelRenderer.Raster first = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.SIDE, true), cubeColors(), 4, true);
        ElementModelRenderer.Raster second = renderFull(
            cube(BLOCK_ANGLE, ElementModelRenderer.GuiLight.SIDE, true), cubeColors(), 4, true);
        assertEquals(first.offsetX(), second.offsetX());
        assertEquals(first.offsetY(), second.offsetY());
        ImageAssertions.assertPixelsEqual(first.image(), second.image(), "repeat orthographic render");
    }

    @Test
    void fullRotationsKeepClassifiedRotationsExact() {
        // The flag must not perturb identity- or mirror-classified rotations: they stay on the
        // pinned flat fast paths.
        ElementModelRenderer.Raster strictMirror = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        ElementModelRenderer.Raster fullMirror = renderFull(instance(
                new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), fullQuad(null, face())),
            lookup(quadrants()), 4, false);
        ImageAssertions.assertPixelsEqual(strictMirror.image(), fullMirror.image(),
            "the flag must not perturb exactly-classified rotations");

        ElementModelRenderer.Raster strictTilt = render(quadrants(),
            new GuiTransform(0, 2, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(face(), null));
        ElementModelRenderer.Raster fullTilt = renderFull(instance(
                new GuiTransform(0, 2, 0, 0, 0, 0, 1, 1, 1), fullQuad(face(), null)),
            lookup(quadrants()), 4, false);
        ImageAssertions.assertPixelsEqual(strictTilt.image(), fullTilt.image(),
            "decorative tilts keep snapping to identity under the flag");
    }

    @Test
    void fullRotationCanvasOverThePaintedPixelCapFailsLoudly() {
        // At 512 px per GUI px the slot canvas alone meets the 64M cap; the rotated quad's
        // first clipped paint rect pushes the accumulated total over it.
        assertThrows(PackResolveException.class, () -> renderFull(
            instance(BLOCK_ANGLE, fullQuad(null, face())), lookup(quadrants()), 512, false));
    }

    @Test
    void rotatedHugeElementCoordinatesFailBeforeCanvasAllocation() {
        // A crafted element spanning +-1e9 model units through the orthographic path: the
        // projected bounds explode the oversized canvas and the area cap must reject the
        // render before any allocation.
        ModelElement huge = new ModelElement(-1_000_000_000f, -1_000_000_000f, 0,
            1_000_000_000f, 1_000_000_000f, 1, null, true,
            Map.of(ModelElement.Direction.NORTH, face()));
        assertThrows(PackResolveException.class, () -> renderFull(
            instance(BLOCK_ANGLE, huge), lookup(quadrants()), 1, true));
    }

    // ---------------------------------------------------------------------------------------
    // Orthographic pipeline: per-element rotations.
    // ---------------------------------------------------------------------------------------

    private static ModelElement rotatedQuad(ModelElement.Rotation rotation) {
        return new ModelElement(0, 0, 0, 16, 16, 1, rotation, true,
            Map.of(ModelElement.Direction.SOUTH, face()));
    }

    @Test
    void elementRotationAboutZTurnsTheQuadToADiamond() {
        // 45 degrees about z through the center turns the full quad into a diamond with tips
        // 11.31 gui units from the center; the original corners are vacated. Element rotations
        // no longer fail loudly - they render through the orthographic pipeline even at the
        // identity gui rotation.
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, false)));
        assertEquals(-14, raster.offsetX(), "the oversized extents track the rotated bounds");
        assertEquals(-14, raster.offsetY());
        assertTrue((rgbAtGui(raster, 8.125, 8.125, 4) >>> 24) != 0, "center stays covered");
        assertTrue((rgbAtGui(raster, 8.125, -3.125, 4) >>> 24) != 0, "top tip pixel is painted");
        assertEquals(0, rgbAtGui(raster, 5.125, -3.125, 4), "outside the diamond near the tip");
        assertEquals(0, rgbAtGui(raster, 0.125, 0.125, 4), "the original quad corner is vacated");
        // Corner-to-tip mapping pins the rotation direction: the model's top-right corner
        // (green quadrant) reaches the TOP tip under +45 (counterclockwise as viewed).
        assertEquals(GREEN, rgbAtGui(raster, 8.125, -3.125, 4));
        assertEquals(YELLOW, rgbAtGui(raster, 18.875, 8.125, 4),
            "the bottom-right corner (yellow) reaches the right tip");
    }

    @Test
    void negativeElementRotationSpinsTheOtherWay() {
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(-45, ModelElement.Axis.Z, 8, 8, 8, false)));
        assertEquals(GREEN, rgbAtGui(raster, 18.875, 8.125, 4),
            "under -45 the top-right corner (green) reaches the RIGHT tip instead");
    }

    @Test
    void elementRotationAboutXForeshortensTheQuad() {
        // A zero-thickness quad at z = 8 rotated 45 degrees about x through the center: the
        // projected height shrinks to 16 * cos(45) = 11.31 gui units (y in [2.34, 13.66]) and
        // the quadrant colors compress into it.
        ModelElement slanted = new ModelElement(0, 0, 8, 16, 16, 8,
            new ModelElement.Rotation(45, ModelElement.Axis.X, 8, 8, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 4, false, slanted);
        assertEquals(0, rgbAtGui(raster, 8.125, 1.125, 4), "above the foreshortened top edge");
        assertTrue((rgbAtGui(raster, 8.125, 2.625, 4) >>> 24) != 0, "inside the top edge");
        assertTrue((rgbAtGui(raster, 8.125, 13.375, 4) >>> 24) != 0, "inside the bottom edge");
        assertEquals(0, rgbAtGui(raster, 8.125, 13.875, 4), "below the foreshortened bottom edge");
        assertEquals(RED, rgbAtGui(raster, 4.125, 5.125, 4), "top-left quadrant compressed");
        assertEquals(YELLOW, rgbAtGui(raster, 12.125, 11.125, 4), "bottom-right quadrant compressed");
    }

    @Test
    void rescaleStretchesTheRotatedElement() {
        // rescale on a 45-degree rotation scales the perpendicular axes by 1/cos(45): the
        // diamond tips reach 16 gui units from the center instead of 11.31.
        ElementModelRenderer.Raster plain = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, false)));
        ElementModelRenderer.Raster rescaled = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, true)));
        assertEquals(-32, rescaled.offsetX(), "tips at gui -8 with rescale");
        assertTrue((rgbAtGui(rescaled, 22.125, 8.125, 4) >>> 24) != 0,
            "gui x = 22 is inside the rescaled diamond (tip at 24)");
        assertEquals(0, rgbAtGui(plain, 15.875, 2.125, 4), "outside the unrescaled diamond");
        assertTrue((rgbAtGui(rescaled, 15.875, 2.125, 4) >>> 24) != 0, "inside the rescaled one");
    }

    @Test
    void rescaleAt22Point5DegreesUsesTheExactCosineFactor() {
        // The corner (16,16) rotates to gui (12.33, -2.45) at 22.5 degrees; rescale multiplies
        // the offset by 1/cos(22.5) = 1.0824, pushing it to (12.69, -3.31). The probe pixel at
        // gui (12.625, -2.125) sits inside the rescaled square but outside the unrescaled one.
        ElementModelRenderer.Raster plain = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(22.5f, ModelElement.Axis.Z, 8, 8, 8, false)));
        ElementModelRenderer.Raster rescaled = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(22.5f, ModelElement.Axis.Z, 8, 8, 8, true)));
        assertEquals(0, rgbAtGui(plain, 12.625, -2.125, 4));
        assertTrue((rgbAtGui(rescaled, 12.625, -2.125, 4) >>> 24) != 0);
    }

    @Test
    void elementRotationOriginAnchorsTheRotation() {
        // Rotating a quad spanning [0,8]x[0,8] about the model origin corner (0,0,z) by -45
        // swings it toward positive x: the far corner (8,8) lands at model (11.31, 0) = gui
        // (11.31, 16), while the fan around the anchored origin corner stays covered.
        ModelElement quad = new ModelElement(0, 0, 0, 8, 8, 1,
            new ModelElement.Rotation(-45, ModelElement.Axis.Z, 0, 0, 0, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 4, true, quad);
        assertTrue((rgbAtGui(raster, 1.375, 15.875, 4) >>> 24) != 0, "the fan near the origin stays put");
        assertTrue((rgbAtGui(raster, 10.625, 15.625, 4) >>> 24) != 0,
            "the far corner swings to gui x = 11.31");
        assertEquals(0, rgbAtGui(raster, 0.375, 8.625, 4),
            "the quad's old upper region near x = 0 rotates away");
    }

    @Test
    void exact45DegreeElementRotationsTieShadeLikeVanillaFloats() {
        // Rotating a south face 45 degrees about x leaves its outward normal exactly between
        // two axes. Vanilla compares dot products in FLOAT, where (float) cos(45deg) equals
        // (float) sin(45deg), so Direction.getNearest ties and its iteration order decides:
        // DOWN for the +45 normal (0, -0.707, 0.707) and UP for the -45 normal
        // (0, 0.707, 0.707). Double math never ties (cos and sin of 45 degrees differ by one
        // ulp toward the original axis) and would shade both faces 0.8.
        BufferedImage white = solid(WHITE);
        ModelElement down45 = new ModelElement(0, 0, 0, 16, 16, 1,
            new ModelElement.Rotation(45, ModelElement.Axis.X, 8, 8, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.ModelInstance downModel = new ElementModelRenderer.ModelInstance(
            List.of(down45), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(),
            ElementModelRenderer.GuiLight.SIDE);
        ElementModelRenderer.Raster down = ElementModelRenderer.render(List.of(downModel), 4, false,
            lookup(white), CONTEXT);
        assertEquals(0xFF808080, down.image().getRGB(32, 16),
            "+45 about x re-keys the south normal to DOWN: shade 0.5, round(127.5) = 128");

        ModelElement up45 = new ModelElement(0, 0, 0, 16, 16, 1,
            new ModelElement.Rotation(-45, ModelElement.Axis.X, 8, 8, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.ModelInstance upModel = new ElementModelRenderer.ModelInstance(
            List.of(up45), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(),
            ElementModelRenderer.GuiLight.SIDE);
        ElementModelRenderer.Raster up = ElementModelRenderer.render(List.of(upModel), 4, false,
            lookup(white), CONTEXT);
        assertEquals(WHITE, up.image().getRGB(32, 48),
            "-45 about x re-keys the south normal to UP: shade 1.0");
    }

    @Test
    void elementRotationKeepsSouthShadeUnderSideLight() {
        // A z-axis rotation leaves the south normal untouched: the side-lit shade stays the
        // north/south 0.8 regardless of the in-plane spin.
        BufferedImage white = solid(WHITE);
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1,
            new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(),
            ElementModelRenderer.GuiLight.SIDE);
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 4, false,
            lookup(white), CONTEXT);
        assertEquals(0xFFCCCCCC, raster.image().getRGB(32, 32));
    }

    @Test
    void orthographicPathMatchesTheFlatPathForAxisAlignedGeometry() {
        // An active element rotation forces the whole model through the orthographic pipeline.
        // With an escort element rotated entirely outside the slot box, the remaining
        // axis-aligned quad must rasterize pixel-identically to the flat fast path - the two
        // pipelines share coverage and sampling conventions.
        ModelElement escort = new ModelElement(20, 0, 0, 28, 8, 1,
            new ModelElement.Rotation(45, ModelElement.Axis.Z, 24, 4, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));

        ElementModelRenderer.Raster flat = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(face(), null));
        ElementModelRenderer.Raster ortho = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(face(), null), escort);
        ImageAssertions.assertPixelsEqual(flat.image(), ortho.image(), "identity through the orthographic path");

        ElementModelRenderer.Raster flatMirror = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        ElementModelRenderer.Raster orthoMirror = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()), escort);
        ImageAssertions.assertPixelsEqual(flatMirror.image(), orthoMirror.image(),
            "the snapped mirror through the orthographic path");
    }

    @Test
    void mirroredModelWithRotatedElementKeepsExactOversizedExtents() {
        // A mirror-classified model forced through the orthographic pipeline by a rotated
        // element must keep the exact Ry(180) projection: with the raw Math.sin(Math.PI)
        // residue the mirrored quad's left edge lands at -9.8e-16 gui units and the oversized
        // canvas gains a phantom column (floor of a barely-negative extent reports offset -1).
        ModelElement diamond = new ModelElement(6, 6, 0, 10, 10, 1,
            new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, true,
            fullQuad(null, face()), diamond);
        assertEquals(0, raster.offsetX(), "the mirrored quad spans exactly gui [0, 16)");
        assertEquals(0, raster.offsetY());
        assertEquals(64, raster.image().getWidth());
        assertEquals(64, raster.image().getHeight());
        assertEquals(RED, raster.image().getRGB(0, 0), "the north face still reads upright");
    }

    @Test
    void negativeZScaleThroughTheOrthographicPathMatchesTheFlatPath() {
        // Scale (1, 1, -1) mirrors the cuboid through the slot plane: its determinant is
        // negative, so every projected winding flips and the culling test must flip with it
        // (the north face becomes the visible one, exactly like the pinned flat path). An
        // escort element rotated entirely outside the slot box forces the orthographic
        // pipeline without touching the visible pixels.
        ModelElement escort = new ModelElement(20, 0, 0, 28, 8, 1,
            new ModelElement.Rotation(45, ModelElement.Axis.Z, 24, 4, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        GuiTransform mirrorByScale = new GuiTransform(0, 0, 0, 0, 0, 0, 1, 1, -1);

        ElementModelRenderer.Raster flat = render(quadrants(), mirrorByScale, 4, false,
            fullQuad(null, face()));
        ElementModelRenderer.Raster ortho = render(quadrants(), mirrorByScale, 4, false,
            fullQuad(null, face()), escort);
        assertEquals(GREEN, ortho.image().getRGB(0, 0),
            "the north face survives the flipped-winding cull and reads mirrored");
        ImageAssertions.assertPixelsEqual(flat.image(), ortho.image(),
            "negative-determinant culling matches the flat path's pinned face choice");
    }

    @Test
    void quarterTurnRotationsShowEachSideFaceUpright() {
        // Exact quarter turns bring each of the four remaining faces flush to the viewer, and
        // every face must read its texture upright: the default uv origin at the screen
        // top-left. Pins the faceCorners orientation and the defaultUv projection of
        // EAST/WEST/UP/DOWN, which per-face solid colors cannot distinguish.
        record View(ModelElement.Direction direction, GuiTransform transform) {
        }
        List<View> views = List.of(
            new View(ModelElement.Direction.EAST, new GuiTransform(0, -90, 0, 0, 0, 0, 1, 1, 1)),
            new View(ModelElement.Direction.WEST, new GuiTransform(0, 90, 0, 0, 0, 0, 1, 1, 1)),
            new View(ModelElement.Direction.UP, new GuiTransform(90, 0, 0, 0, 0, 0, 1, 1, 1)),
            new View(ModelElement.Direction.DOWN, new GuiTransform(-90, 0, 0, 0, 0, 0, 1, 1, 1)));
        for (View view : views) {
            ModelElement cube = new ModelElement(0, 0, 0, 16, 16, 16, null, true,
                Map.of(view.direction(), face()));
            ElementModelRenderer.Raster raster = renderFull(instance(view.transform(), cube),
                lookup(quadrants()), 4, false);
            String label = view.direction().name();
            assertEquals(RED, raster.image().getRGB(0, 0), label + " reads its top-left texel top-left");
            assertEquals(GREEN, raster.image().getRGB(63, 0), label + " top-right");
            assertEquals(BLUE, raster.image().getRGB(0, 63), label + " bottom-left");
            assertEquals(YELLOW, raster.image().getRGB(63, 63), label + " bottom-right");
        }
    }

    @Test
    void displayTransformComposesScaleThenRotationThenTranslation() {
        // Scale (1, 0.5, 1), rotation [30, 225, 0], translation (2, 3, 0): scaling y BEFORE
        // the rotation compresses the rotated y extent to +-9.121 about the translated center
        // (rotating first would give +-6.29), and translating AFTER the rotation shifts gui x
        // by exactly +2 (translating first would rotate the offset to -1.41). Extents and
        // face centers are hand-derived from the [30, 225, 0] matrix in the class comment.
        ElementModelRenderer.Raster raster = renderFull(
            cube(new GuiTransform(30, 225, 0, 2, 3, 0, 1, 0.5f, 1),
                ElementModelRenderer.GuiLight.FRONT, true), cubeColors(), 4, true);
        assertEquals(-6, raster.offsetX(), "floor((10 - 11.3137) * 4)");
        assertEquals(-17, raster.offsetY(), "floor((5 - 9.1210) * 4)");
        assertEquals(92, raster.image().getWidth(), "ceil((10 + 11.3137) * 4) - floor(-5.2548)");
        assertEquals(81, raster.image().getHeight(), "the slot bottom (gui 16) still bounds the canvas");
        assertEquals(RED, rgbAtGui(raster, 10.125, 1.625, 4), "up face center lands at (10, 1.54)");
        assertEquals(GREEN, rgbAtGui(raster, 15.625, 7.875, 4), "north face center lands at (15.66, 7.83)");
        assertEquals(BLUE, rgbAtGui(raster, 4.375, 7.875, 4), "east face center lands at (4.34, 7.83)");
        assertEquals(Set.of(0, RED, GREEN, BLUE), distinctColors(raster.image()));
    }

    @Test
    void elementRotationAppliesBeforeTheDisplayTransform() {
        // A 45-degree z element rotation, then display scale (1, 0.5, 1) and translation
        // (2, 0, 0): the diamond's tips reach gui x = 10 +- 11.3137 but only y = 8 +- 5.6569.
        // Scaling before the element rotation would shrink x to +-8.49 and stretch y to +-8.49
        // instead; translating before it would swing the +2 offset to (1.41, 1.41).
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 2, 0, 0, 1, 0.5f, 1), 4, true,
            rotatedQuad(new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, false)));
        assertEquals(-6, raster.offsetX(), "left tip at gui 10 - 11.3137");
        assertEquals(0, raster.offsetY(), "the squashed diamond stays inside the slot vertically");
        assertEquals(92, raster.image().getWidth());
        assertEquals(64, raster.image().getHeight());
        assertTrue((rgbAtGui(raster, 20.875, 8.125, 4) >>> 24) != 0, "right tip is painted");
        assertTrue((rgbAtGui(raster, 10.125, 2.625, 4) >>> 24) != 0, "squashed top tip is painted");
        assertEquals(0, rgbAtGui(raster, 10.125, 1.875, 4),
            "above the squashed top tip - scale-before-rotation would paint here");
        assertEquals(0, rgbAtGui(raster, 2.125, 2.125, 4), "outside the diamond");
    }

    @Test
    void freeElementRotationAnglesRender() {
        // Modern vanilla (1.21.6+) allows any element rotation angle; a 30-degree z rotation
        // swings the quad's top-left corner to gui (-2.93, 5.07) - geometry unreachable with
        // the legacy 22.5-degree-step whitelist.
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(30, ModelElement.Axis.Z, 8, 8, 8, false)));
        assertEquals(-12, raster.offsetX(), "floor(-2.9282 * 4)");
        assertEquals(-12, raster.offsetY());
        assertTrue((rgbAtGui(raster, 8.125, 8.125, 4) >>> 24) != 0, "center stays covered");
        assertTrue((rgbAtGui(raster, -2.625, 5.125, 4) >>> 24) != 0, "the swung corner is painted");
        assertEquals(0, rgbAtGui(raster, 15.875, 0.125, 4), "the original top-right corner is vacated");
    }

    @Test
    void elementRotationRenderingIsDeterministic() {
        ElementModelRenderer.Raster first = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(22.5f, ModelElement.Axis.Z, 8, 8, 8, true)));
        ElementModelRenderer.Raster second = render(quadrants(), GuiTransform.IDENTITY, 4, true,
            rotatedQuad(new ModelElement.Rotation(22.5f, ModelElement.Axis.Z, 8, 8, 8, true)));
        assertEquals(first.offsetX(), second.offsetX());
        assertEquals(first.offsetY(), second.offsetY());
        ImageAssertions.assertPixelsEqual(first.image(), second.image(), "repeat element-rotation render");
    }

    // ---------------------------------------------------------------------------------------
    // Shared plumbing.
    // ---------------------------------------------------------------------------------------

    @Test
    void smallTiltsSnapToIdentityAndNearMirrorToMirror() {
        ElementModelRenderer.Raster tilted = render(quadrants(),
            new GuiTransform(0, 2, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(face(), null));
        assertEquals(RED, tilted.image().getRGB(0, 0), "MCC's 2-degree tilt renders as identity");

        ElementModelRenderer.Raster nearMirror = render(quadrants(),
            new GuiTransform(0, 184, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        assertEquals(RED, nearMirror.image().getRGB(0, 0), "184 degrees snaps to the mirror");

        ElementModelRenderer.Raster negativeMirror = render(quadrants(),
            new GuiTransform(0, -180, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        assertEquals(RED, negativeMirror.image().getRGB(0, 0));
    }

    @Test
    void rotationToleranceBoundariesAreInclusive() {
        // Exactly 5 degrees is identity and exactly 175 degrees is the mirror - the spec's
        // "|y| <= 5 degrees treated as identity" is an INCLUSIVE bound on both sides of both
        // supported rotations.
        ElementModelRenderer.Raster atFive = render(quadrants(),
            new GuiTransform(0, 5, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(face(), null));
        assertEquals(RED, atFive.image().getRGB(0, 0), "exactly 5 degrees snaps to identity");

        ElementModelRenderer.Raster atMinusFive = render(quadrants(),
            new GuiTransform(0, -5, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(face(), null));
        assertEquals(RED, atMinusFive.image().getRGB(0, 0));

        ElementModelRenderer.Raster at175 = render(quadrants(),
            new GuiTransform(0, 175, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        assertEquals(RED, at175.image().getRGB(0, 0), "exactly 175 degrees snaps to the mirror");

        ElementModelRenderer.Raster at185 = render(quadrants(),
            new GuiTransform(0, 185, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        assertEquals(RED, at185.image().getRGB(0, 0), "exactly 185 degrees snaps to the mirror");

        // Just past the tolerance on either side throws.
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(0, 5.5f, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(face(), null)));
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(0, 174.5f, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face())));
    }

    @Test
    void neitherNorthNorSouthRendersNothingInTheFlatProjection() {
        ModelElement sideways = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.UP, face(), ModelElement.Direction.EAST, face()));
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 2, false, sideways);
        for (int y = 0; y < 32; y += 4) {
            for (int x = 0; x < 32; x += 4) {
                assertEquals(0, raster.image().getRGB(x, y));
            }
        }
    }

    @Test
    void backFaceOnlyRendersNothingUnderIdentity() {
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 2, false,
            fullQuad(null, face()));
        assertEquals(0, raster.image().getRGB(16, 16));
    }

    @Test
    void faceRotationTurnsTheTextureClockwise() {
        ElementModelRenderer.Raster r90 = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(new ModelElement.Face(null, "#main", 90, -1), null));
        assertEquals(BLUE, r90.image().getRGB(0, 0), "90 degrees clockwise: BL texel reaches the top-left");
        assertEquals(RED, r90.image().getRGB(63, 0), "the original TL texel lands top-right");
        assertEquals(GREEN, r90.image().getRGB(63, 63));

        ElementModelRenderer.Raster r180 = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(new ModelElement.Face(null, "#main", 180, -1), null));
        assertEquals(YELLOW, r180.image().getRGB(0, 0));
        assertEquals(RED, r180.image().getRGB(63, 63));

        ElementModelRenderer.Raster r270 = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(new ModelElement.Face(null, "#main", 270, -1), null));
        assertEquals(GREEN, r270.image().getRGB(0, 0));
        assertEquals(RED, r270.image().getRGB(0, 63), "270 clockwise: TL texel lands bottom-left");
    }

    @Test
    void reversedUvMirrorsTheTexture() {
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(new ModelElement.Face(new ModelElement.FaceUv(16, 0, 0, 16), "#main", 0, -1), null));
        assertEquals(GREEN, raster.image().getRGB(0, 0), "reversed u mirrors horizontally");
        assertEquals(RED, raster.image().getRGB(63, 0));
    }

    @Test
    void tintMultipliesTintedFacesOnly() {
        BufferedImage white = solid(0xFFFFFFFF);
        ModelElement tintedQuad = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, 0)));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(tintedQuad), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(0xFF8000));
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 2, false,
            lookup(white), CONTEXT);
        assertEquals(0xFFFF8000, raster.image().getRGB(16, 16));
    }

    @Test
    void tintAppliesInTheOrthographicPipelineToo() {
        BufferedImage white = solid(0xFFFFFFFF);
        ModelElement tintedQuad = new ModelElement(0, 0, 0, 16, 16, 1,
            new ModelElement.Rotation(45, ModelElement.Axis.Z, 8, 8, 8, false), true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, 0)));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(tintedQuad), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(0xFF8000));
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 2, false,
            lookup(white), CONTEXT);
        assertEquals(0xFFFF8000, raster.image().getRGB(16, 16), "the rotated diamond still covers the center");
    }

    @Test
    void tintIndexBeyondTheListIsWhite() {
        BufferedImage white = solid(0xFFFFFFFF);
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, 3)));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(0xFF8000));
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 2, false,
            lookup(white), CONTEXT);
        assertEquals(0xFFFFFFFF, raster.image().getRGB(16, 16), "absent tint entries are a white no-op");
    }

    @Test
    void textureKeyIndirectionResolvesAndCyclesThrow() {
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#a", 0, -1)));
        ElementModelRenderer.ModelInstance chained = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("a", "#b", "b", "tex:main"), GuiTransform.IDENTITY, List.of());
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(chained), 2, false,
            lookup(quadrants()), CONTEXT);
        assertEquals(RED, raster.image().getRGB(0, 0));

        ElementModelRenderer.ModelInstance cyclic = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("a", "#b", "b", "#a"), GuiTransform.IDENTITY, List.of());
        assertThrows(PackResolveException.class, () -> ElementModelRenderer.render(List.of(cyclic), 2, false,
            lookup(quadrants()), CONTEXT));
    }

    @Test
    void undefinedTextureKeyThrows() {
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#nope", 0, -1)));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of());
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> ElementModelRenderer.render(List.of(model), 2, false, lookup(quadrants()), CONTEXT));
        assertTrue(exception.getMessage().contains("nope"));
    }

    @Test
    void renderingIsDeterministic() {
        ElementModelRenderer.Raster first = render(quadrants(),
            new GuiTransform(0, 0, 0, 1, 2, 0, 1.5f, 0.75f, 1), 6, true, fullQuad(face(), null));
        ElementModelRenderer.Raster second = render(quadrants(),
            new GuiTransform(0, 0, 0, 1, 2, 0, 1.5f, 0.75f, 1), 6, true, fullQuad(face(), null));
        assertEquals(first.offsetX(), second.offsetX());
        assertEquals(first.offsetY(), second.offsetY());
        ImageAssertions.assertPixelsEqual(first.image(), second.image(), "repeat render");
    }

    @Test
    void translationClampsToVanillaLimit() {
        // Translation clamps to +-80: a quad pushed 500 units right lands at gui [80, 96).
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 500, 0, 0, 1, 1, 1), 1, true, fullQuad(face(), null));
        assertEquals(80, raster.offsetX() + firstOpaqueColumn(raster.image()));
    }

    @Test
    void scaleClampsToVanillaLimit() {
        // Scale 8 clamps to 4: the full quad spans gui [-24, 40) about the slot center, never
        // the unclamped [-56, 72).
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 0, 0, 0, 8, 8, 8), 1, true, fullQuad(face(), null));
        assertEquals(-24, raster.offsetX());
        assertEquals(-24, raster.offsetY());
        assertEquals(64, raster.image().getWidth(), "the clamped art is exactly 64 GUI px wide");
        assertEquals(64, raster.image().getHeight());
        assertTrue((raster.image().getRGB(0, 0) >>> 24) != 0, "the art fills its clamped extent");
    }

    @Test
    void negativeScaleClampsToVanillaLimitAndStillMirrors() {
        // Scale -8 clamps to -4: same 64-GUI-px extent, with the horizontal mirror preserved.
        ElementModelRenderer.Raster raster = render(quadrants(),
            new GuiTransform(0, 0, 0, 0, 0, 0, -8, 4, 4), 1, true, fullQuad(face(), null));
        assertEquals(-24, raster.offsetX());
        assertEquals(64, raster.image().getWidth());
        assertEquals(GREEN, raster.image().getRGB(0, 0), "the negative x scale still mirrors");
    }

    @Test
    void modelAtTheElementCapRenders() {
        ElementModelRenderer.ModelInstance model = instance(GuiTransform.IDENTITY,
            manyFullQuads(ElementModelRenderer.MAX_ELEMENTS_PER_MODEL));
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 1, false,
            lookup(quadrants()), CONTEXT);
        assertEquals(RED, raster.image().getRGB(0, 0));
    }

    @Test
    void modelOverTheElementCapFailsLoudly() {
        ElementModelRenderer.ModelInstance model = instance(GuiTransform.IDENTITY,
            manyFullQuads(ElementModelRenderer.MAX_ELEMENTS_PER_MODEL + 1));
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> ElementModelRenderer.render(List.of(model), 1, false, lookup(quadrants()), CONTEXT));
        assertTrue(exception.getMessage().contains("element"), exception.getMessage());
        assertTrue(exception.getMessage().contains(CONTEXT), exception.getMessage());
    }

    @Test
    void canvasAreaOverThePaintedPixelCapFailsBeforeAllocation() {
        // At 2048 px per GUI px the 16-GUI-px slot canvas alone is 2^30 px, past the 64M cap.
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> render(quadrants(), GuiTransform.IDENTITY, 2048, false, fullQuad(face(), null)));
        assertTrue(exception.getMessage().contains("canvas px"), exception.getMessage());
    }

    @Test
    void hugeElementCoordinatesFailBeforeCanvasAllocation() {
        // An oversized render of a crafted element spanning +-1e9 model units would demand a
        // multi-gigapixel canvas; the area cap must reject it before any allocation.
        ModelElement huge = new ModelElement(-1_000_000_000f, -1_000_000_000f, 0,
            1_000_000_000f, 1_000_000_000f, 1, null, true,
            Map.of(ModelElement.Direction.SOUTH, face()));
        assertThrows(PackResolveException.class,
            () -> render(quadrants(), GuiTransform.IDENTITY, 1, true, huge));
    }

    @Test
    void accumulatedFaceAreaOverThePaintedPixelCapFailsLoudly() {
        // At 512 px per GUI px the canvas is exactly the 64M cap; the first full-canvas face
        // pushes the accumulated total over it.
        assertThrows(PackResolveException.class,
            () -> render(quadrants(), GuiTransform.IDENTITY, 512, false, fullQuad(face(), null)));
    }

    private static ModelElement[] manyFullQuads(int count) {
        ModelElement[] elements = new ModelElement[count];
        for (int i = 0; i < count; i++) {
            elements[i] = fullQuad(face(), null);
        }
        return elements;
    }

    private static int firstOpaqueColumn(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, image.getHeight() / 2) >>> 24) != 0) {
                return x;
            }
        }
        return -1;
    }

    private static BufferedImage solid(int argb) {
        return FixturePacks.solid(2, 2, argb);
    }
}
