package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.testsupport.ImageAssertions;
import net.aerh.imagegenerator.testsupport.TestResources;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins each tool's render pixel for pixel, once against vanilla and once through a fixture pack.
 * A failure means output changed; if the change is intended, run {@link #regenerateGoldens()} once,
 * review the image diff, and commit.
 *
 * <p>Every fixture case must depend on pack content: {@link #fixtureGoldenDiffersFromVanilla} fails
 * if one stops exercising its pack. The fixture requests use whichever fixture pack the tool can
 * actually exercise: the item pack ({@code testpack:item/simple}, style {@code testpack:fancy}) for
 * display, item and recipe, and the tooltip-only pack (style {@code themepack:ruby} and a default
 * tooltip override) for text, inventory (hover tooltip), power stone and dialogue.</p>
 */
class ToolGoldenImageTest {

    private static final String UNSCALED = "damage";

    private static final String PACK_ITEM = "testpack:item/simple";
    private static final String ITEM_PACK_TOOLTIP_STYLE = "testpack:fancy";
    private static final String THEMED_TOOLTIP_STYLE = "themepack:ruby";

    private static final String VANILLA_DIR = "golden/tools/";
    private static final String FIXTURE_DIR = "golden/tools/fixture/";

    /** One render, bound to the pack service it runs against. */
    interface Render {
        GeneratedObject render() throws IOException;
    }

    private static Map<String, Render> vanillaCases() {
        Map<String, Render> cases = new LinkedHashMap<>();
        cases.put("display", () -> new DisplayTool(ToolTestSupport.vanillaService()).render(
            DisplayRequest.builder().itemId("diamond_sword").durability(40).build(), null));
        cases.put("item", () -> new ItemTool(ToolTestSupport.vanillaService()).render(
            ItemRequest.builder().itemName("&6Aegis").itemLore("&7A blade.\\n\\n&6Damage: &c+340").rarity("legendary").type("SWORD").itemId("diamond_sword").build(), null));
        cases.put("text", () -> new TextTool(ToolTestSupport.vanillaService()).render(
            TextRequest.builder().text("&aHello\\n&7World").build(), null));
        cases.put("inventory", () -> new InventoryTool(ToolTestSupport.vanillaService()).render(
            InventoryRequest.builder().rows(2).slotsPerRow(9).inventoryString("stone:1%%diamond:{11:64}").containerName("Chest").hoveredItemString("&7Hover").build(), null));
        cases.put("recipe", () -> new RecipeTool(ToolTestSupport.vanillaService()).render(
            RecipeRequest.builder().recipe("stick:5%%stick:8%%diamond:2").build(), null));
        cases.put("powerstone", () -> new PowerStoneTool(ToolTestSupport.vanillaService()).render(
            PowerStoneRequest.builder().powerName("Bloody").powerStrength("Strong").magicalPower(1000).scalingStats(UNSCALED + ":100").itemId("stone").build(), null));
        cases.put("dialogue_single", () -> new DialogueTool(ToolTestSupport.vanillaService()).render(
            DialogueRequest.single("Steve", "Hello there\\nPick one {options: Yes, No}").build(), null));
        cases.put("dialogue_multi", () -> new DialogueTool(ToolTestSupport.vanillaService()).render(
            DialogueRequest.multi("Steve, Alex", "0, Hi\\n1, Hey").abiphone(true).build(), null));
        return cases;
    }

    /** Same eight tools and texts as the vanilla cases, changed only where a pack has to take effect. */
    private static Map<String, Render> fixtureCases() {
        Map<String, Render> cases = new LinkedHashMap<>();
        // Item pack: the item comes from the pack instead of the vanilla spritesheet.
        cases.put("display", () -> new DisplayTool(ToolTestSupport.fixtureService()).render(
            DisplayRequest.builder().itemModel(PACK_ITEM).durability(40).packId(ToolTestSupport.FIXTURE_PACK).build(), null));
        cases.put("item", () -> new ItemTool(ToolTestSupport.fixtureService()).render(
            ItemRequest.builder().itemName("&6Aegis").itemLore("&7A blade.\\n\\n&6Damage: &c+340").rarity("legendary").type("SWORD").itemModel(PACK_ITEM)
                .packId(ToolTestSupport.FIXTURE_PACK).tooltipStyle(ITEM_PACK_TOOLTIP_STYLE).build(), null));
        cases.put("recipe", () -> new RecipeTool(ToolTestSupport.fixtureService()).render(
            RecipeRequest.builder().recipe(PACK_ITEM + ":5%%" + PACK_ITEM + ":8%%" + PACK_ITEM + ":2").packId(ToolTestSupport.FIXTURE_PACK).build(), null));
        // Tooltip-only pack: the tooltip frame comes from the pack's style or default override.
        cases.put("text", () -> new TextTool(ToolTestSupport.themedFixtureService()).render(
            TextRequest.builder().text("&aHello\\n&7World").packId(ToolTestSupport.THEMED_PACK).tooltipStyle(THEMED_TOOLTIP_STYLE).renderBorder(true).build(), null));
        cases.put("inventory", () -> new InventoryTool(ToolTestSupport.themedFixtureService()).render(
            InventoryRequest.builder().rows(2).slotsPerRow(9).inventoryString("stone:1%%diamond:{11:64}").containerName("Chest").hoveredItemString("&7Hover")
                .packId(ToolTestSupport.THEMED_PACK).build(), null));
        cases.put("powerstone", () -> new PowerStoneTool(ToolTestSupport.themedFixtureService()).render(
            PowerStoneRequest.builder().powerName("Bloody").powerStrength("Strong").magicalPower(1000).scalingStats(UNSCALED + ":100").itemId("stone")
                .packId(ToolTestSupport.THEMED_PACK).build(), null));
        cases.put("dialogue_single", () -> new DialogueTool(ToolTestSupport.themedFixtureService()).render(
            DialogueRequest.single("Steve", "Hello there\\nPick one {options: Yes, No}").renderBackground(true).packId(ToolTestSupport.THEMED_PACK).build(), null));
        cases.put("dialogue_multi", () -> new DialogueTool(ToolTestSupport.themedFixtureService()).render(
            DialogueRequest.multi("Steve, Alex", "0, Hi\\n1, Hey").abiphone(true).renderBackground(true).packId(ToolTestSupport.THEMED_PACK).build(), null));
        return cases;
    }

    static Stream<String> vanillaNames() {
        return vanillaCases().keySet().stream();
    }

    static Stream<String> fixtureNames() {
        return fixtureCases().keySet().stream();
    }

    @ParameterizedTest
    @MethodSource("vanillaNames")
    void vanillaRenderMatchesGolden(String name) throws IOException {
        assertMatchesGolden(vanillaCases(), VANILLA_DIR, name);
    }

    @ParameterizedTest
    @MethodSource("fixtureNames")
    void fixturePackRenderMatchesGolden(String name) throws IOException {
        assertMatchesGolden(fixtureCases(), FIXTURE_DIR, name);
    }

    /** A fixture golden equal to its vanilla golden pins nothing about the pack. */
    @ParameterizedTest
    @MethodSource("fixtureNames")
    void fixtureGoldenDiffersFromVanilla(String name) throws IOException {
        BufferedImage vanilla = readGolden(VANILLA_DIR, name);
        BufferedImage fixture = readGolden(FIXTURE_DIR, name);

        assertFalse(ImageAssertions.pixelsEqual(vanilla, fixture),
            name + ": the fixture golden equals the vanilla golden, so this case no longer exercises its pack");
    }

    private static void assertMatchesGolden(Map<String, Render> cases, String goldenDir, String name) throws IOException {
        GeneratedObject actual = cases.get(name).render();
        assertFalse(actual.isAnimated(), name + " golden must be static");

        ImageAssertions.assertPixelsEqual(readGolden(goldenDir, name), actual.getImage(), goldenDir + name);
    }

    private static BufferedImage readGolden(String goldenDir, String name) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(TestResources.readBytes(goldenDir + name + ".png")));
    }

    /** Run manually once (remove @Disabled) to (re)capture goldens; never in CI. */
    @Disabled("golden regeneration only - run manually, review the diff, commit")
    @Test
    void regenerateGoldens() throws IOException {
        capture(vanillaCases(), VANILLA_DIR);
        capture(fixtureCases(), FIXTURE_DIR);
    }

    private static void capture(Map<String, Render> cases, String goldenDir) throws IOException {
        Path dir = Files.createDirectories(Path.of("src/test/resources", goldenDir));
        for (Map.Entry<String, Render> entry : cases.entrySet()) {
            ImageIO.write(entry.getValue().render().getImage(), "png", dir.resolve(entry.getKey() + ".png").toFile());
        }
    }
}
