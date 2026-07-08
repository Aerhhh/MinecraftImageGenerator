package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryPackSourceTest {

    private static final PackLimits LIMITS = new PackLimits(100, 32, 1024, 1024);

    @TempDir
    Path root;

    @TempDir
    Path outsideDir;

    @BeforeEach
    void writeFixture() throws IOException {
        Files.createDirectories(root.resolve("assets/testpack/items/item"));
        Files.write(root.resolve("assets/testpack/items/item/simple.json"), "{}".getBytes());
    }

    @Test
    void readsExistingFile() throws IOException {
        try (PackSource source = PackSource.directory(root, LIMITS)) {
            assertArrayEquals("{}".getBytes(), source.read("assets/testpack/items/item/simple.json"));
            assertTrue(source.exists("assets/testpack/items/item/simple.json"));
            assertFalse(source.exists("assets/testpack/nope.json"));
        }
    }

    @Test
    void listsFilesUnderPrefix() throws IOException {
        try (PackSource source = PackSource.directory(root, LIMITS)) {
            List<String> entries = source.list("assets/");
            assertTrue(entries.contains("assets/testpack/items/item/simple.json"));
        }
    }

    @Test
    void rejectsPathTraversal() throws IOException {
        try (PackSource source = PackSource.directory(root, LIMITS)) {
            assertThrows(PackLoadException.class, () -> source.read("assets/../../outside.txt"));
        }
    }

    @Test
    void rejectsMissingFileOnRead() throws IOException {
        try (PackSource source = PackSource.directory(root, LIMITS)) {
            assertThrows(PackLoadException.class, () -> source.read("assets/testpack/missing.json"));
        }
    }

    @Test
    void rejectsFileLargerThanEntryCap() throws IOException {
        Files.write(root.resolve("assets/testpack/big.bin"), new byte[64]);
        try (PackSource source = PackSource.directory(root, LIMITS)) {
            assertThrows(PackLoadException.class, () -> source.read("assets/testpack/big.bin"));
        }
    }

    @Test
    void rejectsNonexistentRootDirectory() {
        assertThrows(PackLoadException.class, () -> PackSource.directory(root.resolve("nope"), LIMITS));
    }

    @Test
    void rejectsSymlinkEscapingRoot() throws IOException {
        Path outside = Files.write(outsideDir.resolve("outside.txt"), "secret".getBytes());
        Path link = root.resolve("assets/testpack/link.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.assumeTrue(false, "symlinks not supported here");
        }
        try (PackSource source = PackSource.directory(root, LIMITS)) {
            assertThrows(PackLoadException.class, () -> source.read("assets/testpack/link.txt"));
        }
    }

    @Test
    void rejectsMalformedPath() throws IOException {
        try (PackSource source = PackSource.directory(root, LIMITS)) {
            assertThrows(PackLoadException.class, () -> source.read("assets/\0bad"));
            assertFalse(source.exists("assets/\0bad"));
        }
    }

    @Test
    void rejectsListingBeyondMaxEntries() throws IOException {
        PackLimits capped = new PackLimits(2, 32, 1024, 1024);
        Files.write(root.resolve("assets/testpack/extra1.json"), "{}".getBytes());
        Files.write(root.resolve("assets/testpack/extra2.json"), "{}".getBytes());
        try (PackSource source = PackSource.directory(root, capped)) {
            assertThrows(PackLoadException.class, () -> source.list("assets/"));
        }
    }

    @Test
    void listsUpToMaxEntries() throws IOException {
        PackLimits capped = new PackLimits(2, 32, 1024, 1024);
        Files.write(root.resolve("assets/testpack/extra1.json"), "{}".getBytes());
        try (PackSource source = PackSource.directory(root, capped)) {
            List<String> entries = source.list("assets/");
            assertTrue(entries.contains("assets/testpack/items/item/simple.json"));
            assertTrue(entries.contains("assets/testpack/extra1.json"));
        }
    }
}
