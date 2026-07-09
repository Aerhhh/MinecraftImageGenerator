package net.aerh.imagegenerator.impl.tooltip;

import net.aerh.imagegenerator.cache.GeneratorCacheKey;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.PackResolveException;
import net.aerh.imagegenerator.item.GeneratedObject;
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

/**
 * Integration coverage for pack-themed tooltips: sprite chrome, loud missing/broken style
 * policy, default-override behavior, cache-key discipline, and slash-command round-trip.
 * Fixture colors: fancy frame ring 0xFF445566 over background 0xFF112233; plain frame
 * 0xFF664422; theme-only pack's vanilla-override frame 0xFF202020.
 */
class MinecraftTooltipGeneratorPackTest {

    @TempDir
    Path packDir;
    @TempDir
    Path themeDir;

    private PackRepository repository;
    private PackId packId;
    private PackId themePackId;

    @BeforeEach
    void setUp() {
        repository = new PackRepository();
        FixturePacks.writeDefaultPack(packDir);
        packId = repository.register("test:pack", PackSource.directory(packDir, PackLimits.fromSystemProperties()));
        FixturePacks.writeTooltipOnlyPack(themeDir);
        themePackId = repository.register("test:theme", PackSource.directory(themeDir, PackLimits.fromSystemProperties()));
    }

    private MinecraftTooltipGenerator.Builder baseBuilder() {
        return new MinecraftTooltipGenerator.Builder()
            .withName("Test Item")
            .withRarity(Rarity.byName("EPIC"))
            .withItemLore("&7A line of lore")
            .withPackRepository(repository);
    }

    private static void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth(), "width");
        assertEquals(expected.getHeight(), actual.getHeight(), "height");
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void themedTooltipDrawsPackSprites() {
        BufferedImage image = baseBuilder()
            .withPack(packId)
            .withTooltipStyle("testpack:fancy")
            .build().generate().getImage();
        assertEquals(0xFF445566, image.getRGB(0, 0), "frame corner covers the canvas corner");
        assertEquals(0xFF112233, image.getRGB(8, 8), "background shows through the transparent frame center");
        assertEquals(0, image.getWidth() % 2, "themed canvas is a whole number of GUI px");
        assertEquals(0, image.getHeight() % 2, "themed canvas is a whole number of GUI px");
    }

    @Test
    void missingStyleFailsLoudly() {
        MinecraftTooltipGenerator generator = baseBuilder()
            .withPack(packId).withTooltipStyle("testpack:nope").build();
        GeneratorException exception = assertThrows(GeneratorException.class, generator::generate);
        assertTrue(exception.getMessage().contains("testpack:nope"), exception.getMessage());
    }

    @Test
    void brokenStyleFailsLoudly() {
        MinecraftTooltipGenerator generator = baseBuilder()
            .withPack(packId).withTooltipStyle("testpack:half").build();
        assertThrows(PackResolveException.class, generator::generate);
    }

    @Test
    void styleWithoutPackIsRejected() {
        MinecraftTooltipGenerator generator = baseBuilder().withTooltipStyle("testpack:fancy").build();
        assertThrows(GeneratorException.class, generator::generate);
    }

    @Test
    void unregisteredPackFailsFast() {
        MinecraftTooltipGenerator generator = baseBuilder()
            .withPack("no:pack").withTooltipStyle("testpack:fancy").build();
        assertThrows(PackResolveException.class, generator::generate);
    }

    @Test
    void packDefaultOverrideThemesStylelessTooltip() {
        BufferedImage image = baseBuilder().withPack(themePackId).build().generate().getImage();
        assertEquals(0xFF202020, image.getRGB(0, 0), "vanilla-override frame sprite covers the canvas");
    }

    @Test
    void packWithoutDefaultOverrideKeepsProgrammaticChrome() {
        BufferedImage themed = baseBuilder().withPack(packId).build().generate().getImage();
        BufferedImage plain = baseBuilder().build().generate().getImage();
        assertImagesEqual(plain, themed);
    }

    @Test
    void borderlessTooltipRendersNoThemeChrome() {
        BufferedImage themed = baseBuilder()
            .withPack(packId).withTooltipStyle("testpack:fancy")
            .withRenderBorder(false).withAlpha(100)
            .build().generate().getImage();
        BufferedImage plain = baseBuilder()
            .withRenderBorder(false).withAlpha(100)
            .build().generate().getImage();
        assertImagesEqual(plain, themed);
    }

    @Test
    void differentStylesProduceDifferentChromeAndCacheKeys() {
        MinecraftTooltipGenerator fancyGenerator = baseBuilder().withPack(packId)
            .withTooltipStyle("testpack:fancy").build();
        MinecraftTooltipGenerator plainGenerator = baseBuilder().withPack(packId)
            .withTooltipStyle("testpack:plain").build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(fancyGenerator),
            GeneratorCacheKey.fromGenerator(plainGenerator),
            "style must split the cache key or themed renders collide");
        assertEquals(0xFF445566, fancyGenerator.generate().getImage().getRGB(0, 0));
        assertEquals(0xFF664422, plainGenerator.generate().getImage().getRGB(0, 0));
    }

    @Test
    void cacheKeysDifferAcrossPacks() {
        MinecraftTooltipGenerator withPack = baseBuilder().withPack(packId).build();
        MinecraftTooltipGenerator withoutPack = baseBuilder().build();
        assertNotEquals(GeneratorCacheKey.fromGenerator(withPack), GeneratorCacheKey.fromGenerator(withoutPack));
    }

    @Test
    void cacheKeysIgnoreRepositoryInstance() {
        MinecraftTooltipGenerator first = baseBuilder()
            .withPack(packId).withTooltipStyle("testpack:fancy").build();
        MinecraftTooltipGenerator second = baseBuilder()
            .withPack(packId).withTooltipStyle("testpack:fancy")
            .withPackRepository(new PackRepository()).build();
        assertEquals(GeneratorCacheKey.fromGenerator(first), GeneratorCacheKey.fromGenerator(second),
            "the transient repository seam must not split the cache key");
    }

    @Test
    void buildSlashCommandRoundTripsPackAndStyle() {
        String command = new MinecraftTooltipGenerator.Builder()
            .withName("X").withItemLore("y")
            .withPack("test:pack").withTooltipStyle("testpack:fancy")
            .buildSlashCommand();
        assertTrue(command.contains("pack: test:pack"), command);
        assertTrue(command.contains("tooltip_style: testpack:fancy"), command);
    }

    @Test
    void obfuscatedThemedTooltipAnimatesWithThemeOnEveryFrame() {
        GeneratedObject generated = baseBuilder().withItemLore("&kaaa")
            .withPack(packId).withTooltipStyle("testpack:fancy").build().generate();
        assertTrue(generated.isAnimated());
        assertTrue(generated.getAnimationFrames().size() > 1);
        for (BufferedImage frame : generated.getAnimationFrames()) {
            assertEquals(0xFF445566, frame.getRGB(0, 0), "theme chrome present on every animation frame");
        }
    }
}
