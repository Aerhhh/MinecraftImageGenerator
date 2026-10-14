package net.aerh.imagegenerator.tooling.spritesheet;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtlasWriteFailureTest {
    @TempDir Path directory;
    @Test void outputParentMustBeWritableDirectory() throws Exception {
        Path blocker = directory.resolve("file");
        Files.writeString(blocker, "not a directory");
        var generator = new AtlasGenerator(256, blocker.resolve("atlas.png").toString(), directory.resolve("coords.json").toString()) {
            protected List<AtlasEntry> loadTextures() {
                return List.of(new AtlasEntry("item", new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)));
            }
            protected JsonObject entryToJson(AtlasEntry entry) { return new JsonObject(); }
        };
        assertThrows(IllegalStateException.class, generator::generate);
        assertFalse(Files.exists(directory.resolve("coords.json")));
    }

    @Test void laterPageWriteFailureStopsBeforeCoordinatesArePublished() throws Exception {
        Files.createDirectory(directory.resolve("atlas-1.png"));
        Files.writeString(directory.resolve("atlas-1.png/blocker"), "not an image file");
        var generator = new AtlasGenerator(new AtlasLayout(2, 2, 2, 0),
            directory.resolve("atlas.png").toString(), directory.resolve("coords.json").toString()) {
            protected List<AtlasEntry> loadTextures() {
                return List.of(
                    new AtlasEntry("first", new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)),
                    new AtlasEntry("second", new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)));
            }
            protected JsonObject entryToJson(AtlasEntry entry) { return new JsonObject(); }
        };
        assertThrows(IllegalStateException.class, generator::generate);
        assertTrue(Files.isRegularFile(directory.resolve("atlas.png")));
        assertFalse(Files.exists(directory.resolve("coords.json")));
    }

}
