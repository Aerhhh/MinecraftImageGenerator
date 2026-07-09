package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipPackSourceTest {

    private static final PackLimits LIMITS = new PackLimits(3, 32, 1024, 1024);

    @TempDir
    Path dir;

    private Path writeZip(Map<String, byte[]> entries) throws IOException {
        Path zip = dir.resolve("pack.zip");
        try (OutputStream fileOut = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(fileOut)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void readsEntriesAndLists() throws IOException {
        Path zip = writeZip(Map.of("assets/testpack/items/item/simple.json", "{}".getBytes()));
        try (PackSource source = PackSource.zip(zip, LIMITS)) {
            assertArrayEquals("{}".getBytes(), source.read("assets/testpack/items/item/simple.json"));
            assertTrue(source.exists("assets/testpack/items/item/simple.json"));
            assertTrue(source.list("assets/").contains("assets/testpack/items/item/simple.json"));
        }
    }

    @Test
    void rejectsZipSlipEntryNames() throws IOException {
        Path zip = writeZip(Map.of("../evil.json", "{}".getBytes()));
        assertThrows(PackLoadException.class, () -> PackSource.zip(zip, LIMITS));
    }

    @Test
    void rejectsAbsoluteEntryNames() throws IOException {
        Path zip = writeZip(Map.of("/abs.json", "{}".getBytes()));
        assertThrows(PackLoadException.class, () -> PackSource.zip(zip, LIMITS));
    }

    @Test
    void rejectsTooManyEntries() throws IOException {
        Path zip = writeZip(Map.of("a", new byte[1], "b", new byte[1], "c", new byte[1], "d", new byte[1]));
        assertThrows(PackLoadException.class, () -> PackSource.zip(zip, LIMITS));
    }

    @Test
    void rejectsDirectoryPaddedZipExceedingEntryCap() throws IOException {
        Path zip = writeZip(Map.of(
            "a", new byte[1],
            "d1/", new byte[0], "d2/", new byte[0], "d3/", new byte[0], "d4/", new byte[0]));
        assertThrows(PackLoadException.class, () -> PackSource.zip(zip, LIMITS));
    }

    @Test
    void acceptsZipWithExactlyMaxEntries() throws IOException {
        Path zip = writeZip(Map.of("a", new byte[1], "b", new byte[1], "c", new byte[1]));
        try (PackSource source = PackSource.zip(zip, LIMITS)) {
            assertEquals(List.of("a", "b", "c"), source.list(""));
        }
    }

    @Test
    void rejectsEntryExceedingByteCapOnRead() throws IOException {
        Path zip = writeZip(Map.of("big.bin", new byte[64]));
        try (PackSource source = PackSource.zip(zip, LIMITS)) {
            assertThrows(PackLoadException.class, () -> source.read("big.bin"));
        }
    }

    @Test
    void rejectsMissingZipFile() {
        assertThrows(PackLoadException.class, () -> PackSource.zip(dir.resolve("nope.zip"), LIMITS));
    }

    @Test
    void rejectsMissingEntryOnRead() throws IOException {
        Path zip = writeZip(Map.of("present.json", "{}".getBytes()));
        try (PackSource source = PackSource.zip(zip, LIMITS)) {
            assertThrows(PackLoadException.class, () -> source.read("absent.json"));
        }
    }
}
