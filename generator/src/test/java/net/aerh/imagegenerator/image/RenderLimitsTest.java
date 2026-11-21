package net.aerh.imagegenerator.image;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.impl.MinecraftInventoryGenerator;
import net.aerh.imagegenerator.impl.scene.MinecraftSceneGenerator;
import net.aerh.imagegenerator.impl.template.ContentTemplate;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.parser.text.IconParser;
import net.aerh.imagegenerator.tools.InventoryRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

/**
 * The shared pixel budget and the input clamps that keep short, untrusted input from asking for
 * gigabytes: each case here used to exhaust the heap or burn seconds of CPU.
 */
class RenderLimitsTest {

    @Test
    void canvasWithinBudgetPasses() {
        assertDoesNotThrow(() -> RenderLimits.checkCanvas(1_408, 1_000));
        // A full chest with the 182-frame glint is the largest stock animation and must keep working.
        assertDoesNotThrow(() -> RenderLimits.checkFrames(1_408, 1_000, 182));
    }

    @Test
    void oversizedCanvasIsRejected() {
        assertThrows(GeneratorValidationException.class, () -> RenderLimits.checkCanvas(10_000, 10_000));
        assertThrows(GeneratorValidationException.class, () -> RenderLimits.checkCanvas(0, 10));
        assertThrows(GeneratorValidationException.class, () -> RenderLimits.checkCanvas(-5, 10));
        assertThrows(GeneratorValidationException.class,
            () -> RenderLimits.checkCanvas(Integer.MAX_VALUE + 1L, 1));
    }

    @Test
    void oversizedAnimationIsRejected() {
        assertDoesNotThrow(() -> RenderLimits.checkCanvas(4_000, 4_000));
        assertThrows(GeneratorValidationException.class, () -> RenderLimits.checkFrames(4_000, 4_000, 182));
    }

    @Test
    void limitsFollowSystemProperties() {
        System.setProperty(RenderLimits.MAX_CANVAS_PIXELS_PROPERTY, "100");
        try {
            assertEquals(100, RenderLimits.maxCanvasPixels());
            assertThrows(GeneratorValidationException.class, () -> RenderLimits.checkCanvas(11, 10));
            assertDoesNotThrow(() -> RenderLimits.checkCanvas(10, 10));
        } finally {
            System.clearProperty(RenderLimits.MAX_CANVAS_PIXELS_PROPERTY);
        }
        assertEquals(RenderLimits.DEFAULT_MAX_CANVAS_PIXELS, RenderLimits.maxCanvasPixels());
    }

    @Test
    void iconRepeatCountIsClamped() {
        IconParser parser = new IconParser();
        assertEquals("•".repeat(IconParser.MAX_REPEAT), parser.parse("%%dot:5000000%%"));
        assertEquals("❤".repeat(IconParser.MAX_REPEAT), parser.parse("%%icon:health:2000000000%%"));
        assertEquals("", parser.parse("%%dot:-3%%"));
    }

    @Test
    void tooltipPaddingIsClamped() {
        GeneratedObject padded = new GeneratorImageBuilder()
            .addGenerator(new MinecraftTooltipGenerator.Builder().withName("x").withItemLore("hello").withPadding(8_000).build())
            .build();
        GeneratedObject plain = new GeneratorImageBuilder()
            .addGenerator(new MinecraftTooltipGenerator.Builder().withName("x").withItemLore("hello").build())
            .build();

        assertEquals(plain.getImage().getWidth() + 2 * MinecraftTooltip.MAX_PADDING, padded.getImage().getWidth());
        assertEquals(plain.getImage().getHeight() + 2 * MinecraftTooltip.MAX_PADDING, padded.getImage().getHeight());
    }

    @Test
    void hugeIconRepeatInLoreStillRenders() {
        GeneratedObject result = new GeneratorImageBuilder()
            .addGenerator(new MinecraftTooltipGenerator.Builder().withName("x").withItemLore("%%dot:5000000%%").build())
            .build();
        assertTrue(result.getImage().getWidth() > 0);
    }

    @Test
    void oversizedInventoryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftInventoryGenerator.Builder()
            .withRows(120).withSlotsPerRow(120).withInventoryString("stone:[1]").build());
        // Within the per-axis cap, but 60x60 slots is still far over the canvas budget.
        assertThrows(GeneratorException.class, () -> new MinecraftInventoryGenerator.Builder()
            .withRows(60).withSlotsPerRow(60).withInventoryString("stone:[1]").build());
        assertThrows(GeneratorValidationException.class, () -> InventoryRequest.builder()
            .rows(120).slotsPerRow(9).inventoryString("stone:[1]").build());
    }

    @Test
    void hugeExponentNumberIsRejectedQuickly() {
        IllegalArgumentException thrown = assertTimeoutPreemptively(Duration.ofSeconds(2),
            () -> assertThrows(IllegalArgumentException.class,
                () -> MinecraftSceneGenerator.fromScene("{\"schema_version\":1e400000000,\"regions\":[]}")));
        assertTrue(thrown.getMessage().length() < 200, "the error must not print the expanded number");

        assertThrows(IllegalArgumentException.class,
            () -> MinecraftSceneGenerator.fromScene("{\"schema_version\":1e-400000000,\"regions\":[]}"));
        assertThrows(IllegalArgumentException.class,
            () -> MinecraftSceneGenerator.fromScene("{\"schema_version\":1" + "0".repeat(100) + ",\"regions\":[]}"));
    }

    @Test
    void sceneOptionsHaveUpperBounds() {
        String region = "\"regions\":[{\"name\":\"t\",\"type\":\"tooltip\",\"lore\":\"hi\"}]";
        assertThrows(IllegalArgumentException.class,
            () -> MinecraftSceneGenerator.fromScene("{\"schema_version\":1,\"scale\":200," + region + "}"));
        assertThrows(IllegalArgumentException.class,
            () -> MinecraftSceneGenerator.fromScene("{\"schema_version\":1,\"margin\":50000," + region + "}"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSceneGenerator.fromScene(
            "{\"schema_version\":1,\"regions\":[{\"name\":\"t\",\"type\":\"tooltip\",\"lore\":\"hi\",\"at\":[100000,0]}]}"));
        assertDoesNotThrow(() -> MinecraftSceneGenerator.fromScene("{\"schema_version\":1,\"scale\":2,\"margin\":8," + region + "}"));
    }

    @Test
    void templateFillOutputIsBounded() {
        ContentTemplate template = ContentTemplate.parse(
            "{\"schema_version\":1,\"placeholders\":{\"a\":{}},\"content\":{\"lore\":\"" + "{a}".repeat(1_000) + "\"}}");

        assertDoesNotThrow(() -> template.fill(Map.of("a", "x".repeat(100))));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> template.fill(Map.of("a", "x".repeat(2_000))));
        assertTrue(thrown.getMessage().contains("too large"));
    }
}
