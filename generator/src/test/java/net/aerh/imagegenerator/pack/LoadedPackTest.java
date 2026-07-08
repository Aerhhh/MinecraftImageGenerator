package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedPackTest {

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

    @Test
    void indexesNamespaces() {
        assertEquals(Set.of(FixturePacks.NAMESPACE), pack.assetNamespaces());
    }

    @Test
    void resolvesSimpleItemToItsTexture() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/simple").orElseThrow();
        assertEquals(16, sprite.getWidth());
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8));
    }

    @Test
    void resolvesConditionToOnFalseModel() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/conditional").orElseThrow();
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8), "on_false (simple/red), not on_true (in_hand/green)");
    }

    @Test
    void resolvesSelectToGuiCase() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/selecty").orElseThrow();
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8));
    }

    @Test
    void resolvesRangeDispatchToFallback() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/ranged").orElseThrow();
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8));
    }

    @Test
    void compositesLayersInOrder() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/layered").orElseThrow();
        assertEquals(0xFF00FF00, sprite.getRGB(0, 0), "overlay pixel wins at (0,0)");
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8), "base shows elsewhere");
    }

    @Test
    void animatedItemRendersFirstFramesListEntry() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/animated").orElseThrow();
        assertEquals(16, sprite.getHeight(), "flipbook cropped to one frame");
        assertEquals(0xFF0000FF, sprite.getRGB(8, 8), "frames list starts at index 2 (blue)");
    }

    @Test
    void emissiveAlphaIsNormalized() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/emissive").orElseThrow();
        assertEquals(0xFFFFAA00, sprite.getRGB(0, 0));
    }

    @Test
    void unknownItemResolvesEmpty() {
        assertEquals(Optional.empty(), pack.resolveSprite("testpack:item/nope"));
    }

    @Test
    void foreignNamespaceResolvesEmpty() {
        assertEquals(Optional.empty(), pack.resolveSprite("otherpack:item/simple"));
    }

    @Test
    void bareRefResolvesEmpty() {
        assertEquals(Optional.empty(), pack.resolveSprite("diamond_sword"));
    }

    @Test
    void unsupportedNodeThrowsAtResolveNotRegister() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveSprite("testpack:item/special"));
        assertTrue(exception.getMessage().contains("special"));
    }

    @Test
    void malformedItemJsonThrowsAtResolveNotRegister() {
        assertThrows(PackResolveException.class, () -> pack.resolveSprite("testpack:item/malformed"));
    }

    @Test
    void danglingModelRefThrows() {
        assertThrows(PackResolveException.class, () -> pack.resolveSprite("testpack:item/broken_model_ref"));
    }

    @Test
    void modelWithoutLayer0AndVanillaParentThrows() {
        assertThrows(PackResolveException.class, () -> pack.resolveSprite("testpack:item/no_layer0"));
    }

    @Test
    void missingTextureThrowsPackResolveNotPackLoad() {
        assertThrows(PackResolveException.class, () -> pack.resolveSprite("testpack:item/broken_texture_ref"));
    }

    @Test
    void malformedLayer0RefThrowsPackResolveNotIllegalArgument() {
        assertThrows(PackResolveException.class, () -> pack.resolveSprite("testpack:item/malformed_ref"));
    }

    @Test
    void parentCycleThrowsAtDepthCap() {
        assertThrows(PackResolveException.class, () -> pack.resolveSprite("testpack:item/cyclic"));
    }

    @Test
    void resolvesBigTextureAtFullSize() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/big").orElseThrow();
        assertEquals(32, sprite.getWidth());
        assertEquals(32, sprite.getHeight());
        assertEquals(0xFF123456, sprite.getRGB(16, 16));
    }
}
