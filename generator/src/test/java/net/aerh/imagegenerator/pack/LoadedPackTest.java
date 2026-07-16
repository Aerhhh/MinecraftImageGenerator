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
    void compositesGeneratedLayersOfOneModel() {
        // Regression: real packs stack layer0 + layer1 on ONE item/generated model (the MCC
        // battle_box ball shape); vanilla bakes every contiguous layerN, not just layer0.
        BufferedImage sprite = pack.resolveSprite("testpack:item/two_layer").orElseThrow();
        assertEquals(0xFF00FF00, sprite.getRGB(0, 0), "layer1 overlay pixel paints over the base");
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8), "layer0 base shows elsewhere");
    }

    @Test
    void tintsGeneratedLayersByIndex() {
        // The client bakes layer i with tintindex i: tint 0 must dye only layer0 and tint 1
        // only the layer1 overlay.
        BufferedImage sprite = pack.resolveSprite("testpack:item/two_layer_tinted").orElseThrow();
        assertEquals(0xFF0000FF, sprite.getRGB(0, 0), "layer1 white pixel tinted blue by tint 1");
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8), "layer0 white base tinted red by tint 0");
    }

    @Test
    void generatedLayersStopAtFirstMissingIndex() {
        // Vanilla's ItemModelGenerator walks layer0..layer4 and stops at the first gap, so a
        // model declaring layer0 and layer2 renders layer0 only.
        BufferedImage sprite = pack.resolveSprite("testpack:item/gapped_layer").orElseThrow();
        assertEquals(0xFFFF0000, sprite.getRGB(0, 0), "layer2 must not render across the layer1 gap");
    }

    @Test
    void generatedLayersMergeAcrossParentChain() {
        // Texture maps merge along the parent chain with child entries winning, so a parent's
        // layer1 composes over the child's layer0.
        BufferedImage sprite = pack.resolveSprite("testpack:item/inherited_layer").orElseThrow();
        assertEquals(0xFF00FF00, sprite.getRGB(0, 0), "parent layer1 overlay pixel paints over the base");
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8), "child layer0 base shows elsewhere");
    }

    @Test
    void generatedLayersStopAtTheVanillaLayer4Cap() {
        // Vanilla's ItemModelGenerator bakes layer0..layer4 only: the six_layer fixture's green
        // layer4 pixel must paint and its blue layer5 pixel at the same position must not.
        BufferedImage sprite = pack.resolveSprite("testpack:item/six_layer").orElseThrow();
        assertEquals(0xFF00FF00, sprite.getRGB(0, 0), "layer4 (the last vanilla layer) paints on top");
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8), "the layer0 base shows elsewhere");
    }

    @Test
    void generatedLayerRefsFollowTextureMapIndirections() {
        // Vanilla resolves #key references for generated layers exactly like element faces:
        // layer0 "#icon" -> "testpack:item/simple" through the merged texture map.
        BufferedImage sprite = pack.resolveSprite("testpack:item/indirect_layer").orElseThrow();
        assertEquals(0xFFFF0000, sprite.getRGB(8, 8));
    }

    @Test
    void undefinedGeneratedLayerIndirectionFailsLoudly() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveSprite("testpack:item/broken_indirect_layer"));
        assertTrue(exception.getMessage().contains("void"), exception.getMessage());
    }

    @Test
    void spritePathWalksTheFullParentChainLikeItemVisuals() {
        // A layer0 below a BROKEN in-pack parent: the multi-layer path merges the whole chain
        // (parents may contribute layer1..layer4), so both public APIs fail loudly and
        // identically instead of silently dropping whatever the parent was meant to supply.
        PackResolveException spriteError = assertThrows(PackResolveException.class,
            () -> pack.resolveSprite("testpack:item/layered_orphan"));
        assertTrue(spriteError.getMessage().contains("nowhere"), spriteError.getMessage());
        PackResolveException visualError = assertThrows(PackResolveException.class,
            () -> pack.resolveItemVisual("testpack:item/layered_orphan", CustomModelData.EMPTY, 4));
        assertEquals(spriteError.getMessage(), visualError.getMessage(),
            "the sprite and item-visual APIs agree on the failure");
    }

    @Test
    void animatedItemRendersFirstFramesListEntry() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/animated").orElseThrow();
        assertEquals(16, sprite.getHeight(), "flipbook cropped to one frame");
        assertEquals(0xFF0000FF, sprite.getRGB(8, 8), "frames list starts at index 2 (blue)");
    }

    @Test
    void emissiveAlphaPreservedForNonHypixelPacks() {
        // Alpha 252 is a Hypixel SkyBlock shader convention; other packs may ship legitimate
        // alpha-252 pixels which must survive decode untouched.
        BufferedImage sprite = pack.resolveSprite("testpack:item/emissive").orElseThrow();
        assertEquals((252 << 24) | 0xFFAA00, sprite.getRGB(0, 0));
    }

    @Test
    void emissiveAlphaNormalizedForHypixelSkyblockPack() {
        LoadedPack hypixelPack = new LoadedPack(PackId.parse("hypixel:skyblock"),
            PackSource.directory(packDir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
        BufferedImage sprite = hypixelPack.resolveSprite("testpack:item/emissive").orElseThrow();
        assertEquals(0xFFFFAA00, sprite.getRGB(0, 0), "alpha 252 becomes opaque under the Hypixel convention");
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
        assertTrue(exception.getMessage().contains("testpack:item/special"),
            "dispatch errors name the offending item like every other resolve error: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("test:pack"),
            "dispatch errors name the pack: " + exception.getMessage());
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
    void emptyCompositeThrowsInsteadOfSilentVanillaFallback() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> pack.resolveSprite("testpack:item/empty_composite"));
        assertTrue(exception.getMessage().contains("empty_composite"));
    }

    @Test
    void resolvesBigTextureAtFullSize() {
        BufferedImage sprite = pack.resolveSprite("testpack:item/big").orElseThrow();
        assertEquals(32, sprite.getWidth());
        assertEquals(32, sprite.getHeight());
        assertEquals(0xFF123456, sprite.getRGB(16, 16));
    }
}
