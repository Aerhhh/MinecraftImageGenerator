package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayeredResourcePackTest {

    @TempDir
    Path tempDir;

    private Path createPack(String name, int format) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pack.mcmeta"),
                """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s"
                  }
                }
                """.formatted(format, name));
        return dir;
    }

    private void writeFile(Path packDir, String relPath, String content) throws IOException {
        Path file = packDir.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new LayeredResourcePack(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_rejectsEmpty() {
        assertThatThrownBy(() -> new LayeredResourcePack(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getResource_higherPriorityWins() throws IOException {
        Path customPack = createPack("custom", 46);
        Path vanillaPack = createPack("vanilla", 46);

        writeFile(customPack, "assets/minecraft/textures/item/diamond_sword.png", "custom_content");
        writeFile(vanillaPack, "assets/minecraft/textures/item/diamond_sword.png", "vanilla_content");

        try (FolderResourcePack custom = new FolderResourcePack(customPack);
             FolderResourcePack vanilla = new FolderResourcePack(vanillaPack);
             LayeredResourcePack layered = new LayeredResourcePack(List.of(custom, vanilla))) {

            Optional<InputStream> result = layered.getResource("assets/minecraft/textures/item/diamond_sword.png");
            assertThat(result).isPresent();
            String content = new String(result.get().readAllBytes());
            assertThat(content).isEqualTo("custom_content");
        }
    }

    @Test
    void getResource_fallsBackToLowerPriority() throws IOException {
        Path customPack = createPack("custom", 46);
        Path vanillaPack = createPack("vanilla", 46);

        // Only vanilla has this resource
        writeFile(vanillaPack, "assets/minecraft/models/item/diamond_sword.json", "model_json");

        try (FolderResourcePack custom = new FolderResourcePack(customPack);
             FolderResourcePack vanilla = new FolderResourcePack(vanillaPack);
             LayeredResourcePack layered = new LayeredResourcePack(List.of(custom, vanilla))) {

            Optional<InputStream> result = layered.getResource("assets/minecraft/models/item/diamond_sword.json");
            assertThat(result).isPresent();
            String content = new String(result.get().readAllBytes());
            assertThat(content).isEqualTo("model_json");
        }
    }

    @Test
    void getResource_returnsEmptyWhenNoPackHasResource() throws IOException {
        Path pack = createPack("empty", 46);

        try (FolderResourcePack p = new FolderResourcePack(pack);
             LayeredResourcePack layered = new LayeredResourcePack(List.of(p))) {

            assertThat(layered.getResource("nonexistent.txt")).isEmpty();
        }
    }

    @Test
    void hasResource_checksAllPacks() throws IOException {
        Path customPack = createPack("custom", 46);
        Path vanillaPack = createPack("vanilla", 46);

        writeFile(customPack, "assets/custom_file.txt", "x");
        writeFile(vanillaPack, "assets/vanilla_file.txt", "y");

        try (FolderResourcePack custom = new FolderResourcePack(customPack);
             FolderResourcePack vanilla = new FolderResourcePack(vanillaPack);
             LayeredResourcePack layered = new LayeredResourcePack(List.of(custom, vanilla))) {

            assertThat(layered.hasResource("assets/custom_file.txt")).isTrue();
            assertThat(layered.hasResource("assets/vanilla_file.txt")).isTrue();
            assertThat(layered.hasResource("assets/nonexistent.txt")).isFalse();
        }
    }

    @Test
    void listResources_returnsUnionOfAllPacks() throws IOException {
        Path customPack = createPack("custom", 46);
        Path vanillaPack = createPack("vanilla", 46);

        writeFile(customPack, "assets/minecraft/textures/item/custom_sword.png", "x");
        writeFile(vanillaPack, "assets/minecraft/textures/item/vanilla_sword.png", "y");
        writeFile(vanillaPack, "assets/minecraft/textures/item/diamond_sword.png", "z");

        try (FolderResourcePack custom = new FolderResourcePack(customPack);
             FolderResourcePack vanilla = new FolderResourcePack(vanillaPack);
             LayeredResourcePack layered = new LayeredResourcePack(List.of(custom, vanilla))) {

            Set<String> resources = layered.listResources("assets/minecraft/textures/item");
            assertThat(resources).containsExactlyInAnyOrder(
                    "assets/minecraft/textures/item/custom_sword.png",
                    "assets/minecraft/textures/item/vanilla_sword.png",
                    "assets/minecraft/textures/item/diamond_sword.png"
            );
        }
    }

    @Test
    void metadata_returnsHighestPriorityPackMetadata() throws IOException {
        Path customPack = createPack("custom", 46);
        Path vanillaPack = createPack("vanilla", 34);

        try (FolderResourcePack custom = new FolderResourcePack(customPack);
             FolderResourcePack vanilla = new FolderResourcePack(vanillaPack);
             LayeredResourcePack layered = new LayeredResourcePack(List.of(custom, vanilla))) {

            assertThat(layered.metadata().packFormat()).isEqualTo(46);
            assertThat(layered.metadata().description()).isEqualTo("custom");
        }
    }

    @Test
    void textureOnlyPack_modelsFromVanillaTexturesFromCustom() throws IOException {
        Path customPack = createPack("custom", 46);
        Path vanillaPack = createPack("vanilla", 46);

        // Vanilla has the model, custom only has the texture
        writeFile(vanillaPack, "assets/minecraft/models/item/diamond_sword.json",
                """
                {
                  "parent": "minecraft:item/generated",
                  "textures": { "layer0": "minecraft:item/diamond_sword" }
                }
                """);
        writeFile(customPack, "assets/minecraft/textures/item/diamond_sword.png", "custom_texture_data");
        writeFile(vanillaPack, "assets/minecraft/textures/item/diamond_sword.png", "vanilla_texture_data");

        try (FolderResourcePack custom = new FolderResourcePack(customPack);
             FolderResourcePack vanilla = new FolderResourcePack(vanillaPack);
             LayeredResourcePack layered = new LayeredResourcePack(List.of(custom, vanilla))) {

            // Model should resolve (from vanilla)
            ModelResolver resolver = new ModelResolver();
            Optional<ItemModelData> model = resolver.resolve(layered, "diamond_sword");
            assertThat(model).isPresent();
            assertThat(model.get().textures()).containsEntry("layer0", "minecraft:item/diamond_sword");

            // Texture should come from custom (higher priority)
            Optional<InputStream> texture = layered.getResource("assets/minecraft/textures/item/diamond_sword.png");
            assertThat(texture).isPresent();
            assertThat(new String(texture.get().readAllBytes())).isEqualTo("custom_texture_data");
        }
    }
}
