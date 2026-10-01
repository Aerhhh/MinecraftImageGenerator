package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import net.aerh.imagegenerator.tools.pack.PackDefinition;
import net.aerh.imagegenerator.tools.pack.PackRegistrationConfig;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackThemingTest {

    // FixturePacks.writeTooltipOnlyPack defines its non-default style under THEME_NAMESPACE
    // ("themepack"), not under NAMESPACE ("testpack") as the brief's snippet assumed; the style
    // it defines is "ruby", not "fancy". See LoadedPackTooltipTest for the same style ref.
    private static final String STYLE = FixturePacks.THEME_NAMESPACE + ":ruby";

    private ResourcePackService service;
    private PackId packId;

    @BeforeEach
    void registerThemedPack() throws IOException {
        Path root = Files.createDirectories(Path.of("target", "pack-fixtures"));
        Path packDir = Files.createTempDirectory(root, "theming-");
        FixturePacks.writeTooltipOnlyPack(packDir);

        service = new ResourcePackService(new PackRepository());
        PackDefinition definition = new PackDefinition("tools:theming", packDir.toString(),
            Map.of("legendary", STYLE), Map.of("#555555", "#3f3f3f"));
        service.registerConfiguredPacks(PackRegistrationConfig.of(null, definition));
        packId = PackId.parse("tools:theming");
    }

    @Test
    void borderIsRequiredForAnExplicitStyle() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> PackTheming.requireBorderForTooltipStyle("x:y", false));

        assertEquals("The tooltip_style option only renders with the border; set render_border: true too!", exception.getMessage());
    }

    @Test
    void borderCheckIgnoresBlankStyle() {
        PackTheming.requireBorderForTooltipStyle(" ", false);
        PackTheming.requireBorderForTooltipStyle(null, false);
        PackTheming.requireBorderForTooltipStyle("x:y", true);
    }

    @Test
    void explicitStyleWithoutPackIsRejected() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> PackTheming.applyPackTheme(service, new MinecraftTooltipGenerator.Builder(), null, "x:y", null));

        assertEquals("The tooltip_style option needs a resource pack; set the pack option too!", exception.getMessage());
    }

    @Test
    void noPackLeavesBuilderUntouched() {
        MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder();

        PackTheming.applyPackTheme(service, builder, null, null, Rarity.byName("legendary"));

        assertNull(builder.getPack());
        assertNull(builder.getTooltipStyle());
    }

    @Test
    void explicitStyleWinsOverRarityMapping() {
        MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder();

        PackTheming.applyPackTheme(service, builder, packId, " " + STYLE + " ", Rarity.byName("common"));

        assertEquals(STYLE, builder.getTooltipStyle());
    }

    @Test
    void rarityMappingAppliesWhenNoExplicitStyle() {
        MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder();

        PackTheming.applyPackTheme(service, builder, packId, null, Rarity.byName("legendary"));

        assertEquals(packId, builder.getPack());
        assertEquals(STYLE, builder.getTooltipStyle());
        assertNotNull(builder.getTextColorRemap(), "remap from the definition is applied");
    }

    @Test
    void unmappedRarityLeavesStyleUnset() {
        MinecraftTooltipGenerator.Builder builder = new MinecraftTooltipGenerator.Builder();

        PackTheming.applyPackTheme(service, builder, packId, null, Rarity.byName("common"));

        assertEquals(packId, builder.getPack());
        assertNull(builder.getTooltipStyle());
    }
}
