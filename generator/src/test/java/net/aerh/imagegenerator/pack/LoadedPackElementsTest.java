package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end elements resolution through {@link LoadedPack#resolveItemVisual}. */
class LoadedPackElementsTest {

    private static final int SCALE = 4;

    @TempDir
    Path packDir;

    private LoadedPack pack;

    @BeforeEach
    void loadFixturePack() {
        FixturePacks.writeElementsPack(packDir);
        pack = new LoadedPack(PackId.parse("test:elements"),
            PackSource.directory(packDir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
    }

    private PackItemVisual.ElementsRaster raster(String itemRef, CustomModelData data) {
        PackItemVisual visual = pack.resolveItemVisual(itemRef, data, SCALE).orElseThrow();
        return assertInstanceOf(PackItemVisual.ElementsRaster.class, visual);
    }

    @Test
    void flatQuadRendersAtTargetResolution() {
        PackItemVisual.ElementsRaster raster = raster("testpack:item/flat", CustomModelData.EMPTY);
        BufferedImage image = raster.image();
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertEquals(0, raster.offsetX());
        assertEquals(0, raster.offsetY());
        assertFalse(raster.oversized());
        assertEquals(0xFFFF0000, image.getRGB(8, 32), "paint's left half is red");
        assertEquals(0xFF0000FF, image.getRGB(56, 32), "paint's right half is blue");
    }

    @Test
    void mirroredModelShowsTheBackFace() {
        // The (0,180,0) child inherits elements and textures from its parent; the north face
        // (backpaint) becomes visible and reads upright through the mirrored projection.
        BufferedImage image = raster("testpack:item/mirrored", CustomModelData.EMPTY).image();
        assertEquals(0xFFFFFF00, image.getRGB(8, 32), "backpaint's left half is yellow");
        assertEquals(0xFFFF00FF, image.getRGB(56, 32), "backpaint's right half is magenta");
    }

    @Test
    void smallTiltRendersAsIdentity() {
        ImageAssertions.assertPixelsEqual(
            raster("testpack:item/flat", CustomModelData.EMPTY).image(),
            raster("testpack:item/tilted", CustomModelData.EMPTY).image(),
            "2-degree decorative tilt");
    }

    @Test
    void elementFaceTextureCropsToAnimationFirstFrame() {
        // The element texture path shares the flat sprite path's mcmeta handling: a flipbook
        // strip contributes the frames list's FIRST frame, not the whole strip.
        BufferedImage image = raster("testpack:item/animated_quad", CustomModelData.EMPTY).image();
        assertEquals(64, image.getWidth(), "canvas is the slot box, not the strip");
        assertEquals(0xFF0000FF, image.getRGB(32, 8), "frames list starts at index 2 (blue)");
        assertEquals(0xFF0000FF, image.getRGB(32, 56), "the whole quad samples the one frame");
    }

    @Test
    void unsupportedGuiRotationThrows() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/badspin", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("gui rotation"));
    }

    @Test
    void fullGuiRotationsRenderTheOrthographicProjection() {
        // badspin's [30,225,0] turns the south face away; the north face (backpaint) projects
        // as a parallelogram from gui (8, 6.73) with edge vectors (11.31, -5.66) and (0, 13.86).
        // The slot pixel at gui (12.125, 8.125) inverse-maps to face fractions (0.36, 0.25),
        // sampling backpaint's yellow left half; gui (4.125, 8.125) falls outside the face.
        PackItemVisual visual = pack.resolveItemVisual("testpack:item/badspin",
            CustomModelData.EMPTY, null, SCALE, true).orElseThrow();
        BufferedImage image = assertInstanceOf(PackItemVisual.ElementsRaster.class, visual).image();
        assertEquals(0xFFFFFF00, image.getRGB(48, 32), "backpaint's left half lands right of the pivot");
        assertEquals(0, image.getRGB(16, 32), "the rotated quad vacates the slot's left side");
        ImageAssertions.assertPixelsDiffer(
            raster("testpack:item/mirrored", CustomModelData.EMPTY).image(), image,
            "the true projection is not the flat mirror approximation");

        BufferedImage repeat = assertInstanceOf(PackItemVisual.ElementsRaster.class,
            pack.resolveItemVisual("testpack:item/badspin", CustomModelData.EMPTY, null, SCALE, true)
                .orElseThrow()).image();
        ImageAssertions.assertPixelsEqual(image, repeat, "the orthographic projection is deterministic");
    }

    @Test
    void fullGuiRotationsRenderFrontFacingSpinsWithForeshortening() {
        // frontspin's [30,45,10] keeps the south face toward the viewer: the pixel at gui
        // (5.875, 3.625) inverse-maps to face fractions (0.75, 0.51), sampling paint's blue
        // right half - in-plane spin and foreshortening included, unlike the old flat
        // approximation which rendered the untransformed quad.
        PackItemVisual visual = pack.resolveItemVisual("testpack:item/frontspin",
            CustomModelData.EMPTY, null, SCALE, true).orElseThrow();
        BufferedImage image = assertInstanceOf(PackItemVisual.ElementsRaster.class, visual).image();
        assertEquals(0xFF0000FF, image.getRGB(23, 14));
        ImageAssertions.assertPixelsDiffer(
            raster("testpack:item/flat", CustomModelData.EMPTY).image(), image,
            "the rotation visibly transforms the quad");
    }

    @Test
    void fullGuiRotationsLeaveClassifiedRotationsExact() {
        // The flag only affects rotations that would otherwise throw; identity, decorative
        // tilts and the exact mirror keep their strict classification.
        ImageAssertions.assertPixelsEqual(
            raster("testpack:item/mirrored", CustomModelData.EMPTY).image(),
            assertInstanceOf(PackItemVisual.ElementsRaster.class,
                pack.resolveItemVisual("testpack:item/mirrored", CustomModelData.EMPTY, null, SCALE, true)
                    .orElseThrow()).image(),
            "exact mirror under the flag");
    }

    @Test
    void childTextureMapEntryOverridesParent() {
        BufferedImage image = raster("testpack:item/retextured", CustomModelData.EMPTY).image();
        assertEquals(0xFF00FF00, image.getRGB(8, 32), "child #front (green) wins over parent paint");
        assertEquals(0xFF00FF00, image.getRGB(56, 32));
    }

    @Test
    void childDisplayGuiEntryOverridesParentWholesale() {
        // elem_unmirrored declares an EMPTY display.gui over elem_mirrored's (0,180,0): vanilla
        // replaces the whole gui entry, so the child's default rotation (identity) wins and the
        // render matches the un-mirrored flat model exactly.
        ImageAssertions.assertPixelsEqual(
            raster("testpack:item/flat", CustomModelData.EMPTY).image(),
            raster("testpack:item/unmirrored", CustomModelData.EMPTY).image(),
            "child display.gui override");
    }

    @Test
    void missingInPackParentModelFailsLoudly() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/orphan", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("nope_parent"),
            "a broken parent ref must not silently drop inherited transforms: " + exception.getMessage());
    }

    @Test
    void overDeepParentChainFailsLoudly() {
        // The 9-model elem_deep chain parks display.gui on the 9th ancestor; silently stopping
        // after 8 hops would render at identity scale instead of failing like resolveLayer0.
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/deep", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("exceeds depth"), exception.getMessage());
    }

    @Test
    void rangeDispatchPicksModelsFromFloats() {
        assertEquals(0xFF0000FF, raster("testpack:item/gauge",
            new CustomModelData(List.of(2.0f), List.of(), List.of(), List.of())).image().getRGB(32, 32));
        assertEquals(0xFF00FF00, raster("testpack:item/gauge",
            new CustomModelData(List.of(1.5f), List.of(), List.of(), List.of())).image().getRGB(32, 32));
        assertEquals(0xFF808080, raster("testpack:item/gauge", CustomModelData.EMPTY).image().getRGB(32, 32),
            "missing float falls back");
    }

    @Test
    void conditionPicksModelsFromFlags() {
        assertEquals(0xFF00FF00, raster("testpack:item/flagged",
            new CustomModelData(List.of(), List.of(true), List.of(), List.of())).image().getRGB(32, 32));
        assertEquals(0xFFFF0000, raster("testpack:item/flagged", CustomModelData.EMPTY).image().getRGB(32, 32));
    }

    @Test
    void selectPicksModelsFromStrings() {
        assertEquals(0xFFFF0000, raster("testpack:item/named",
            new CustomModelData(List.of(), List.of(), List.of("ruby"), List.of())).image().getRGB(32, 32));
        assertEquals(0xFF0000FF, raster("testpack:item/named",
            new CustomModelData(List.of(), List.of(), List.of("amber"), List.of())).image().getRGB(32, 32));
        assertEquals(0xFF0000FF, raster("testpack:item/named", CustomModelData.EMPTY).image().getRGB(32, 32));
    }

    @Test
    void customModelDataTintColorsTheWhiteQuad() {
        assertEquals(0xFF00FF00, raster("testpack:item/colored",
            new CustomModelData(List.of(), List.of(), List.of(), List.of(0x00FF00))).image().getRGB(32, 32));
        assertEquals(0xFFFFFFFF, raster("testpack:item/colored", CustomModelData.EMPTY).image().getRGB(32, 32),
            "missing color uses the declared white default - a no-op");
    }

    @Test
    void constantTintApplies() {
        assertEquals(0xFFFF8000,
            raster("testpack:item/constant_tint", CustomModelData.EMPTY).image().getRGB(32, 32));
    }

    @Test
    void unsupportedTintSourceThrows() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/unknown_tint", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("team"));
        assertTrue(exception.getMessage().contains("testpack:item/unknown_tint"),
            "tint errors name the offending item like every other resolve error: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("test:elements"),
            "tint errors name the pack: " + exception.getMessage());
    }

    @Test
    void dyeTintAppliesItsDefaultColor() {
        // No per-item dye data exists in this library, so the REQUIRED default (0x3366FF in the
        // fixture) always colors the quad.
        assertEquals(0xFF3366FF, raster("testpack:item/dyed", CustomModelData.EMPTY).image().getRGB(32, 32));
    }

    @Test
    void constantTintColorsTheFlatSpriteLikeVanilla() {
        // Vanilla's item/generated bakes layer0 with tintindex 0, so constant tints DO color
        // flat sprites in-game - the sprite branch must apply the same tint list the elements
        // branch does.
        PackItemVisual visual = pack.resolveItemVisual("testpack:item/sprite_constant_tint",
            CustomModelData.EMPTY, SCALE).orElseThrow();
        PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class, visual);
        assertEquals(0xFFFF8000, sprite.sprite().getRGB(0, 0));
        assertEquals(0xFFFF8000, pack.resolveSprite("testpack:item/sprite_constant_tint")
            .orElseThrow().getRGB(0, 0), "resolveSprite tints identically");
    }

    @Test
    void customModelDataTintColorsTheFlatSprite() {
        PackItemVisual.Sprite tinted = assertInstanceOf(PackItemVisual.Sprite.class,
            pack.resolveItemVisual("testpack:item/sprite_cmd_tint",
                new CustomModelData(List.of(), List.of(), List.of(), List.of(0x00FF00)), SCALE).orElseThrow());
        assertEquals(0xFF00FF00, tinted.sprite().getRGB(0, 0));

        PackItemVisual.Sprite untinted = assertInstanceOf(PackItemVisual.Sprite.class,
            pack.resolveItemVisual("testpack:item/sprite_cmd_tint", CustomModelData.EMPTY, SCALE).orElseThrow());
        assertEquals(0xFFFFFFFF, untinted.sprite().getRGB(0, 0),
            "missing color uses the declared white default - a no-op");
    }

    @Test
    void unsupportedTintSourceOnTheFlatSpriteIsIgnoredNotFatal() {
        // Vanilla-style layer0 items commonly carry team/potion tint sources; they rendered
        // fine (untinted) before tints were parsed, so the sprite branch must not hard-fail.
        PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class,
            pack.resolveItemVisual("testpack:item/sprite_team_tint", CustomModelData.EMPTY, SCALE).orElseThrow());
        assertEquals(0xFFFFFFFF, sprite.sprite().getRGB(0, 0), "the team tint is skipped, not applied");
        assertEquals(0xFFFFFFFF, pack.resolveSprite("testpack:item/sprite_team_tint")
            .orElseThrow().getRGB(0, 0), "resolveSprite keeps rendering too");
    }

    @Test
    void dyeTintColorsTheFlatSpriteWithItsDefault() {
        PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class,
            pack.resolveItemVisual("testpack:item/sprite_dyed", CustomModelData.EMPTY, SCALE).orElseThrow());
        assertEquals(0xFF3366FF, sprite.sprite().getRGB(0, 0));
        assertEquals(0xFF3366FF, pack.resolveSprite("testpack:item/sprite_dyed")
            .orElseThrow().getRGB(0, 0), "resolveSprite tints identically");
    }

    @Test
    void oversizedItemReportsFullExtent() {
        PackItemVisual.ElementsRaster raster = raster("testpack:item/oversized", CustomModelData.EMPTY);
        assertTrue(raster.oversized());
        assertEquals(128, raster.image().getWidth(), "scale 2 spans gui [-8, 24) at 4 px per GUI px");
        assertEquals(128, raster.image().getHeight());
        assertEquals(-32, raster.offsetX());
        assertEquals(-32, raster.offsetY());
        assertEquals(0xFFFF0000, raster.image().getRGB(0, 64), "red half starts at the far left");
        assertEquals(0xFF0000FF, raster.image().getRGB(127, 64));
    }

    @Test
    void sameModelWithoutTheFlagClipsToTheSlotBox() {
        PackItemVisual.ElementsRaster raster = raster("testpack:item/clipped", CustomModelData.EMPTY);
        assertFalse(raster.oversized());
        assertEquals(64, raster.image().getWidth());
        assertEquals(0, raster.offsetX());
        assertEquals(0xFFFF0000, raster.image().getRGB(0, 32), "the slot box shows the middle of the art");
        assertEquals(0xFF0000FF, raster.image().getRGB(63, 32));
    }

    @Test
    void elementRotationRendersWithoutAnyFlag() {
        // The rotated fixture's 45-degree z rotation renders through the orthographic pipeline
        // even in strict mode - element rotations are vanilla geometry, not a gui-rotation
        // approximation. The model declares no gui_light, so the vanilla side default shades
        // the south face at 0.8: white * 0.8 = 0xCC per channel.
        BufferedImage image = raster("testpack:item/rotated", CustomModelData.EMPTY).image();
        assertEquals(0xFFCCCCCC, image.getRGB(32, 32), "the diamond covers the center, side-shaded");
        assertEquals(0, image.getRGB(2, 2), "the original quad corner rotates away");
    }

    @Test
    void mixedElementsAndSpriteCompositeThrows() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/mixed", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("mixes"));
    }

    @Test
    void spriteItemsReturnTheClassicSpriteBranch() {
        PackItemVisual visual = pack.resolveItemVisual("testpack:item/plain_sprite",
            CustomModelData.EMPTY, SCALE).orElseThrow();
        PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class, visual);
        ImageAssertions.assertPixelsEqual(pack.resolveSprite("testpack:item/plain_sprite").orElseThrow(),
            sprite.sprite(), "sprite branch");
    }

    @Test
    void legacySpritePathStillRejectsElementsModels() {
        // resolveSprite is the flat layer0 API; elements models have no layer0 and fail there
        // exactly as before this wave.
        assertThrows(PackResolveException.class, () -> pack.resolveSprite("testpack:item/flat"));
    }

    @Test
    void bareLayer0TextureRefResolvesInTheMinecraftNamespace() {
        // Vanilla resource location semantics: a bare texture reference lives in the minecraft
        // namespace. The fixture plants a decoy under testpack/ so binding bare references to
        // the model's own namespace would surface as the wrong color.
        BufferedImage sprite = pack.resolveSprite("testpack:item/bare_sprite").orElseThrow();
        assertEquals(0xFF00AA77, sprite.getRGB(0, 0),
            "the layer0 path reads assets/minecraft/textures/item/bare.png, not the testpack decoy");
    }

    @Test
    void bareElementTextureRefResolvesInTheMinecraftNamespace() {
        BufferedImage image = raster("testpack:item/bare_quad", CustomModelData.EMPTY).image();
        assertEquals(0xFF00AA77, image.getRGB(32, 32),
            "the elements path reads assets/minecraft/textures/item/bare.png, not the testpack decoy");
    }

    @Test
    void bareTextureRefsAgreeAcrossTheLayer0AndElementsPaths() {
        // The unified rule, pinned end to end: both texture resolution paths must sample the
        // SAME texture for the same bare reference.
        BufferedImage sprite = pack.resolveSprite("testpack:item/bare_sprite").orElseThrow();
        BufferedImage quad = raster("testpack:item/bare_quad", CustomModelData.EMPTY).image();
        assertEquals(sprite.getRGB(0, 0), quad.getRGB(32, 32));
    }

    @Test
    void chainEndingAtVanillaGeneratedRendersTheFlatSprite() {
        // The pack claims the minecraft namespace (filler model) without shipping the builtin
        // templates - the real-pack shape. The chain must terminate at item/generated with flat
        // layer0 semantics instead of throwing model-not-found.
        PackItemVisual visual = pack.resolveItemVisual("testpack:item/vanilla_exit",
            CustomModelData.EMPTY, SCALE).orElseThrow();
        PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class, visual);
        assertEquals(0xFFFFFFFF, sprite.sprite().getRGB(0, 0));
        assertEquals(0xFFFFFFFF, pack.resolveSprite("testpack:item/vanilla_exit").orElseThrow().getRGB(0, 0),
            "resolveSprite terminates the chain identically");
    }

    @Test
    void chainEndingAtVanillaHandheldRendersTheFlatSprite() {
        PackItemVisual visual = pack.resolveItemVisual("testpack:item/handheld_exit",
            CustomModelData.EMPTY, SCALE).orElseThrow();
        assertEquals(0xFF00FF00, assertInstanceOf(PackItemVisual.Sprite.class, visual).sprite().getRGB(0, 0));
    }

    @Test
    void chainEndingAtOtherMissingVanillaModelStillFailsLoudly() {
        // Only item/generated and item/handheld are known texture-less flat templates; any
        // other missing minecraft-namespace parent was meant to supply something.
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/vanilla_dead_end", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("other_template"), exception.getMessage());
    }

    @Test
    void chainEndingAtVanillaGeneratedWithoutLayer0FailsLoudly() {
        // The builtin templates declare no layer0 of their own: a chain ending there without
        // one anywhere has nothing to render and must say so.
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/generated_no_layer0", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("layer0"), exception.getMessage());
    }

    @Test
    void normalizedDamageDispatchPicksByDamageFraction() {
        assertEquals(0xFF00FF00, damaged("testpack:item/worn", new ItemDamage(50, 100)),
            "0.5 crosses the 0.25 threshold but not 0.75");
        assertEquals(0xFF0000FF, damaged("testpack:item/worn", new ItemDamage(90, 100)),
            "0.9 crosses the 0.75 threshold");
        assertEquals(0xFF808080, damaged("testpack:item/worn", new ItemDamage(10, 100)),
            "0.1 crosses no threshold and falls back");
        assertEquals(0xFF808080, damaged("testpack:item/worn", null),
            "no damage state evaluates the property at 0");
    }

    @Test
    void rawDamageDispatchReadsTheUnnormalizedValue() {
        assertEquals(0xFFFF0000, damaged("testpack:item/worn_raw", new ItemDamage(3, 100)),
            "raw damage 3 meets the raw threshold 3");
        assertEquals(0xFF808080, damaged("testpack:item/worn_raw", new ItemDamage(2, 100)),
            "raw damage 2 does not; the same fraction would under normalize:true only at 300+");
        assertEquals(0xFF808080, damaged("testpack:item/worn_raw", null));
    }

    @Test
    void zeroMaxDamageEvaluatesNormalizedPropertyAtZero() {
        // 0/0 must not leak NaN into threshold comparison; the property reads 0 and the
        // sub-threshold fallback wins.
        assertEquals(0xFF808080, damaged("testpack:item/worn", new ItemDamage(0, 0)));
    }

    /** The center pixel of an elements render resolved with the given damage state. */
    private int damaged(String itemRef, ItemDamage damage) {
        PackItemVisual visual = pack.resolveItemVisual(itemRef, CustomModelData.EMPTY, damage, SCALE, false)
            .orElseThrow();
        return assertInstanceOf(PackItemVisual.ElementsRaster.class, visual).image().getRGB(32, 32);
    }

    @Test
    void unknownItemsResolveEmpty() {
        assertEquals(Optional.empty(),
            pack.resolveItemVisual("testpack:item/nope", CustomModelData.EMPTY, SCALE));
        assertEquals(Optional.empty(),
            pack.resolveItemVisual("bare_ref", CustomModelData.EMPTY, SCALE));
        assertEquals(Optional.empty(),
            pack.resolveItemVisual("other:item/flat", CustomModelData.EMPTY, SCALE));
    }

    @Test
    void renderingIsDeterministic() {
        ImageAssertions.assertPixelsEqual(
            raster("testpack:item/oversized", CustomModelData.EMPTY).image(),
            raster("testpack:item/oversized", CustomModelData.EMPTY).image(),
            "repeat resolve");
    }

    @Test
    void invalidScaleIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> pack.resolveItemVisual("testpack:item/flat", CustomModelData.EMPTY, 0));
    }
}
