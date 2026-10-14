package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aerh.imagegenerator.tooling.ToolingPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayGeneratorTest {

    @TempDir
    Path root;

    @Test
    void armorAndTrimOverlaysAreDiscoveredFromTheMinecraftAssets() throws IOException {
        writeItemTexture("leather_boots_overlay");
        writeItemTexture("stone");
        // The game ships one greyscale overlay per armour piece, named "<armor>_trim.png".
        writeTrimTexture("helmet_trim");
        writeTrimTexture("chestplate_trim");
        writeTrimTexture("coast_helmet");

        new OverlayGenerator(paths()).generate();

        Map<String, JsonObject> entries = entriesByName();
        assertEquals("NORMAL", entries.get("leather_boots_overlay").get("type").getAsString());
        assertEquals("leather_armor", entries.get("leather_boots_overlay").get("colorOptions").getAsString());
        assertTrue(entries.containsKey("helmet_trim"), "helmet_trim should be discovered: " + entries.keySet());
        assertTrue(entries.containsKey("chestplate_trim"), "chestplate_trim should be discovered: " + entries.keySet());
        assertEquals("MAPPED", entries.get("helmet_trim").get("type").getAsString());
        assertEquals("armor_trim", entries.get("chestplate_trim").get("colorOptions").getAsString());
        assertFalse(entries.containsKey("coast_helmet"), "only *_trim.png files are trim overlays");
        assertFalse(entries.containsKey("trim_trim"));
        assertFalse(entries.containsKey("stone"));
    }

    @Test
    void overlaysStayOnOneTwoFiftySixPixelPage() throws IOException {
        writeItemTexture("leather_boots_overlay");
        writeTrimTexture("helmet_trim");

        new OverlayGenerator(paths()).generate();

        BufferedImage sheet = ImageIO.read(paths().spritesheets().resolve("overlays.png").toFile());
        assertEquals(1024, sheet.getWidth());
        assertEquals(256, sheet.getHeight());
        assertFalse(Files.exists(paths().spritesheets().resolve("overlays-1.png")));
        for (JsonObject entry : entriesByName().values()) {
            assertEquals(256, entry.get("size").getAsInt());
            assertEquals(0, entry.get("page").getAsInt());
            assertEquals("overlays.png", entry.get("atlas").getAsString());
        }
    }

    @Test
    void bundledDefinitionsIncludeSpecialOverlaysThatExistInTheAssets() throws IOException {
        writeItemTexture("potion_overlay");
        writeItemTexture("tipped_arrow_head");

        new OverlayGenerator(paths()).generate();

        Map<String, JsonObject> entries = entriesByName();
        assertEquals("potion", entries.get("potion_overlay").get("colorOptions").getAsString());
        assertEquals("OVERLAY", entries.get("potion_overlay").get("colorMode").getAsString());
        assertEquals("effect", entries.get("tipped_arrow_head").get("colorOptions").getAsString());
        assertFalse(entries.containsKey("firework_star_overlay"), "special overlays without a texture are skipped");
    }

    @Test
    void aDefinitionsFileOverrideReplacesTheBundledOne() throws IOException {
        writeItemTexture("leather_boots_overlay");
        writeItemTexture("wolf_armor_overlay");
        writeItemTexture("custom_overlay");
        Path definitions = root.resolve("definitions.json");
        Files.writeString(definitions, """
            {
              "specialOverlays": [
                {"fileName": "custom_overlay", "colorOptions": "potion", "type": "NORMAL"}
              ],
              "armorOverlayPatterns": ["wolf_"]
            }
            """);

        new OverlayGenerator(paths(), definitions).generate();

        Map<String, JsonObject> entries = entriesByName();
        assertTrue(entries.containsKey("custom_overlay"));
        assertTrue(entries.containsKey("wolf_armor_overlay"));
        assertFalse(entries.containsKey("leather_boots_overlay"), "patterns come from the override only");
        assertFalse(entries.get("custom_overlay").has("colorMode"));
    }

    @Test
    void aMissingDefinitionsFileIsAnError() {
        IOException error = assertThrows(IOException.class,
            () -> new OverlayGenerator(paths(), root.resolve("missing.json")));
        assertTrue(error.getMessage().contains("missing.json"), error.getMessage());
    }

    @Test
    void aNullColorModeInTheDefinitionsIsAnError() throws IOException {
        Path definitions = root.resolve("definitions.json");
        Files.writeString(definitions, """
            {"specialOverlays": [{"fileName": "x", "colorOptions": "potion", "type": "NORMAL", "colorMode": null}]}
            """);

        IOException error = assertThrows(IOException.class, () -> new OverlayGenerator(paths(), definitions));

        assertTrue(error.getMessage().contains("colorMode"), error.getMessage());
        assertTrue(error.getMessage().contains("definitions.json"), error.getMessage());
    }

    @Test
    void aNonStringArmorPatternIsAnError() throws IOException {
        Path definitions = root.resolve("definitions.json");
        Files.writeString(definitions, "{\"armorOverlayPatterns\": [\"leather_\", {\"x\": 1}]}");

        IOException error = assertThrows(IOException.class, () -> new OverlayGenerator(paths(), definitions));

        assertTrue(error.getMessage().contains("armorOverlayPatterns"), error.getMessage());
    }

    @Test
    void aMalformedDefinitionsFileIsAnError() throws IOException {
        Path definitions = root.resolve("broken.json");
        Files.writeString(definitions, "{ not json");

        IOException error = assertThrows(IOException.class, () -> new OverlayGenerator(paths(), definitions));
        assertTrue(error.getMessage().contains("broken.json"), error.getMessage());
    }

    private ToolingPaths paths() {
        return new ToolingPaths(root.resolve("work"), root.resolve("out"), root.resolve("renderer"));
    }

    private Map<String, JsonObject> entriesByName() throws IOException {
        Path json = paths().json().resolve("overlay_coordinates.json");
        assertTrue(Files.isRegularFile(json), "overlay coordinates should exist");
        JsonArray array = JsonParser.parseString(Files.readString(json)).getAsJsonArray();
        Map<String, JsonObject> entries = new HashMap<>();
        for (JsonElement element : array) {
            entries.put(element.getAsJsonObject().get("name").getAsString(), element.getAsJsonObject());
        }
        return entries;
    }

    private void writeItemTexture(String name) throws IOException {
        writePng(paths().minecraftAssets().resolve("textures/item").resolve(name + ".png"));
    }

    private void writeTrimTexture(String name) throws IOException {
        writePng(paths().minecraftAssets().resolve("textures/trims/items").resolve(name + ".png"));
    }

    private static void writePng(Path file) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xff808080);
            }
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }
}
