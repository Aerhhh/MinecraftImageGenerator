package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the animation-aware resolution paths of {@link LoadedPack} against synthetic packs. */
class LoadedPackAnimationTest {

    @TempDir
    Path packDir;

    private LoadedPack load(Path root) {
        return new LoadedPack(PackId.parse("test:pack"),
            PackSource.directory(root, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
    }

    @Test
    void animatedLayer0SpriteResolvesItsTimeline() {
        FixturePacks.writeDefaultPack(packDir);
        LoadedPack pack = load(packDir);

        // The "animated" fixture: frametime 3, frames [2, 0, 1] over a red/green/blue flipbook.
        PackAnimatedVisual animation = pack.resolveItemVisualAnimation(
            "testpack:item/animated", CustomModelData.EMPTY, null, 16, false).orElseThrow();

        assertEquals(3, animation.steps().size());
        assertEquals(List.of(3, 3, 3), animation.stepTicks());
        int[] expected = {0xFF0000FF, 0xFFFF0000, 0xFF00FF00};
        for (int step = 0; step < 3; step++) {
            PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class, animation.steps().get(step));
            assertEquals(16, sprite.sprite().getWidth(), "sprites stay at native frame resolution");
            assertEquals(expected[step], sprite.sprite().getRGB(8, 8), "step " + step);
        }
    }

    @Test
    void mixedFrameTimesProduceALongHoldStep() {
        FixturePacks.writeDefaultPack(packDir);
        LoadedPack pack = load(packDir);

        PackAnimatedVisual animation = pack.resolveItemVisualAnimation(
            "testpack:item/animated_hold", CustomModelData.EMPTY, null, 16, false).orElseThrow();

        assertEquals(List.of(2, 2, 100), animation.stepTicks());
        PackItemVisual.Sprite first = assertInstanceOf(PackItemVisual.Sprite.class, animation.steps().get(0));
        PackItemVisual.Sprite hold = assertInstanceOf(PackItemVisual.Sprite.class, animation.steps().get(2));
        assertEquals(first.sprite().getRGB(8, 8), hold.sprite().getRGB(8, 8),
            "the hold step shows frame 0 again");
    }

    @Test
    void staticItemsResolveEmpty() {
        FixturePacks.writeDefaultPack(packDir);
        LoadedPack pack = load(packDir);

        assertEquals(Optional.empty(), pack.resolveItemVisualAnimation(
            "testpack:item/simple", CustomModelData.EMPTY, null, 16, false));
        assertEquals(Optional.empty(), pack.resolveItemVisualAnimation(
            "testpack:item/unknown_item", CustomModelData.EMPTY, null, 16, false));
    }

    @Test
    void invalidFrameIndexFallsBackToStaticWithoutFailing() {
        FixturePacks.writeDefaultPack(packDir);
        LoadedPack pack = load(packDir);

        // The animated_badmeta fixture lists frame 99 of a 2-frame flipbook: the animated
        // resolution warns and resolves empty; the static resolution keeps working.
        assertEquals(Optional.empty(), pack.resolveItemVisualAnimation(
            "testpack:item/animated_badmeta", CustomModelData.EMPTY, null, 16, false));
        assertTrue(pack.resolveItemVisual("testpack:item/animated_badmeta", CustomModelData.EMPTY, 16).isPresent());
    }

    @Test
    void malformedAnimationMcmetaStillCropsTheFirstFrameStatically() {
        FixturePacks.writeDefaultPack(packDir);
        LoadedPack pack = load(packDir);

        // animated_badtime has {"animation":{"frametime":0}} - rejected by the strict
        // full-animation parse, but the STATIC load must keep the pre-full-model first-frame crop
        // (a 16x16 sprite), not fall back to the raw uncropped 16x32 flipbook sheet.
        PackItemVisual.Sprite sprite = assertInstanceOf(PackItemVisual.Sprite.class,
            pack.resolveItemVisual("testpack:item/animated_badtime", CustomModelData.EMPTY, 16).orElseThrow());
        assertEquals(16, sprite.sprite().getHeight(), "the malformed mcmeta still yields the first-frame crop");
        assertEquals(0xFF00AAAA, sprite.sprite().getRGB(8, 8), "frame 0 (teal), not the squashed full sheet");

        // The strict animated resolution still rejects the same mcmeta and falls back to static.
        assertEquals(Optional.empty(), pack.resolveItemVisualAnimation(
            "testpack:item/animated_badtime", CustomModelData.EMPTY, null, 16, false));
    }

    @Test
    void animatedElementsFaceTextureResolvesRasterSteps() {
        FixturePacks.writeElementsPack(packDir);
        LoadedPack pack = load(packDir);

        // The animated_quad fixture: a full-slot quad over the [2, 0, 1] flipbook, frametime 3.
        PackAnimatedVisual animation = pack.resolveItemVisualAnimation(
            "testpack:item/animated_quad", CustomModelData.EMPTY, null, 4, false).orElseThrow();

        assertEquals(List.of(3, 3, 3), animation.stepTicks());
        int[] expected = {0xFF0000FF, 0xFFFF0000, 0xFF00FF00};
        for (int step = 0; step < 3; step++) {
            PackItemVisual.ElementsRaster raster =
                assertInstanceOf(PackItemVisual.ElementsRaster.class, animation.steps().get(step));
            assertEquals(64, raster.image().getWidth(), "clipped slot box at 4 px per GUI px");
            assertEquals(expected[step], raster.image().getRGB(32, 32), "step " + step);
        }
    }

    @Test
    void animatedTooltipStyleResolvesTheAnimatedSpriteOnly() {
        FixturePacks.writeDefaultPack(packDir);
        LoadedPack pack = load(packDir);

        // The anim style: animated background (frametime 2, frames [2, 0, 1]), static frame.
        AnimatedTooltipSprites animated = pack.resolveTooltipSpritesAnimation("testpack:anim").orElseThrow();

        assertEquals(List.of(2, 2, 2), animated.background().frameTicks());
        assertNull(animated.frame(), "the frame sprite is static");
        TooltipSprites step0 = animated.spritesAt(0, 0);
        TooltipSprites step1 = animated.spritesAt(1, 0);
        assertEquals(0xFF0000FF, step0.background().texture().getRGB(4, 4), "frames list starts at flipbook frame 2");
        assertEquals(0xFFFF0000, step1.background().texture().getRGB(4, 4));
        assertEquals(0xFF030303, step0.frame().texture().getRGB(0, 0), "static frame sprite carried through");
    }

    @Test
    void staticTooltipStylesResolveEmptyAnimation() {
        FixturePacks.writeDefaultPack(packDir);
        LoadedPack pack = load(packDir);

        assertEquals(Optional.empty(), pack.resolveTooltipSpritesAnimation("testpack:plain"));
        assertEquals(Optional.empty(), pack.resolveTooltipSpritesAnimation("testpack:unknown_style"));
    }

    @Test
    void tallStripStyleResolvesTheRealPackTimingShape() {
        FixturePacks.writeTallAnimatedTooltipPack(packDir);
        LoadedPack pack = load(packDir);

        AnimatedTooltipSprites animated = pack.resolveTooltipSpritesAnimation("testpack:tallstrip").orElseThrow();

        assertEquals(18, animated.background().frames().size(), "17 strip frames plus the long-hold entry");
        assertEquals(100, animated.background().frames().getLast().timeTicks());
        assertEquals(0, animated.background().frames().getLast().index(), "the hold shows frame 0");
        assertEquals(146, animated.background().frameWidth());
        assertEquals(146, animated.background().frameHeight());
    }

    @Test
    void animatedContainerBackgroundResolvesItsFrames() {
        FixturePacks.writeAnimatedContainerPack(packDir);
        LoadedPack pack = load(packDir);

        PackAnimation animation = pack.resolveContainerBackgroundAnimation().orElseThrow();

        assertEquals(List.of(2, 2), animation.frameTicks());
        assertEquals(0xFF113355, animation.frameImage(0).getRGB(100, 100));
        assertEquals(0xFF553311, animation.frameImage(1).getRGB(100, 100));

        BufferedImage staticBackground = pack.resolveContainerBackground().orElseThrow();
        assertEquals(256, staticBackground.getHeight(), "the static resolution crops the first frame");
        assertEquals(0xFF113355, staticBackground.getRGB(100, 100));
    }

    @Test
    void staticContainerBackgroundResolvesEmptyAnimation() {
        FixturePacks.writeContainerArtPack(packDir);
        LoadedPack pack = load(packDir);
        assertEquals(Optional.empty(), pack.resolveContainerBackgroundAnimation());
    }
}
