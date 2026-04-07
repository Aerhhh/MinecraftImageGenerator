package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcePackSpriteProviderTest {

    @TempDir
    static Path tempDir;

    private static Path packDir;

    // -------------------------------------------------------------------------
    // Pack setup helpers
    // -------------------------------------------------------------------------

    @BeforeAll
    static void setUpPack() throws IOException {
        packDir = tempDir.resolve("test_pack");
        Files.createDirectories(packDir);

        writeString(packDir.resolve("pack.mcmeta"),
                """
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "Test pack"
                  }
                }
                """);

        // --- sword: single-layer item, layer0 -> solid red 16x16 PNG ---
        writePng(packDir, "assets/minecraft/textures/item/test_sword.png",
                solidColor(16, 16, Color.RED));

        writeModel(packDir, "item/test_sword",
                """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "minecraft:item/test_sword"
                  }
                }
                """);

        // --- layered item: layer0 solid blue 16x16, layer1 green 4x4 center transparent ---
        writePng(packDir, "assets/minecraft/textures/item/test_base.png",
                solidColor(16, 16, Color.BLUE));

        writePng(packDir, "assets/minecraft/textures/item/test_overlay.png",
                greenCenterOverlay(16, 16));

        writeModel(packDir, "item/test_layered",
                """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "minecraft:item/test_base",
                    "layer1": "minecraft:item/test_overlay"
                  }
                }
                """);
    }

    // -------------------------------------------------------------------------
    // Test 1: single-layer item returns the texture as-is
    // -------------------------------------------------------------------------

    @Test
    void getSprite_singleLayer_returnsTexture() throws IOException {
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ResourcePackSpriteProvider provider = new ResourcePackSpriteProvider(pack);

            Optional<BufferedImage> result = provider.getSprite("test_sword");

            assertThat(result).isPresent();
            BufferedImage img = result.get();
            assertThat(img.getWidth()).isEqualTo(16);
            assertThat(img.getHeight()).isEqualTo(16);

            // Center pixel should be pure red (in ARGB, alpha may be 0xFF or varying depending on PNG type)
            int centerRgb = img.getRGB(8, 8);
            assertThat(centerRgb & 0x00FF0000).isEqualTo(0x00FF0000); // red channel set
            assertThat(centerRgb & 0x0000FF00).isEqualTo(0x00000000); // green channel zero
            assertThat(centerRgb & 0x000000FF).isEqualTo(0x00000000); // blue channel zero
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: multi-layer item composites layers correctly
    // -------------------------------------------------------------------------

    @Test
    void getSprite_multiLayer_compositesLayers() throws IOException {
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ResourcePackSpriteProvider provider = new ResourcePackSpriteProvider(pack);

            Optional<BufferedImage> result = provider.getSprite("test_layered");

            assertThat(result).isPresent();
            BufferedImage img = result.get();

            // Corner pixel (0,0) has no overlay - should be pure blue
            int cornerRgb = img.getRGB(0, 0);
            assertThat(cornerRgb & 0x000000FF).isEqualTo(0x000000FF); // blue channel max
            assertThat(cornerRgb & 0x0000FF00).isEqualTo(0x00000000); // green channel zero

            // Center pixel (8,8) lies within the green center square - green should dominate
            int centerRgb = img.getRGB(8, 8);
            int greenChannel = (centerRgb >> 8) & 0xFF;
            int blueChannel = centerRgb & 0xFF;
            assertThat(greenChannel).isGreaterThan(0);    // green blended in
            assertThat(blueChannel).isGreaterThan(0);     // blue still visible underneath
            assertThat(greenChannel).isGreaterThan(blueChannel); // green dominates at center
        }
    }

    // -------------------------------------------------------------------------
    // Test 3: missing item returns empty
    // -------------------------------------------------------------------------

    @Test
    void getSprite_missingItem_returnsEmpty() throws IOException {
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ResourcePackSpriteProvider provider = new ResourcePackSpriteProvider(pack);

            Optional<BufferedImage> result = provider.getSprite("nonexistent_item_xyz");

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: availableSprites lists item model IDs (excluding "generated")
    // -------------------------------------------------------------------------

    @Test
    void availableSprites_listsItemModels() throws IOException {
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ResourcePackSpriteProvider provider = new ResourcePackSpriteProvider(pack);

            Collection<String> sprites = provider.availableSprites();

            assertThat(sprites).contains("test_sword", "test_layered");
            assertThat(sprites).doesNotContain("generated");
        }
    }

    // -------------------------------------------------------------------------
    // Image creation helpers
    // -------------------------------------------------------------------------

    private static BufferedImage solidColor(int width, int height, Color color) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Creates a 16x16 ARGB image that is fully transparent except for a green center square
     * from (4,4) to (11,11) with alpha 200 (nearly opaque).
     */
    private static BufferedImage greenCenterOverlay(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0, 255, 0, 200));
            g.fillRect(4, 4, 8, 8);
        } finally {
            g.dispose();
        }
        return img;
    }

    // -------------------------------------------------------------------------
    // File write helpers
    // -------------------------------------------------------------------------

    private static void writeString(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static void writeModel(Path packRoot, String modelRelPath, String json) throws IOException {
        Path modelPath = packRoot.resolve("assets/minecraft/models/" + modelRelPath + ".json");
        Files.createDirectories(modelPath.getParent());
        Files.writeString(modelPath, json);
    }

    private static void writePng(Path packRoot, String relPath, BufferedImage img) throws IOException {
        Path file = packRoot.resolve(relPath);
        Files.createDirectories(file.getParent());
        ImageIO.write(img, "png", file.toFile());
    }
}
