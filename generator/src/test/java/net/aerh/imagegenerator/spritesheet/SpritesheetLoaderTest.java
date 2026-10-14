package net.aerh.imagegenerator.spritesheet;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.hypixel.nerdbot.marmalade.functional.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpritesheetLoaderTest {

    private static final String BASE = "/fixture";
    private static final String COORDINATES_PATH = BASE + "/json/atlas_coordinates.json";
    private static final String PAGE_ZERO_FILE = "minecraft_texture_atlas.png";
    private static final String PAGE_ONE_FILE = "minecraft_texture_atlas-1.png";
    private static final String PAGE_ZERO_PATH = BASE + "/spritesheets/" + PAGE_ZERO_FILE;
    private static final String PAGE_ONE_PATH = BASE + "/spritesheets/" + PAGE_ONE_FILE;
    private static final int TILE_SIZE = 2;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int YELLOW = 0xFFFFFF00;

    @TempDir
    Path root;

    @BeforeEach
    void writeFixture() throws IOException {
        writeAtlas(PAGE_ZERO_FILE, RED, GREEN);
        writeAtlas(PAGE_ONE_FILE, BLUE, YELLOW);
        writeCoordinates(
            entry("red", 0, 0, 0, PAGE_ZERO_FILE),
            entry("green", TILE_SIZE, 0, 0, PAGE_ZERO_FILE),
            entry("blue", 0, 0, 1, PAGE_ONE_FILE),
            entry("yellow", TILE_SIZE, 0, 1, PAGE_ONE_FILE)
        );
    }

    @Test
    void textureOnPageZeroReturnsPixelsFromFirstAtlas() {
        CountingLoader loader = new CountingLoader();

        BufferedImage texture = loader.getTexture("green");

        assertSolidTile(texture, GREEN);
    }

    @Test
    void textureOnPageZeroDoesNotOpenPageOneAtlas() {
        CountingLoader loader = new CountingLoader();

        loader.getTexture("red");

        assertEquals(0, loader.opensOf(PAGE_ONE_PATH), "page 1 opens");
    }

    @Test
    void textureOnPageOneReturnsPixelsFromSecondAtlas() {
        CountingLoader loader = new CountingLoader();

        BufferedImage texture = loader.getTexture("yellow");

        assertSolidTile(texture, YELLOW);
        assertEquals(1, loader.opensOf(PAGE_ONE_PATH), "page 1 opens");
    }

    @Test
    void unknownNameReturnsNull() {
        CountingLoader loader = new CountingLoader();

        assertNull(loader.getTexture("does_not_exist"));
    }

    @Test
    void nullNameReturnsNull() {
        CountingLoader loader = new CountingLoader();

        assertNull(loader.getTexture(null));
    }

    @Test
    void texturesOnTheSamePageOpenItsAtlasOnce() {
        CountingLoader loader = new CountingLoader();

        loader.getTexture("red");
        loader.getTexture("green");

        assertEquals(1, loader.opensOf(PAGE_ZERO_PATH), "page 0 opens");
    }

    @Test
    void repeatedGetTextureReturnsSameInstance() {
        CountingLoader loader = new CountingLoader();

        BufferedImage first = loader.getTexture("red");
        BufferedImage second = loader.getTexture("red");

        assertSame(first, second);
    }

    @Test
    void entriesWithoutAtlasFieldLoadFromDefaultAtlas() throws IOException {
        writeCoordinates(entryWithoutAtlas("legacy", TILE_SIZE, 0));
        CountingLoader loader = new CountingLoader();

        BufferedImage texture = loader.getTexture("legacy");

        assertSolidTile(texture, GREEN);
        assertEquals(1, loader.opensOf(PAGE_ZERO_PATH), "page 0 opens");
    }

    @Test
    void missingPageAtlasThrowsGeneratorExceptionNamingPath() throws IOException {
        writeCoordinates(entry("ghost", 0, 0, 2, "minecraft_texture_atlas-2.png"));
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("ghost"));

        assertTrue(exception.getMessage().contains(BASE + "/spritesheets/minecraft_texture_atlas-2.png"),
            exception.getMessage());
    }

    @Test
    void missingCoordinatesFileThrowsGeneratorExceptionNamingPath() throws IOException {
        Files.delete(fixturePath("/json/atlas_coordinates.json"));
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("red"));

        assertTrue(exception.getMessage().contains(COORDINATES_PATH), exception.getMessage());
    }

    @Test
    void getImageMapContainsEveryNameWithItsTexture() {
        CountingLoader loader = new CountingLoader();

        Map<String, BufferedImage> imageMap = loader.getImageMap();

        assertEquals(Set.of("red", "green", "blue", "yellow"), imageMap.keySet());
        assertSolidTile(imageMap.get("red"), RED);
        assertSolidTile(imageMap.get("green"), GREEN);
        assertSolidTile(imageMap.get("blue"), BLUE);
        assertSolidTile(imageMap.get("yellow"), YELLOW);
    }

    @Test
    void getImageMapDecodesEveryPageOnce() {
        CountingLoader loader = new CountingLoader();

        loader.getImageMap();

        assertEquals(1, loader.opensOf(PAGE_ZERO_PATH), "page 0 opens");
        assertEquals(1, loader.opensOf(PAGE_ONE_PATH), "page 1 opens");
    }

    @Test
    void getImageMapReturnsSameInstancesAsGetTexture() {
        CountingLoader loader = new CountingLoader();

        BufferedImage fromGetTexture = loader.getTexture("blue");
        Map<String, BufferedImage> imageMap = loader.getImageMap();

        assertSame(fromGetTexture, imageMap.get("blue"));
    }

    @Test
    void searchForTextureReturnsSubstringMatchesOnly() {
        CountingLoader loader = new CountingLoader();

        List<Pair<String, BufferedImage>> matches = loader.searchForTexture("re");

        assertEquals(List.of("red", "green"), matches.stream().map(Pair::first).toList());
        assertSolidTile(matches.get(0).second(), RED);
        assertSolidTile(matches.get(1).second(), GREEN);
    }

    @Test
    void searchForTextureIsCaseSensitive() {
        CountingLoader loader = new CountingLoader();

        assertTrue(loader.searchForTexture("RE").isEmpty());
    }

    @Test
    void searchForTextureReturnsEmptyListWhenNothingMatches() {
        CountingLoader loader = new CountingLoader();

        assertTrue(loader.searchForTexture("purple").isEmpty());
    }

    @Test
    void searchForTextureOnlyDecodesPagesOfMatches() {
        CountingLoader loader = new CountingLoader();

        loader.searchForTexture("re");

        assertEquals(1, loader.opensOf(PAGE_ZERO_PATH), "page 0 opens");
        assertEquals(0, loader.opensOf(PAGE_ONE_PATH), "page 1 opens");
    }

    @Test
    void searchForTextureRejectsNullQuery() {
        CountingLoader loader = new CountingLoader();

        assertThrows(NullPointerException.class, () -> loader.searchForTexture(null));
    }

    @Test
    void coordinatesOutsidePageThrowGeneratorExceptionNamingTextureAndPath() throws IOException {
        writeCoordinates(entry("offscreen", 100, 0, 0, PAGE_ZERO_FILE));
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("offscreen"));

        assertTrue(exception.getMessage().contains("offscreen"), exception.getMessage());
        assertTrue(exception.getMessage().contains(PAGE_ZERO_PATH), exception.getMessage());
    }

    @Test
    void coordinatesEntryMissingFieldThrowsGeneratorExceptionNamingField() throws IOException {
        writeCoordinates("{\"name\":\"nameless\",\"y\":0,\"size\":" + TILE_SIZE + "}");
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("nameless"));

        assertTrue(exception.getMessage().contains("'x'"), exception.getMessage());
        assertTrue(exception.getMessage().contains(COORDINATES_PATH), exception.getMessage());
    }

    @Test
    void coordinatesEntryWithNonNumericFieldThrowsGeneratorException() throws IOException {
        writeCoordinates("{\"name\":\"odd\",\"x\":\"left\",\"y\":0,\"size\":" + TILE_SIZE + "}");
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("odd"));

        assertTrue(exception.getMessage().contains(COORDINATES_PATH), exception.getMessage());
    }

    @Test
    void malformedCoordinatesFileThrowsGeneratorExceptionNamingPath() throws IOException {
        Files.writeString(fixturePath("/json/atlas_coordinates.json"), "[{\"name\": ");
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("red"));

        assertTrue(exception.getMessage().contains(COORDINATES_PATH), exception.getMessage());
    }

    @Test
    void coordinatesFileThatIsNotAnArrayThrowsGeneratorExceptionNamingPath() throws IOException {
        Files.writeString(fixturePath("/json/atlas_coordinates.json"), "{\"name\": \"red\"}");
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("red"));

        assertTrue(exception.getMessage().contains(COORDINATES_PATH), exception.getMessage());
    }

    @Test
    void pageAtlasThatIsNotAnImageThrowsGeneratorExceptionNamingPath() throws IOException {
        Files.write(fixturePath("/spritesheets/" + PAGE_ZERO_FILE), new byte[] {1, 2, 3, 4});
        CountingLoader loader = new CountingLoader();

        GeneratorException exception = assertThrows(GeneratorException.class, () -> loader.getTexture("red"));

        assertTrue(exception.getMessage().contains(PAGE_ZERO_PATH), exception.getMessage());
    }

    @Test
    void duplicateNamesKeepTheLastEntry() throws IOException {
        writeCoordinates(entry("dup", 0, 0, 0, PAGE_ZERO_FILE), entry("dup", TILE_SIZE, 0, 0, PAGE_ZERO_FILE));
        CountingLoader loader = new CountingLoader();

        assertSolidTile(loader.getTexture("dup"), GREEN);
    }

    @Test
    void coordinatesFileIsReadOnce() {
        CountingLoader loader = new CountingLoader();

        loader.getTexture("red");
        loader.getTexture("blue");
        loader.searchForTexture("re");
        loader.getImageMap();

        assertEquals(1, loader.opensOf(COORDINATES_PATH), "coordinates opens");
    }

    @Test
    void concurrentRequestsOpenEachResourceOnce() throws Exception {
        CountingLoader loader = new CountingLoader();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<BufferedImage>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String name = i % 2 == 0 ? "red" : "blue";
                futures.add(executor.submit(() -> {
                    start.await();
                    return loader.getTexture(name);
                }));
            }
            start.countDown();
            for (Future<BufferedImage> future : futures) {
                assertNotNull(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, loader.opensOf(COORDINATES_PATH), "coordinates opens");
        assertEquals(1, loader.opensOf(PAGE_ZERO_PATH), "page 0 opens");
        assertEquals(1, loader.opensOf(PAGE_ONE_PATH), "page 1 opens");
    }

    /**
     * Writes a 4x2 ARGB PNG made of two 2x2 tiles, the left one filled with {@code leftColour}
     * and the right one with {@code rightColour}.
     */
    private void writeAtlas(String fileName, int leftColour, int rightColour) throws IOException {
        BufferedImage atlas = new BufferedImage(TILE_SIZE * 2, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                atlas.setRGB(x, y, leftColour);
                atlas.setRGB(x + TILE_SIZE, y, rightColour);
            }
        }
        Path file = fixturePath("/spritesheets/" + fileName);
        Files.createDirectories(file.getParent());
        ImageIO.write(atlas, "png", file.toFile());
    }

    private void writeCoordinates(String... entries) throws IOException {
        Path file = fixturePath("/json/atlas_coordinates.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "[" + String.join(",", entries) + "]");
    }

    private static String entry(String name, int x, int y, int page, String atlas) {
        return "{\"name\":\"" + name + "\",\"x\":" + x + ",\"y\":" + y + ",\"size\":" + TILE_SIZE
            + ",\"page\":" + page + ",\"atlas\":\"" + atlas + "\"}";
    }

    private static String entryWithoutAtlas(String name, int x, int y) {
        return "{\"name\":\"" + name + "\",\"x\":" + x + ",\"y\":" + y + ",\"size\":" + TILE_SIZE + "}";
    }

    private Path fixturePath(String pathUnderBase) {
        return root.resolve((BASE + pathUnderBase).substring(1));
    }

    private static void assertSolidTile(BufferedImage image, int argb) {
        assertNotNull(image, "texture");
        assertEquals(TILE_SIZE, image.getWidth(), "width");
        assertEquals(TILE_SIZE, image.getHeight(), "height");
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                assertEquals(argb, image.getRGB(x, y), "pixel at (" + x + ", " + y + ")");
            }
        }
    }

    /**
     * Serves resources from the temp directory and counts how many times each path was requested.
     */
    private final class CountingLoader extends SpritesheetLoader {

        private final Map<String, Integer> opens = new ConcurrentHashMap<>();

        CountingLoader() {
            super(BASE);
        }

        @Override
        protected InputStream getResource(String path) {
            opens.merge(path, 1, Integer::sum);
            Path file = root.resolve(path.substring(1));
            if (!Files.isRegularFile(file)) {
                return null;
            }
            try {
                return Files.newInputStream(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        int opensOf(String path) {
            return opens.getOrDefault(path, 0);
        }
    }
}
