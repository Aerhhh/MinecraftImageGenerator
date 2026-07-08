package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
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
}
