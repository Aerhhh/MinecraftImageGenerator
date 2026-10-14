package net.aerh.imagegenerator.tooling.spritesheet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A page or coordinates file that cannot be written has to fail the run before the coordinates are published,
 * so a build never packages a JSON index that points at pages which were not written.
 */
class AtlasWriteFailureTest {

    private static final AtlasLayout ONE_TILE_PER_PAGE = new AtlasLayout(2, 2, 2, 0);

    @TempDir
    Path directory;

    @Test
    void anAtlasParentThatIsAFileFailsBeforeAnythingIsWritten() throws IOException {
        Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        TestAtlas atlas = new TestAtlas(ONE_TILE_PER_PAGE, List.of(blank("item")),
            blocker.resolve("atlas.png"), directory.resolve("coords.json"));

        UncheckedIOException error = assertThrows(UncheckedIOException.class, atlas::generate);

        assertTrue(error.getMessage().contains("atlas.png"), error.getMessage());
        assertFalse(Files.exists(directory.resolve("coords.json")));
    }

    @Test
    void aLaterPageThatCannotBeWrittenStopsBeforeTheCoordinatesArePublished() throws IOException {
        Path secondPage = directory.resolve("atlas-1.png");
        Files.createDirectory(secondPage);
        Files.writeString(secondPage.resolve("occupant"), "keeps the directory from being deleted");
        TestAtlas atlas = new TestAtlas(ONE_TILE_PER_PAGE, List.of(blank("first"), blank("second")),
            directory.resolve("atlas.png"), directory.resolve("coords.json"));

        UncheckedIOException error = assertThrows(UncheckedIOException.class, atlas::generate);

        assertTrue(error.getMessage().contains("atlas-1.png"), error.getMessage());
        assertTrue(Files.isRegularFile(directory.resolve("atlas.png")), "the first page was written before the failure");
        assertFalse(Files.exists(directory.resolve("coords.json")));
    }

    @Test
    void aCoordinatesParentThatIsAFileFailsTheRun() throws IOException {
        Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        TestAtlas atlas = new TestAtlas(ONE_TILE_PER_PAGE, List.of(blank("item")),
            directory.resolve("atlas.png"), blocker.resolve("coords.json"));

        UncheckedIOException error = assertThrows(UncheckedIOException.class, atlas::generate);

        assertTrue(error.getMessage().contains("coords.json"), error.getMessage());
    }

    private static AtlasGenerator.AtlasEntry blank(String name) {
        return new AtlasGenerator.AtlasEntry(name, new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));
    }
}
