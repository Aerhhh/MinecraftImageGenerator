package net.aerh.imagegenerator.impl.template;

import com.google.gson.JsonObject;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The convenience bridge end to end: a filled template is exactly the item NBT tree the tooltip
 * generator ingests through {@link MinecraftTooltipGenerator.Builder#parseNbtJson}, so a template
 * fills straight into a rendered image.
 */
class ContentTemplateRenderTest {

    @Test
    void filledTemplateRendersThroughTheTooltipGenerator() {
        ContentTemplate template = ContentTemplate.parse("""
            {"schema_version": 1,
             "placeholders": {"owner": {}, "item": {}, "count": {}},
             "content": {"components": {
               "minecraft:custom_name": {"text": "{owner}'s Loot", "color": "gold"},
               "minecraft:lore": [
                 {"text": "Stash", "color": "gray"},
                 {"template_repeat": "entries", "text": "{item} x{count}", "color": "aqua"}
               ]
             }}}
            """);

        JsonObject nbt = template.fill(Map.of("owner", "Aerh"), Map.of("entries", List.of(
            Map.of("item", "Diamond", "count", "64"),
            Map.of("item", "Emerald", "count", "12"))));

        BufferedImage image = new MinecraftTooltipGenerator.Builder()
            .parseNbtJson(nbt)
            .build()
            .render(null)
            .getImage();

        assertNotNull(image, "template must render to an image through the tooltip pipeline");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0, "rendered image must have positive dimensions");
    }
}
