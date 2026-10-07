package net.aerh.imagegenerator.impl.tooltip;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for packs registered with {@code variantOf}: the tooltip generator must
 * resolve placeholder overrides through the registered pack's lineage, so a variant pack (like the
 * alpha SkyBlock pack) draws the same pack glyphs as the pack it is a variant of.
 */
class TooltipVariantPackGlyphTest {

    private static final int ZONE_RED = 0xFFFF0000;
    // White text so the glyph keeps its native color; pack glyphs are tinted by the text color.
    private static final String LORE = "&fWhile in %%zone%% &6Mining Zones";

    @TempDir
    Path packDir;

    private PackRepository repository;

    @BeforeEach
    void setUp() {
        FixturePacks.writeZoneGlyphPack(packDir);
        repository = new PackRepository();
    }

    private PackSource source() {
        return PackSource.directory(packDir, PackLimits.fromSystemProperties());
    }

    private BufferedImage render(PackId packId) {
        return new MinecraftTooltipGenerator.Builder()
            .withItemLore(LORE)
            .withPack(packId)
            .withPackRepository(repository)
            .build().generate().getImage();
    }

    private static boolean containsColor(BufferedImage image, int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == argb) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void basePackDrawsItsZoneGlyph() {
        PackId skyblock = repository.register("hypixel:skyblock", source());

        assertTrue(containsColor(render(skyblock), ZONE_RED), "the hypixel:skyblock override picks U+E067");
    }

    @Test
    void variantPackDrawsTheBasePacksZoneGlyph() {
        PackId alpha = repository.register(PackRepository.prepare("hypixel:alpha", "hypixel:skyblock", source(),
            PackLimits.fromSystemProperties()));

        assertTrue(containsColor(render(alpha), ZONE_RED),
            "a variant of hypixel:skyblock uses its overrides, so the zone placeholder becomes U+E067");
    }

    @Test
    void packWithoutVariantKeepsTheBaseZoneCharacter() {
        PackId alpha = repository.register("hypixel:alpha", source());

        assertFalse(containsColor(render(alpha), ZONE_RED),
            "without variantOf no override key matches, so the classic zone character is drawn");
    }
}
