package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.PackResolveException;
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
}
