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
            () -> packBuilder().withItem("testpack:item/rotated").build().generate());
        assertThrows(PackResolveException.class,
            () -> packBuilder().withItem("testpack:item/badspin").build().generate());
        assertThrows(PackResolveException.class,
            () -> packBuilder().withItem("testpack:item/mixed").build().generate());
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
    void approximateGuiRotationsRenderTheUnsupportedRotation() {
        // [30,225,0] picks the mirrored back view (cos 30 * cos 225 < 0): identical pixels to
        // the explicit (0,180,0) mirror of the same model.
        BufferedImage approximated = packBuilder().withItem("testpack:item/badspin")
            .withApproximateGuiRotations(true).build().generate().getImage();
        BufferedImage mirrored = packBuilder().withItem("testpack:item/mirrored").build()
            .generate().getImage();
        ImageAssertions.assertPixelsEqual(mirrored, approximated, "approximated [30,225,0]");

        BufferedImage repeat = packBuilder().withItem("testpack:item/badspin")
            .withApproximateGuiRotations(true).build().generate().getImage();
        ImageAssertions.assertPixelsEqual(approximated, repeat, "approximation is deterministic");
    }

    @Test
    void unsupportedRotationStillThrowsWithoutTheFlag() {
        assertThrows(PackResolveException.class,
            () -> packBuilder().withItem("testpack:item/badspin")
                .withApproximateGuiRotations(false).build().generate());
    }

    @Test
    void cacheKeysDifferAcrossApproximateGuiRotations() {
        MinecraftItemGenerator strict = packBuilder().withItem("testpack:item/badspin").build();
        MinecraftItemGenerator approximate = packBuilder().withItem("testpack:item/badspin")
            .withApproximateGuiRotations(true).build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(strict), GeneratorCacheKey.fromGenerator(approximate),
            "the approximation flag changes rendered pixels, so it must enter the cache key");
    }
}
