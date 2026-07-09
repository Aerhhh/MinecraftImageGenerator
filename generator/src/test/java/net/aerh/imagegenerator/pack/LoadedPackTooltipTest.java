package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
