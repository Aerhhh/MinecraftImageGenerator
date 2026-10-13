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
}
