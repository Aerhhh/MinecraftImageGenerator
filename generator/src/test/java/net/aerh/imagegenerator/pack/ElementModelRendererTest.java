package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Precision tests for the flat front projection: transforms, sampling, ordering, clipping.
 * Coordinates in comments are GUI px relative to the slot box origin unless stated otherwise.
 */
class ElementModelRendererTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int YELLOW = 0xFFFFFF00;
    private static final String CONTEXT = "test model";

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
        return new ModelElement(0, 0, 0, 16, 16, 1, 0, Map.copyOf(faces));
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
        ModelElement left = new ModelElement(0, 0, 0, 8, 16, 1, 0, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#l", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#l", 0, -1)));
        ModelElement right = new ModelElement(8, 0, 0, 16, 16, 1, 0, Map.of(
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
        ModelElement quad = new ModelElement(0, 0, 0, 4, 4, 1, 0,
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
        ModelElement sliver = new ModelElement(0, 0, 0, 0.75f, 16, 1, 0,
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
        ModelElement front = new ModelElement(0, 0, 2, 16, 16, 3, 0,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#front", 0, -1)));
        ModelElement back = new ModelElement(0, 0, 0, 16, 16, 1, 0,
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
        ModelElement back = new ModelElement(0, 0, 0, 16, 16, 1, 0, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#a", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#a", 0, -1)));
        ModelElement front = new ModelElement(0, 0, 2, 16, 16, 3, 0, Map.of(
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
        ModelElement outer = new ModelElement(0, 0, 0, 16, 16, 3, 0, Map.of(
            ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#a", 0, -1),
            ModelElement.Direction.NORTH, new ModelElement.Face(null, "#a", 0, -1)));
        ModelElement inner = new ModelElement(0, 0, 1, 16, 16, 2, 0, Map.of(
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
        ModelElement nearQuad = new ModelElement(0, 0, 2, 16, 16, 3, 0,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, -1)));
        ModelElement farQuad = new ModelElement(0, 0, 0, 16, 16, 1, 0,
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
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, 0,
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
        ModelElement first = new ModelElement(0, 0, 0, 16, 16, 1, 0,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#a", 0, -1)));
        ModelElement second = new ModelElement(0, 0, 0, 16, 16, 1, 0,
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
        ModelElement quad = new ModelElement(0, 12, 0, 4, 16, 1, 0,
            Map.of(ModelElement.Direction.SOUTH, face()));
        ElementModelRenderer.Raster raster = render(quadrants(), GuiTransform.IDENTITY, 2, true, quad);
        assertEquals(32, raster.image().getWidth());
        assertEquals(32, raster.image().getHeight());
        assertEquals(0, raster.offsetX());
        assertEquals(0, raster.offsetY());
    }

    @Test
    void unsupportedGuiRotationsThrow() {
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(30, 225, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(0, 90, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(10, 0, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
        assertThrows(PackResolveException.class, () -> render(quadrants(),
            new GuiTransform(0, 7, 0, 0, 0, 0, 1, 1, 1), 2, false, fullQuad(face(), null)));
    }

    /** The render helper with approximate rotations enabled. */
    private static ElementModelRenderer.Raster renderApproximate(BufferedImage texture, GuiTransform transform,
                                                                 ModelElement... elements) {
        return ElementModelRenderer.render(List.of(instance(transform, elements)), 4, false, true,
            lookup(texture), CONTEXT);
    }

    @Test
    void approximateRotationsPickTheMirrorWhenTheBackFacesTheViewer() {
        // [30,225,0]: cos 30 * cos 225 < 0, so the rotated south normal points away and the
        // mirrored back view is nearest - identical pixels to the exact (0,180,0) mirror.
        ElementModelRenderer.Raster approximated = renderApproximate(quadrants(),
            new GuiTransform(30, 225, 0, 0, 0, 0, 1, 1, 1), fullQuad(null, face()));
        ElementModelRenderer.Raster mirrored = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        ImageAssertions.assertPixelsEqual(mirrored.image(), approximated.image(), "approximated [30,225,0]");
    }

    @Test
    void approximateRotationsPickTheFrontWhenItStaysNearest() {
        // [30,45,10]: cos 30 * cos 45 > 0 - the front view stays nearest; the z component
        // contributes no in-plane spin in the approximation (documented fidelity limit).
        ElementModelRenderer.Raster approximated = renderApproximate(quadrants(),
            new GuiTransform(30, 45, 10, 0, 0, 0, 1, 1, 1), fullQuad(face(), null));
        ElementModelRenderer.Raster front = render(quadrants(), GuiTransform.IDENTITY, 4, false,
            fullQuad(face(), null));
        ImageAssertions.assertPixelsEqual(front.image(), approximated.image(), "approximated [30,45,10]");
    }

    @Test
    void approximateRotationsTreatEdgeOnViewsAsFront() {
        // cos 0 * cos 90 = 0: the tie breaks toward the front view (documented).
        ElementModelRenderer.Raster edgeOn = renderApproximate(quadrants(),
            new GuiTransform(0, 90, 0, 0, 0, 0, 1, 1, 1), fullQuad(face(), null));
        assertEquals(RED, edgeOn.image().getRGB(0, 0), "edge-on approximates as the front view");
    }

    @Test
    void approximateRotationsKeepSupportedRotationsExact() {
        ElementModelRenderer.Raster strict = render(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), 4, false, fullQuad(null, face()));
        ElementModelRenderer.Raster approximate = renderApproximate(quadrants(),
            new GuiTransform(0, 180, 0, 0, 0, 0, 1, 1, 1), fullQuad(null, face()));
        ImageAssertions.assertPixelsEqual(strict.image(), approximate.image(),
            "the flag must not perturb exactly-classified rotations");
    }

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
    void nonZeroElementRotationThrows() {
        ModelElement rotated = new ModelElement(0, 0, 0, 16, 16, 1, 45,
            Map.of(ModelElement.Direction.SOUTH, face()));
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> render(quadrants(), GuiTransform.IDENTITY, 2, false, rotated));
        assertTrue(exception.getMessage().contains("rotation"));
    }

    @Test
    void neitherNorthNorSouthRendersNothing() {
        ModelElement sideways = new ModelElement(0, 0, 0, 16, 16, 1, 0,
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
        ModelElement tintedQuad = new ModelElement(0, 0, 0, 16, 16, 1, 0,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, 0)));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(tintedQuad), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(0xFF8000));
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 2, false,
            lookup(white), CONTEXT);
        assertEquals(0xFFFF8000, raster.image().getRGB(16, 16));
    }

    @Test
    void tintIndexBeyondTheListIsWhite() {
        BufferedImage white = solid(0xFFFFFFFF);
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, 0,
            Map.of(ModelElement.Direction.SOUTH, new ModelElement.Face(null, "#main", 0, 3)));
        ElementModelRenderer.ModelInstance model = new ElementModelRenderer.ModelInstance(
            List.of(quad), Map.of("main", "tex:main"), GuiTransform.IDENTITY, List.of(0xFF8000));
        ElementModelRenderer.Raster raster = ElementModelRenderer.render(List.of(model), 2, false,
            lookup(white), CONTEXT);
        assertEquals(0xFFFFFFFF, raster.image().getRGB(16, 16), "absent tint entries are a white no-op");
    }

    @Test
    void textureKeyIndirectionResolvesAndCyclesThrow() {
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, 0,
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
        ModelElement quad = new ModelElement(0, 0, 0, 16, 16, 1, 0,
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
            1_000_000_000f, 1_000_000_000f, 1, 0,
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
