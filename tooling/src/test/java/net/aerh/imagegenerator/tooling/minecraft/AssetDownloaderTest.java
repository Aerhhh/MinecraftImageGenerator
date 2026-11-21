package net.aerh.imagegenerator.tooling.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client JAR is checked against Mojang's published SHA-1 before anything reads from it. */
class AssetDownloaderTest {

    /** SHA-1 of the ASCII bytes "abc". */
    private static final String SHA1_OF_ABC = "a9993e364706816aba3e25717850c26c9cd0d89d";

    @Test
    void matchingChecksumPasses(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("client.jar"), "abc".getBytes(StandardCharsets.US_ASCII));

        assertDoesNotThrow(() -> AssetDownloader.verifySha1(file, SHA1_OF_ABC));
        assertDoesNotThrow(() -> AssetDownloader.verifySha1(file, SHA1_OF_ABC.toUpperCase()));
        assertTrue(Files.exists(file));
    }

    @Test
    void mismatchedChecksumFailsAndDeletesTheFile(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("client.jar"), "abd".getBytes(StandardCharsets.US_ASCII));

        IOException thrown = assertThrows(IOException.class, () -> AssetDownloader.verifySha1(file, SHA1_OF_ABC));
        assertTrue(thrown.getMessage().contains("checksum"));
        assertFalse(Files.exists(file), "a JAR that fails its checksum must not stay in the cache");
    }
}
