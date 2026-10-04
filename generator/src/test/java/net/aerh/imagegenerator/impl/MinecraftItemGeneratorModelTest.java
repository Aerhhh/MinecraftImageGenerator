package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.CustomModelData;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static net.aerh.imagegenerator.testsupport.CustomModelDatas.floats;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wave 4: withItemModel + withCustomModelData end to end through the item generator. */
class MinecraftItemGeneratorModelTest {

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void registerFixturePack() {
        FixturePacks.writeElementsPack(packDir);
        repository = new PackRepository();
        packId = repository.register("test:elements", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftItemGenerator.Builder packBuilder() {
        return new MinecraftItemGenerator.Builder()
            .withPack(packId)
            .withPackRepository(repository);
    }

    @Test
    void elementsModelRendersThroughWithItem() {
        BufferedImage image = packBuilder().withItem("testpack:item/flat").build().generate().getImage();
        assertEquals(256, image.getWidth(), "elements rasterize at the 256-per-16 canvas convention");
        assertEquals(256, image.getHeight());
        assertEquals(0xFFFF0000, image.getRGB(64, 128), "paint's left half is red");
        assertEquals(0xFF0000FF, image.getRGB(192, 128), "paint's right half is blue");
    }

    @Test
    void withItemModelRendersTheSameElementsModel() {
        BufferedImage viaItem = packBuilder().withItem("testpack:item/flat").build().generate().getImage();
        BufferedImage viaModel = packBuilder().withItemModel("testpack:item/flat").build().generate().getImage();
        ImageAssertions.assertPixelsEqual(viaItem, viaModel, "withItemModel");
    }

    @Test
    void withItemModelEvaluatesCustomModelData() {
        BufferedImage blue = packBuilder().withItemModel("testpack:item/gauge")
            .withCustomModelData(floats(2.0f)).build().generate().getImage();
        assertEquals(0xFF0000FF, blue.getRGB(128, 128));

        BufferedImage green = packBuilder().withItemModel("testpack:item/gauge")
            .withCustomModelData(floats(1.5f)).build().generate().getImage();
        assertEquals(0xFF00FF00, green.getRGB(128, 128));

        BufferedImage fallback = packBuilder().withItemModel("testpack:item/gauge").build().generate().getImage();
        assertEquals(0xFF808080, fallback.getRGB(128, 128), "no data falls back");
    }

    @Test
    void withItemEvaluatesCustomModelDataToo() {
        BufferedImage tinted = packBuilder().withItem("testpack:item/colored")
            .withCustomModelData(new CustomModelData(List.of(), List.of(), List.of(), List.of(0x00FF00)))
            .build().generate().getImage();
        assertEquals(0xFF00FF00, tinted.getRGB(128, 128));
    }

    @Test
    void oversizedItemProducesTheFullExtentImage() {
        BufferedImage image = packBuilder().withItem("testpack:item/oversized").build().generate().getImage();
        assertEquals(512, image.getWidth(), "gui [-8, 24) at 16 px per GUI px");
        assertEquals(512, image.getHeight());
        assertEquals(0xFFFF0000, image.getRGB(0, 256));
        assertEquals(0xFF0000FF, image.getRGB(511, 256));
    }

    @Test
    void withItemModelBareRefFallsBackToVanilla() {
        BufferedImage viaModel = packBuilder().withItemModel("stone").build().generate().getImage();
        BufferedImage vanilla = new MinecraftItemGenerator.Builder().withItem("stone").build()
            .generate().getImage();
        ImageAssertions.assertPixelsEqual(vanilla, viaModel, "vanilla fallback");
    }

    @Test
    void withItemModelWithoutPackFallsBackToVanilla() {
        BufferedImage viaModel = new MinecraftItemGenerator.Builder()
            .withItemModel("minecraft:diamond_sword").build().generate().getImage();
        BufferedImage vanilla = new MinecraftItemGenerator.Builder().withItem("diamond_sword").build()
            .generate().getImage();
        ImageAssertions.assertPixelsEqual(vanilla, viaModel, "namespaced vanilla fallback");
    }

    @Test
    void withItemModelMissEverywhereThrowsNamingThePack() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> packBuilder().withItemModel("testpack:item/nope").build().generate());
        assertTrue(exception.getMessage().contains("test:elements"));
    }

