package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedPackTooltipTest {

    @TempDir
    Path packDir;

    private LoadedPack pack;

    @BeforeEach
    void loadFixturePack() {
        FixturePacks.writeDefaultPack(packDir);
        pack = new LoadedPack(PackId.parse("test:pack"),
            PackSource.directory(packDir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
    }

    private static LoadedPack loadThemeOnlyPack(Path dir) {
        FixturePacks.writeTooltipOnlyPack(dir);
        return new LoadedPack(PackId.parse("test:theme"),
            PackSource.directory(dir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
    }

    private static LoadedPack loadTallStripPack(Path dir, PackLimits limits) {
        FixturePacks.writeTallAnimatedTooltipPack(dir);
        return new LoadedPack(PackId.parse("test:tallstrip"),
            PackSource.directory(dir, limits), limits);
    }

    @Test
    void resolvesCompleteStyleWithNineSliceScaling() {
        TooltipSprites sprites = pack.resolveTooltipSprites("testpack:fancy").orElseThrow();
        assertEquals(new GuiScaling.NineSlice(8, 8, new GuiScaling.NineSlice.Border(2, 2, 2, 2), false),
            sprites.background().scaling());
        assertEquals(new GuiScaling.NineSlice(8, 8, new GuiScaling.NineSlice.Border(3, 3, 3, 3), true),
            sprites.frame().scaling());
        assertEquals(0xFF112233, sprites.background().texture().getRGB(4, 4));
        assertEquals(0xFF445566, sprites.frame().texture().getRGB(0, 0));
        assertEquals(0, sprites.frame().texture().getRGB(4, 4), "frame center is transparent");
    }

    @Test
    void styleWithoutMcmetaDefaultsToStretchScaling() {
        TooltipSprites sprites = pack.resolveTooltipSprites("testpack:plain").orElseThrow();
        assertEquals(new GuiScaling.Stretch(), sprites.background().scaling());
        assertEquals(new GuiScaling.Stretch(), sprites.frame().scaling());
        assertEquals(0xFF224466, sprites.background().texture().getRGB(2, 2));
    }

    @Test
    void unknownStyleResolvesEmpty() {
        assertEquals(Optional.empty(), pack.resolveTooltipSprites("testpack:nope"));
    }

    @Test
    void incompleteStyleThrowsInsteadOfSilentFallback() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveTooltipSprites("testpack:half"));
        assertTrue(exception.getMessage().contains("frame"), exception.getMessage());
    }

    @Test
    void malformedGuiScalingMcmetaThrows() {
        assertThrows(PackResolveException.class, () -> pack.resolveTooltipSprites("testpack:brokenmeta"));
    }

    @Test
    void malformedStyleRefIsRejectedAsCallerError() {
        assertThrows(IllegalArgumentException.class, () -> pack.resolveTooltipSprites("a:b:c"));
    }

    @Test
    void bareStyleRefDefaultsToMinecraftNamespace() {
        // "fancy" lives in the testpack namespace; a bare ref means minecraft:fancy, which is absent.
        assertEquals(Optional.empty(), pack.resolveTooltipSprites("fancy"));
    }

    @Test
    void animatedTooltipSpriteUsesFlipbookFirstFrameAndKeepsScaling() {
        TooltipSprites sprites = pack.resolveTooltipSprites("testpack:anim").orElseThrow();
        assertEquals(8, sprites.background().texture().getHeight(), "flipbook cropped to one frame");
        assertEquals(0xFF0000FF, sprites.background().texture().getRGB(4, 4), "frames list starts at index 2 (blue)");
        assertEquals(new GuiScaling.NineSlice(8, 8, new GuiScaling.NineSlice.Border(1, 1, 1, 1), false),
            sprites.background().scaling());
    }

    @Test
    void tallAnimatedStripDecodesUnderSheetCapAndCropsToFirstFrame(@TempDir Path tallDir) {
        // 146x2482 strip: over the 1024 item texture cap, under the 8192 sheet cap.
        LoadedPack tallPack = loadTallStripPack(tallDir, PackLimits.fromSystemProperties());
        TooltipSprites sprites = tallPack.resolveTooltipSprites("testpack:tallstrip").orElseThrow();
        assertEquals(146, sprites.background().texture().getWidth());
        assertEquals(146, sprites.background().texture().getHeight(),
            "flipbook cropped to one width-square frame");
        assertEquals(0xFF112233, sprites.background().texture().getRGB(73, 73),
            "frames list [0..16,{index:0,time:100}] starts at index 0");
        assertEquals(new GuiScaling.NineSlice(146, 146, new GuiScaling.NineSlice.Border(8, 9, 10, 11), true),
            sprites.background().scaling(), "per-side border object and stretch_inner survive the crop");
    }

    @Test
    void tallAnimatedStripRendersNineSliceFromCroppedFirstFrame(@TempDir Path tallDir) {
        LoadedPack tallPack = loadTallStripPack(tallDir, PackLimits.fromSystemProperties());
        GuiSprite background = tallPack.resolveTooltipSprites("testpack:tallstrip").orElseThrow().background();
        BufferedImage rendered = GuiSpriteRenderer.render(background.texture(), background.scaling(), 200, 60);
        assertEquals(0xFFAA0000, rendered.getRGB(0, 0), "top-left corner copied 1:1 from frame 0");
        assertEquals(0xFF00AA00, rendered.getRGB(199, 0), "top-right corner copied 1:1 from frame 0");
        assertEquals(0xFF0000AA, rendered.getRGB(0, 59), "bottom-left corner copied 1:1 from frame 0");
        assertEquals(0xFFAAAA00, rendered.getRGB(199, 59), "bottom-right corner copied 1:1 from frame 0");
        assertEquals(0xFF112233, rendered.getRGB(100, 30), "stretch_inner center sampled from frame 0");
    }

    @Test
    void tallAnimatedStripOverSheetCapFailsLoudly(@TempDir Path tallDir) {
        PackLimits cappedSheets = new PackLimits(20_000, 8L * 1024 * 1024, 1_024, 64L * 1024 * 1024, 2_048);
        LoadedPack tallPack = loadTallStripPack(tallDir, cappedSheets);
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> tallPack.resolveTooltipSprites("testpack:tallstrip"));
        assertTrue(exception.getMessage().contains("2048"), exception.getMessage());
    }

    @Test
    void itemTexturesKeepTheStrictItemCap(@TempDir Path tallDir) {
        // The same 146x2482 dimensions that decode as a tooltip sheet fail as an item texture:
        // the sheet cap is scoped to tooltip sprite usage, not item art.
        LoadedPack tallPack = loadTallStripPack(tallDir, PackLimits.fromSystemProperties());
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> tallPack.resolveSprite("testpack:item/oversized"));
        assertTrue(exception.getMessage().contains("1024"), exception.getMessage());
    }

    @Test
    void itemTextureUnderTheTooltipSpritePathStillFailsAtTheItemCap(@TempDir Path tallDir) {
        // The decode cap is selected by USAGE, never by path: an item model whose layer0 points
        // at the oversized strip stored under assets/*/textures/gui/sprites/tooltip/ must fail
        // at the strict item cap even after the same file decoded fine as a tooltip sheet -
        // otherwise the item image-bomb guard would be bypassable by path choice.
        LoadedPack tallPack = loadTallStripPack(tallDir, PackLimits.fromSystemProperties());
        assertTrue(tallPack.resolveTooltipSprites("testpack:tallstrip").isPresent(),
            "the strip itself decodes as a tooltip sheet");
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> tallPack.resolveSprite("testpack:item/smuggled"));
        assertTrue(exception.getMessage().contains("1024"), exception.getMessage());
    }

    @Test
    void enumeratesOnlyCompleteStylesSorted() {
        assertEquals(List.of("testpack:anim", "testpack:brokenmeta", "testpack:fancy", "testpack:plain"),
            pack.tooltipStyleRefs());
    }

    @Test
    void tooltipOnlyPackRegistersWithoutItemDefinitions(@TempDir Path themeDir) {
        LoadedPack themePack = assertDoesNotThrow(() -> loadThemeOnlyPack(themeDir));
        assertEquals(List.of(FixturePacks.THEME_NAMESPACE + ":ruby"), themePack.tooltipStyleRefs());
        assertTrue(themePack.resolveTooltipSprites(FixturePacks.THEME_NAMESPACE + ":ruby").isPresent());
    }

    @Test
    void tooltipOnlyPackResolvesVanillaDefaultOverride(@TempDir Path themeDir) {
        LoadedPack themePack = loadThemeOnlyPack(themeDir);
        TooltipSprites sprites = themePack.resolveDefaultTooltipSprites().orElseThrow();
        assertEquals(new GuiScaling.NineSlice(8, 8, new GuiScaling.NineSlice.Border(2, 2, 2, 2), false),
            sprites.background().scaling());
        assertEquals(new GuiScaling.Stretch(), sprites.frame().scaling());
        assertEquals(0xFF101010, sprites.background().texture().getRGB(4, 4));
    }

    @Test
    void packWithoutDefaultOverrideResolvesDefaultEmpty() {
        assertEquals(Optional.empty(), pack.resolveDefaultTooltipSprites());
    }

    @Test
    void packWithNeitherItemsNorTooltipSpritesIsRejected(@TempDir Path emptyDir) throws IOException {
        Files.writeString(emptyDir.resolve("pack.mcmeta"), """
            {"pack":{"pack_format":88,"description":"empty"}}""");
        assertThrows(PackLoadException.class, () -> new LoadedPack(PackId.parse("test:empty"),
            PackSource.directory(emptyDir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties()));
    }
}
