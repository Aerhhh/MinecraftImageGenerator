package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayToolTest {

    @Test
    void defaultsMatchTheDiscordCommand() {
        DisplayRequest request = DisplayRequest.builder().itemId("stone").build();

        assertEquals("stone", request.itemId());
        assertNull(request.itemModel());
        assertNull(request.data());
        assertNull(request.color());
        assertFalse(request.enchanted());
        assertFalse(request.hoverEffect());
        assertNull(request.skinValue());
        assertEquals(100, request.durability());
        assertNull(request.packId());
    }

    @Test
    void nullBoxedValuesKeepDefaults() {
        DisplayRequest request = DisplayRequest.builder().itemId("stone")
            .enchanted(null).hoverEffect(null).durability(null).build();

        assertFalse(request.enchanted());
        assertFalse(request.hoverEffect());
        assertEquals(100, request.durability());
    }

    @Test
    void bothAddressesAreRejected() {
        DisplayRequest request = DisplayRequest.builder().itemId("stone").itemModel("x:y").build();

        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new DisplayTool(ToolTestSupport.vanillaService()).render(request, null));

        assertEquals("The item_id and item_model options are mutually exclusive; use one or the other!", exception.getMessage());
    }

    @Test
    void noAddressIsRejected() {
        DisplayRequest request = DisplayRequest.builder().build();

        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new DisplayTool(ToolTestSupport.vanillaService()).render(request, null));

        assertEquals("Set either the item_id or the item_model option to pick what to render!", exception.getMessage());
    }

    @Test
    void rendersAVanillaItem() throws IOException {
        GeneratedObject result = new DisplayTool(ToolTestSupport.vanillaService())
            .render(DisplayRequest.builder().itemId("stone").build(), null);

        assertFalse(result.isAnimated());
        assertNotNull(result.getImage());
        assertTrue(result.getImage().getWidth() > 0);
    }

    @Test
    void enchantedRenderIsAnimated() throws IOException {
        GeneratedObject result = new DisplayTool(ToolTestSupport.vanillaService())
            .render(DisplayRequest.builder().itemId("diamond_sword").enchanted(true).build(), null);

        assertTrue(result.isAnimated());
    }

    @Test
    @Tag("network")
    void playerHeadWithSkinRendersAHead() throws IOException {
        DisplayTool tool = new DisplayTool(ToolTestSupport.vanillaService());

        GeneratedObject head = tool.render(DisplayRequest.builder().itemId("player_head").skinValue("Aerh").build(), null);
        GeneratedObject item = tool.render(DisplayRequest.builder().itemId("player_head").build(), null);

        assertNotNull(head.getImage());
        assertTrue(head.getImage().getWidth() != item.getImage().getWidth() || head.getImage().getHeight() != item.getImage().getHeight(),
            "a head render has different dimensions from the player_head item sprite");
    }

    @Test
    void rendersAPackItemModel() throws IOException {
        GeneratedObject result = new DisplayTool(ToolTestSupport.fixtureService())
            .render(DisplayRequest.builder().itemModel("testpack:item/simple").packId(ToolTestSupport.FIXTURE_PACK).build(), null);

        assertNotNull(result.getImage());
    }

    @Test
    void colorDyesAPackItemThroughTheTool() throws IOException {
        GeneratedObject result = new DisplayTool(ToolTestSupport.elementsFixtureService()).render(
            DisplayRequest.builder().itemModel("testpack:item/dyed").color("light_blue")
                .packId(ToolTestSupport.ELEMENTS_PACK).build(), null);

        assertEquals(0xFF3AB3DA, result.getImage().getRGB(128, 128), "light_blue is vanilla's #3AB3DA");
    }

    @Test
    void nonDyeColorOnAPackItemReachesTheUserAsAGeneratorError() throws IOException {
        DisplayRequest request = DisplayRequest.builder().itemModel("testpack:item/dyed").color("speed")
            .packId(ToolTestSupport.ELEMENTS_PACK).build();
        DisplayTool tool = new DisplayTool(ToolTestSupport.elementsFixtureService());

        GeneratorException exception = assertThrows(GeneratorException.class, () -> tool.render(request, null));

        assertTrue(exception.getMessage().contains("use #RRGGBB or a dye name"), exception.getMessage());
    }

    @Test
    void blankOptionalStringsBecomeNull() {
        DisplayRequest request = DisplayRequest.builder().itemId("stone").itemModel("").data(" ").color("  ").skinValue("	").build();

        assertEquals("stone", request.itemId());
        assertNull(request.itemModel());
        assertNull(request.data());
        assertNull(request.color());
        assertNull(request.skinValue());
    }

    @Test
    @Tag("network")
    void playerHeadWithABlankItemModelTakesTheHeadBranch() throws IOException {
        DisplayTool tool = new DisplayTool(ToolTestSupport.vanillaService());

        GeneratedObject blankModel = tool.render(DisplayRequest.builder().itemId("player_head").itemModel("").skinValue("Aerh").build(), null);
        GeneratedObject omittedModel = tool.render(DisplayRequest.builder().itemId("player_head").skinValue("Aerh").build(), null);

        assertEquals(omittedModel.getImage().getWidth(), blankModel.getImage().getWidth());
        assertEquals(omittedModel.getImage().getHeight(), blankModel.getImage().getHeight());
    }
}
