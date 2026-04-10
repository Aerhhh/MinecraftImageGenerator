package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderResourcePackTest {

    @TempDir
    Path tempDir;

    private Path createMinimalPack() throws IOException {
        Path packDir = tempDir.resolve("test_pack");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("pack.mcmeta"),
                """
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "Test pack"
                  }
                }
                """);
        return packDir;
    }

    @Test
    void metadata_parsesPackMcmeta() throws IOException {
        Path packDir = createMinimalPack();
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            assertThat(pack.metadata().packFormat()).isEqualTo(34);
            assertThat(pack.metadata().description()).isEqualTo("Test pack");
        }
    }

    @Test
    void constructor_missingPackMcmeta_throws() {
        Path emptyDir = tempDir.resolve("empty");
        assertThatThrownBy(() -> new FolderResourcePack(emptyDir))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getResource_existingFile_returnsStream() throws IOException {
        Path packDir = createMinimalPack();
        Path texDir = packDir.resolve("assets/minecraft/textures/item");
        Files.createDirectories(texDir);
        Files.writeString(texDir.resolve("test.png"), "fake png data");

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Optional<InputStream> stream = pack.getResource("assets/minecraft/textures/item/test.png");
            assertThat(stream).isPresent();
            stream.get().close();
        }
    }

    @Test
    void getResource_missingFile_returnsEmpty() throws IOException {
        Path packDir = createMinimalPack();
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            assertThat(pack.getResource("nonexistent.txt")).isEmpty();
        }
    }

    @Test
    void hasResource_existingFile_returnsTrue() throws IOException {
        Path packDir = createMinimalPack();
        Files.writeString(packDir.resolve("test.txt"), "hello");

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            assertThat(pack.hasResource("test.txt")).isTrue();
            assertThat(pack.hasResource("missing.txt")).isFalse();
        }
    }

    @Test
    void listResources_returnsMatchingPaths() throws IOException {
        Path packDir = createMinimalPack();
        Path modelsDir = packDir.resolve("assets/minecraft/models/item");
        Files.createDirectories(modelsDir);
        Files.writeString(modelsDir.resolve("sword.json"), "{}");
        Files.writeString(modelsDir.resolve("pickaxe.json"), "{}");

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Set<String> resources = pack.listResources("assets/minecraft/models/item/");
            assertThat(resources).containsExactlyInAnyOrder(
                    "assets/minecraft/models/item/sword.json",
                    "assets/minecraft/models/item/pickaxe.json"
            );
        }
    }
}
