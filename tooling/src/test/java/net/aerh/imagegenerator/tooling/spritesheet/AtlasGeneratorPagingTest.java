package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasGeneratorPagingTest {

    private static final int RED = 0xffff0000;
    private static final int GREEN = 0xff00ff00;
    private static final int BLUE = 0xff0000ff;

    @TempDir
    Path directory;

    @Test
    void defaultLayoutPacksSixteenTilesPerRowWithoutGutters() throws IOException {
        List<AtlasGenerator.AtlasEntry> entries = solidEntries(17, 256);
        new TestAtlas(AtlasLayout.DEFAULT, entries).generate();

        BufferedImage page = ImageIO.read(directory.resolve("atlas.png").toFile());
        assertEquals(4096, page.getWidth());
        assertEquals(512, page.getHeight());
        assertFalse(Files.exists(directory.resolve("atlas-1.png")));

        JsonArray json = readCoordinates();
        for (int i = 0; i < entries.size(); i++) {
            JsonObject coordinate = json.get(i).getAsJsonObject();
            assertEquals((i % 16) * 256, coordinate.get("x").getAsInt(), "x of entry " + i);
            assertEquals((i / 16) * 256, coordinate.get("y").getAsInt(), "y of entry " + i);
            assertEquals(256, coordinate.get("size").getAsInt());
            assertEquals(0, coordinate.get("page").getAsInt());
            assertEquals("atlas.png", coordinate.get("atlas").getAsString());
        }
    }

    @Test
    void pagesKeepPixelsAndTransparentGutters() throws IOException {
        List<AtlasGenerator.AtlasEntry> entries = List.of(entry("red", RED, 2), entry("green", GREEN, 2), entry("blue", BLUE, 2));
        new TestAtlas(new AtlasLayout(2, 8, 4, 1), entries).generate();

        BufferedImage first = ImageIO.read(directory.resolve("atlas.png").toFile());
        BufferedImage second = ImageIO.read(directory.resolve("atlas-1.png").toFile());
        assertEquals(8, first.getWidth());
        assertEquals(4, first.getHeight());
        assertTile(first, 1, 1, 2, RED);
        assertTile(first, 5, 1, 2, GREEN);
        assertTile(second, 1, 1, 2, BLUE);
        assertTransparent(first, 0, 0, 8, 1);
        assertTransparent(first, 0, 3, 8, 1);
        assertTransparent(first, 3, 0, 2, 4);

        JsonArray json = readCoordinates();
        assertEquals(0, json.get(0).getAsJsonObject().get("page").getAsInt());
        assertEquals("atlas.png", json.get(0).getAsJsonObject().get("atlas").getAsString());
        assertEquals(1, json.get(2).getAsJsonObject().get("page").getAsInt());
        assertEquals("atlas-1.png", json.get(2).getAsJsonObject().get("atlas").getAsString());
    }

    @Test
    void singlePageKeepsTheOriginalFilename() throws IOException {
        new TestAtlas(new AtlasLayout(2, 4, 4, 0), List.of(entry("red", RED, 2))).generate();

        assertTrue(Files.isRegularFile(directory.resolve("atlas.png")));
        assertFalse(Files.exists(directory.resolve("atlas-0.png")));
        assertFalse(Files.exists(directory.resolve("atlas-1.png")));

        JsonObject coordinate = readCoordinates().get(0).getAsJsonObject();
        assertEquals("red", coordinate.get("name").getAsString());
        assertEquals(0, coordinate.get("x").getAsInt());
        assertEquals(0, coordinate.get("y").getAsInt());
        assertEquals(2, coordinate.get("size").getAsInt());
    }

    @Test
    void exactlyCapacityEntriesFillOnePage() throws IOException {
        new TestAtlas(new AtlasLayout(2, 4, 4, 0), solidEntries(4, 2)).generate();

        assertTrue(Files.exists(directory.resolve("atlas.png")));
        assertFalse(Files.exists(directory.resolve("atlas-1.png")));
        for (int i = 0; i < 4; i++) {
            assertEquals(0, readCoordinates().get(i).getAsJsonObject().get("page").getAsInt());
        }
    }

    @Test
    void oneEntryOverCapacityStartsASecondPage() throws IOException {
        new TestAtlas(new AtlasLayout(2, 4, 4, 0), solidEntries(5, 2)).generate();

        assertTrue(Files.exists(directory.resolve("atlas-1.png")));
        assertFalse(Files.exists(directory.resolve("atlas-2.png")));

        JsonObject last = readCoordinates().get(4).getAsJsonObject();
        assertEquals(1, last.get("page").getAsInt());
        assertEquals(0, last.get("x").getAsInt());
        assertEquals(0, last.get("y").getAsInt());
    }

    @Test
    void paddedLayoutOffsetsBothAxesAndLeavesGuttersBetweenRows() throws IOException {
        List<AtlasGenerator.AtlasEntry> entries = List.of(
            entry("a", RED, 2), entry("b", GREEN, 2), entry("c", BLUE, 2), entry("d", RED, 2)
        );
        new TestAtlas(new AtlasLayout(2, 8, 8, 1), entries).generate();

        BufferedImage page = ImageIO.read(directory.resolve("atlas.png").toFile());
        assertEquals(8, page.getWidth());
        assertEquals(8, page.getHeight());
        assertTile(page, 1, 1, 2, RED);
        assertTile(page, 5, 1, 2, GREEN);
        assertTile(page, 1, 5, 2, BLUE);
        assertTile(page, 5, 5, 2, RED);
        assertTransparent(page, 0, 3, 8, 2);
        assertTransparent(page, 3, 0, 2, 8);
        assertTransparent(page, 0, 7, 8, 1);
        assertTransparent(page, 7, 0, 1, 8);

        JsonArray json = readCoordinates();
        assertEquals(1, json.get(2).getAsJsonObject().get("x").getAsInt());
        assertEquals(5, json.get(2).getAsJsonObject().get("y").getAsInt());
    }

    @Test
    void lastPageIsOnlyAsTallAsItsRows() throws IOException {
        new TestAtlas(new AtlasLayout(2, 4, 8, 0), solidEntries(10, 2)).generate();

        BufferedImage first = ImageIO.read(directory.resolve("atlas.png").toFile());
        BufferedImage second = ImageIO.read(directory.resolve("atlas-1.png").toFile());
        assertEquals(8, first.getHeight());
        assertEquals(2, second.getHeight());
        assertEquals(4, second.getWidth());
    }

    @Test
    void stalePagesFromAnEarlierRunAreRemoved() throws IOException {
        Files.write(directory.resolve("atlas-1.png"), new byte[]{1, 2, 3});
        Files.write(directory.resolve("atlas-2.png"), new byte[]{1, 2, 3});

        new TestAtlas(new AtlasLayout(2, 4, 4, 0), List.of(entry("red", RED, 2))).generate();

        assertFalse(Files.exists(directory.resolve("atlas-1.png")));
        assertFalse(Files.exists(directory.resolve("atlas-2.png")));
    }

    @Test
    void stalePagesBeyondAGapAreRemovedAndUnrelatedFilesAreKept() throws IOException {
        Files.write(directory.resolve("atlas-3.png"), new byte[]{1, 2, 3});
        Files.write(directory.resolve("other-1.png"), new byte[]{1, 2, 3});
        Files.write(directory.resolve("atlas-x.png"), new byte[]{1, 2, 3});

        new TestAtlas(new AtlasLayout(2, 4, 4, 0), List.of(entry("red", RED, 2))).generate();

        assertFalse(Files.exists(directory.resolve("atlas-3.png")));
        assertTrue(Files.exists(directory.resolve("other-1.png")), "a different atlas's pages are left alone");
        assertTrue(Files.exists(directory.resolve("atlas-x.png")), "only numbered suffixes are pages");
    }

    @Test
    void aShrinkingRunKeepsThePagesItWroteAndRemovesTheRest() throws IOException {
        Files.write(directory.resolve("atlas-1.png"), new byte[]{1, 2, 3});
        Files.write(directory.resolve("atlas-2.png"), new byte[]{1, 2, 3});

        new TestAtlas(new AtlasLayout(2, 4, 4, 0), solidEntries(5, 2)).generate();

        assertEquals(4, ImageIO.read(directory.resolve("atlas-1.png").toFile()).getWidth());
        assertFalse(Files.exists(directory.resolve("atlas-2.png")));
    }

    @Test
    void coordinatesCarryPageAndAtlasButNotPageDimensions() throws IOException {
        new TestAtlas(new AtlasLayout(2, 4, 4, 0), solidEntries(5, 2)).generate();

        JsonArray json = readCoordinates();
        assertEquals(5, json.size());
        for (int i = 0; i < json.size(); i++) {
            assertEquals(Set.of("name", "x", "y", "size", "page", "atlas"), json.get(i).getAsJsonObject().keySet());
        }
    }

    @Test
    void pageOutputPathNumbersPagesBeforeTheExtension() {
        TestAtlas atlas = new TestAtlas(AtlasLayout.DEFAULT, List.of());
        assertEquals(directory.resolve("atlas.png"), atlas.pageOutputPath(0));
        assertEquals(directory.resolve("atlas-2.png"), atlas.pageOutputPath(2));
    }

    @Test
    void pageOutputPathIgnoresDotsInDirectoryNames() {
        Path output = directory.resolve("v26.3").resolve("atlas");
        TestAtlas atlas = new TestAtlas(AtlasLayout.DEFAULT, List.of(), output);
        assertEquals(output, atlas.pageOutputPath(0));
        assertEquals(directory.resolve("v26.3").resolve("atlas-1"), atlas.pageOutputPath(1));
    }

    @Test
    void generatingNothingWritesNoFiles() {
        new TestAtlas(new AtlasLayout(2, 4, 4, 0), List.of()).generate();

        assertFalse(Files.exists(directory.resolve("atlas.png")));
        assertFalse(Files.exists(directory.resolve("coordinates.json")));
    }

    private JsonArray readCoordinates() throws IOException {
        return JsonParser.parseString(Files.readString(directory.resolve("coordinates.json"))).getAsJsonArray();
    }

    private static List<AtlasGenerator.AtlasEntry> solidEntries(int count, int size) {
        List<AtlasGenerator.AtlasEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(entry("entry" + i, 0xff000000 | (i * 0x010101), size));
        }
        return entries;
    }

    private static AtlasGenerator.AtlasEntry entry(String name, int argb, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return new AtlasGenerator.AtlasEntry(name, image);
    }

    private static void assertTile(BufferedImage image, int x0, int y0, int size, int argb) {
        for (int y = y0; y < y0 + size; y++) {
            for (int x = x0; x < x0 + size; x++) {
                assertEquals(argb, image.getRGB(x, y), "pixel at " + x + "," + y);
            }
        }
    }

    private static void assertTransparent(BufferedImage image, int x0, int y0, int width, int height) {
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                assertEquals(0, image.getRGB(x, y), "pixel at " + x + "," + y + " should be transparent");
            }
        }
    }

    private class TestAtlas extends AtlasGenerator {

        private final List<AtlasEntry> entries;

        TestAtlas(AtlasLayout layout, List<AtlasEntry> entries) {
            this(layout, entries, directory.resolve("atlas.png"));
        }

        TestAtlas(AtlasLayout layout, List<AtlasEntry> entries, Path atlasOutputPath) {
            super(layout, atlasOutputPath, directory.resolve("coordinates.json"));
            this.entries = entries;
        }

        @Override
        protected List<AtlasEntry> loadTextures() {
            return entries;
        }

        @Override
        protected JsonObject entryToJson(AtlasEntry entry) {
            JsonObject json = new JsonObject();
            json.addProperty("name", entry.name);
            json.addProperty("x", entry.x);
            json.addProperty("y", entry.y);
            json.addProperty("size", layout.tileSize());
            return json;
        }
    }
}
