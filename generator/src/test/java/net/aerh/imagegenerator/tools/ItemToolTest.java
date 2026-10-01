package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
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

class ItemToolTest {

    private static ItemRequest.Builder minimal() {
        return ItemRequest.builder().itemName("Test").itemLore("&7Lore");
    }

    @Test
    void defaultsMatchTheDiscordCommand() {
        ItemRequest request = minimal().build();

        assertEquals("", request.type());
        assertEquals("none", request.rarity());
        assertNull(request.itemId());
        assertNull(request.itemModel());
        assertNull(request.color());
        assertNull(request.skinValue());
        assertNull(request.recipe());
        assertEquals(MinecraftTooltip.DEFAULT_ALPHA, request.alpha());
        assertEquals(245, request.alpha());
        assertEquals(0, request.padding());
        assertFalse(request.enchanted());
        assertFalse(request.centered());
        assertTrue(request.firstLinePadding());
        assertEquals(36, request.maxLineLength());
        assertEquals(MinecraftTooltipGenerator.TooltipSide.RIGHT, request.tooltipSide());
        assertTrue(request.renderBorder());
        assertEquals(100, request.durability());
        assertNull(request.packId());
        assertNull(request.tooltipStyle());
    }

    @Test
    void requiredFieldsAreEnforced() {
        assertThrows(GeneratorValidationException.class, () -> ItemRequest.builder().itemLore("x").build());
        assertThrows(GeneratorValidationException.class, () -> ItemRequest.builder().itemName("x").build());
    }

    @Test
    void invalidTooltipSideFallsBackToRight() {
        ItemRequest request = minimal().tooltipSide("sideways").build();

        assertEquals(MinecraftTooltipGenerator.TooltipSide.RIGHT, request.tooltipSide());
        assertEquals(MinecraftTooltipGenerator.TooltipSide.LEFT, minimal().tooltipSide("left").build().tooltipSide());
    }

    @Test
    void bothAddressesAreRejected() {
        ItemRequest request = minimal().itemId("stone").itemModel("x:y").build();

        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new ItemTool(ToolTestSupport.vanillaService()).render(request, null));

        assertEquals("The item_id and item_model options are mutually exclusive; use one or the other!", exception.getMessage());
    }

    @Test
    void tooltipStyleWithoutBorderIsRejected() {
        ItemRequest request = minimal().renderBorder(false).tooltipStyle("x:y").build();

        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new ItemTool(ToolTestSupport.vanillaService()).render(request, null));

        assertEquals("The tooltip_style option only renders with the border; set render_border: true too!", exception.getMessage());
    }

    @Test
    void tooltipStyleWithoutPackIsRejected() {
        ItemRequest request = minimal().tooltipStyle("x:y").build();

        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new ItemTool(ToolTestSupport.vanillaService()).render(request, null));

        assertEquals("The tooltip_style option needs a resource pack; set the pack option too!", exception.getMessage());
    }

    @Test
    void rendersTooltipOnly() throws IOException {
        GeneratedObject result = new ItemTool(ToolTestSupport.vanillaService()).render(minimal().build(), null);

        assertFalse(result.isAnimated());
        assertNotNull(result.getImage());
    }

    @Test
    void tooltipOnTheLeftIsWiderThanTooltipOnly() throws IOException {
        ItemTool tool = new ItemTool(ToolTestSupport.vanillaService());

        GeneratedObject tooltipOnly = tool.render(minimal().build(), null);
        GeneratedObject withItem = tool.render(minimal().itemId("stone").tooltipSide("LEFT").build(), null);

        assertTrue(withItem.getImage().getWidth() > tooltipOnly.getImage().getWidth());
    }

    @Test
    @Tag("network")
    void playerHeadRendersBesideTheTooltip() throws IOException {
        ItemTool tool = new ItemTool(ToolTestSupport.vanillaService());

        GeneratedObject tooltipOnly = tool.render(minimal().build(), null);
        GeneratedObject withHead = tool.render(minimal().itemId("player_head").skinValue("Aerh").build(), null);

        assertTrue(withHead.getImage().getWidth() > tooltipOnly.getImage().getWidth());
    }

    @Test
    void recipeGridRendersInFront() throws IOException {
        ItemTool tool = new ItemTool(ToolTestSupport.vanillaService());

        GeneratedObject withRecipe = tool.render(minimal().recipe("stone:1%%stone:2").build(), null);
        GeneratedObject without = tool.render(minimal().build(), null);

        assertTrue(withRecipe.getImage().getWidth() > without.getImage().getWidth());
    }

    @Test
    void slashCommandRoundTripsNameLoreAndItem() {
        ItemRequest request = minimal().itemId("minecraft:stone").enchanted(true).build();

        String command = new ItemTool(ToolTestSupport.vanillaService()).slashCommand(request);

        assertTrue(command.startsWith("/gen item "), command);
        assertTrue(command.contains("item_name: Test"), command);
        assertTrue(command.contains("item_lore: &7Lore"), command);
        assertTrue(command.endsWith(" item_id: stone enchanted: True"), command);
    }

    @Test
    void blankOptionalStringsBecomeNull() {
        ItemRequest request = minimal().itemId(" ").itemModel("  ").color("").skinValue("	").recipe(" ").tooltipStyle("").build();

        assertNull(request.itemId());
        assertNull(request.itemModel());
        assertNull(request.color());
        assertNull(request.skinValue());
        assertNull(request.recipe());
        assertNull(request.tooltipStyle());
    }

    @Test
    void blankItemModelRendersLikeANullItemModel() throws IOException {
        ItemTool tool = new ItemTool(ToolTestSupport.vanillaService());

        GeneratedObject blank = tool.render(minimal().itemId("stone").itemModel("  ").build(), null);
        GeneratedObject omitted = tool.render(minimal().itemId("stone").build(), null);

        assertEquals(omitted.getImage().getWidth(), blank.getImage().getWidth());
        assertEquals(omitted.getImage().getHeight(), blank.getImage().getHeight());
    }

    @Test
    void nullTooltipSideKeepsTheSideSetEarlier() {
        assertEquals(MinecraftTooltipGenerator.TooltipSide.LEFT, minimal().tooltipSide("left").tooltipSide((String) null).build().tooltipSide());
        assertEquals(MinecraftTooltipGenerator.TooltipSide.LEFT,
            minimal().tooltipSide(MinecraftTooltipGenerator.TooltipSide.LEFT).tooltipSide((MinecraftTooltipGenerator.TooltipSide) null).build().tooltipSide());
        assertEquals(MinecraftTooltipGenerator.TooltipSide.RIGHT, minimal().tooltipSide((String) null).build().tooltipSide());
    }
}