    @Test
    void resolveFailuresPropagateLoudly() {
        assertThrows(PackResolveException.class,
            () -> packBuilder().withItem("testpack:item/badspin").build().generate());
        assertThrows(PackResolveException.class,
            () -> packBuilder().withItem("testpack:item/mixed").build().generate());
    }

    @Test
    void elementRotationsRenderWithoutAnyFlag() {
        // The rotated fixture (45-degree element rotation, no gui_light so the vanilla side
        // default applies) renders through the orthographic pipeline in strict mode: the
        // white diamond covers the slot center at the south-face shade 0.8.
        BufferedImage image = packBuilder().withItem("testpack:item/rotated").build().generate().getImage();
        assertEquals(0xFFCCCCCC, image.getRGB(128, 128));
        assertEquals(0, image.getRGB(8, 8), "the original quad corner rotates away");
    }

    /**
     * Regression: unlike container slots (which drop item modifiers for elements-model slot
     * items with a warning), the standalone item generator applies its effect pipeline to
     * elements renders like any other render. The README documents both behaviors; this pins
     * the item-generator side so the two paths cannot drift silently.
     */
    @Test
    void effectPipelineAppliesToElementsRenders() {
        GeneratedObject plain = packBuilder().withItem("testpack:item/flat").build().generate();
        GeneratedObject enchanted = packBuilder().withItem("testpack:item/flat")
            .isEnchanted(true).build().generate();
        GeneratedObject hovered = packBuilder().withItem("testpack:item/flat")
            .withHoverEffect(true).build().generate();

        assertFalse(plain.isAnimated(), "the unmodified elements render stays static");
        assertTrue(enchanted.isAnimated(), "the enchant glint animates the elements render");
        ImageAssertions.assertPixelsDiffer(plain.getImage(), enchanted.getImage(),
            "glint over the elements raster");
        ImageAssertions.assertPixelsDiffer(plain.getImage(), hovered.getImage(),
            "hover over the elements raster");
    }

    @Test
    void spritePathIsUnchangedForLayer0Items() {
        BufferedImage image = packBuilder().withItem("testpack:item/plain_sprite").build().generate().getImage();
        assertEquals(256, image.getWidth());
        assertEquals(0xFFAA5500, image.getRGB(128, 128));
    }

    @Test
    void renderingIsDeterministic() {
        BufferedImage first = packBuilder().withItem("testpack:item/oversized").build().generate().getImage();
        BufferedImage second = packBuilder().withItem("testpack:item/oversized").build().generate().getImage();
        ImageAssertions.assertPixelsEqual(first, second, "repeat render");
    }

