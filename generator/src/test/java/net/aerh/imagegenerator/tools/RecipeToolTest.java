package net.aerh.imagegenerator.tools;

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

class RecipeToolTest {

    @Test
    void defaultsMatchTheDiscordCommand() {
        RecipeRequest request = RecipeRequest.builder().recipe("stone:1").build();

        assertEquals("stone:1", request.recipe());
        assertTrue(request.renderBackground());
        assertNull(request.packId());
    }

    @Test
    void recipeIsRequired() {
        assertThrows(GeneratorValidationException.class, () -> RecipeRequest.builder().build());
    }

    @Test
    void rendersAThreeByThreeGrid() throws IOException {
        GeneratedObject result = new RecipeTool(ToolTestSupport.vanillaService())
            .render(RecipeRequest.builder().recipe("stone:1%%stone:5%%stone:9").build(), null);

        assertFalse(result.isAnimated());
        assertNotNull(result.getImage());
        assertEquals(result.getImage().getWidth(), result.getImage().getHeight(), "3x3 without border is square");
    }

    @Test
    void backgroundCanBeDisabled() throws IOException {
        RecipeTool tool = new RecipeTool(ToolTestSupport.vanillaService());

        GeneratedObject with = tool.render(RecipeRequest.builder().recipe("stone:1").build(), null);
        GeneratedObject without = tool.render(RecipeRequest.builder().recipe("stone:1").renderBackground(false).build(), null);

        assertEquals(with.getImage().getWidth(), without.getImage().getWidth());
        assertTrue(without.getImage().getRGB(0, 0) >>> 24 == 0, "corner pixel is transparent without background");
    }
}
