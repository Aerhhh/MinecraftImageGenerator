package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
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

class PowerStoneToolTest {

    private static final String UNSCALED = "damage";

    private static PowerStoneRequest.Builder minimal() {
        return PowerStoneRequest.builder().powerName("Bloody").powerStrength("Strong").magicalPower(1000);
    }

    @Test
    void defaultsMatchTheDiscordCommand() {
        PowerStoneRequest request = minimal().build();

        assertNull(request.scalingStats());
        assertNull(request.uniqueBonus());
        assertNull(request.itemId());
        assertNull(request.color());
        assertNull(request.skinValue());
        assertEquals(245, request.alpha());
        assertEquals(0, request.padding());
        assertTrue(request.selected());
        assertFalse(request.enchanted());
        assertNull(request.packId());
    }

    @Test
    void requiredFieldsAreEnforced() {
        assertThrows(GeneratorValidationException.class, () -> PowerStoneRequest.builder().powerStrength("x").magicalPower(1).build());
        assertThrows(GeneratorValidationException.class, () -> PowerStoneRequest.builder().powerName("x").magicalPower(1).build());
    }

    @Test
    void rendersATooltip() throws IOException {
        GeneratedObject result = new PowerStoneTool(ToolTestSupport.vanillaService())
            .render(minimal().scalingStats(UNSCALED + ":100").uniqueBonus(UNSCALED + ":5").build(), null);

        assertFalse(result.isAnimated());
        assertNotNull(result.getImage());
    }

    @Test
    void itemBesideTheTooltipWidensTheImage() throws IOException {
        PowerStoneTool tool = new PowerStoneTool(ToolTestSupport.vanillaService());

        GeneratedObject plain = tool.render(minimal().build(), null);
        GeneratedObject withItem = tool.render(minimal().itemId("stone").build(), null);

        assertTrue(withItem.getImage().getWidth() > plain.getImage().getWidth());
    }

    @Test
    @Tag("network")
    void playerHeadRendersBesideTheTooltip() throws IOException {
        PowerStoneTool tool = new PowerStoneTool(ToolTestSupport.vanillaService());

        GeneratedObject plain = tool.render(minimal().build(), null);
        GeneratedObject withHead = tool.render(minimal().itemId("player_head").skinValue("Aerh").build(), null);

        assertTrue(withHead.getImage().getWidth() > plain.getImage().getWidth());
    }

    @Test
    void invalidStatSurfacesTheLoreMessage() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new PowerStoneTool(ToolTestSupport.vanillaService()).render(minimal().scalingStats("nope:1").build(), null));

        assertEquals("`nope` is not a valid stat", exception.getMessage());
    }

    @Test
    void slashCommandAppendsItemOptions() {
        String command = new PowerStoneTool(ToolTestSupport.vanillaService())
            .slashCommand(minimal().itemId("stone").enchanted(true).build());

        assertTrue(command.startsWith("/gen item "), command);
        assertTrue(command.contains("item_name: &aBloody"), command);
        assertTrue(command.endsWith(" item_id: stone enchanted: True"), command);
    }

    @Test
    void blankOptionalStringsBecomeNull() {
        PowerStoneRequest request = minimal().scalingStats(" ").uniqueBonus("").itemId("  ").color("").skinValue("	").build();

        assertNull(request.scalingStats());
        assertNull(request.uniqueBonus());
        assertNull(request.itemId());
        assertNull(request.color());
        assertNull(request.skinValue());
    }
}
