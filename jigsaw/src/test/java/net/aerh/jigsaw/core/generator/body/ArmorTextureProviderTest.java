package net.aerh.jigsaw.core.generator.body;

import net.aerh.jigsaw.core.resource.FolderResourcePack;
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

    private Path createPackWithArmor(String material) throws IOException {
        Path packDir = tempDir.resolve("test_pack");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("pack.mcmeta"),
                """
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "Test armor pack"
                  }
                }
                """);

        Path armorDir = packDir.resolve("assets/minecraft/textures/models/armor");
        Files.createDirectories(armorDir);

        // Write valid 64x32 PNG for layer_1
        BufferedImage layer1 = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                layer1.setRGB(x, y, 0xFFFF0000); // red
            }
        }
        ImageIO.write(layer1, "png", armorDir.resolve(material + "_layer_1.png").toFile());

        // Write valid 64x32 PNG for layer_2
        BufferedImage layer2 = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                layer2.setRGB(x, y, 0xFF0000FF); // blue
            }
        }
        ImageIO.write(layer2, "png", armorDir.resolve(material + "_layer_2.png").toFile());

        return packDir;
    }

    @Test
    void piece_helmet_usesLayer1() throws IOException {
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            Optional<ArmorPiece> piece = provider.piece(ArmorSlot.HELMET, "iron");

            assertThat(piece).isPresent();
            assertThat(piece.get().slot()).isEqualTo(ArmorSlot.HELMET);
            // layer_1 is red
            assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFFFF0000);
            assertThat(piece.get().color()).isEmpty();
        }
    }

    @Test
    void piece_chestplate_usesLayer1() throws IOException {
        Path packDir = createPackWithArmor("diamond");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            Optional<ArmorPiece> piece = provider.piece(ArmorSlot.CHESTPLATE, "diamond");

            assertThat(piece).isPresent();
            assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFFFF0000);
        }
    }

    @Test
    void piece_boots_usesLayer1() throws IOException {
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            Optional<ArmorPiece> piece = provider.piece(ArmorSlot.BOOTS, "iron");

            assertThat(piece).isPresent();
            assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFFFF0000);
        }
    }

    @Test
    void piece_leggings_usesLayer2() throws IOException {
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            Optional<ArmorPiece> piece = provider.piece(ArmorSlot.LEGGINGS, "iron");

            assertThat(piece).isPresent();
            // layer_2 is blue
            assertThat(piece.get().armorTexture().getRGB(0, 0)).isEqualTo(0xFF0000FF);
        }
    }

    @Test
    void piece_missingMaterial_returnsEmpty() throws IOException {
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            assertThat(provider.piece(ArmorSlot.HELMET, "netherite")).isEmpty();
        }
    }

    @Test
    void piece_withDyeColor_setsDyeColor() throws IOException {
        Path packDir = createPackWithArmor("leather");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            Optional<ArmorPiece> piece = provider.piece(ArmorSlot.CHESTPLATE, "leather", 0xFFA06540);

            assertThat(piece).isPresent();
            assertThat(piece.get().color()).contains(0xFFA06540);
        }
    }

    @Test
    void hasTexture_existingMaterial_returnsTrue() throws IOException {
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            assertThat(provider.hasTexture("iron", ArmorSlot.HELMET)).isTrue();
            assertThat(provider.hasTexture("iron", ArmorSlot.LEGGINGS)).isTrue();
        }
    }

    @Test
    void hasTexture_missingMaterial_returnsFalse() throws IOException {
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            assertThat(provider.hasTexture("gold", ArmorSlot.HELMET)).isFalse();
        }
    }

    @Test
    void loadTexture_cachesResults() throws IOException {
        Path packDir = createPackWithArmor("iron");
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
        Path packDir = createPackWithArmor("iron");
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
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            assertThatThrownBy(() -> provider.piece(null, "iron"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void piece_nullMaterial_throws() throws IOException {
        Path packDir = createPackWithArmor("iron");
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ArmorTextureProvider provider = new ArmorTextureProvider(pack);
            assertThatThrownBy(() -> provider.piece(ArmorSlot.HELMET, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
