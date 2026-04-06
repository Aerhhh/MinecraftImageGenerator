package net.aerh.jigsaw.core.resource;

import net.aerh.jigsaw.api.sprite.SpriteProvider;
import net.aerh.jigsaw.core.sprite.AtlasSpriteProvider;
import net.aerh.jigsaw.core.sprite.ChainedSpriteProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcePackIntegrationTest {

    @TempDir
    static Path tempDir;
    static Path packDir;

    @BeforeAll
    static void setUp() throws IOException {
        packDir = tempDir.resolve("custom_pack");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("pack.mcmeta"),
                """
                {"pack":{"pack_format":34,"description":"Custom"}}
                """);

        // Create a custom item model and texture
        Path modelDir = packDir.resolve("assets/minecraft/models/item");
        Files.createDirectories(modelDir);
        Files.writeString(modelDir.resolve("custom_item.json"), """
                {"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/custom_item"}}
                """);
        Files.writeString(modelDir.resolve("generated.json"), """
                {"parent":"builtin/generated"}
                """);

        Path texDir = packDir.resolve("assets/minecraft/textures/item");
        Files.createDirectories(texDir);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        ImageIO.write(img, "png", texDir.resolve("custom_item.png").toFile());
    }

    @Test
    void chainedProvider_customPackOverridesVanilla() throws IOException {
        try (FolderResourcePack customPack = new FolderResourcePack(packDir)) {
            SpriteProvider custom = new ResourcePackSpriteProvider(customPack);
            SpriteProvider vanilla = AtlasSpriteProvider.fromDefaults();
            SpriteProvider chained = new ChainedSpriteProvider(List.of(custom, vanilla));

            // Custom item should come from our pack
            assertThat(chained.getSprite("custom_item")).isPresent();

            // Vanilla items should still work via fallback
            assertThat(chained.getSprite("diamond_sword")).isPresent();

            // Available sprites should include both
            assertThat(chained.availableSprites()).contains("custom_item");
        }
    }

    @Test
    void resourcePackProvider_standalone() throws IOException {
        try (FolderResourcePack pack = new FolderResourcePack(packDir)) {
            ResourcePackSpriteProvider provider = new ResourcePackSpriteProvider(pack);

            assertThat(provider.getSprite("custom_item")).isPresent();
            BufferedImage sprite = provider.getSprite("custom_item").get();
            // Should be our magenta test texture
            Color pixel = new Color(sprite.getRGB(8, 8), true);
            assertThat(pixel).isEqualTo(Color.MAGENTA);
        }
    }
}
