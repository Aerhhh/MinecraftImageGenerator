package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftItemGeneratorPackTest {

    @TempDir
    Path packDir;

    private PackRepository repository;
    private PackId packId;

    @BeforeEach
    void registerFixturePack() {
        FixturePacks.writeDefaultPack(packDir);
        repository = new PackRepository();
        packId = repository.register("test:pack", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftItemGenerator.Builder packBuilder(String itemRef) {
        return new MinecraftItemGenerator.Builder()
            .withPack(packId)
            .withPackRepository(repository)
            .withItem(itemRef);
    }

    @Test
    void rendersPackItemUpscaledTo256() {
        BufferedImage image = packBuilder("testpack:item/simple").build().generate().getImage();
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
        assertEquals(0xFFFF0000, image.getRGB(128, 128), "16x16 red sprite upscaled to fill the canvas");
    }

    @Test
    void renders32x32PackItemUpscaledTo256() {
        BufferedImage image = packBuilder("testpack:item/big").build().generate().getImage();
        assertEquals(256, image.getWidth());
        assertEquals(0xFF123456, image.getRGB(128, 128));
    }

    @Test
    void packMissFallsBackToVanilla() {
        BufferedImage viaPack = packBuilder("diamond_sword").build().generate().getImage();
        BufferedImage vanilla = new MinecraftItemGenerator.Builder().withItem("diamond_sword").build()
            .generate().getImage();
        assertEquals(vanilla.getWidth(), viaPack.getWidth());
        for (int y = 0; y < vanilla.getHeight(); y += 16) {
            for (int x = 0; x < vanilla.getWidth(); x += 16) {
                assertEquals(vanilla.getRGB(x, y), viaPack.getRGB(x, y));
            }
        }
    }

    @Test
    void missInBothPackAndVanillaThrowsNamingThePack() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> packBuilder("testpack:item/nope").build().generate());
        assertTrue(exception.getMessage().contains("test:pack"));
    }

    @Test
    void enchantEffectAppliesToPackSprites() {
        var generated = packBuilder("testpack:item/simple").isEnchanted(true).build().generate();
        assertTrue(generated.isAnimated(), "glint pipeline runs on pack sprites too");
    }

    @Test
    void vanillaPackIdRoutesToSpritesheet() {
        BufferedImage viaVanillaId = new MinecraftItemGenerator.Builder()
            .withPack(PackId.VANILLA).withItem("stone").build().generate().getImage();
        BufferedImage plain = new MinecraftItemGenerator.Builder().withItem("stone").build().generate().getImage();
        assertEquals(plain.getRGB(10, 10), viaVanillaId.getRGB(10, 10));
    }

    @Test
    void withPackStringParses() {
        assertThrows(IllegalArgumentException.class,
            () -> new MinecraftItemGenerator.Builder().withPack("not-a-pack-id"));
    }

    @Test
    void cacheKeysDifferAcrossPacks() {
        MinecraftItemGenerator withPack = packBuilder("testpack:item/simple").build();
        MinecraftItemGenerator withoutPack = new MinecraftItemGenerator.Builder()
            .withItem("testpack:item/simple").build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(withPack), GeneratorCacheKey.fromGenerator(withoutPack));
    }
}