    @Test
    void builderRejectsItemAndItemModelTogether() {
        MinecraftItemGenerator.Builder builder = new MinecraftItemGenerator.Builder()
            .withItem("stone").withItemModel("testpack:item/flat");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void builderRejectsNeitherItemNorItemModel() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftItemGenerator.Builder().build());
    }

    @Test
    void builderRejectsBlankArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftItemGenerator.Builder().withItemModel(" "));
        assertThrows(NullPointerException.class,
            () -> new MinecraftItemGenerator.Builder().withCustomModelData(null));
    }

    @Test
    void cacheKeysDifferAcrossCustomModelData() {
        MinecraftItemGenerator plain = packBuilder().withItem("testpack:item/gauge").build();
        MinecraftItemGenerator withData = packBuilder().withItem("testpack:item/gauge")
            .withCustomModelData(floats(2.0f)).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(plain), GeneratorCacheKey.fromGenerator(withData),
            "custom model data must enter the render cache key");
    }

    @Test
    void cacheKeysDifferBetweenItemAndItemModelAddressing() {
        MinecraftItemGenerator viaItem = packBuilder().withItem("testpack:item/flat").build();
        MinecraftItemGenerator viaModel = packBuilder().withItemModel("testpack:item/flat").build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(viaItem), GeneratorCacheKey.fromGenerator(viaModel));
    }

    @Test
    void withItemDamageDrivesNormalizedDamageDispatch() {
        BufferedImage worn = packBuilder().withItem("testpack:item/worn")
            .withItemDamage(90, 100).build().generate().getImage();
        assertEquals(0xFF0000FF, worn.getRGB(128, 128), "0.9 crosses the 0.75 threshold");

        BufferedImage lightlyWorn = packBuilder().withItem("testpack:item/worn")
            .withItemDamage(50, 100).build().generate().getImage();
        assertEquals(0xFF00FF00, lightlyWorn.getRGB(128, 128), "0.5 crosses the 0.25 threshold only");

        BufferedImage pristine = packBuilder().withItem("testpack:item/worn").build().generate().getImage();
        assertEquals(0xFF808080, pristine.getRGB(128, 128), "unset damage evaluates the property at 0");
    }

    @Test
    void withItemDamageDrivesRawDamageDispatch() {
        BufferedImage broken = packBuilder().withItem("testpack:item/worn_raw")
            .withItemDamage(3, 100).build().generate().getImage();
        assertEquals(0xFFFF0000, broken.getRGB(128, 128), "raw damage 3 meets the raw threshold 3");

        BufferedImage nearlyNew = packBuilder().withItem("testpack:item/worn_raw")
            .withItemDamage(2, 100).build().generate().getImage();
        assertEquals(0xFF808080, nearlyNew.getRGB(128, 128));
    }

    @Test
    void withItemDamageValidatesItsArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftItemGenerator.Builder().withItemDamage(-1, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftItemGenerator.Builder().withItemDamage(0, -1));
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftItemGenerator.Builder().withItemDamage(11, 10));
    }

    @Test
    void cacheKeysDifferAcrossItemDamage() {
        MinecraftItemGenerator pristine = packBuilder().withItem("testpack:item/worn").build();
        MinecraftItemGenerator zeroOfMax = packBuilder().withItem("testpack:item/worn")
            .withItemDamage(0, 100).build();
        MinecraftItemGenerator worn = packBuilder().withItem("testpack:item/worn")
            .withItemDamage(90, 100).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(pristine), GeneratorCacheKey.fromGenerator(worn),
            "item damage must enter the render cache key");
        assertNotEquals(GeneratorCacheKey.fromGenerator(pristine), GeneratorCacheKey.fromGenerator(zeroOfMax),
            "unset damage and damage 0/100 are distinct configurations");
        assertNotEquals(GeneratorCacheKey.fromGenerator(zeroOfMax), GeneratorCacheKey.fromGenerator(worn));
    }

    @Test
    void fullGuiRotationsRenderTheOrthographicProjection() {
        // badspin's [30,225,0] shows the north face (backpaint) as a true rotated
        // parallelogram: at the 16-px-per-GUI-px item canvas, the pixel at gui (12.03, 8.03)
        // inverse-maps to face fractions (0.36, 0.24) and samples backpaint's yellow left
        // half, while gui (4.03, 8.03) falls outside the projected face.
        BufferedImage rotated = packBuilder().withItem("testpack:item/badspin")
            .withFullGuiRotations(true).build().generate().getImage();
        assertEquals(0xFFFFFF00, rotated.getRGB(192, 128), "backpaint lands right of the pivot");
        assertEquals(0, rotated.getRGB(64, 128), "the rotated quad vacates the slot's left side");

        BufferedImage repeat = packBuilder().withItem("testpack:item/badspin")
            .withFullGuiRotations(true).build().generate().getImage();
        ImageAssertions.assertPixelsEqual(rotated, repeat, "the orthographic projection is deterministic");
    }

    @Test
    void unsupportedRotationStillThrowsWithoutTheFlag() {
        assertThrows(PackResolveException.class,
            () -> packBuilder().withItem("testpack:item/badspin")
                .withFullGuiRotations(false).build().generate());
    }

    @Test
    void cacheKeysDifferAcrossFullGuiRotations() {
        MinecraftItemGenerator strict = packBuilder().withItem("testpack:item/badspin").build();
        MinecraftItemGenerator full = packBuilder().withItem("testpack:item/badspin")
            .withFullGuiRotations(true).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(strict), GeneratorCacheKey.fromGenerator(full),
            "the full-rotation flag changes rendered pixels, so it must enter the cache key");
    }

    @Test
    void undyedPackItemUsesTheDyeDefault() {
        BufferedImage image = packBuilder().withItem("testpack:item/dyed").build().generate().getImage();
        assertEquals(0xFF3366FF, image.getRGB(128, 128));
    }

    @Test
    void hexColorDyesAPackItem() {
        BufferedImage image = packBuilder().withItem("testpack:item/dyed").withColor("#8932B8")
            .build().generate().getImage();
        assertEquals(0xFF8932B8, image.getRGB(128, 128));
    }

    @Test
    void dyeNameDyesAPackItemLikeItsHex() {
        BufferedImage byName = packBuilder().withItemModel("testpack:item/dyed").withColor("purple")
            .build().generate().getImage();
        BufferedImage byHex = packBuilder().withItemModel("testpack:item/dyed").withColor("#8932B8")
            .build().generate().getImage();
        ImageAssertions.assertPixelsEqual(byHex, byName, "purple is #8932B8");
    }

    @Test
    void dyeDyesFlatSpritePackItemsToo() {
        BufferedImage image = packBuilder().withItem("testpack:item/sprite_dyed").withColor("red")
            .build().generate().getImage();
        assertEquals(0xFFB02E26, image.getRGB(128, 128));
    }

    @Test
    void blankColorLeavesThePackItemUndyed() {
        BufferedImage blank = packBuilder().withItem("testpack:item/dyed").withColor("  ")
            .build().generate().getImage();
        assertEquals(0xFF3366FF, blank.getRGB(128, 128));
    }

    @Test
    void nonDyeColorOnAPackItemFailsClearly() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> packBuilder().withItem("testpack:item/dyed").withColor("speed").build().generate());
        assertTrue(exception.getMessage().contains("`speed` is not a dye color"), exception.getMessage());
        assertTrue(exception.getMessage().contains("testpack:item/dyed"), exception.getMessage());
    }

    @Test
    void nonDyeColorFailsEvenWhenThePackItemHasNoDyeTint() {
        // The color is the item's dyed color whatever its model reads, so a typo never passes
        // silently on one pack item and fails on another.
        assertThrows(GeneratorException.class,
            () -> packBuilder().withItem("testpack:item/flat").withColor("not a dye").build().generate());
    }

    @Test
    void nonDyeColorOnAnAnimatedPackItemFailsClearly() {
        assertThrows(GeneratorException.class,
            () -> packBuilder().withItem("testpack:item/animated_quad").withColor("speed").build().generate());
    }

    @Test
    void dyeColorOnAPackItemWithoutADyeTintChangesNothing() {
        BufferedImage plain = packBuilder().withItem("testpack:item/flat").build().generate().getImage();
        BufferedImage colored = packBuilder().withItem("testpack:item/flat").withColor("red")
            .build().generate().getImage();
        ImageAssertions.assertPixelsEqual(plain, colored, "flat has no dye tint");
    }

    @Test
    void vanillaItemsKeepAcceptingOverlayColorNames() {
        // "speed" is a potion overlay option, not a dye: vanilla items still route the color to
        // the overlay exactly as the data option does.
        BufferedImage viaColor = new MinecraftItemGenerator.Builder().withItem("potion").withColor("speed")
            .build().generate().getImage();
        BufferedImage viaData = new MinecraftItemGenerator.Builder().withItem("potion").withData("speed")
            .build().generate().getImage();
        ImageAssertions.assertPixelsEqual(viaData, viaColor, "color and data feed the overlay alike");
    }

    @Test
    void packMissFallsBackToVanillaWithTheOverlayColor() {
        // A pack that lacks the item hands it to vanilla, where a non-dye overlay name is fine.
        BufferedImage fallback = packBuilder().withItem("potion").withColor("speed").build().generate().getImage();
        BufferedImage vanilla = new MinecraftItemGenerator.Builder().withItem("potion").withColor("speed")
            .build().generate().getImage();
        ImageAssertions.assertPixelsEqual(vanilla, fallback, "vanilla fallback keeps its overlay");
    }

    @Test
    void cacheKeysDifferAcrossDyeColors() {
        MinecraftItemGenerator red = packBuilder().withItem("testpack:item/dyed").withColor("red").build();
        MinecraftItemGenerator blue = packBuilder().withItem("testpack:item/dyed").withColor("blue").build();
        MinecraftItemGenerator undyed = packBuilder().withItem("testpack:item/dyed").build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(red), GeneratorCacheKey.fromGenerator(blue));
        assertNotEquals(GeneratorCacheKey.fromGenerator(red), GeneratorCacheKey.fromGenerator(undyed));
    }
}
