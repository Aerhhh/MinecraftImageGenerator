package net.aerh.jigsaw.core.generator.body;

import net.aerh.jigsaw.core.resource.FolderResourcePack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmorTextureProviderTest {

    @TempDir
    Path tempDir;

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "pack_format": 34,
                "description": "Test armor pack"
              }
            }
            """;

    private static void writeTestPng(Path file, int color) throws IOException {
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                image.setRGB(x, y, color);
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }

    // -------------------------------------------------------------------------
    // Modern format (1.21.4+)
    // -------------------------------------------------------------------------

    @Nested
    class ModernFormat {

        private Path createModernPack(String material) throws IOException {
            Path packDir = tempDir.resolve("modern_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            // Equipment JSON
            Path equipDir = packDir.resolve("assets/minecraft/equipment");
            Files.createDirectories(equipDir);
            Files.writeString(equipDir.resolve(material + ".json"), """
                    {
                      "layers": {
                        "humanoid": [{ "texture": "minecraft:%s" }],
                        "humanoid_leggings": [{ "texture": "minecraft:%s" }]
                      }
                    }
                    """.formatted(material, material));

            // Texture PNGs
            writeTestPng(packDir.resolve("assets/minecraft/textures/entity/equipment/humanoid/" + material + ".png"),
                    0xFFFF0000); // red for humanoid (layer 1)
            writeTestPng(packDir.resolve("assets/minecraft/textures/entity/equipment/humanoid_leggings/" + material + ".png"),
                    0xFF0000FF); // blue for leggings (layer 2)

            return packDir;
        }

        @Test
        void helmet_loadsFromHumanoidTexture() throws IOException {
            Path packDir = createModernPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.HELMET, "iron");

                assertThat(piece).isPresent();
                assertThat(piece.get().slot()).isEqualTo(ArmorSlot.HELMET);
                assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFFFF0000);
                assertThat(piece.get().color()).isEmpty();
            }
        }

        @Test
        void chestplate_loadsFromHumanoidTexture() throws IOException {
            Path packDir = createModernPack("diamond");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.CHESTPLATE, "diamond");

                assertThat(piece).isPresent();
                assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFFFF0000);
            }
        }

        @Test
        void boots_loadsFromHumanoidTexture() throws IOException {
            Path packDir = createModernPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.BOOTS, "iron");

                assertThat(piece).isPresent();
                assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFFFF0000);
            }
        }

        @Test
        void leggings_loadsFromHumanoidLeggingsTexture() throws IOException {
            Path packDir = createModernPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.LEGGINGS, "iron");

                assertThat(piece).isPresent();
                assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFF0000FF);
            }
        }

        @Test
        void withDyeColor_prebakesTintWhenOverlayExists() throws IOException {
            // When a dyeable material has an overlay (leather_overlay on classpath),
            // the dye color is pre-baked into the texture and ArmorPiece.color() is empty
            Path packDir = createModernPack("leather");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.CHESTPLATE, "leather", 0xFFA06540);

                assertThat(piece).isPresent();
                // Color is pre-baked - no runtime tint needed
                assertThat(piece.get().color()).isEmpty();
            }
        }

        @Test
        void withDyeColor_preservesColorForNonDyeableMaterial() throws IOException {
            // Non-dyeable materials keep the dye color on ArmorPiece for runtime tinting
            Path packDir = createModernPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.CHESTPLATE, "iron", 0xFFA06540);

                assertThat(piece).isPresent();
                assertThat(piece.get().color()).contains(0xFFA06540);
            }
        }

        @Test
        void textureNameDiffersFromMaterial() throws IOException {
            // Equipment JSON references a different texture name than the material
            Path packDir = tempDir.resolve("remap_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            Path equipDir = packDir.resolve("assets/minecraft/equipment");
            Files.createDirectories(equipDir);
            Files.writeString(equipDir.resolve("custom_mat.json"), """
                    {
                      "layers": {
                        "humanoid": [{ "texture": "minecraft:special_texture" }]
                      }
                    }
                    """);

            writeTestPng(packDir.resolve("assets/minecraft/textures/entity/equipment/humanoid/special_texture.png"),
                    0xFF00FF00); // green

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.HELMET, "custom_mat");

                assertThat(piece).isPresent();
                assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFF00FF00);
            }
        }

        @Test
        void modernFallback_noEquipmentJson_loadsDirectly() throws IOException {
            // Pack has textures at modern paths but no equipment JSON
            Path packDir = tempDir.resolve("nojson_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            writeTestPng(packDir.resolve("assets/minecraft/textures/entity/equipment/humanoid/iron.png"),
                    0xFFFF0000);
            writeTestPng(packDir.resolve("assets/minecraft/textures/entity/equipment/humanoid_leggings/iron.png"),
                    0xFF0000FF);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);

                assertThat(provider.piece(ArmorSlot.HELMET, "iron")).isPresent();
                assertThat(provider.piece(ArmorSlot.LEGGINGS, "iron")).isPresent();
            }
        }

        @Test
        void hasTexture_modernFormat_returnsTrue() throws IOException {
            Path packDir = createModernPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThat(provider.hasTexture("iron", ArmorSlot.HELMET)).isTrue();
                assertThat(provider.hasTexture("iron", ArmorSlot.LEGGINGS)).isTrue();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Legacy format (pre-1.21.4)
    // -------------------------------------------------------------------------

    @Nested
    class LegacyFormat {

        private Path createLegacyPack(String material) throws IOException {
            Path packDir = tempDir.resolve("legacy_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            writeTestPng(packDir.resolve("assets/minecraft/textures/models/armor/" + material + "_layer_1.png"),
                    0xFFFF0000); // red
            writeTestPng(packDir.resolve("assets/minecraft/textures/models/armor/" + material + "_layer_2.png"),
                    0xFF0000FF); // blue

            return packDir;
        }

        @Test
        void helmet_loadsFromLayer1() throws IOException {
            Path packDir = createLegacyPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.HELMET, "iron");

                assertThat(piece).isPresent();
                assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFFFF0000);
            }
        }

        @Test
        void leggings_loadsFromLayer2() throws IOException {
            Path packDir = createLegacyPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<ArmorPiece> piece = provider.piece(ArmorSlot.LEGGINGS, "iron");

                assertThat(piece).isPresent();
                assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFF0000FF);
            }
        }

        @Test
        void hasTexture_legacyFormat_returnsTrue() throws IOException {
            Path packDir = createLegacyPack("iron");
            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThat(provider.hasTexture("iron", ArmorSlot.HELMET)).isTrue();
                assertThat(provider.hasTexture("iron", ArmorSlot.LEGGINGS)).isTrue();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Missing textures and edge cases
    // -------------------------------------------------------------------------

    @Nested
    class EdgeCases {

        @Test
        void missingMaterial_returnsEmpty() throws IOException {
            Path packDir = tempDir.resolve("empty_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThat(provider.piece(ArmorSlot.HELMET, "nonexistent_fantasy_material")).isEmpty();
            }
        }

        @Test
        void hasTexture_missingMaterial_returnsFalse() throws IOException {
            Path packDir = tempDir.resolve("empty_pack2");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThat(provider.hasTexture("nonexistent_fantasy_material", ArmorSlot.HELMET)).isFalse();
            }
        }

        @Test
        void withDefaults_loadsVanillaIronTexture() {
            ArmorTextureProvider provider = ArmorTextureProvider.withDefaults();
            Optional<ArmorPiece> piece = provider.piece(ArmorSlot.HELMET, "iron");

            assertThat(piece).isPresent();
            assertThat(piece.get().slot()).isEqualTo(ArmorSlot.HELMET);
            assertThat(piece.get().armorTexture().getWidth()).isEqualTo(64);
            assertThat(piece.get().armorTexture().getHeight()).isEqualTo(32);
        }

        @Test
        void withDefaults_loadsLeggingsFromClasspath() {
            ArmorTextureProvider provider = ArmorTextureProvider.withDefaults();
            Optional<ArmorPiece> piece = provider.piece(ArmorSlot.LEGGINGS, "diamond");

            assertThat(piece).isPresent();
            assertThat(piece.get().slot()).isEqualTo(ArmorSlot.LEGGINGS);
        }

        @Test
        void withDefaults_missingMaterial_returnsEmpty() {
            ArmorTextureProvider provider = ArmorTextureProvider.withDefaults();
            assertThat(provider.piece(ArmorSlot.HELMET, "nonexistent_fantasy_material")).isEmpty();
        }

        @Test
        void loadTexture_cachesResults() throws IOException {
            Path packDir = tempDir.resolve("cache_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);
            writeTestPng(packDir.resolve("assets/minecraft/textures/entity/equipment/humanoid/iron.png"),
                    0xFFFF0000);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                Optional<BufferedImage> first = provider.loadTexture("iron", 1);
                Optional<BufferedImage> second = provider.loadTexture("iron", 1);

                assertThat(first).isPresent();
                assertThat(second).isPresent();
                assertThat(first.get()).isSameAs(second.get());
            }
        }

        @Test
        void loadTexture_invalidLayer_throws() throws IOException {
            Path packDir = tempDir.resolve("layer_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThatThrownBy(() -> provider.loadTexture("iron", 3))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("layer must be 1 or 2");
            }
        }

        @Test
        void constructor_nullResourcePack_throws() {
            assertThatThrownBy(() -> new ArmorTextureProvider(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void piece_nullSlot_throws() throws IOException {
            Path packDir = tempDir.resolve("null_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThatThrownBy(() -> provider.piece(null, "iron"))
                        .isInstanceOf(NullPointerException.class);
            }
        }

        @Test
        void piece_nullMaterial_throws() throws IOException {
            Path packDir = tempDir.resolve("null_mat_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThatThrownBy(() -> provider.piece(ArmorSlot.HELMET, null))
                        .isInstanceOf(NullPointerException.class);
            }
        }

        @Test
        void equipmentJson_noHumanoidLayer_returnsEmpty() throws IOException {
            // Equipment JSON exists but has no humanoid layer (like an item that only has horse_body)
            Path packDir = tempDir.resolve("nolayer_pack");
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve("pack.mcmeta"), PACK_MCMETA);

            Path equipDir = packDir.resolve("assets/minecraft/equipment");
            Files.createDirectories(equipDir);
            Files.writeString(equipDir.resolve("horse_only.json"), """
                    {
                      "layers": {
                        "horse_body": [{ "texture": "minecraft:horse_only" }]
                      }
                    }
                    """);

            try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
                ArmorTextureProvider provider = new ArmorTextureProvider(pack);
                assertThat(provider.piece(ArmorSlot.HELMET, "horse_only")).isEmpty();
            }
        }
    }
}
