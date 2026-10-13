package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtlasLayoutTest {
    @TempDir Path directory;

    @Test void pagesKeepPixelsAndTransparentGutters() throws Exception {
        var entries = List.of(entry("red", 0xffff0000), entry("green", 0xff00ff00), entry("blue", 0xff0000ff));
        new TestAtlas(new AtlasLayout(2, 8, 4, 1), entries).generate();
        var first = ImageIO.read(directory.resolve("atlas.png").toFile());
        var second = ImageIO.read(directory.resolve("atlas-1.png").toFile());
        assertEquals(8, first.getWidth());
        assertEquals(4, first.getHeight());
        assertEquals(0xffff0000, first.getRGB(1, 1));
        assertEquals(0xff00ff00, first.getRGB(5, 1));
        assertEquals(0xff0000ff, second.getRGB(1, 1));
        assertEquals(0, first.getRGB(0, 1));
        assertEquals(0, first.getRGB(3, 1));
        assertEquals(0, first.getRGB(4, 1));
        var json = JsonParser.parseString(Files.readString(directory.resolve("coordinates.json"))).getAsJsonArray();
        assertEquals(0, json.get(0).getAsJsonObject().get("page").getAsInt());
        assertEquals(1, json.get(2).getAsJsonObject().get("page").getAsInt());
        assertEquals("atlas-1.png", json.get(2).getAsJsonObject().get("atlas").getAsString());
        assertEquals(4, json.get(2).getAsJsonObject().get("atlasHeight").getAsInt());
    }

    @Test void singlePageKeepsOriginalFilenameAndCoordinateFields() throws Exception {
        new TestAtlas(new AtlasLayout(2, 4, 4, 0), List.of(entry("red", 0xffff0000))).generate();
        assertTrue(Files.isRegularFile(directory.resolve("atlas.png")));
        assertFalse(Files.exists(directory.resolve("atlas-0.png")));
        var coordinate = JsonParser.parseString(Files.readString(directory.resolve("coordinates.json")))
            .getAsJsonArray().get(0).getAsJsonObject();
        assertEquals(0, coordinate.get("x").getAsInt());
        assertEquals(0, coordinate.get("y").getAsInt());
        assertEquals(2, coordinate.get("size").getAsInt());
    }

    @Test void invalidLayoutsFailBeforePacking() {
        assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(0, 8, 8, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(4, 5, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(4, 8, 8, -1));
        assertThrows(IllegalArgumentException.class, () -> new AtlasLayout(Integer.MAX_VALUE, 8, 8, 1));
    }

    @Test void unlimitedHeightDoesNotOverflowCapacity() {
        assertEquals(Integer.MAX_VALUE, new AtlasLayout(1, 4096, Integer.MAX_VALUE, 0).capacity());
    }

    private AtlasGenerator.AtlasEntry entry(String name, int color) {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) image.setRGB(x, y, color);
        return new AtlasGenerator.AtlasEntry(name, image);
    }

    private class TestAtlas extends AtlasGenerator {
        private final List<AtlasEntry> entries;
        TestAtlas(AtlasLayout layout, List<AtlasEntry> entries) {
            super(layout, directory.resolve("atlas.png").toString(), directory.resolve("coordinates.json").toString());
            this.entries = entries;
        }
        protected List<AtlasEntry> loadTextures() { return entries; }
        protected JsonObject entryToJson(AtlasEntry entry) {
            var json = new JsonObject();
            json.addProperty("name", entry.name);
            json.addProperty("x", entry.x);
            json.addProperty("y", entry.y);
            json.addProperty("size", layout.tileSize());
            return json;
        }
    }
}
