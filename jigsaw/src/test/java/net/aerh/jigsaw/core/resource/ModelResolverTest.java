package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

    // =========================================================================
    // Pre-1.21.4 model resolution (models/item)
    // =========================================================================

    @Nested
    class PreModernModelResolution {

        @Test
        void resolve_simpleItem_returnsLayer0() throws IOException {
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
                assertThat(result.get().parentType()).isEqualTo("item/generated");
            }
        }

        @Test
        void resolve_multiLayer_mergesTextures() throws IOException {
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

        @Test
        void resolve_missingModel_returnsEmpty() throws IOException {
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "nonexistent_item");

                assertThat(result).isEmpty();
            }
        }
    }

    // =========================================================================
    // Terminal parent handling
    // =========================================================================

    @Nested
    class TerminalParents {

        @Test
        void itemGenerated_isTreatedAsTerminal() throws IOException {
            // item/generated should be terminal - no generated.json file needed
            writeModel("assets/minecraft/models/item/test_item.json",
                    """
                    {
                      "parent": "item/generated",
                      "textures": { "layer0": "item/test" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "test_item");

                assertThat(result).isPresent();
                assertThat(result.get().parentType()).isEqualTo("item/generated");
                assertThat(result.get().textures()).containsEntry("layer0", "item/test");
            }
        }

        @Test
        void minecraftItemGenerated_isTreatedAsTerminal() throws IOException {
            writeModel("assets/minecraft/models/item/test_item.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "item/test" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "test_item");

                assertThat(result).isPresent();
                assertThat(result.get().parentType()).isEqualTo("item/generated");
            }
        }

        @Test
        void handheldParent_resolvesWithoutGeneratedJson() throws IOException {
            // Simulates real vanilla chain: diamond_sword -> item/handheld -> item/generated
            // item/generated is terminal, so handheld.json must exist but generated.json does not
            writeModel("assets/minecraft/models/item/handheld.json",
                    """
                    {
                      "parent": "item/generated"
                    }
                    """);
            writeModel("assets/minecraft/models/item/diamond_sword.json",
                    """
                    {
                      "parent": "minecraft:item/handheld",
                      "textures": { "layer0": "minecraft:item/diamond_sword" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "diamond_sword");

                assertThat(result).isPresent();
                assertThat(result.get().parentType()).isEqualTo("item/generated");
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/diamond_sword");
            }
        }
    }

    // =========================================================================
    // 1.21.4+ item definition resolution
    // =========================================================================

    @Nested
    class ItemDefinitionResolution {

        @Test
        void resolve_simpleModelDefinition() throws IOException {
            // 1.21.4+ item definition pointing to a model
            writeModel("assets/minecraft/items/diamond_sword.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "minecraft:item/diamond_sword"
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/item/diamond_sword.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/diamond_sword" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "diamond_sword");

                assertThat(result).isPresent();
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/diamond_sword");
            }
        }

        @Test
        void resolve_blockItemDefinition_pointingToBlockModel() throws IOException {
            // Block items in 1.21.4+ point directly to block models, no models/item file
            writeModel("assets/minecraft/items/stone.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "minecraft:block/stone"
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/block/cube_all.json",
                    """
                    {
                      "parent": "builtin/generated",
                      "textures": { "all": "minecraft:block/stone" }
                    }
                    """);
            writeModel("assets/minecraft/models/block/stone.json",
                    """
                    {
                      "parent": "minecraft:block/cube_all",
                      "textures": { "all": "minecraft:block/stone" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "stone");

                assertThat(result).isPresent();
                assertThat(result.get().textures()).containsEntry("all", "minecraft:block/stone");
            }
        }

        @Test
        void resolve_conditionDefinition_usesOnFalse() throws IOException {
            // Condition types like bows use on_false for the default (idle) state
            writeModel("assets/minecraft/items/bow.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:condition",
                        "property": "minecraft:using_item",
                        "on_false": {
                          "type": "minecraft:model",
                          "model": "minecraft:item/bow"
                        },
                        "on_true": {
                          "type": "minecraft:model",
                          "model": "minecraft:item/bow_pulling_0"
                        }
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/item/bow.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/bow" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "bow");

                assertThat(result).isPresent();
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/bow");
            }
        }

        @Test
        void resolve_selectDefinition_usesFallback() throws IOException {
            writeModel("assets/minecraft/items/crossbow.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:select",
                        "property": "minecraft:charge_type",
                        "cases": [
                          {
                            "when": "rocket",
                            "model": {
                              "type": "minecraft:model",
                              "model": "minecraft:item/crossbow_rocket"
                            }
                          }
                        ],
                        "fallback": {
                          "type": "minecraft:model",
                          "model": "minecraft:item/crossbow"
                        }
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/item/crossbow.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/crossbow_standby" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "crossbow");

                assertThat(result).isPresent();
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/crossbow_standby");
            }
        }

        @Test
        void resolve_rangeDispatchDefinition_usesFallback() throws IOException {
            writeModel("assets/minecraft/items/durability_item.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:range_dispatch",
                        "property": "minecraft:damage",
                        "entries": [
                          {
                            "threshold": 0.5,
                            "model": {
                              "type": "minecraft:model",
                              "model": "minecraft:item/durability_half"
                            }
                          }
                        ],
                        "fallback": {
                          "type": "minecraft:model",
                          "model": "minecraft:item/durability_full"
                        }
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/item/durability_full.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/durability_full" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "durability_item");

                assertThat(result).isPresent();
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/durability_full");
            }
        }

        @Test
        void resolve_compositeDefinition_usesFirstModel() throws IOException {
            writeModel("assets/minecraft/items/composite_item.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:composite",
                        "models": [
                          {
                            "type": "minecraft:model",
                            "model": "minecraft:item/composite_base"
                          },
                          {
                            "type": "minecraft:model",
                            "model": "minecraft:item/composite_overlay"
                          }
                        ]
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/item/composite_base.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/composite_base" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "composite_item");

                assertThat(result).isPresent();
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/composite_base");
            }
        }

        @Test
        void resolve_specialDefinition_usesBaseModel() throws IOException {
            writeModel("assets/minecraft/items/shield.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:special",
                        "model": {
                          "type": "minecraft:shield"
                        },
                        "base": "minecraft:item/shield_base"
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/item/shield_base.json",
                    """
                    {
                      "parent": "builtin/entity"
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "shield");

                assertThat(result).isPresent();
                assertThat(result.get().parentType()).isEqualTo("builtin/entity");
            }
        }

        @Test
        void resolve_itemDefinitionTakesPriorityOverModelsItem() throws IOException {
            // If both items/ and models/item/ exist, items/ wins
            writeModel("assets/minecraft/items/diamond_sword.json",
                    """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "minecraft:item/diamond_sword_new"
                      }
                    }
                    """);
            writeModel("assets/minecraft/models/item/diamond_sword.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/diamond_sword_old" }
                    }
                    """);
            writeModel("assets/minecraft/models/item/diamond_sword_new.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/diamond_sword_new" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "diamond_sword");

                assertThat(result).isPresent();
                // Should resolve via the items/ definition, not models/item/ directly
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/diamond_sword_new");
            }
        }

        @Test
        void resolve_fallsBackToModelsItemWhenNoDefinition() throws IOException {
            // No items/ file, should fall back to models/item/
            writeModel("assets/minecraft/models/item/diamond_sword.json",
                    """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "minecraft:item/diamond_sword" }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                Optional<ItemModelData> result = resolver.resolve(pack, "diamond_sword");

                assertThat(result).isPresent();
                assertThat(result.get().textures()).containsEntry("layer0", "minecraft:item/diamond_sword");
            }
        }
    }
}
