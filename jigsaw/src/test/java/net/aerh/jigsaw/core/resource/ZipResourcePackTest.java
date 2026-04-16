package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipResourcePackTest {

    @TempDir
    Path tempDir;

    private Path createMinimalZipPack() throws IOException {
        Path zipPath = tempDir.resolve("test_pack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("""
                    {
                      "pack": {
                        "pack_format": 34,
                        "description": "Zip test pack"
                      }
                    }
                    """.getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("assets/minecraft/textures/item/test.png"));
            zos.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("assets/minecraft/models/item/sword.json"));
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        return zipPath;
    }

    @Test
    void metadata_parsesPackMcmeta() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThat(pack.metadata().packFormat()).isEqualTo(34);
            assertThat(pack.metadata().description()).isEqualTo("Zip test pack");
        }
    }

    @Test
    void constructor_missingPackMcmeta_throws() throws IOException {
        Path zipPath = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("dummy.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }
        assertThatThrownBy(() -> new ZipResourcePack(zipPath))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getResource_existingEntry_returnsStream() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            Optional<InputStream> stream = pack.getResource("assets/minecraft/textures/item/test.png");
            assertThat(stream).isPresent();
            stream.get().close();
        }
    }

    @Test
    void getResource_missingEntry_returnsEmpty() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThat(pack.getResource("nonexistent.txt")).isEmpty();
        }
    }

    @Test
    void hasResource_existingEntry_returnsTrue() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            assertThat(pack.hasResource("pack.mcmeta")).isTrue();
            assertThat(pack.hasResource("missing.txt")).isFalse();
        }
    }

    @Test
    void listResources_returnsMatchingPaths() throws IOException {
        Path zipPath = createMinimalZipPack();
        try (ZipResourcePack pack = new ZipResourcePack(zipPath)) {
            Set<String> resources = pack.listResources("assets/minecraft/models/item/");
            assertThat(resources).containsExactly("assets/minecraft/models/item/sword.json");
        }
    }
}
