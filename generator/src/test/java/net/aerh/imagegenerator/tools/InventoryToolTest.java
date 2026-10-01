package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryToolTest {

    private static InventoryRequest.Builder minimal() {
        return InventoryRequest.builder().rows(1).slotsPerRow(9).inventoryString("stone:1");
    }

    @Test
    void defaultsMatchTheDiscordCommand() {
        InventoryRequest request = minimal().build();

        assertEquals(1, request.rows());
        assertEquals(9, request.slotsPerRow());
        assertEquals("stone:1", request.inventoryString());
        assertNull(request.hoveredItemString());
        assertNull(request.containerName());
        assertTrue(request.drawBorder());
        assertEquals(36, request.maxLineLength());
        assertFalse(request.animateGlint());
        assertNull(request.packId());
    }

    @Test
    void inventoryStringIsRequired() {
        assertThrows(GeneratorValidationException.class, () -> InventoryRequest.builder().rows(1).slotsPerRow(1).build());
    }

    @Test
    void rendersAGrid() throws IOException {
        GeneratedObject result = new InventoryTool(ToolTestSupport.vanillaService()).render(minimal().build(), null);

        assertFalse(result.isAnimated());
        assertNotNull(result.getImage());
    }

    @Test
    void hoveredItemAddsATooltipBesideTheGrid() throws IOException {
        InventoryTool tool = new InventoryTool(ToolTestSupport.vanillaService());

        GeneratedObject plain = tool.render(minimal().build(), null);
        GeneratedObject hovered = tool.render(minimal().hoveredItemString("&7Some lore").build(), null);

        assertTrue(hovered.getImage().getWidth() > plain.getImage().getWidth());
    }

    @Test
    void blankHoveredItemIsIgnored() throws IOException {
        InventoryTool tool = new InventoryTool(ToolTestSupport.vanillaService());

        GeneratedObject plain = tool.render(minimal().build(), null);
        GeneratedObject blank = tool.render(minimal().hoveredItemString("   ").build(), null);

        assertEquals(plain.getImage().getWidth(), blank.getImage().getWidth());
    }

    @Test
    void hoverTooltipScaleIsCappedAtTwo() {
        assertEquals(1, InventoryTool.hoverTooltipScale(1));
        assertEquals(2, InventoryTool.hoverTooltipScale(2));
        assertEquals(2, InventoryTool.hoverTooltipScale(3));
        assertEquals(2, InventoryTool.hoverTooltipScale(10));
    }

    @Test
    void rowsMustBePositive() {
        GeneratorValidationException exception = assertThrows(GeneratorValidationException.class,
            () -> InventoryRequest.builder().rows(0).slotsPerRow(9).inventoryString("stone:1").build());

        assertEquals("rows must be positive", exception.getMessage());
        assertThrows(GeneratorValidationException.class,
            () -> InventoryRequest.builder().rows(-1).slotsPerRow(9).inventoryString("stone:1").build());
    }

    @Test
    void slotsPerRowMustBePositive() {
        GeneratorValidationException exception = assertThrows(GeneratorValidationException.class,
            () -> InventoryRequest.builder().rows(1).slotsPerRow(0).inventoryString("stone:1").build());

        assertEquals("slotsPerRow must be positive", exception.getMessage());
    }

    @Test
    void blankOptionalStringsBecomeNull() {
        InventoryRequest request = minimal().hoveredItemString("  ").containerName("").build();

        assertNull(request.hoveredItemString());
        assertNull(request.containerName());
    }
}
