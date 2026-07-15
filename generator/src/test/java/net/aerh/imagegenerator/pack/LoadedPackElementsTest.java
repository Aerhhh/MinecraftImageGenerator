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
            "2-degree MCC tilt");
    }

    @Test
    void unsupportedGuiRotationThrows() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/badspin", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("gui rotation"));
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
        assertTrue(exception.getMessage().contains("dye"));
        assertTrue(exception.getMessage().contains("testpack:item/unknown_tint"),
            "tint errors name the offending item like every other resolve error: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("test:elements"),
            "tint errors name the pack: " + exception.getMessage());
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
        // Vanilla-style layer0 items commonly carry dye/potion tint sources; they rendered
        // fine (untinted) before tints were parsed, so the sprite branch must not hard-fail.
        PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class,
            pack.resolveItemVisual("testpack:item/sprite_dye_tint", CustomModelData.EMPTY, SCALE).orElseThrow());
        assertEquals(0xFFFFFFFF, sprite.sprite().getRGB(0, 0), "the dye tint is skipped, not applied");
        assertEquals(0xFFFFFFFF, pack.resolveSprite("testpack:item/sprite_dye_tint")
            .orElseThrow().getRGB(0, 0), "resolveSprite keeps rendering too");
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
    void nonZeroElementRotationThrows() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/rotated", CustomModelData.EMPTY, SCALE));
        assertTrue(exception.getMessage().contains("rotation"));
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
