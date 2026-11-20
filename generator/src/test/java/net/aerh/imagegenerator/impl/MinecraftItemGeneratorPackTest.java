package net.aerh.imagegenerator.impl;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.effect.EffectPipeline;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.testsupport.BenchmarkArtifacts;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void unregisteredPackThrowsNamingThePack() {
        MinecraftItemGenerator generator = new MinecraftItemGenerator.Builder()
            .withPack(PackId.parse("ghost:pack"))
            .withPackRepository(new PackRepository())
            .withItem("stone")
            .build();
        PackResolveException exception = assertThrows(PackResolveException.class, generator::generate);
        assertTrue(exception.getMessage().contains("ghost:pack"),
            "unregistered packs fail fast at render time, never silently fall back to vanilla");
    }

    @Test
    void enchantEffectAppliesToPackSprites() {
        var generated = packBuilder("testpack:item/simple").isEnchanted(true).build().generate();
        assertTrue(generated.isAnimated(), "glint pipeline runs on pack sprites too");
    }

    @EnabledIfSystemProperty(named = "benchmarks", matches = "true")
    @Test
    void benchmarkDirtBlockWithEnchantGlint() throws IOException {
        final int warmupIterations = 3;
        final int measuredIterations = 10;

        for (int i = 0; i < warmupIterations; i++) {
            generateDirtWithGlint();
        }

        long[] elapsedNanos = new long[measuredIterations];
        for (int i = 0; i < measuredIterations; i++) {
            long startNanos = System.nanoTime();
            generateDirtWithGlint();
            elapsedNanos[i] = System.nanoTime() - startNanos;
        }

        long totalNanos = 0;
        long minNanos = Long.MAX_VALUE;
        long maxNanos = 0;
        for (long elapsed : elapsedNanos) {
            totalNanos += elapsed;
            minNanos = Math.min(minNanos, elapsed);
            maxNanos = Math.max(maxNanos, elapsed);
        }

        double averageMillis = totalNanos / 1_000_000.0 / measuredIterations;
        double minMillis = minNanos / 1_000_000.0;
        double maxMillis = maxNanos / 1_000_000.0;
        System.out.printf(
            "Dirt + enchant glint benchmark: warmup=%d, iterations=%d, average=%.1f ms, min=%.1f ms, max=%.1f ms%n",
            warmupIterations, measuredIterations, averageMillis, minMillis, maxMillis);

        GeneratedObject generated = generateDirtWithGlint();
        Path output = BenchmarkArtifacts.storeBenchmarkGif("dirt-enchant-glint.gif", generated);
        System.out.println("Dirt benchmark image: " + output.toAbsolutePath());
    }

    private static GeneratedObject generateDirtWithGlint() {
        var generated = new MinecraftItemGenerator.Builder()
            .withItem("dirt")
            .isEnchanted(true)
            .build()
            .generate();
        assertTrue(generated.isAnimated(), "enchanted dirt must generate a GIF");
        assertNotNull(generated.getGifData(), "enchanted dirt GIF bytes must be present");
        return generated;
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

    @Test
    void cacheKeysIgnoreRepositoryInstance() {
        // Hold the effect pipeline constant: GeneratorCacheKey stringifies it via identity hash
        // (pre-existing behavior), so a shared instance isolates the repository as the only variable.
        EffectPipeline sharedPipeline = new EffectPipeline.Builder().build();
        MinecraftItemGenerator first = new MinecraftItemGenerator.Builder()
            .withPack(packId)
            .withPackRepository(repository)
            .withEffectPipeline(sharedPipeline)
            .withItem("testpack:item/simple")
            .build();
        MinecraftItemGenerator second = new MinecraftItemGenerator.Builder()
            .withPack(packId)
            .withPackRepository(new PackRepository())
            .withEffectPipeline(sharedPipeline)
            .withItem("testpack:item/simple")
            .build();
        assertEquals(GeneratorCacheKey.fromGenerator(first), GeneratorCacheKey.fromGenerator(second),
            "the transient repository seam must not split the cache key");
    }
}
