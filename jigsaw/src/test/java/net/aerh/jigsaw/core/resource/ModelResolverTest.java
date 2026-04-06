package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ModelResolverTest {

    @TempDir
    Path tempDir;

    private Path packDir;
    private ModelResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        packDir = tempDir.resolve("test_pack");
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
        resolver = new ModelResolver();
    }

    private void writeModel(String relPath, String json) throws IOException {
        Path file = packDir.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    // -------------------------------------------------------------------------
    // Test 1: simple item with item/generated parent resolves layer0 texture
    // -------------------------------------------------------------------------

    @Test
    void resolve_simpleItem_returnsLayer0() throws IOException {
        writeModel("assets/minecraft/models/item/generated.json",
                """
                {
                  "parent": "builtin/generated"
                }
                """);
        writeModel("assets/minecraft/models/item/diamond_sword.json",
                """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "minecraft:item/diamond_sword"
                  }
                }
                """);

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Optional<ItemModelData> result = resolver.resolve(pack, "diamond_sword");

            assertThat(result).isPresent();
            assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/diamond_sword");
            assertThat(result.get().parentType()).isEqualTo("builtin/generated");
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: item with multiple layers - both textures are present
    // -------------------------------------------------------------------------

    @Test
    void resolve_multiLayer_mergesTextures() throws IOException {
        writeModel("assets/minecraft/models/item/generated.json",
                """
                {
                  "parent": "builtin/generated"
                }
                """);
        writeModel("assets/minecraft/models/item/bow.json",
                """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "minecraft:item/bow",
                    "layer1": "minecraft:item/bow_pulling_0"
                  }
                }
                """);

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Optional<ItemModelData> result = resolver.resolve(pack, "bow");

            assertThat(result).isPresent();
            assertThat(result.get().textures())
                    .containsEntry("layer0", "minecraft:item/bow")
                    .containsEntry("layer1", "minecraft:item/bow_pulling_0");
        }
    }

    // -------------------------------------------------------------------------
    // Test 3: child texture overrides parent texture for the same key
    // -------------------------------------------------------------------------

    @Test
    void resolve_childOverridesParentTexture() throws IOException {
        writeModel("assets/minecraft/models/item/base.json",
                """
                {
                  "parent": "builtin/generated",
                  "textures": {
                    "layer0": "minecraft:item/default_texture"
                  }
                }
                """);
        writeModel("assets/minecraft/models/item/custom_item.json",
                """
                {
                  "parent": "minecraft:item/base",
                  "textures": {
                    "layer0": "minecraft:item/custom_texture"
                  }
                }
                """);

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Optional<ItemModelData> result = resolver.resolve(pack, "custom_item");

            assertThat(result).isPresent();
            assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/custom_texture");
            assertThat(result.get().textures()).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: block item follows into block model directory
    // -------------------------------------------------------------------------

    @Test
    void resolve_blockItem_followsIntoBlockModel() throws IOException {
        writeModel("assets/minecraft/models/block/cube_all.json",
                """
                {
                  "parent": "builtin/entity",
                  "textures": {
                    "all": "minecraft:block/stone"
                  }
                }
                """);
        writeModel("assets/minecraft/models/block/stone.json",
                """
                {
                  "parent": "minecraft:block/cube_all"
                }
                """);
        writeModel("assets/minecraft/models/item/stone.json",
                """
                {
                  "parent": "minecraft:block/stone"
                }
                """);

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Optional<ItemModelData> result = resolver.resolve(pack, "stone");

            assertThat(result).isPresent();
            assertThat(result.get().textures()).containsEntry("all", "minecraft:block/stone");
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: circular references return empty
    // -------------------------------------------------------------------------

    @Test
    void resolve_circularReference_returnsEmpty() throws IOException {
        writeModel("assets/minecraft/models/item/model_a.json",
                """
                {
                  "parent": "minecraft:item/model_b"
                }
                """);
        writeModel("assets/minecraft/models/item/model_b.json",
                """
                {
                  "parent": "minecraft:item/model_a"
                }
                """);

        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Optional<ItemModelData> result = resolver.resolve(pack, "model_a");

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Test 6: missing model returns empty
    // -------------------------------------------------------------------------

    @Test
    void resolve_missingModel_returnsEmpty() throws IOException {
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            Optional<ItemModelData> result = resolver.resolve(pack, "nonexistent_item");

            assertThat(result).isEmpty();
        }
    }
}
